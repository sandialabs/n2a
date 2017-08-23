/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class ImportJob
{
    LinkedList<File> sources   = new LinkedList<File> ();
    MNode            models    = new MVolatile ();
    String           modelName = "";
    List<MNode>      resolve   = new ArrayList<MNode> ();  // Nodes which contain a $inherit that refers to a locally defined part rather than a standard part. The local part must either be copied in or converted into a global model.

    Pattern floatParser   = Pattern.compile ("[-+]?(NaN|Infinity|([0-9]*\\.?[0-9]*([eE][-+]?[0-9]+)?))");
    Pattern forbiddenUCUM = Pattern.compile ("[.,;><=!&|+\\-*/%\\^~]");
    static final double epsilon = Math.ulp (1);

    // Note: Utility functions and helper classes are at the end of this class.
    // These include generic functions to extract data from elements, such as their text or attributes.

    public void process (File source)
    {
        sources.push (source);
        try
        {
            // Open and parse XML document
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance ();
            factory.setCoalescing (true);
            factory.setIgnoringComments (true);
            factory.setIgnoringElementContentWhitespace (true);
            factory.setXIncludeAware (true);  // Doesn't seem to actually include other files, at least on the samples I've tried so far. Must be missing something.
            DocumentBuilder builder = factory.newDocumentBuilder ();
            Document doc = builder.parse (source);

            // Extract models
            //dump (System.out, doc, "");
            process (doc);
        }
        catch (IOException e)
        {
        }
        catch (ParserConfigurationException e)
        {
        }
        catch (SAXException e)
        {
        }
        sources.pop ();
    }

    public void process (Node node)
    {
        if (node.getNodeType () != Node.ELEMENT_NODE)  // For all other node types, recurse into structure, looking for ELEMENT nodes.
        {
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ()) process (child);
            return;
        }

        // Triage the element type
        switch (node.getNodeName ())
        {
            case "neuroml": neuroml (node); break;
        }
    }

    public void neuroml (Node node)
    {
        if (modelName.isEmpty ())
        {
            modelName = getAttribute (node, "id");
            if (modelName.isEmpty ()) modelName = "New Model";
        }

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            switch (child.getNodeName ())
            {
                case "include":
                    File nextSource = new File (sources.getLast ().getParentFile (), getAttribute (child, "href"));
                    process (nextSource);
                    break;
                case "cell":
                    cell (child);
                    break;
                case "network":
                    network (child);
                    break;
                default:
                    genericPart (child, models.childOrCreate (modelName));  // Assume that any top-level element not captured above is an abstract cell type.
                    break;
            }
        }
    }

    /**
        Perform data manipulations that must wait until all nodes are read.
    **/
    public void postprocess ()
    {
        for (MNode r : resolve)
        {
            String partName = r.get ("$inherit").replace ("\"", "");
            MNode part = models.child (modelName, partName);
            System.out.println ("processing:" + r.key () + " " + partName);

            // Triage
            int count = part.getInt ("$count");
            if (count > 0)  // triage is necessary
            {
                if (count == 1)
                {
                    count = -1;  // part has only one user, so it should simply be deleted after merging
                }
                else  // count > 1, so part could be moved out to its own model
                {
                    // Criterion: If a part has subparts, then it is heavy-weight and should be moved out.
                    // A part that merely sets some parameters on an inherited model is light-weight, and should simply be merged everywhere it is used.
                    boolean heavy = false;
                    for (MNode s : part)
                    {
                        if (MPart.isPart (s))
                        {
                            heavy = true;
                            break;
                        }
                    }
                    if (heavy) count = -2;  // part should be made into an independent model
                    else       count = -1;
                }
                part.set ("$count", count);
            }
            System.out.println ("  count=" + count);
            if (count == -1)
            {
                System.out.println ("  merge");
                r.merge (part);
                r.clear ("$count");
            }
            else
            {
                System.out.println ("  inherit");
                r.set ("$inherit", modelName + " " + partName);
            }
        }

        // Move heavy-weight parts into separate models
        Iterator<MNode> it = models.child (modelName).iterator ();
        while (it.hasNext ())
        {
            MNode part = it.next ();
            int count = part.getInt ("$count");
            if (count < 0)
            {
                if (count == -2)
                {
                    part.clear ("$count");
                    String partName = part.key ();
                    MNode model = models.childOrCreate (modelName + " " + partName);
                    model.merge (part);
                }
                it.remove ();
            }
        }

        // Move network items up to top level
        MNode network = models.child (modelName, "$network");
        if (network != null)
        {
            for (MNode p : network)
            {
                MNode dest = models.childOrCreate (modelName, p.key ());
                dest.merge (p);
            }
        }
        models.clear (modelName, "$network");
    }

    public void cell (Node node)
    {
        String id   = getAttribute (node, "id", "MISSING_ID");  // MISSING_ID indicates an ill-formed nml file.
        MNode  cell = models.childOrCreate (modelName, id);
        cell.set ("$inherit", "cell");

        Map<Integer,Segment> segments = new HashMap<Integer,Segment> ();
        MatrixBoolean        G        = new MatrixBoolean ();

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            switch (child.getNodeName ())
            {
                case "notes"                : cell.set ("$metadata", "notes", getText (child)); break;
                case "morphology"           : morphology (child, cell, segments, G);            break;
                case "biophysicalProperties": biophysicalProperties (child, cell, G);           break;
            }
        }


        // Finish parts

        for (Entry<Integer,Segment> e : segments.entrySet ())
        {
            Segment s = e.getValue ();
            if (s.parentID >= 0) s.parent = segments.get (s.parentID);
        }
        for (Entry<Integer,Segment> e : segments.entrySet ()) e.getValue ().resolveProximal ();

        MNode properties = cell.child ("$properties");
        if (properties != null)
        {
            for (MNode p : properties)
            {
                String groupName = p.get ();
                if (groupName.startsWith ("[")  &&  ! groupName.equals ("[all]"))
                {
                    // Determine better name for segment-specific part.
                    int length = groupName.length ();
                    int r = Integer.parseInt (groupName.substring (1, length-1));
                    int currentColumn = cell.getInt ("$group", groupName, "$G");
                    int bestColumn = currentColumn;
                    int bestCount  = 1;
                    for (int c = 0; c < G.columns (); c++)
                    {
                        if (c == currentColumn) continue;
                        if (! G.get (r, c)) continue;

                        int count = G.columnNorm0 (c);
                        if (count > bestCount)
                        {
                            bestColumn = c;
                            bestCount  = count;
                        }
                    }
                    String newName = "segment_" + r;
                    if (bestColumn != currentColumn) newName = cell.get ("$groupIndex", bestColumn) + "_" + r;
                    cell.child ("$group").move (groupName, newName);
                    cell.set ("$groupIndex", currentColumn, newName);
                    p.set (newName);
                    groupName = newName;
                }

                // Distribute properties to original segment groups
                if (groupName.equals ("[all]"))
                {
                    for (MNode part : cell.child ("$group"))
                    {
                        part.merge (p);
                        part.set ("");
                    }
                }
                else
                {
                    MNode part = cell.child ("$group", groupName);
                    part.merge (p);
                    part.set ("");
                }
            }
        }


        // Create subparts for all combinations of parameters.
        // Each subpart will hold a set of segments that is mutually exclusive of the other sets.
        // Ideally, the sets exactly match the segment groups, but in general there may be more sets.
        // If a set exactly matches an original segment group, it gets that group's name.
        // Then, the set that has the largest overlap with an unclaimed segment group, without exceeding it, gets its name.
        // All remaining sets get a name formed from a concatenation of each segment it overlaps with.

        MatrixBoolean O = new MatrixBoolean ();
        MatrixBoolean M = new MatrixBoolean ();
        G.foldRows (M, O);

        // Scan for exact matches
        int columnsG = G.columns ();
        int columnsM = M.columns ();
        Set<Integer> newIndices = new HashSet<Integer>  (columnsM);
        for (int i = 0; i < columnsM; i++)
        {
            MatrixBoolean A = M.column (i);
            boolean found = false;
            for (int j = 0; j < columnsG; j++)
            {
                if (A.equals (G.column (j)))
                {
                    found = true;
                    String name = cell.get ("$groupIndex", j);  // maps index to group name
                    cell.set (name, "$M", i);
                    break;
                }
            }
            if (! found) newIndices.add (i);
        }

        // Scan for largest overlaps
        Iterator<Integer> it = newIndices.iterator ();
        while (it.hasNext ())
        {
            int i = it.next ();
            boolean[] columnA = M.data.get (i);
            int bestCount = 0;
            int bestIndex = -1;
            for (int j = 0; j < columnsG; j++)
            {
                int count = 0;
                boolean subset = true;
                boolean[] columnG = G.data.get (j);
                int rows = Math.min (columnA.length, columnG.length);
                int r = 0;
                for (; r < rows; r++)
                {
                    if (columnA[r])
                    {
                        if (columnG[r])
                        {
                            count++;
                        }
                        else
                        {
                            subset = false;
                            break;
                        }
                    }
                }
                for (; r < columnA.length  &&  subset; r++) if (columnA[r]) subset = false;
                if (subset  &&  count > bestCount)
                {
                    bestCount = count;
                    bestIndex = j;
                }
            }
            if (bestCount > 0)
            {
                it.remove ();

                String name = cell.get ("$groupIndex", bestIndex);
                cell.set (name, "$M", i);
            }
        }

        // Build concatenated parts
        for (int i : newIndices)
        {
            String name = "";
            for (int j = 0; j < columnsG; j++)
            {
                if (O.get (i, j))
                {
                    String groupName = cell.get ("$groupIndex", j);
                    if (! name.isEmpty ()) name = name + "_";
                    name = name + groupName;
                }
            }
            cell.set (name, "$M", i);
        }

        // Add segments and properties to the parts
        for (MNode part : cell)
        {
            int c = part.getOrDefaultInt ("$M", "-1");  // column of M, the mapping from segments to new groups
            if (c < 0) continue;

            // Merge original groups into new part
            for (int i = 0; i < columnsG; i++)
            {
                if (O.get (c, i))  // rows of O are new parts; columns are original segment groups
                {
                    String groupName = cell.get ("$groupIndex", i);
                    part.merge (cell.child ("$group", groupName));
                }
            }
            part.clear ("$G");
            part.clear ("$M");
            for (MNode property : part)
            {
                // Any subparts of a membrane property are likely ion channels, so need to tag any top-level definitions they use
                for (MNode m : property)
                {
                    String inherit = m.get ("$inherit").replace ("\"", "");
                    if (inherit.isEmpty ()) continue;
                    // check for standard part
                    MNode model = AppData.models.child (inherit);
                    if (model == null  ||  model.child ("$metadata", "backend.neuroml.part") == null) addDependency (m, inherit);
                }
            }

            // Add segments
            int count = M.columnNorm0 (c);
            part.set ("$n", count);
            int index = 0;
            for (int r = 0; r < M.rows (); r++)
            {
                if (! M.get (r, c)) continue;
                Segment s = segments.get (r);
                if (count > 1) s.output (part, index++);
                else           s.output (part, -1);
            }
        }

        // Create connections to complete the cables
        for (Entry<Integer,Segment> e : segments.entrySet ())
        {
            Segment s = e.getValue ();
            if (s.parent == null) continue;
            int n = s.part.getInt ("$n");
            String connectionName = s.part.key () + "_to_" + s.parent.part.key ();
            MNode connection = cell.child (connectionName);
            if (connection == null)
            {
                connection = cell.set (connectionName, "");
                connection.set ("$inherit", "\"Coupling Voltage\"");

                String parentName = "parent";
                int suffix = 2;
                while (s.part.child (parentName) != null) parentName = "parent" + suffix++;
                connection.set ("$parent", parentName);  // temporary memo

                connection.set ("A", s.part.key ());
                connection.set ("B", s.parent.part.key ());
                connection.set ("R", "$kill");  // Force use of container's value.
                connection.set ("$p", "B.$index==A." + parentName);
                if (n > 1) s.part.set (parentName, "@", "-1");
            }

            String parentName = connection.get ("$parent");
            if (n == 1) s.part.set (parentName, s.parent.index);
            else        s.part.set (parentName, "@" + s.index, s.parent.index);
        }

        // Clean up temporary nodes.
        for (MNode part : cell) part.clear ("$parent");
        cell.clear ("$properties");
        cell.clear ("$group");
        cell.clear ("$groupIndex");
    }

    public void network (Node node)
    {
        MNode network = models.childOrCreate (modelName, "$network");
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            switch (child.getNodeName ())
            {
                case "population":
                    MNode part = genericPart (child, network);
                    int    n         = part.getInt ("size");
                    String component = part.get    ("component").trim ();  // Should always be defined.
                    part.clear ("size");
                    part.clear ("component");
                    if (n > 1) part.set ("$n", n);
                    part.set ("$inherit", "\"" + component + "\"");
                    addDependency (part, component);
                    break;
                case "continuousProjection":
                    part = genericPart (child, network);
                    part.move ("presynapticPopulation",  "A");
                    part.move ("postsynapticPopulation", "B");
                    break;
                case "explicitInput":
                    explicitInput (child);
                    break;
            }
        }
    }

    public MNode genericPart (Node node, MNode container)
    {
        String idField = "id";
        if (node.getNodeName ().contains ("Rate")) idField = "type";  // TODO: test more carefully for various nodes
        String id = getAttribute (node, idField);

        MNode part = container.set (id, "");
        part.set ("$inherit", "\"" + node.getNodeName ().trim () + "\"");

        NamedNodeMap attributes = node.getAttributes ();
        int count = attributes.getLength ();
        for (int i = 0; i < count; i++)
        {
            Node a = attributes.item (i);
            if (a.getNodeName ().equals (idField)) continue;
            part.set (a.getNodeName (), biophysicalUnits (a.getNodeValue ()));  // biophysicalUnits() will only modify text if there is a numeric value
        }

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () == Node.ELEMENT_NODE) genericPart (child, part);
        }

        return part;
    }

    public void morphology (Node node, MNode cell, Map<Integer,Segment> segments, MatrixBoolean G)
    {
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            switch (child.getNodeName ())
            {
                case "segment":
                    Segment s = new Segment (child);
                    segments.put (s.id, s);
                    break;
                case "segmentGroup":
                    segmentGroup (child, cell, G);
                    break;
            }
        }
    }

    public void segmentGroup (Node node, MNode cell, MatrixBoolean G)
    {
        int c = G.columns ();
        String groupName = getAttribute (node, "id");
        MNode part = cell.childOrCreate ("$group", groupName);
        part.set ("$G", c);
        cell.set ("$groupIndex", c, groupName);

        NamedNodeMap attributes = node.getAttributes ();
        int count = attributes.getLength ();
        for (int i = 0; i < count; i++)
        {
            Node a = attributes.item (i);
            if (a.getNodeName ().equals ("id")) continue;
            part.set ("$metadata", a.getNodeName (), a.getNodeValue ());
        }

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            if (child.getNodeName ().equals ("member"))
            {
                String segment = getAttribute (child, "segment");
                if (segment.isEmpty ()) continue;
                G.set (Integer.parseInt (segment), c);
            }
        }
    }

    public void biophysicalProperties (Node node, MNode cell, MatrixBoolean G)
    {
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            switch (child.getNodeName ())
            {
                case "membraneProperties":
                    biophysicalProperties (child, cell, G);
                    break;
                case "channelPopulation":
                case "channelDensity":
                    MNode property = allocateProperty (child, cell, G);
                    property.set ("$inherit", "\"" + child.getNodeName () + "\"");
                    String ionChannel = property.get ("ionChannel").trim ();
                    property.clear ("ionChannel");
                    property.set ("ionChannel", "$inherit", "\"" + ionChannel + "\"");
                    break;
                case "specificCapacitance":
                    property = allocateProperty (child, cell, G);
                    property.set ("C", biophysicalUnits (getAttribute (child, "value")));
                    break;
                case "intracellularProperties":
                    biophysicalProperties (child, cell, G);
                    break;
                case "resistivity":
                    cell.set ("R", biophysicalUnits (getAttribute (child, "value")));
                    break;
            }
        }
    }

    public MNode allocateProperty (Node node, MNode cell, MatrixBoolean G)
    {
        MNode  properties = cell.childOrCreate ("$properties");
        String id         = getAttribute (node, "id");
        String group      = getAttribute (node, "segmentGroup");
        if (group.isEmpty ())
        {
            group = getAttribute (node, "segment");
            if (group.isEmpty ())
            {
                group = "[all]";
            }
            else
            {
                int r = Integer.parseInt (group);
                group = "[" + group + "]";  // proper name will be assigned later, during post-processing
                if (cell.child ("$group", group) == null)
                {
                    int c = G.columns ();
                    G.set (r, c);
                    cell.set ("$group", group, "$G", c);
                    cell.set ("$groupIndex", c, group);
                }
            }
        }
        MNode result = properties.set (String.valueOf (properties.length ()), group);
        if (id.isEmpty ()) return result;

        // Create a subpart with the given name
        result = result.set (id, "");  
        NamedNodeMap attributes = node.getAttributes ();
        int count = attributes.getLength ();
        for (int i = 0; i < count; i++)
        {
            Node a = attributes.item (i);
            if (a.getNodeName ().equals ("id")) continue;
            if (a.getNodeName ().equals ("segment")) continue;
            if (a.getNodeName ().equals ("segmentGroup")) continue;
            result.set (a.getNodeName (), biophysicalUnits (a.getNodeValue ()));  // biophysicalUnits() will only modify text if there is a numeric value
        }
        return result;
    }

    public void addDependency (MNode part, String inherit)
    {
        resolve.add (part);
        MNode component = models.childOrCreate (modelName, inherit);
        int count = component.getInt ("$count");
        component.set ("$count", count + 1);
    }

    public void explicitInput (Node node)
    {
        String input  = getAttribute (node, "input");
        String target = getAttribute (node, "target");
        int index = 0;
        int i = target.indexOf ('[');
        if (i >= 0)
        {
            String suffix = target.substring (i + 1);
            target = target.substring (0, i);
            i = suffix.indexOf (']');
            if (i >= 0) suffix = suffix.substring (0, i);
            index = Integer.valueOf (suffix);
        }
        // TODO: get documentation to figure out the intent of this element
    }

    /**
        Convert the given value to be in appropriate units, in the context of a morphology section.
    **/
    public String morphologyUnits (String value)
    {
        value = value.trim ();
        int unitIndex = findUnits (value);
        if (unitIndex == 0) return value;  // no number
        if (unitIndex >= value.length ()) return value + "um";  // default morphology units are micometers

        String units = value.substring (unitIndex).trim ();
        value        = value.substring (0, unitIndex);

        return value + cleanupUnits (units);
    }

    public int findUnits (String value)
    {
        Matcher m = floatParser.matcher (value);
        m.find ();
        return m.end ();
    }

    public String cleanupUnits (String units)
    {
        units = units.replace ("_per_", "/");
        units = units.replace ("per_",  "/");
        units = units.replace ("_",     ".");
        units = units.replace (" ",     ".");
        units = units.replace ("ohm",   "Ohm");
        units = units.replace ("KOhm",  "kOhm");
        if (forbiddenUCUM.matcher (units).find ()) return "(" + units + ")";
        return units;
    }

    /**
        Convert the given value to be in appropriate units, in the context of a physiology section.
    **/
    public String biophysicalUnits (String value)
    {
        value = value.trim ();
        int unitIndex = findUnits (value);
        if (unitIndex == 0) return value;  // no number
        if (unitIndex >= value.length ()) return value;  // no units; need to apply defaults here

        String units = value.substring (unitIndex).trim ();
        value        = value.substring (0, unitIndex);

        return value + cleanupUnits (units);
    }

    public String getText (Node node)
    {
        String result = "";
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () == Node.TEXT_NODE) result = result + child.getNodeValue ();
        }
        return result;
    }

    public String getAttribute (Node node, String name)
    {
        return getAttribute (node, name, "");
    }

    public String getAttribute (Node node, String name, String defaultValue)
    {
        Node attribute = node.getAttributes ().getNamedItem (name);
        if (attribute == null) return defaultValue;
        return attribute.getNodeValue ();
    }

    public int getAttribute (Node node, String name, int defaultValue)
    {
        try
        {
            return Integer.parseInt (getAttribute (node, name, String.valueOf (defaultValue)));
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    public double getAttribute (Node node, String name, double defaultValue)
    {
        try
        {
            return Double.parseDouble (getAttribute (node, name, String.valueOf (defaultValue)));
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    public class Segment
    {
        int     id;
        int     parentID         = -1;
        Segment parent;

        MNode   part;   // compartment of which this segment is an instance
        int     index;  // within part

        double  fractionAlong    = 1;
        Matrix  proximal;
        Matrix  distal;
        double  proximalDiameter = -1;
        double  distalDiameter   = -1;

        public Segment (Node node)
        {
            id = Integer.parseInt (getAttribute (node, "id", "0"));
            for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
            {
                if (child.getNodeType () != Node.ELEMENT_NODE) continue;
                switch (child.getNodeName ())
                {
                    case "parent":
                        parentID      = getAttribute (child, "segment",       -1);
                        fractionAlong = getAttribute (child, "fractionAlong", 1.0);
                        break;
                    case "proximal":
                        proximal = new Matrix (3, 1);
                        proximal.set   (0, Matrix.convert (morphologyUnits (getAttribute (child, "x"))));
                        proximal.set   (1, Matrix.convert (morphologyUnits (getAttribute (child, "y"))));
                        proximal.set   (2, Matrix.convert (morphologyUnits (getAttribute (child, "z"))));
                        proximalDiameter = Matrix.convert (morphologyUnits (getAttribute (child, "diameter")));
                        break;
                    case "distal":
                        distal = new Matrix (3, 1);
                        distal.set   (0, Matrix.convert (morphologyUnits (getAttribute (child, "x"))));
                        distal.set   (1, Matrix.convert (morphologyUnits (getAttribute (child, "y"))));
                        distal.set   (2, Matrix.convert (morphologyUnits (getAttribute (child, "z"))));
                        distalDiameter = Matrix.convert (morphologyUnits (getAttribute (child, "diameter")));
                        break;
                }
            }
        }

        /**
            Requires that parent be resolved already.
        **/
        public void resolveProximal ()
        {
            if (proximal != null  ||  parent == null) return;
            if (fractionAlong == 1)
            {
                proximal = parent.distal;
                proximalDiameter = parent.distalDiameter;
            }
            else
            {
                parent.resolveProximal ();
                Matrix A = parent.proximal;
                Matrix B = parent.distal;
                proximal = (Matrix) B.subtract (A).multiply (new Scalar (fractionAlong)).add (A);

                double a = parent.proximalDiameter;
                double b = parent.distalDiameter;
                proximalDiameter = (b - a) * fractionAlong + a;
            }
        }

        public void output (MNode part, int index)
        {
            this.part = part;
            if (index < 0)  // only one instance, so make values unconditional
            {
                this.index = 0;
                if (distal           != null) part.set ("$xyz",      format (distal));
                if (proximal         != null) part.set ("xyz0",      format (proximal));
                if (distalDiameter   >= 0   ) part.set ("diameter",  format (distalDiameter));
                if (proximalDiameter >= 0   ) part.set ("diameter0", format (proximalDiameter));
            }
            else  // multiple instances
            {
                this.index = index;
                if (distal           != null) part.set ("$xyz",      "@$index==" + index, format (distal));
                if (proximal         != null) part.set ("xyz0",      "@$index==" + index, format (proximal));
                if (distalDiameter   >= 0   ) part.set ("diameter",  "@$index==" + index, format (distalDiameter));
                if (proximalDiameter >= 0   ) part.set ("diameter0", "@$index==" + index, format (proximalDiameter));
            }
        }

        public String format (double a)
        {
            a *= 1e6;
            double i = Math.round (a);
            if (Math.abs (i - a) < epsilon)  // This is an integer
            {
                if (i == 0) return "0";
                return ((long) i) + "um";
            }
            return a + "um";
        }

        public String format (Matrix A)
        {
            return "[" + format (A.getDouble (0)) + ";" + format (A.getDouble (1)) + ";" + format (A.getDouble (2)) + "]";
        }
    }
}

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
    LinkedList<File>          sources     = new LinkedList<File> ();
    MNode                     models      = new MVolatile ();
    String                    modelName   = "";
    List<MNode>               resolve     = new ArrayList<MNode> ();  // Nodes which contain a $inherit that refers to a locally defined part rather than a standard part. The local part must either be copied in or converted into a global model.
    Map<String,MatrixBoolean> cellSegment = new HashMap<String,MatrixBoolean> ();  // Map from cell IDs to their associated segment matrix. The matrix itself maps from segment index to group index.

    Pattern floatParser   = Pattern.compile ("[-+]?(NaN|Infinity|([0-9]*\\.?[0-9]*([eE][-+]?[0-9]+)?))");
    Pattern forbiddenUCUM = Pattern.compile ("[.,;><=!&|+\\-*/%\\^~]");
    static final double epsilon = Math.ulp (1);

    // Note: Utility functions are at the end of this class. These include generic functions to extract data from elements, such as their text or attributes.

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
            case "morphml":
            case "channelml":
            case "networkml":
            case "neuroml":
                neuroml (node);
                break;
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
                case "ionChannel":
                case "ionChannelHH":
                case "ionChannelKS":
                    ionChannel (child);
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

    public void addDependency (MNode part, String inherit)
    {
        resolve.add (part);
        MNode component = models.childOrCreate (modelName, inherit);
        int count = component.getInt ("$count");
        component.set ("$count", count + 1);
    }

    /**
        Perform data manipulations that must wait until all nodes are read.
    **/
    public void postprocess ()
    {
        for (MNode r : resolve)
        {
            String partName = r.get ("$inherit").replace ("\"", "");
            if (partName.isEmpty ()) partName = r.get ();  // For connections, the part name might be a direct value.
            MNode part = models.child (modelName, partName);

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
            if (count == -1)
            {
                r.merge (part);
                r.clear ("$count");
            }
            else
            {
                r.set ("$inherit", "\"" + modelName + " " + partName + "\"");
            }
        }

        // Move heavy-weight parts into separate models
        // Also remove remaining temporary keys
        Iterator<MNode> it = models.child (modelName).iterator ();
        while (it.hasNext ())
        {
            MNode part = it.next ();
            part.clear ("$groupIndex");
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
                p.clear ("$instance");
                MNode dest = models.childOrCreate (modelName, p.key ());
                dest.merge (p);
            }
        }
        models.clear (modelName, "$network");
    }

    public void ionChannel (Node node)
    {
        String id      = getAttribute (node, "id", "MISSING_ID");
        String type    = getAttribute (node, "type");
        String species = getAttribute (node, "species");
        String inherit;
        if (type.isEmpty ()) inherit = node.getNodeName ();
        else                 inherit = type;
        MNode part = models.childOrCreate (modelName, id);  // Expect to always create this part rather than fetch an existing child.
        part.set ("$inherit", "\"" + inherit + "\"");
        if (! species.isEmpty ()) part.set ("$metadata", "species", species);

        NamedNodeMap attributes = node.getAttributes ();
        int count = attributes.getLength ();
        for (int i = 0; i < count; i++)
        {
            Node a = attributes.item (i);
            String name = a.getNodeName ();
            if (name.equals ("id")) continue;
            if (name.equals ("type")) continue;
            if (name.equals ("species")) continue;
            part.set (name, biophysicalUnits (a.getNodeValue ()));  // biophysicalUnits() will only modify text if there is a numeric value
        }

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            String nodeName = child.getNodeName ();
            if (nodeName.startsWith ("gate")) gate (child, part);
            else genericPart (child, part);
        }
    }

    public void gate (Node node, MNode container)
    {
        String id   = getAttribute (node, "id", "MISSING_ID");
        String type = getAttribute (node, "type");
        String inherit;
        if (type.isEmpty ()) inherit = node.getNodeName ();
        else                 inherit = type;
        MNode part = container.set (id, "");
        part.set ("$inherit", "\"" + inherit + "\"");

        NamedNodeMap attributes = node.getAttributes ();
        int count = attributes.getLength ();
        for (int i = 0; i < count; i++)
        {
            Node a = attributes.item (i);
            String name = a.getNodeName ();
            if (name.equals ("id")) continue;
            if (name.equals ("type")) continue;
            part.set (name, biophysicalUnits (a.getNodeValue ()));  // biophysicalUnits() will only modify text if there is a numeric value
        }

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            switch (child.getNodeName ())
            {
                case "forwardRate":
                case "reverseRate":
                    rate (child, part);
                    break;
            }
        }
    }

    public void rate (Node node, MNode container)
    {
        String type = getAttribute (node, "type", "MISSING_TYPE");
        MNode part = container.set (node.getNodeName (), "");
        part.set ("$inherit", "\"" + type + "\"");

        NamedNodeMap attributes = node.getAttributes ();
        int count = attributes.getLength ();
        for (int i = 0; i < count; i++)
        {
            Node a = attributes.item (i);
            String name = a.getNodeName ();
            if (name.equals ("type")) continue;
            part.set (name, biophysicalUnits (a.getNodeValue ()));  // biophysicalUnits() will only modify text if there is a numeric value
        }

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () == Node.ELEMENT_NODE) genericPart (child, part);
        }
    }

    public void cell (Node node)
    {
        String id   = getAttribute (node, "id", "MISSING_ID");  // MISSING_ID indicates an ill-formed nml file.
        MNode  cell = models.childOrCreate (modelName, id);
        cell.set ("$inherit", "\"cell\"");

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
        cellSegment.put (id, M);

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
        cell.clear ("$properties");
        cell.clear ("$group");
        cell.clear ("$groupIndex");
        for (MNode part : cell)
        {
            part.clear ("$parent");
            part.clear ("$G");
            int c = part.getOrDefaultInt ("$M", "-1");
            if (c >= 0) cell.set ("$groupIndex", c, part.key ());
            part.clear ("$M");
        }
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
            String name = a.getNodeName ();
            if (name.equals ("id")) continue;
            part.set ("$metadata", name, a.getNodeValue ());
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
            String name = child.getNodeName ();
            if      (name.startsWith ("membrane")) membraneProperties      (child, cell, G);
            else if (name.startsWith ("extra"   )) extracellularProperties (child, cell);
            else if (name.startsWith ("intra"   )) intracellularProperties (child, cell);
        }
    }

    public void membraneProperties (Node node, MNode cell, MatrixBoolean G)
    {
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;

            MNode property = allocateProperty (child, cell, G);
            String name = child.getNodeName ();
            if (name.startsWith ("channel"))
            {
                property.set ("$inherit", "\"" + name + "\"");

                String ionChannel = property.get ("ionChannel");
                property.clear ("ionChannel");
                property.set ("ionChannel", "$inherit", "\"" + ionChannel + "\"");

                String ion = property.get ("ion");
                property.clear ("ion");
                property.set ("$metadata", "ion", ion);
            }
            else
            {
                switch (name)
                {
                    case "spikeThresh":
                        property.set ("Vpeak", biophysicalUnits (getAttribute (child, "value")));
                        break;
                    case "specificCapacitance":
                        property.set ("C", biophysicalUnits (getAttribute (child, "value")));
                        break;
                    case "initMembPotential":
                        property.set ("V", "@$init", biophysicalUnits (getAttribute (child, "value")));
                        break;
                }
            }
        }
    }

    public void intracellularProperties (Node node, MNode cell)
    {
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            String name = child.getNodeName ();

            switch (name)
            {
                case "species":
                    break;
                case "resistivity":
                    cell.set ("R", biophysicalUnits (getAttribute (child, "value")));
                    break;
            }
        }
    }

    public void extracellularProperties (Node node, MNode cell)
    {
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            String name = child.getNodeName ();

            switch (name)
            {
                case "species":
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
            String name = a.getNodeName ();
            if (name.equals ("id")) continue;
            if (name.equals ("segment")) continue;
            if (name.equals ("segmentGroup")) continue;
            if (name.equals ("value")) continue;  // Caller will extract this directly from XML node.
            result.set (name, biophysicalUnits (a.getNodeValue ()));  // biophysicalUnits() will only modify text if there is a numeric value
        }
        return result;
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
        units = units.replace ("degC",  "Cel");
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

    public void network (Node node)
    {
        MNode network = models.childOrCreate (modelName, "$network");
        String temperature = getAttribute (node, "temperature");
        if (! temperature.isEmpty ()) network.set ("temperature", biophysicalUnits (temperature));

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            switch (child.getNodeName ())
            {
                case "space":
                    space (child, network);
                    break;
                case "region":
                    String spaceID = getAttribute (child, "space");
                    network.set ("$region", child.getNodeValue (), spaceID);  // Region is little more than an alias of a space, as of NeuroML 2 beta 4.
                    break;
                case "extracellularProperties":
                    // TODO
                    break;
                case "population":
                    population (child, network);
                    break;
                case "projection":
                case "continuousProjection":
                case "electricalProjection":
                    projection (child, network);
                    break;
                case "explicitInput":
                    explicitInput (child, network);
                    break;
            }
        }

        network.clear ("$space");
        network.clear ("$region");
    }

    public void space (Node node, MNode network)
    {
        String id = getAttribute (node, "id");

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            // The standard as written allows more than one structure element, but not sure how to make sense of that.
            // This code simply overwrites the data if more than one such element exists.
            if (child.getNodeType () == Node.ELEMENT_NODE  &&  child.getNodeName ().equals ("structure"))
            {
                double sx = getAttribute (child, "xSpacing", 1.0);
                double sy = getAttribute (child, "ySpacing", 1.0);
                double sz = getAttribute (child, "zSpacing", 1.0);
                double ox = getAttribute (child, "xStart",   0.0);
                double oy = getAttribute (child, "yStart",   0.0);
                double oz = getAttribute (child, "zStart",   0.0);
                MNode p = network.childOrCreate ("$space", id);
                p.set ("scale",  "[" + sx + ";" + sy + ";" + sz + "]");
                p.set ("offset", "[" + ox + ";" + oy + ";" + oz + "]");
            }
        }
    }

    public void population (Node node, MNode network)
    {
        String id        = getAttribute (node, "id");
        int    n         = getAttribute (node, "size", 0);
        String component = getAttribute (node, "component").trim ();  // Should always be defined.
        String exID      = getAttribute (node, "extracellularProperties").trim ();

        MNode part = network.set (id, "");
        part.set ("$inherit", "\"" + component + "\"");
        addDependency (part, component);
        if (n > 1) part.set ("$n", n);
        if (! exID.isEmpty ())
        {
            MNode ex = part.childOrCreate ("extracellularProperties");
            ex.set ("$inherit", "\"" + exID + "\"");
            addDependency (ex, exID);
        }

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            switch (child.getNodeName ())
            {
                case "layout"  : populationLayout   (child, network, part); break;
                case "instance": populationInstance (child, network, part); break;
            }
        }

        // Post-process instances, hopefully matching their IDs to their $index values.
        MNode instances = part.child ("$instance");
        if (instances != null)
        {
            int count = instances.length ();
            int index = 0;
            for (MNode i : instances)
            {
                String xyz = i.get ("$xyz");
                String ijk = i.get ("ijk");
                if (count == 1)
                {
                    if (! xyz.isEmpty ()) part.set ("$xyz", xyz);
                    if (! ijk.isEmpty ()) part.set ("ijk",  ijk);
                }
                else
                {
                    if (! xyz.isEmpty ()) part.set ("$xyz", "@$index==" + index, xyz);
                    if (! ijk.isEmpty ()) part.set ("ijk",  "@$index==" + index, ijk);
                }
                i.set ("$index", index++);
            }
        }
    }

    public void populationLayout (Node node, MNode network, MNode part)
    {
        String spaceID = getAttribute (node, "space");

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () == Node.ELEMENT_NODE)
            {
                String regionID = getAttribute (child, "region");
                if (! regionID.isEmpty ()) spaceID = network.get ("$region", regionID);
                MNode space = null;
                if (! spaceID.isEmpty ()) space = network.child ("$space", spaceID);

                switch (child.getNodeName ())
                {
                    case "random":
                        part.set ("$n", getAttribute (child, "number", 1));
                        if (space != null) part.set ("$xyz", "uniform(" + space.get ("scale") + ")+" + space.get ("offset"));
                        break;
                    case "grid":
                        int x = getAttribute (child, "xSize", 1);
                        int y = getAttribute (child, "ySize", 1);
                        int z = getAttribute (child, "zSize", 1);
                        part.set ("$n", x * y * z);
                        if (space == null) part.set ("$xyz", "grid($index," + x + "," + y + "," + z + ")");
                        else               part.set ("$xyz", "grid($index," + x + "," + y + "," + z + ",\"raw\")&" + space.get ("scale") + "+" + space.get ("offset"));
                        break;
                    case "unstructured":
                        part.set ("$n", getAttribute (child, "number", 1));
                        break;
                }
            }
        }
    }

    public void populationInstance (Node node, MNode network, MNode part)
    {
        int id = getAttribute (node, "id", 0);
        int i  = getAttribute (node, "i", -1);
        int j  = getAttribute (node, "j", -1);
        int k  = getAttribute (node, "k", -1);
        double x = 0;
        double y = 0;
        double z = 0;
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            // As written, the standard allows multiple xyz positions. Not sure what that means.
            // This code overwrites the position if more than one are specified.
            if (child.getNodeType () == Node.ELEMENT_NODE  &&  child.getNodeName ().equals ("location"))
            {
                x = getAttribute (child, "x", 0.0);
                y = getAttribute (child, "y", 0.0);
                z = getAttribute (child, "z", 0.0);
            }
        }

        part.set ("$instance", id, "$xyz", "[" + x + ";" + y + ";" + z + "]");
        if (i >= 0  ||  j >= 0  ||  k >= 0)
        {
            if (i < 0) i = 0;
            if (j < 0) j = 0;
            if (k < 0) k = 0;
            part.set ("$instance", id, "ijk",  "[" + i + ";" + j + ";" + k + "]");
        }
    }

    public static class Connection
    {
        // null means wildcard
        // empty string means required to be blank
        public String preGroup;
        public String postGroup;
        public String synapse;
        public String preComponent;
        public String postComponent;
        public MNode  part;

        public static boolean check (String a, String b)
        {
            if (a == null  ||  b == null) return true;  // wildcard match
            return a.equals (b);
        }

        @Override
        public boolean equals (Object o)
        {
            if (! (o instanceof Connection)) return false;
            Connection that = (Connection) o;

            if (! check (preGroup,      that.preGroup     )) return false;
            if (! check (postGroup,     that.postGroup    )) return false;
            if (! check (synapse,       that.synapse      )) return false;
            if (! check (preComponent,  that.preComponent )) return false;
            if (! check (postComponent, that.postComponent)) return false;
            return true;
        }
    }

    public void projection (Node node, MNode network)
    {
        String id             = getAttribute (node, "id");
        String synapse        = getAttribute (node, "synapse");
        String A              = getAttribute (node, "presynapticPopulation");
        String B              = getAttribute (node, "postsynapticPopulation");
        String projectionType = node.getNodeName ();

        List<Connection> connections = new ArrayList<Connection> ();
        Connection base = new Connection ();
        connections.add (base);
        base.part = network.set (id, "");
        if (projectionType.equals ("continuousprojection"))
        {
            base.part.set ("$inherit", "\"" + projectionType + "\"");
        }
        else
        {
            if (! synapse.isEmpty ())
            {
                base.part.set ("$inherit", "\"" + synapse + "\"");
                base.synapse = synapse;
            }
        }
        base.part.set ("A", A);
        base.part.set ("B", B);

        NamedNodeMap attributes = node.getAttributes ();
        int count = attributes.getLength ();
        for (int i = 0; i < count; i++)
        {
            Node a = attributes.item (i);
            String name = a.getNodeName ();
            if (name.equals ("id"                    )) continue;
            if (name.equals ("synapse"               )) continue;
            if (name.equals ("presynapticPopulation" )) continue;
            if (name.equals ("postsynapticPopulation")) continue;
            base.part.set (name, biophysicalUnits (a.getNodeValue ()));  // biophysicalUnits() will only modify text if there is a numeric value
        }

        // Children are specific connections.
        // In the case of "continuous" connections, there are pre- and post-synaptic components which can vary
        // from one entry to the next. These must be made into separate connection objects, so try to fold
        // them as much as possible.
        // For other connection types, attributes can be set up as conditional constants.
        MNode instancesA = network.child (A, "$instance");
        MNode instancesB = network.child (B, "$instance");
        boolean preSingleton  = network.getOrDefaultInt (A, "$n", "1") == 1;  // This assumes that a population always has $n set if it is anything besides 1.
        boolean postSingleton = network.getOrDefaultInt (B, "$n", "1") == 1;
        boolean wroteDefaultP            = false;
        boolean wroteDefaultPreFraction  = false;
        boolean wroteDefaultPostFraction = false;
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;

            int    childID       = getAttribute  (child, "id", 0);

            String preComponent  = getAttribute  (child, "preComponent");
            String preCell       = getAttributes (child, "preCell", "preCellId");
            int    preSegment    = getAttribute  (child, "preSegment", -1);
            if (preSegment < 0)
                   preSegment    = getAttribute  (child, "preSegmentId", -1);
            double preFraction   = getAttribute  (child, "preFractionAlong", 0.5);

            String postComponent = getAttribute  (child, "postComponent");
            String postCell      = getAttributes (child, "postCell", "postCellId");
            int    postSegment   = getAttribute  (child, "postSegment", -1);
            if (postSegment < 0)
                   postSegment   = getAttribute  (child, "postSegmentId", -1);
            double postFraction  = getAttribute  (child, "postFractionAlong", 0.5);

            if (child.getNodeName ().endsWith ("Instance"))  // ID is in "instance" format, which seems to be an XPath of sorts
            {
                preCell  = extractIDfromPath (preCell);
                postCell = extractIDfromPath (postCell);
            }
            if (instancesA != null) preCell  = instancesA.getOrDefault (preCell,  "$index", preCell);  // Map NeuroML ID to assigned N2A $index, falling back on ID if $index has not been assigned.
            if (instancesB != null) postCell = instancesB.getOrDefault (postCell, "$index", postCell);

            Connection query = new Connection ();
            if (! synapse      .isEmpty ()) query.synapse       = synapse;
            if (! preComponent .isEmpty ()) query.preComponent  = preComponent;
            if (! postComponent.isEmpty ()) query.postComponent = postComponent;

            int preSegmentIndex = 0;
            if (preSegment >= 0)
            {
                // preSegment is the ID, effectively the row in the segment*group matrix
                // We must find the column associated with it, so we can map to a group part.
                MatrixBoolean M = cellSegment.get (A);
                if (M != null)
                {
                    count = M.columns ();
                    int c = 0;
                    for (; c < count; c++) if (M.get (preSegment, c)) break;
                    if (c < count)
                    {
                        query.preGroup = models.get (modelName, A, "$groupIndex", c);
                        preSegmentIndex = M.indexInColumn (preSegment, c);
                    }
                }
            }

            int postSegmentIndex = 0;
            if (postSegment >= 0)
            {
                // preSegment is the ID, effectively the row in the segment*group matrix
                // We must find the column associated with it, so we can map to a group part.
                MatrixBoolean M = cellSegment.get (B);
                if (M != null)
                {
                    count = M.columns ();
                    int c = 0;
                    for (; c < count; c++) if (M.get (postSegment, c)) break;
                    if (c < count)
                    {
                        query.postGroup = models.get (modelName, A, "$groupIndex", c);
                        postSegmentIndex = M.indexInColumn (postSegment, c);
                    }
                }
            }

            // Check for folding
            Connection connection;
            int match = connections.indexOf (query);
            if (match >= 0)
            {
                connection = connections.get (match);

                if (query.preGroup != null  &&  connection.preGroup == null)
                {
                    connection.preGroup = query.preGroup;
                    connection.part.set ("A", A + "." + connection.preGroup);
                }
                if (query.postGroup != null  &&  connection.postGroup == null)
                {
                    connection.postGroup = query.postGroup;
                    connection.part.set ("B", B + "." + connection.postGroup);
                }
                if (query.synapse != null  &&  connection.synapse == null)
                {
                    connection.synapse = query.synapse;
                    connection.part.set ("$inherit", "\"" + connection.synapse + "\"");
                    addDependency (connection.part, connection.synapse);
                }
                if (query.preComponent != null  &&  connection.preComponent == null)
                {
                    connection.preComponent = query.preComponent;
                    connection.part.set ("preComponent", "$inherit", "\"" + connection.preComponent + "\"");
                    addDependency (connection.part.child ("preComponent"), connection.preComponent);
                }
                if (query.postComponent != null  &&  connection.postComponent == null)
                {
                    connection.postComponent = query.postComponent;
                    connection.part.set ("postComponent", "$inherit", "\"" + connection.postComponent + "\"");
                    addDependency (connection.part.child ("postComponent"), connection.postComponent);
                }
            }
            else
            {
                connections.add (query);
                connection = query;
                connection.part = network.set (id + childID, base.part);

                if (query.preGroup  != null) connection.part.set ("A", A + "." + connection.preGroup);
                if (query.postGroup != null) connection.part.set ("B", B + "." + connection.postGroup);
                if (query.synapse   != null)
                {
                    connection.part.set ("$inherit", "\"" + connection.synapse + "\"");
                    addDependency (connection.part, connection.synapse);
                }
                if (query.preComponent != null)
                {
                    connection.part.set ("preComponent", "$inherit", "\"" + connection.preComponent + "\"");
                    addDependency (connection.part.child ("preComponent"), connection.preComponent);
                }
                if (query.postComponent != null)
                {
                    connection.part.set ("postComponent", "$inherit", "\"" + connection.postComponent + "\"");
                    addDependency (connection.part.child ("postComponent"), connection.postComponent);
                }
            }

           
            // Add conditional info
            String condition = "";
            if (! preSingleton)
            {
                if (connection.preGroup == null) condition = "A.$index=="     + preCell;
                else                             condition = "A.$up.$index==" + preCell;
            }
            if (connection.preGroup != null)
            {
                if (! condition.isEmpty ()) condition += "&&";
                condition += "A.$index==" + preSegmentIndex;
            }
            if (! postSingleton)
            {
                if (! condition.isEmpty ()) condition += "&&";
                if (connection.postGroup == null) condition += "B.$index=="     + postCell;
                else                              condition += "B.$up.$index==" + postCell;
            }
            if (connection.postGroup != null)
            {
                if (! condition.isEmpty ()) condition += "&&";
                condition += "B.$index==" + postSegmentIndex;
            }
            if (! condition.isEmpty ())
            {
                connection.part.set ("$p", "@" + condition, "1");
                if (! wroteDefaultP)
                {
                    connection.part.set ("$p", "@", "0");
                    wroteDefaultP = true;
                }
            }

            if (preFraction != 0.5)
            {
                if (connection.preGroup == null)
                {
                    connection.part.set ("preFraction=" + preFraction);  // No segment group is explicitly addressed, so this is more of a memo.
                }
                else
                {
                    connection.part.set ("preFraction", "@" + preSegmentIndex, preFraction);
                    if (! wroteDefaultPreFraction)
                    {
                        connection.part.set ("preFraction", "@", "0.5");
                        wroteDefaultPreFraction = true;
                    }
                }
            }
            if (postFraction != 0.5)
            {
                if (connection.postGroup == null)
                {
                    connection.part.set ("postFraction=" + postFraction);
                }
                else
                {
                    connection.part.set ("postFraction", "@" + postSegmentIndex, postFraction);
                    if (! wroteDefaultPostFraction)
                    {
                        connection.part.set ("postFraction", "@", "0.5");
                        wroteDefaultPostFraction = true;
                    }
                }
            }
        }
    }

    public String extractIDfromPath (String path)
    {
        // I don't yet understand how these paths are supposed to work, because they are thoroughly undocumented.
        // This simple-minded parser works on the examples I've seen.
        String[] pieces = path.split ("/");
        if (pieces.length < 3) return path;
        return pieces[2];
    }

    public void explicitInput (Node node, MNode network)
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

        String name = input + "_to_" + target;
        MNode part = network.childOrCreate (name);
        part.set ("$inherit", "\"Current Injection\"");
        MNode d = part.set ("A", input);
        addDependency (d, input);
        part.set ("B", target);  // The target could also be folded into this connection part during dependency resolution, but that would actually make the model more ugly.

        MNode targetPart = network.child (target);
        if (targetPart == null  ||  ! targetPart.getOrDefault ("$n", "1").equals ("1"))  // We only have to explicitly set $p if the target part has more than one instance.
        {
            part.set ("$p", "@B.$index==" + index, "1");
            part.set ("$p", "@",                   "0");
        }
    }

    public void genericPart (Node node, MNode container)
    {
        String name = node.getNodeName ();
        if (name.equals ("notes"))
        {
            container.set ("$metadata", "notes", getText (node));
            return;
        }

        String id = getAttribute (node, "id");
        MNode part = container.set (id, "");
        part.set ("$inherit", "\"" + name + "\"");

        NamedNodeMap attributes = node.getAttributes ();
        int count = attributes.getLength ();
        for (int i = 0; i < count; i++)
        {
            Node a = attributes.item (i);
            name = a.getNodeName ();
            if (name.equals ("id")) continue;
            part.set (name, biophysicalUnits (a.getNodeValue ()));  // biophysicalUnits() will only modify text if there is a numeric value
        }

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () == Node.ELEMENT_NODE) genericPart (child, part);
        }
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

    public String getAttributes (Node node, String... names)
    {
        for (String name : names)
        {
            String result = getAttribute (node, name);
            if (! result.isEmpty ()) return result;
        }
        return "";
    }

    public String getAttribute (Node node, String name)
    {
        return getAttribute (node, name, "");
    }

    public String getAttribute (Node node, String name, String defaultValue)
    {
        NamedNodeMap attributes = node.getAttributes ();
        Node attribute = attributes.getNamedItem (name);
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
}

/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.undo.AddDoc;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
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
    File source;
    MNode model;
    AddDoc action;

    public void process (File source)
    {
        this.source = source;
        try
        {
            // Open and parse XML document
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance ();
            factory.setCoalescing (true);
            factory.setIgnoringComments (true);
            factory.setIgnoringElementContentWhitespace (true);
            factory.setValidating (false);
            factory.setXIncludeAware (true);  // Doesn't seem to actually include other files, at least on the samples I've tried so far. Must be missing something.
            DocumentBuilder builder = factory.newDocumentBuilder ();
            Document doc = builder.parse (source);

            // Extract models
            //dump (System.out, doc, "");
            process (doc);
            if (action != null) PanelModel.instance.undoManager.add (action);
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
            case "cell"   : cell    (node); break;
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

    public void neuroml (Node node)
    {
        String modelName = getAttribute (node, "id");
        if (modelName.isEmpty ())
        {
            // Calculate name from file
            modelName = source.getName ();
            int lastDot = modelName.lastIndexOf ('.');
            if (lastDot >= 0  &&  modelName.substring (lastDot).equalsIgnoreCase (".nml")) modelName = modelName.substring (0, lastDot);
        }
        if (modelName.isEmpty ()) modelName = "New Model";
        action = new AddDoc (modelName);
        model = action.saved;

        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () == Node.ELEMENT_NODE) process (child);
        }
    }

    public void cell (Node node)
    {
        String id   = getAttribute (node, "id", "MISSING_ID");  // MISSING_ID indicates an ill-formed nml file.
        MNode  cell = model.set (id, "");

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
                    MNode part = cell.child ("$group", p.get ());
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
                    String ionChannel = property.get ("ionChannel");
                    property.clear ("ionChannel");
                    property.set ("$inherit", "\"" + ionChannel + "\"");
                    break;
                case "specificCapacitance":
                    property = allocateProperty (child, cell, G);
                    property.set ("C", getAttribute (child, "value"));
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
            result.set (a.getNodeName (), biophysicalUnits (a.getNodeValue ()));
        }
        return result;
    }

    /**
        Convert the given value to be in appropriate units, in the context of a morphology section.
    **/
    public String morphologyUnits (String value)
    {
        return value;
    }

    /**
        Convert the given value to be in appropriate units, in the context of a physiology section.
    **/
    public String biophysicalUnits (String value)
    {
        return value;
    }

    public class Segment
    {
        int     id;
        int     parentID         = -1;
        Segment parent;

        MNode   part;   // compartment of which this segment is an instance
        int     index;  // within part

        // TODO: better unit handling
        // Convert everything to double (both matrices and scalars).
        // During output, convert back to preferred unit scale. For example, express everything in micrometers.
        double  fractionAlong    = 1;
        String  proximal         = "";
        String  distal           = "";
        String  proximalDiameter = "";
        String  distalDiameter   = "";

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
                        String x = morphologyUnits (getAttribute (child, "x"));
                        String y = morphologyUnits (getAttribute (child, "y"));
                        String z = morphologyUnits (getAttribute (child, "z"));
                        proximal = "[" + x + ";" + y + ";" + z + "]";
                        proximalDiameter = morphologyUnits (getAttribute (child, "diameter"));
                        break;
                    case "distal":
                        x = morphologyUnits (getAttribute (child, "x"));
                        y = morphologyUnits (getAttribute (child, "y"));
                        z = morphologyUnits (getAttribute (child, "z"));
                        distal = "[" + x + ";" + y + ";" + z + "]";
                        distalDiameter = morphologyUnits (getAttribute (child, "diameter"));
                        break;
                }
            }
        }

        /**
            Requires that parent be resolved already.
        **/
        public void resolveProximal ()
        {
            if (! proximal.isEmpty ()  ||  parent == null) return;
            if (fractionAlong == 1)
            {
                proximal = parent.distal;
                proximalDiameter = parent.distalDiameter;
            }
            else
            {
                parent.resolveProximal ();
                // TODO: handle unit conversion inside Matrix load()
                Matrix A = new Matrix (parent.proximal);
                Matrix B = new Matrix (parent.distal);
                proximal = B.subtract (A).multiply (new Scalar (fractionAlong)).add (A).toString ();

                double a = Double.parseDouble (parent.proximalDiameter);
                double b = Double.parseDouble (parent.distalDiameter);
                proximalDiameter = String.valueOf ((b - a) * fractionAlong + a);
            }
        }

        public void output (MNode part, int index)
        {
            this.part = part;
            if (index < 0)  // only one instance, so make values unconditional
            {
                this.index = 0;
                if (! distal          .isEmpty ()) part.set ("$xyz",      distal);
                if (! proximal        .isEmpty ()) part.set ("xyz0",      proximal);
                if (! distalDiameter  .isEmpty ()) part.set ("diameter",  distalDiameter);
                if (! proximalDiameter.isEmpty ()) part.set ("diameter0", proximalDiameter);
            }
            else  // multiple instances
            {
                this.index = index;
                if (! distal          .isEmpty ()) part.set ("$xyz",      "@$index==" + index, distal);
                if (! proximal        .isEmpty ()) part.set ("xyz0",      "@$index==" + index, proximal);
                if (! distalDiameter  .isEmpty ()) part.set ("diameter",  "@$index==" + index, distalDiameter);
                if (! proximalDiameter.isEmpty ()) part.set ("diameter0", "@$index==" + index, proximalDiameter);
            }
        }
    }
}

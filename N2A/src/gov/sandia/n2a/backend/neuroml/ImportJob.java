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
import gov.sandia.n2a.ui.eq.undo.AddDoc;

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
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class ImportJob
{
    LinkedList<File>          sources      = new LinkedList<File> ();
    MNode                     models       = new MVolatile ();
    String                    modelName    = "";
    List<MNode>               resolve      = new ArrayList<MNode> ();  // Nodes which contain a $inherit that refers to a locally defined part rather than a standard part. The local part must either be copied in or converted into a global model.
    Map<String,MatrixBoolean> cellSegment  = new HashMap<String,MatrixBoolean> ();  // Map from cell IDs to their associated segment matrix. The matrix itself maps from segment index to group index.
    Map<String,Node>          morphologies = new HashMap<String,Node> ();  // Map from IDs to top-level morphology blocks.
    Map<String,Node>          biophysics   = new HashMap<String,Node> ();  // Map from IDs to top-level biophysics blocks.

    Pattern floatParser   = Pattern.compile ("[-+]?(NaN|Infinity|([0-9]*\\.?[0-9]*([eE][-+]?[0-9]+)?))");
    Pattern forbiddenUCUM = Pattern.compile ("[.,;><=!&|+\\-*/%\\^~]");
    static final double epsilon = Math.ulp (1);

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
            // Preemptively scan for unique name, so our references to any sibling parts remain valid.
            // IE: a name collision in the main part implies that sibling parts will also have name collisions.
            modelName = AddDoc.uniqueName (modelName);
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
                case "morphology":
                    morphologies.put (getAttribute (child, "id"), child);
                    break;
                case "ionChannel":
                case "ionChannelHH":
                case "ionChannelKS":
                    ionChannel (child);
                    break;
                case "biophysicalProperties":
                    biophysics.put (getAttribute (child, "id"), child);
                    break;
                case "cell":
                    cell (child);
                    break;
                case "spikeArray":
                case "timedSynapticInput":
                    spikeArray (child);
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
        if (part.get ().equals (inherit)) component.set ("$connected", "");
    }

    /**
        Perform data manipulations that must wait until all nodes are read.
    **/
    public void postprocess ()
    {
        // Remove some temporary keys before doing merges.
        for (MNode c : models.child (modelName)) c.clear ("$groupIndex");

        // Resolve referenced parts
        for (MNode r : resolve)
        {
            String partName = r.get ("$inherit").replace ("\"", "");
            if (partName.isEmpty ()) partName = r.get ();  // For connections, the part name might be a direct value.
            MNode part = models.child (modelName, partName);
            if (part == null) continue;

            // Triage
            int count = part.getInt ("$count");
            boolean connected = part.child ("$connected") != null;
            if (count > 0)  // triage is necessary
            {
                if (count == 1)
                {
                    count = -1;  // part has only one user, so it should simply be deleted after merging
                }
                else  // count > 1, so part could be moved out to its own model
                {
                    if (connected)
                    {
                        count = -3;
                    }
                    else
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
                }
                part.set ("$count", count);
            }
            if (count == -1)
            {
                // TODO: This should really be an underride, not override. However, there don't seem to be any conflicting nodes.
                r.merge (part);
                r.clear ("$count");
                r.clear ("$connected");
            }
            else if (count == -2)
            {
                r.set ("$inherit", "\"" + modelName + " " + partName + "\"");
            }
            // count == -3 means leave in place as a connection target
        }

        // Move heavy-weight parts into separate models
        Iterator<MNode> it = models.child (modelName).iterator ();
        while (it.hasNext ())
        {
            MNode part = it.next ();
            int count = part.getInt ("$count");
            if (count < 0)
            {
                part.clear ("$count");
                part.clear ("$connected");
                if (count == -2)
                {
                    String partName = part.key ();
                    MNode model = models.childOrCreate (modelName + " " + partName);
                    model.merge (part);
                }
                if (count > -3) it.remove ();
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
                case "morphology"           : morphology (child, cell, segments, G);            break;
                case "biophysicalProperties": biophysicalProperties (child, cell, G);           break;
                default                     : genericPart (child, cell);  // Also handles generic metadata elements.
            }
        }

        // Alternate attribute-based method for pulling in morphology and biophysics
        // This only works if such sections appear before this cell node. It seems that the NeuroML spec guarantees this.
        String include = getAttribute (node, "morphology");
        if (! include.isEmpty ())
        {
            Node child = morphologies.get (include);
            if (child != null) morphology (child, cell, segments, G);
        }
        include = getAttribute (node, "biophysicalProperties");
        if (! include.isEmpty ())
        {
            Node child = biophysics.get (include);
            if (child != null) biophysicalProperties (child, cell, G);
        }


        // Finish parts

        for (Entry<Integer,Segment> e : segments.entrySet ())
        {
            Segment s = e.getValue ();
            if (s.parentID >= 0  &&  s.parent == null)
            {
                s.parent = segments.get (s.parentID);
                s.parent.children.add (s);
            }
        }
        for (Entry<Integer,Segment> e : segments.entrySet ()) e.getValue ().resolveProximal ();
        MNode groups = cell.child ("$group");
        if (groups != null)
        {
            for (MNode g : groups) applyPaths (segments, g, G);
            for (MNode g : groups) includeGroups (groups, g, G);
        }

        // If no segment groups have been defined, create a single catch-all group.
        if (groups == null  &&  segments.size () > 0)
        {
            int smallestID = Integer.MAX_VALUE;
            for (Entry<Integer,Segment> e : segments.entrySet ())
            {
                int ID = Integer.valueOf (e.getKey ());
                smallestID = Math.min (ID, smallestID);
                G.set (ID, 0);
            }
            String name = segments.get (smallestID).name;  // Name the group after the first segment, if it has a name.
            if (name.isEmpty ()) name = "segments";
            cell.set ("$group", name, "$G", 0);
            cell.set ("$groupIndex", 0, name);
        }

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
                    String newName = segments.get (r).name;
                    if (newName.isEmpty ()  ||  cell.child ("$group", newName) != null)
                    {
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
                        if (bestColumn == currentColumn) newName = "segment_" + r;
                        else                             newName = cell.get ("$groupIndex", bestColumn) + "_" + r;
                    }
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
                    if (part != null)  // Missing group is a symptom of an ill-formed file.
                    {
                        part.merge (p);
                        part.set ("");
                    }
                }
            }
        }


        // Create subparts for all combinations of parameters.
        // Each subpart will hold a set of segments that is mutually exclusive of the other sets.
        // Ideally, the sets exactly match the segment groups, but in general there may be more sets.
        // If a set exactly matches an original segment group, it gets that group's name.
        // Then, the set that has the largest overlap with an unclaimed segment group, without exceeding it, gets its name.
        // All remaining sets get a name formed from a concatenation of each group it overlaps with.

        MatrixBoolean O = new MatrixBoolean ();
        MatrixBoolean M = new MatrixBoolean ();
        G.foldRows (M, O);
        cellSegment.put (id, M);

        // Scan for exact matches
        int columnsG = G.columns ();
        int columnsM = M.columns ();
        Set<Integer> newIndices = new HashSet<Integer>  (columnsM);
        Map<Integer,String> finalNames = new HashMap<Integer,String> (columnsM);
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
                    cell.set (name, "");
                    finalNames.put (i, name);
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
                cell.set (name, "");
                finalNames.put (i, name);
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
            cell.set (name, "");
            finalNames.put (i, name);
        }

        // Add segments and properties to the parts
        for (Entry<Integer,String> e : finalNames.entrySet ())
        {
            int c = e.getKey ();  // column of M, the mapping from segments to new groups
            String currentName = e.getValue ();
            MNode part = cell.child (currentName);

            // Rename any part with a single segment to the name of that segment, provided it is unique.
            if (M.columnNorm0 (c) == 1)
            {
                int r = M.firstNonzeroRow (c);
                String newName = segments.get (r).name;
                // Note that all named parts have already been added to cell, so we can do a lookup there for uniqueness.
                if (! newName.isEmpty ()  &&  ! newName.equals (currentName)  &&  cell.child (newName) == null)
                {
                    cell.move (currentName, newName);
                    e.setValue (newName);
                    currentName = newName;
                }
            }

            // Merge original groups into new part
            //   A heuristic is that smaller groups (ones with fewer segments) are more specific, so their metadata should take precedence.
            //   Thus, we sort the relevant original groups by size and apply them in that order.
            class ColumnSize implements Comparable<ColumnSize>
            {
                public int column;
                public int size;  // number of rows in column
                public ColumnSize (int column, int size)
                {
                    this.column = column;
                    this.size   = size;
                }
                public int compareTo (ColumnSize o)
                {
                    int result = size - o.size;
                    if (result != 0) return result;
                    return column - o.column;  // Just a tie breaker. Favors lower-numbered column.
                }
            }
            TreeSet<ColumnSize> sortedColumns = new TreeSet<ColumnSize> ();
            //   rows of O are new parts (columns of M); columns of O are original segment groups
            for (int i = 0; i < columnsG; i++) if (O.get (c, i)) sortedColumns.add (new ColumnSize (i, G.columnNorm0 (i)));
            for (ColumnSize cs : sortedColumns)
            {
                String groupName = cell.get ("$groupIndex", cs.column);  // a name from the original set of groups, not the new groups
                part.mergeUnder (cell.child ("$group", groupName));
            }
            for (MNode property : part)
            {
                // Any subparts of a membrane property are likely ion channels, so need to tag any top-level definitions they use
                for (MNode m : property)
                {
                    String inherit = m.get ("$inherit").replace ("\"", "");
                    if (inherit.isEmpty ()) continue;  // $metadata and any parameters are at the same level as subparts, but they will of course lack a $inherit line.
                    // check for standard part
                    MNode model = AppData.models.child (inherit);
                    if (model == null  ||  model.child ("$metadata", "backend.neuroml.part") == null) addDependency (m, inherit);
                }
            }

            // Collect inhomogeneous variables
            String pathLength = "";
            for (MNode v : part)
            {
                if (! v.get ("$metadata", "backend.neuroml.param").equals ("pathLength")) continue;
                if (pathLength.isEmpty ())
                {
                    pathLength = "pathLength";
                    int count = 2;
                    while (part.child (pathLength) != null) pathLength = "pathLength" + count++;
                }
                double a = v.getDouble ("$metadata", "backend.neuroml.param.a");
                double b = v.getDouble ("$metadata", "backend.neuroml.param.b");
                String value = pathLength;
                if (a != 1) value += "*" + a;
                if (b != 0) value += "+" + b;
                v.set (value);
            }

            // Add segments
            int n = M.columnNorm0 (c);
            if (n > 1) part.set ("$n", n);
            int index = 0;
            for (int r = 0; r < M.rows (); r++)
            {
                if (! M.get (r, c)) continue;
                Segment s = segments.get (r);
                
                if (n > 1)
                {
                    if (! s.name.isEmpty ()) part.set ("$metadata", "name" + index, s.name);
                    if (! pathLength.isEmpty ()) part.set (pathLength, "@$index==" + index, s.pathLength (0.5));  // mid-point method. TODO: add way to subdivide segments for more precise modeling of varying density
                    s.output (part, index++);
                }
                else
                {
                    if (! s.name.isEmpty ()  &&  ! s.name.equals (currentName)) part.set ("$metadata", "name", s.name);
                    if (! pathLength.isEmpty ()) part.set (pathLength, s.pathLength (0.5));
                    s.output (part, -1);
                    break;  // Since we've already processed the only segment.
                }
            }
        }

        // Create connections to complete the cables
        // Note that all connections are explicit, even within the same group.
        // TODO: Convert these to unary connections directly from child segment to parent segment.
        for (Entry<Integer,Segment> e : segments.entrySet ())
        {
            Segment s = e.getValue ();
            if (s.parent == null) continue;

            int childN = s.part.getOrDefaultInt ("$n", "1");
            String connectionName = s.parent.part.key () + "_to_" + s.part.key ();
            MNode connection = cell.child (connectionName);
            if (connection == null)
            {
                connection = cell.set (connectionName, "");
                connection.set ("$inherit", "\"Coupling Voltage\"");
                connection.set ("A", s.parent.part.key ());
                connection.set ("B", s       .part.key ());
                connection.set ("R", "$kill");  // Force use of container's value.

                int parentN = s.parent.part.getOrDefaultInt ("$n", "1");
                if (parentN > 1)
                {
                    String parentName = "parent";
                    int suffix = 2;
                    while (s.part.child (parentName) != null) parentName = "parent" + suffix++;
                    connection.set ("$parent", parentName);  // temporary memo

                    connection.set ("$p", "A.$index==B." + parentName);
                    if (childN > 1)
                    {
                        s.part.set (parentName, ":");
                        s.part.set (parentName, "@", "-1");
                    }
                }
                else if (childN > 1)
                {
                    connection.set ("$p", "B.$index==" + s.index);
                }
            }

            String parentName = connection.get ("$parent");
            if (! parentName.isEmpty ())
            {
                if (childN > 1) s.part.set (parentName, "@$index==" + s.index, s.parent.index);
                else            s.part.set (parentName, s.parent.index);
            }
        }

        // Clean up temporary nodes.
        cell.clear ("$properties");
        cell.clear ("$group");
        cell.clear ("$groupIndex");
        for (MNode part : cell)
        {
            part.clear ("$parent");
            part.clear ("$G");
            for (MNode v : part)
            {
                v.clear ("$metadata", "backend.neuroml.param.a");
                v.clear ("$metadata", "backend.neuroml.param.b");
            }
        }
        for (Entry<Integer,String> e : finalNames.entrySet ()) cell.set ("$groupIndex", e.getKey (), e.getValue ());
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

    public class Segment implements Comparable<Segment>
    {
        int          id;  // NeuroML id; row number in G matrix
        String       name;
        int          parentID           = -1;
        Segment      parent;
        Set<Segment> children           = new TreeSet<Segment> ();

        MNode        part;   // compartment of which this segment is an instance
        int          index;  // $index within part

        double       fractionAlong      = 1;
        Matrix       proximal;
        Matrix       distal;
        double       proximalDiameter   = -1;
        double       distalDiameter     = -1;
        double       proximalPathLength = -1;  // Path length from proximal end to root. Used to calculate pathLength().

        public Segment (Node node)
        {
            id   = Integer.parseInt (getAttribute (node, "id", "0"));
            name =                   getAttribute (node, "name"); 
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

        public double pathLength (double fraction)
        {
            if (proximalPathLength < 0)
            {
                if (parent == null) proximalPathLength = 0;
                else                proximalPathLength = parent.pathLength (fractionAlong);
            }
            return proximalPathLength + ((Matrix) distal.subtract (proximal)).norm (2) * fraction;
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

        public int compareTo (Segment o)
        {
            return id - o.id;
        }
    }

    public void segmentGroup (Node node, MNode cell, MatrixBoolean G)
    {
        MNode groups = cell.childOrCreate ("$group");
        int c = groups.length ();
        String groupName = getAttribute (node, "id");
        MNode part = groups.childOrCreate (groupName);
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
            switch (child.getNodeName ())
            {
                case "member":
                    G.set (getAttribute (child, "segment", 0), c);
                    break;
                case "include":
                    String include = part.get ("$include");  // not $inherit
                    if (! include.isEmpty ()) include += ",";
                    include += getAttribute (child, "segmentGroup");
                    part.set ("$include", include);
                    break;
                case "path":
                case "subTree":
                    segmentPath (child, part);
                    break;
                case "inhomogeneousParameter":
                    inhomogeneousParameter (child, part);
                    break;
            }
        }
    }

    public void segmentPath (Node node, MNode group)
    {
        int from = -1;
        int to   = -1;
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            switch (child.getNodeName ())
            {
                case "from": from = getAttribute (child, "segment", 0); break; 
                case "to"  : to   = getAttribute (child, "segment", 0); break; 
            }
        }
        if (from >= 0)
        {
            MNode paths = group.childOrCreate ("$paths");
            int index = paths.length ();
            if (to >= 0) paths.set (index, from + "," + to);
            else         paths.set (index, from);
        }
    }

    public void applyPaths (Map<Integer,Segment> segments, MNode group, MatrixBoolean G)
    {
        MNode paths = group.child ("$paths");
        if (paths == null) return;

        int c = group.getInt ("$G");
        for (MNode p : paths)
        {
            String[] pieces = p.get ().split (",");
            if (pieces.length == 1)  // subtree
            {
                int from = Integer.valueOf (pieces[0]);
                applyPath (segments.get (from), G, c);
            }
            else  // path
            {
                int from = Integer.valueOf (pieces[0]);
                int to   = Integer.valueOf (pieces[1]);
                if (from > to)
                {
                    for (int r = to; r <= from; r++) G.set (r, c);
                }
                else
                {
                    for (int r = from; r <= to; r++) G.set (r, c);
                }
            }
        }

        group.clear ("$paths");
    }

    public void applyPath (Segment s, MatrixBoolean G, int c)
    {
        G.set (s.id, c);
        for (Segment t : s.children) applyPath (t, G, c);
    }

    public void includeGroups (MNode groups, MNode g, MatrixBoolean G)
    {
        String include = g.get ("$include");
        if (include.isEmpty ()) return;
        g.clear ("$include");

        int columnG = g.getInt ("$G");
        String[] pieces = include.split (",");
        for (String i : pieces)
        {
            MNode p = groups.child (i);
            if (p == null) continue;
            includeGroups (groups, p, G);
            // Merge segment members
            int columnP = p.getInt ("$G");
            G.OR (columnG, columnP);
            // Don't merge metadata from included groups, only members.
            // All metadata will get combined when groups are finalized in cell().
            // If it is merged here, it gets combined twice, and some items can leak into groups they are not really related to.
        }
    }

    public void inhomogeneousParameter (Node node, MNode group)
    {
        //String id       = getAttribute (node, "id");
        String variable = getAttribute (node, "variable");

        String translationStart = "0";
        String normalizationEnd = "1";
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            switch (child.getNodeName ())
            {
                case "proximal": translationStart = getAttribute (child, "translationStart"); break;
                case "distal"  : normalizationEnd = getAttribute (child, "normalizationEnd"); break;
            }
        }

        // TODO: What is the correct interpretation of translationStart and normalizationEnd?
        // Here we assume they are a linear transformation of the path length: variable = pathLength * normalizatonEnd + translationStart
        group.set (variable, "$metadata", "backend.neuroml.param",   "pathLength");
        group.set (variable, "$metadata", "backend.neuroml.param.a", normalizationEnd);
        group.set (variable, "$metadata", "backend.neuroml.param.b", translationStart);
    }

    public void biophysicalProperties (Node node, MNode cell, MatrixBoolean G)
    {
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;
            String name = child.getNodeName ();
            if      (name.startsWith ("membrane")) membraneProperties      (child, cell, G);
            else if (name.startsWith ("intra"   )) intracellularProperties (child, cell);
            else if (name.startsWith ("extra"   )) extracellularProperties (child, cell);
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
                channel (child, cell, property);
            }
            else
            {
                String value = biophysicalUnits (getAttribute (child, "value"));
                switch (name)
                {
                    case "spikeThresh":
                        property.set ("Vpeak", value);
                        break;
                    case "specificCapacitance":
                        property.set ("C", value);
                        break;
                    case "initMembPotential":
                        property.set ("V", "@$init", value);
                        break;
                }
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
                    MNode groups = cell.childOrCreate ("$group");
                    int c = groups.length ();
                    G.set (r, c);
                    groups.set (group, "$G", c);
                    cell.set ("$groupIndex", c, group);
                }
            }
        }
        MNode result = properties.set (String.valueOf (properties.length ()), group);
        if (id.isEmpty ()) return result;

        // Create a subpart with the given name
        MNode subpart = result.set (id, "");  
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
            subpart.set (name, biophysicalUnits (a.getNodeValue ()));  // biophysicalUnits() will only modify text if there is a numeric value
        }
        return result;
    }

    public void channel (Node node, MNode cell, MNode property)
    {
        MNode subpart = property.iterator ().next ();  // retrieve the first (and only) subpart

        String name = node.getNodeName ();
        subpart.set ("$inherit", "\"" + name + "\"");

        String ionChannel = subpart.get ("ionChannel");
        subpart.clear ("ionChannel");
        subpart.set ("ionChannel", "$inherit", "\"" + ionChannel + "\"");

        String ion = subpart.get ("ion");
        subpart.clear ("ion");
        subpart.set ("$metadata", "ion", ion);

        String parentGroup = property.get ();
        Map<String,MNode> groupProperty = new TreeMap<String,MNode> ();
        groupProperty.put (parentGroup, property);
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE  ||  ! child.getNodeName ().equals ("variableParameter")) continue;
            String parameter    = getAttribute (child, "parameter");
            String segmentGroup = getAttribute (child, "segmentGroup");

            MNode clone = property;
            if (! segmentGroup.equals (parentGroup))
            {
                if (parentGroup.equals ("[all]"))  // segment group was not or should not be assigned at level of channel part
                {
                    groupProperty.remove (parentGroup);
                    groupProperty.put (segmentGroup, property);
                    property.set (segmentGroup);  // assumes that the target group will exist when needed
                    parentGroup = segmentGroup;
                }
                else  // different than previously assigned group, so may need to clone the property
                {
                    clone = groupProperty.get (segmentGroup);
                    if (clone == null)
                    {
                        MNode properties = cell.child ("$properties");
                        clone = properties.set (String.valueOf (properties.length ()), segmentGroup);
                        clone.merge (property);
                        clone.set (segmentGroup);
                        groupProperty.put (segmentGroup, clone);
                    }
                }
                subpart = clone.iterator ().next ();
                subpart.set (parameter, variableParameter (child));
            }
        }
    }

    public String variableParameter (Node node)
    {
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () == Node.ELEMENT_NODE  &&  child.getNodeName ().equals ("inhomogeneousValue")) return getAttribute (child, "value");
        }
        return "";
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

    public void spikeArray (Node node)
    {
        String id = getAttribute (node, "id");
        MNode part = models.childOrCreate (modelName, id);
        part.set ("$inherit", "\"" + node.getNodeName () + "\"");

        NamedNodeMap attributes = node.getAttributes ();
        int count = attributes.getLength ();
        for (int i = 0; i < count; i++)
        {
            Node a = attributes.item (i);
            String name = a.getNodeName ();
            if (name.equals ("id")) continue;
            part.set (name, biophysicalUnits (a.getNodeValue ()));
        }

        // TODO: sort the spikes, to guarantee monotonicity
        String spikes = "[";
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () == Node.ELEMENT_NODE  &&  child.getNodeName ().equals ("spike"))
            {
                if (spikes.length () > 1) spikes += ";";
                spikes += biophysicalUnits (getAttribute (child, "time"));
            }
        }
        part.set ("spikes", spikes + "]");
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
                case "inputList":
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
            if (count > 1) part.set ("$n", count);
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
        public String preGroup      = "";
        public String postGroup     = "";
        public String inherit       = "";
        public String preComponent  = "";
        public String postComponent = "";
        public MNode  part;

        public TreeMap<Double,TreeSet<String>> preFractions  = new TreeMap<Double,TreeSet<String>> ();
        public TreeMap<Double,TreeSet<String>> postFractions = new TreeMap<Double,TreeSet<String>> ();
        public TreeMap<Double,TreeSet<String>> weights       = new TreeMap<Double,TreeSet<String>> ();
        public TreeMap<Double,TreeSet<String>> delays        = new TreeMap<Double,TreeSet<String>> ();

        public void add (TreeMap<Double,TreeSet<String>> collection, String value, String condition)
        {
            add (collection, Matrix.convert (value), condition);
        }

        public void add (TreeMap<Double,TreeSet<String>> collection, double value, String condition)
        {
            TreeSet<String> conditions = collection.get (value);
            if (conditions == null)
            {
                conditions = new TreeSet<String> ();
                collection.put (value, conditions);
            }
            conditions.add (condition);
        }

        public void injectConditionalValues ()
        {
            injectConditionalValue ("preFraction",  preFractions,  0.5, false);
            injectConditionalValue ("postFraction", postFractions, 0.5, false);
            injectConditionalValue ("weight",       weights,       1.0, false);
            injectConditionalValue ("delay",        delays,        0.0, true);
        }

        public void injectConditionalValue (String name, TreeMap<Double,TreeSet<String>> collection, double defaultValue, boolean outputSeconds)
        {
            if (collection.size () == 0) return;
            if (collection.size () == 1)
            {
                double value = collection.firstKey ();
                if (value == defaultValue) return;  // Default value should be inherited, so no need to set it.
                TreeSet<String> conditions = collection.firstEntry ().getValue ();
                if (conditions.size () == 1  &&  conditions.first ().isEmpty ())  // Unconditional value. This occurs when both source and destination populations are singletons.
                {
                    if (outputSeconds)
                    {
                        if (value >= 1) part.set (name, value + "s");
                        else            part.set (name, value * 1000 + "ms");
                    }
                    else
                    {
                        part.set (name, value);
                    }
                    return;
                }
            }

            MNode v = part.childOrCreate (name);
            v.set ("@", defaultValue);
            for (Entry<Double,TreeSet<String>> e : collection.entrySet ())
            {
                String condition = "";
                for (String c : e.getValue ())
                {
                    if (! condition.isEmpty ()) condition += "||";
                    condition += c;
                }
                double value = e.getKey ();
                if (outputSeconds)
                {
                    if (value >= 1) part.set (name, value + "s");
                    else            part.set (name, value * 1000 + "ms");
                }
                else
                {
                    v.set ("@" + condition, value);
                }
            }
        }

        @Override
        public boolean equals (Object o)
        {
            if (! (o instanceof Connection)) return false;
            Connection that = (Connection) o;

            if (! preGroup     .equals (that.preGroup     )) return false;
            if (! postGroup    .equals (that.postGroup    )) return false;
            if (! inherit      .equals (that.inherit      )) return false;
            if (! preComponent .equals (that.preComponent )) return false;
            if (! postComponent.equals (that.postComponent)) return false;
            return true;
        }
    }

    /**
        Contains minor hacks to handle InputList along with the 3 projection types.
    **/
    public void projection (Node node, MNode network)
    {
        String id             = getAttribute  (node, "id");
        String inherit        = getAttribute  (node, "synapse");
        String A              = getAttributes (node, "presynapticPopulation",  "component");
        String B              = getAttributes (node, "postsynapticPopulation", "population");
        String projectionType = node.getNodeName ();

        MNode base = new MVolatile ();
        base.set ("A", A);
        base.set ("B", B);
        if      (projectionType.equals ("continuousProjection")) inherit = "continuousProjection";
        else if (projectionType.equals ("inputList"))            inherit = "Current Injection";

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
            if (name.equals ("component"             )) continue;
            if (name.equals ("population"            )) continue;
            base.set (name, biophysicalUnits (a.getNodeValue ()));  // biophysicalUnits() will only modify text if there is a numeric value
        }

        // Children are specific connections.
        // In the case of "continuous" connections, there are pre- and post-synaptic components which can vary
        // from one entry to the next. These must be made into separate connection objects, so try to fold
        // them as much as possible.
        // For other connection types, attributes can be set up as conditional constants.

        MNode instancesA = network.child (A, "$instance");
        MNode instancesB = network.child (B, "$instance");

        boolean postCellSingleton = network.getOrDefaultInt (B, "$n", "1") == 1;  // This assumes that a population always has $n set if it is anything besides 1.
        boolean preCellSingleton;  // A requires more testing, because it could be the "component" of an input list.
        boolean Acomponent = ! getAttribute (node, "component").isEmpty ();
        if (Acomponent) preCellSingleton = models .getOrDefaultInt (modelName, A, "$n", "1") == 1;
        else            preCellSingleton = network.getOrDefaultInt (           A, "$n", "1") == 1;

        List<Connection> connections = new ArrayList<Connection> ();
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () != Node.ELEMENT_NODE) continue;

            // Collect data and assemble query
            Connection connection = new Connection ();
            int childID        = getAttribute (child, "id", 0);
            connection.inherit = getAttribute (child, "synapse", inherit);

            connection.preComponent  = getAttribute  (child, "preComponent");
            String preCell           = getAttributes (child, "preCell", "preCellId");
            String preSegmentString  = getAttributes (child, "preSegment", "preSegmentId");
            String preFractionString = getAttribute  (child, "preFractionAlong");

            connection.postComponent  = getAttribute  (child, "postComponent");
            String postCell           = getAttributes (child, "postCell", "postCellId", "target");
            String postSegmentString  = getAttributes (child, "postSegment", "postSegmentId", "segmentId");
            String postFractionString = getAttributes (child, "postFractionAlong", "fractionAlong");

            double weight = getAttribute (child, "weight", 1.0);
            String delay  = getAttribute (child, "delay");

            int preSegment = -1;
            if (! preSegmentString.isEmpty ()) preSegment = Integer.valueOf (preSegmentString);
            double preFraction = 0.5;
            if (! preFractionString.isEmpty ()) preFraction = Double.valueOf (preFractionString);

            int postSegment = -1;
            if (! postSegmentString.isEmpty ()) postSegment = Integer.valueOf (postSegmentString);
            double postFraction = 0.5;
            if (! postFractionString.isEmpty ()) postFraction = Double.valueOf (postFractionString);

            preCell  = extractIDfromPath (preCell);
            postCell = extractIDfromPath (postCell);
            if (instancesA != null) preCell  = instancesA.getOrDefault (preCell,  "$index", preCell);  // Map NeuroML ID to assigned N2A $index, falling back on ID if $index has not been assigned.
            if (instancesB != null) postCell = instancesB.getOrDefault (postCell, "$index", postCell);

            int preSegmentIndex = 0;
            boolean preSegmentSingleton = false;
            if (preSegment >= 0)
            {
                // preSegment is the ID, effectively the row in the segment*group matrix
                // We must find the column associated with it, so we can map to a group part.
                String cell = network.get (A, "$inherit").replace ("\"", "");
                MatrixBoolean M = cellSegment.get (cell);
                if (M != null)
                {
                    count = M.columns ();
                    for (int c = 0; c < count; c++)
                    {
                        if (M.get (preSegment, c))
                        {
                            connection.preGroup = models.get (modelName, cell, "$groupIndex", c);
                            preSegmentIndex = M.indexInColumn (preSegment, c);
                            preSegmentSingleton = models.getOrDefaultInt (modelName, cell, connection.preGroup, "$n", "1") == 1;
                            break;
                        }
                    }
                }
            }

            int postSegmentIndex = 0;
            boolean postSegmentSingleton = false;
            if (postSegment >= 0)
            {
                String cell = network.get (B, "$inherit").replace ("\"", "");
                MatrixBoolean M = cellSegment.get (cell);
                if (M != null)
                {
                    count = M.columns ();
                    for (int c = 0; c < count; c++)
                    {
                        if (M.get (postSegment, c))
                        {
                            connection.postGroup = models.get (modelName, cell, "$groupIndex", c);
                            postSegmentIndex = M.indexInColumn (postSegment, c);
                            postSegmentSingleton = models.getOrDefaultInt (modelName, cell, connection.postGroup, "$n", "1") == 1;
                            break;
                        }
                    }
                }
            }

            // Choose part
            int match = connections.indexOf (connection);
            if (match >= 0)  // Use existing part.
            {
                connection = connections.get (match);
            }
            else  // Create new part, cloning relevant info.
            {
                connections.add (connection);
                if (network.child (id) == null) connection.part = network.set (id,           base);
                else                            connection.part = network.set (id + childID, base);  // Another part has already consumed the base name, so augment it with some index. Any index will do, but childID is convenient.

                if (connection.postGroup.isEmpty ()) connection.part.set ("B", B);
                else                                 connection.part.set ("B", B + "." + connection.postGroup);
                if (connection.preGroup.isEmpty ())
                {
                    connection.part.set ("A", A);
                    if (Acomponent) addDependency (connection.part.child ("A"), A);
                }
                else
                {
                    connection.part.set ("A", A + "." + connection.preGroup);
                }
                if (! connection.preComponent.isEmpty ())
                {
                    connection.part.set ("preComponent", "$inherit", "\"" + connection.preComponent + "\"");
                    addDependency (connection.part.child ("preComponent"), connection.preComponent);
                }
                if (! connection.postComponent.isEmpty ())
                {
                    connection.part.set ("postComponent", "$inherit", "\"" + connection.postComponent + "\"");
                    addDependency (connection.part.child ("postComponent"), connection.postComponent);
                }
                if (! connection.inherit.isEmpty ())
                {
                    connection.part.set ("$inherit", "\"" + connection.inherit + "\"");
                    addDependency (connection.part, connection.inherit);
                }
            }
           
            // Add conditional info

            String condition = "";
            if (! preCellSingleton)
            {
                if (connection.preGroup.isEmpty ()) condition = "A.$index=="     + preCell;
                else                                condition = "A.$up.$index==" + preCell;
            }
            if (! postCellSingleton)
            {
                if (! condition.isEmpty ()) condition += "&&";
                if (connection.postGroup.isEmpty ()) condition += "B.$index=="     + postCell;
                else                                 condition += "B.$up.$index==" + postCell;
            }
            if (! preSegmentSingleton  &&  ! connection.preGroup.isEmpty ())
            {
                if (! condition.isEmpty ()) condition += "&&";
                condition += "A.$index==" + preSegmentIndex;
            }
            if (! postSegmentSingleton  &&  ! connection.postGroup.isEmpty ())
            {
                if (! condition.isEmpty ()) condition += "&&";
                condition += "B.$index==" + postSegmentIndex;
            }
            if (! condition.isEmpty ())
            {
                MNode p = connection.part.child ("$p");
                if (p == null)
                {
                    connection.part.set ("$p", condition);
                }
                else
                {
                    if (p.length () == 0)  // There is exactly one condition already existing, and we transition to multi-part equation.
                    {
                        p.set ("@", "0");
                        p.set ("@" + p.get (), "1");
                        p.set ("");
                    }
                    p.set ("@" + condition, "1");
                }
            }

            if (preFraction  != 0.5) connection.add (connection.preFractions,  preFraction,  condition);
            if (postFraction != 0.5) connection.add (connection.postFractions, postFraction, condition);
            if (weight       != 1.0) connection.add (connection.weights,       weight,       condition);
            if (! delay.isEmpty ())  connection.add (connection.delays,        delay,        condition);
        }
        for (Connection c : connections) c.injectConditionalValues ();

        if (connections.size () == 0)  // No connections were added, so add a minimalist projection part.
        {
            MNode part = network.set (id, base);
            if (! inherit.isEmpty ())
            {
                part.set ("$inherit", "\"" + inherit + "\"");
                addDependency (part, inherit);
            }
        }
    }

    /**
        Handle all the random and inconsistent ways that population member IDs are represented.
    **/
    public String extractIDfromPath (String path)
    {
        if (path.isEmpty ()) return path;

        int i = path.indexOf ('[');
        if (i >= 0)
        {
            String suffix = path.substring (i + 1);
            i = suffix.indexOf (']');
            if (i >= 0) suffix = suffix.substring (0, i);
            return suffix;
        }

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

    /**
        Handles elements in a generic manner, including metadata elements.
        Generic elements get processed into parts under the given container.
        Metadata elements get added to the $metadata node the given container.
    **/
    public void genericPart (Node node, MNode container)
    {
        String name = node.getNodeName ();
        if (name.equals ("notes"))
        {
            container.set ("$metadata", "notes", getText (node));
            return;
        }
        if (name.equals ("property"))
        {
            String tag   = getAttribute (node, "tag");
            String value = getAttribute (node, "value");
            container.set ("$metadata", tag, value);
            return;
        }
        if (name.equals ("annotation")) return;

        String id = getAttribute (node, "id", name);
        String stem = id;
        int suffix = 2;
        while (container.child (id) != null) id = stem + suffix++;
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

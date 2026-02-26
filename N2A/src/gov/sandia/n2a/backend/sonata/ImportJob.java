/*
Copyright 2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.sonata;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.JSON;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MNode.Visitor;
import gov.sandia.n2a.language.function.Table;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Backend.AbortRun;
import gov.sandia.n2a.plugins.extpoints.Import;
import gov.sandia.n2a.plugins.extpoints.ImportModel;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.eq.undo.AddDoc;
import io.jhdf.HdfFile;
import io.jhdf.api.Dataset;
import io.jhdf.api.Group;
import io.jhdf.api.Node;
import io.jhdf.exceptions.HdfInvalidPathException;

public class ImportJob
{
    protected MNode                        models        = new MVolatile ();
    protected String                       modelName     = "";
    public    MNode                        model;                                                   // The main model, inside "models", referenced by "modelName".
    protected Path                         dir;                                                     // Working directory, where config file is found.
    protected JSON                         json          = new JSON ();                             // We read a lot of JSON files.
    protected MNode                        config        = new MVolatile ();                        // The top-level config file for this SONATA model.
    public    MNode                        nodeTypes     = new MVolatile ();                        // {node name}/{model template}/{type id}/tree
    public    MNode                        edgeTypes     = new MVolatile ();                        // {edge name}/{model template}/{type id}/tree
    protected Map<String,Map<Long,String>> nodeTypeIndex = new HashMap<String,Map<Long,String>> (); // from node_type_id to model_template
    protected Map<String,Map<Long,String>> edgeTypeIndex = new HashMap<String,Map<Long,String>> (); // from edge_type_id to model_template
    protected String                       target_simulator;
    protected Map<String,ImportSONATApart> backends      = new HashMap<String,ImportSONATApart> ();

    public void process (Path source)
    {
        dir       = source.getParent ();
        modelName = dir.getFileName ().toString ();
        int index = modelName.lastIndexOf ('.');
        if (index > 0) modelName = modelName.substring (0, index);
        modelName = AddDoc.uniqueName (modelName);
        model = models.childOrCreate (modelName);
        model.set ("\"" + dir + "\"", "dir");

        ByteArrayOutputStream boas = new ByteArrayOutputStream ();
        try {Backend.err.set (new PrintStream (boas, false, "UTF-8"));}
        catch (Exception e) {}
        boolean failed = false;

        // Build table of backends that support SONATA.
        for (ExtensionPoint ext : PluginManager.getExtensionsForPoint (Import.class))
        {
            if (! (ext instanceof ImportSONATApart)) continue;
            ImportModel im = (ImportModel) ext;
            String name = im.getName ().toLowerCase ();
            backends.put (name, (ImportSONATApart) ext);
        }

        try
        {
            try (BufferedReader reader = Files.newBufferedReader (source))
            {
                json.read (config, reader);
            }

            target_simulator = config.get ("target_simulator").toLowerCase ();
            model.set (target_simulator, "$meta", "backend");  // TODO: may need to map some strings.

            substituteStrings ();
            collectTypes (nodeTypes, nodeTypeIndex, "node", dir.resolve (config.get ("components", "point_neuron_models_dir")));
            collectTypes (edgeTypes, nodeTypeIndex, "edge", dir.resolve (config.get ("components", "synaptic_models_dir")));
            generateModel ();
            generateTables (nodeTypes);
            generateTables (edgeTypes);
        }
        catch (IOException e)
        {
            failed = true;
            PrintStream ps = Backend.err.get ();
            e.printStackTrace (ps);
        }

        PrintStream ps = Backend.err.get ();
        if (ps != System.err) ps.close ();

        String headline = failed ? "Import failed" : "Import completed with warnings";

        String errors = "";
        try {errors = boas.toString ("UTF-8");}
        catch (Exception e2) {}

        if (! errors.isEmpty ())
        {
            if (AppData.properties.getFlag ("headless"))
            {
                System.err.println (headline);
                System.err.println (errors);
                return;
            }

            JTextArea textArea = new JTextArea (errors);
            JScrollPane scrollPane = new JScrollPane (textArea);
            scrollPane.setPreferredSize (new java.awt.Dimension (640, 480));
            JOptionPane.showMessageDialog
            (
                MainFrame.instance,
                scrollPane,
                headline,
                failed ? JOptionPane.ERROR_MESSAGE :  JOptionPane.WARNING_MESSAGE
            );
        }
    }

    public void substituteStrings ()
    {
        // SONATA naively imitates the appearance of string macros, without using a proper grammar for them.
        // This routine attempts to work around the resulting ambiguity by doing longest substitution first.

        // Fully expand all strings in the manifest.
        // May require multiple passes.
        MNode manifest = config.childOrEmpty ("manifest");
        boolean changed = true;
        for (int i = 0; i < manifest.size ()  &&  changed; i++)
        {
            changed = false;
            for (MNode m : manifest)
            {
                String value = m.get ();

                String longestMatch = "";
                for (MNode m2 : manifest)
                {
                    if (m2 == m) continue;
                    String key2 = m2.key ();
                    int pos = value.indexOf (key2);
                    if (pos >= 0  &&  key2.length () > longestMatch.length ()) longestMatch = key2;
                }
                if (longestMatch.isEmpty ()) continue;

                String value2 = manifest.get (longestMatch);
                m.set (value.replace (longestMatch, value2));
                changed = true;
            }
        }

        // Expand strings everywhere else.
        // Requires only one pass, since manifest is fully expanded.
        config.visit (new Visitor ()
        {
            public boolean visit (MNode node)
            {
                if (node == manifest) return false;

                String value = node.get ();
                boolean changed = false;
                for (int i = 0; i < manifest.size (); i++)
                {
                    String longestMatch = "";
                    for (MNode m2 : manifest)
                    {
                        String key2 = m2.key ();
                        int pos = value.indexOf (key2);
                        if (pos >= 0  &&  key2.length () > longestMatch.length ()) longestMatch = key2;
                    }
                    if (longestMatch.isEmpty ()) break;

                    String value2 = manifest.get (longestMatch);
                    value = value.replace (longestMatch, value2);
                    changed = true;
                }
                if (changed) node.set (value);

                return true;
            }
        });
    }

    public void collectTypes (MNode collection, Map<String,Map<Long,String>> index, String type, Path modelsDir) throws IOException
    {
        for (MNode n : config.childOrEmpty ("networks", type + "s"))
        {
            Path typesPath = dir.resolve (n.get (type + "_types_file"));
            Table.Holder H = new Table.Holder (typesPath);

            int index_population      = H.getColumnIndex ("population");
            int index_type_id         = H.getColumnIndex (type + "_type_id");
            int index_model_template  = H.getColumnIndex ("model_template");
            int index_dynamics_params = H.getColumnIndex ("dynamics_params");
            int rows = H.getRows ();
            int cols = H.getColumns ();

            String population      = "";
            String type_id         = "";
            String model_template  = "";
            String dynamics_params = "";

            // Verify that "population" column is actually present. Fall back if it isn't.
            if (index_population < 0)
            {
                // Our fallback assumption is that only one population is present,
                // and that the paired HDF5 file names this population.
                Path hdfPath = dir.resolve (n.get (type + "s_file"));
                try (HdfFile file = new HdfFile (hdfPath))
                {
                    Group graphElement = (Group) file.getChild (type + "s");
                    population = graphElement.getChildren ().keySet ().iterator ().next ();
                }
            }

            for (int r = 1; r < rows; r++)
            {
                // Extract the core values.
                // We assume that each model_template has only one model_type, so we do not use model_type to distinguish parts.
                if (index_population      >= 0) population      = H.getString (r, index_population);
                if (index_type_id         >= 0) type_id         = H.getString (r, index_type_id);
                if (index_model_template  >= 0) model_template  = H.getString (r, index_model_template);
                if (index_dynamics_params >= 0) dynamics_params = H.getString (r, index_dynamics_params);

                if (! model_template.contains (":")) model_template = target_simulator + ":" + model_template;

                Map<Long,String> populationIndex = index.get (population);
                if (populationIndex == null)
                {
                    populationIndex = new HashMap<Long,String> ();
                    index.put (population, populationIndex);
                }
                populationIndex.put (Long.valueOf (type_id), model_template);

                // Stash everything besides dynamics_params.
                for (int c = 0; c < cols; c++)
                {
                    if (c == index_population  ||  c == index_type_id  ||  c == index_model_template  ||  c == index_dynamics_params) continue;
                    String key   = H.getString (0, c);
                    String value = H.getString (r, c);
                    collection.set (value, population, model_template, type_id, key);
                }

                // Load dynamics_params
                if (dynamics_params.isBlank ()) continue;
                Path modelPath = modelsDir.resolve (dynamics_params);
                try (BufferedReader reader = Files.newBufferedReader (modelPath))
                {
                    MNode params = new MVolatile ();
                    json.read (params, reader);
                    collection.childOrCreate (population, model_template, type_id).merge (params);
                }
            }
        }

        for (MNode population : collection)
        {
            for (MNode model_template : population)
            {
                // Step 1 -- Create a union of model attributes.
                MNode result = new MVolatile ();
                for (MNode m : model_template) result.merge (m);

                // Step 2 -- Find attributes that are constant.
                result.visit (new Visitor ()
                {
                    public boolean visit (MNode node)
                    {
                        if (node == result) return true;  // Skip root.
                        if (! node.data ()) return true;  // Skip interior nodes. (When importing JSON, interior nodes have undefined value.)

                        String keypath[] = node.keyPath ();
                        String constant = node.get ();

                        boolean isString = false;
                        try {Double.valueOf (constant);}
                        catch (NumberFormatException e) {isString = true;}

                        for (MNode m : model_template)
                        {
                            String value = m.get (keypath);
                            if (! value.equals (constant))
                            {
                                node.set (null);  // Set value to undefined. Indicates that this value varies from type to type.

                                // If any value is not a number, then treat them all as string.
                                if (! value.isBlank ())  // The test is only meaningful if the value actually exists for this type.
                                {
                                    try {Double.valueOf (value);}
                                    catch (NumberFormatException e) {isString = true;}
                                }
                            }
                        }
                        if (isString) node.set (null, "$tring");  // Annotate the node as string by adding a child. We will trap this special value later, so it won't be mistaken for a regular model attribute.

                        return true;
                    }
                });

                model_template.set (result, "");
            }
        }
    }

    public static class GroupAttributes
    {
        public List<String>  names    = new ArrayList<String> ();
        public List<Dataset> datasets = new ArrayList<Dataset> ();

        public GroupAttributes (Group group)
        {
            Group dynamics_params = null;
            for (Entry<String,Node> entry : group.getChildren ().entrySet ())
            {
                String name = entry.getKey ();
                Node   node = entry.getValue ();
                if (name.equals ("dynamics_params"))
                {
                    dynamics_params = (Group) node;
                    continue;
                }
                names   .add (name);
                datasets.add ((Dataset) node);
            }

            if (dynamics_params == null) return;
            for (Entry<String,Node> entry : dynamics_params.getChildren ().entrySet ())
            {
                String name = entry.getKey ();
                Node   node = entry.getValue ();
                names   .add ("dynamics_params/" + name);
                datasets.add ((Dataset) node);
            }
        }

        public static HashMap<Integer,GroupAttributes> fromPopulation (Group population)
        {
            HashMap<Integer,GroupAttributes> result = new HashMap<Integer,GroupAttributes> ();
            for (Entry<String,Node> entry : population.getChildren ().entrySet ())
            {
                Node node = entry.getValue ();
                if (! node.isGroup ()) continue;

                int group_id = -1;
                try {group_id = Integer.valueOf (entry.getKey ());}
                catch (NumberFormatException error) {}
                if (group_id < 0) continue;

                result.put (group_id, new GroupAttributes ((Group) node));
            }
            return result;
        }
    }

    public void generateModel ()
    {
        long chunkSize = 1000000;  // Size for partial reads of table. Prevents memory depletion.
        long offset[] = new long[1];
        int  size  [] = new int [1];

        for (MNode n : config.childOrEmpty ("networks", "nodes"))
        {
            String nodes_file = n.get ("nodes_file");
            Path nodesPath = dir.resolve (nodes_file);
            try (HdfFile file = new HdfFile (nodesPath))
            {
                Group nodes = (Group) file.getChild ("nodes");
                for (Entry<String,Node> entry : nodes.getChildren ().entrySet ())
                {
                    Node   node           = entry.getValue ();
                    if (! node.isGroup ()) continue;  // This should never happen.
                    Group  population     = (Group) node;
                    String populationName = entry.getKey ();

                    // Collect group column lists.
                    HashMap<Integer,GroupAttributes> groupAttributes = GroupAttributes.fromPopulation (population);

                    // Determine if we need a map from node_id to $index.
                    long count = 0;
                    try
                    {
                        Dataset node_id = population.getDatasetByPath ("node_id");
                        BigInteger chunk[] = null;
                        count = node_id.getSize ();
                        for (long i = 0; i < count; i++)
                        {
                            if (i % chunkSize == 0)
                            {
                                offset[0] = i;
                                size[0] = (int) Math.min (chunkSize, count - i);
                                chunk = (BigInteger[]) node_id.getData (offset, size);
                            }
                            int index = (int) (i - offset[0]);
                            if (chunk[index].longValueExact () != i)
                            {
                                throw new AbortRun ("For now, SONATA import only handles zero-based contiguous node_id values.");
                            }
                        }
                    }
                    catch (HdfInvalidPathException e) {}  // Absence of "node_id" indicates straight map.

                    // Determine if we can use HDF data directly, or if we need to build sorted data tables.
                    // If there is only a single model and a single group, then it is possible to build a single N2A part.
                    // If the group indexing is zero-based and contiguous, then that single part can read HDF data directly based on $index.
                    // If there are multiple parts or the indexing is not contiguous, then it is necessary to build sorted table(s).
                    boolean canUseHDF = nodeTypes.child (populationName).size () == 1;  // Only one mathematical model.
                    if (canUseHDF)
                    {
                        // Contiguous node_group_index values imply that there is only one group.
                        try
                        {
                            Dataset node_group_index = population.getDatasetByPath ("node_group_index");
                            BigInteger chunk[] = null;
                            count = node_group_index.getSize ();
                            for (long i = 0; i < count; i++)
                            {
                                if (i % chunkSize == 0)
                                {
                                    offset[0] = i;
                                    size[0] = (int) Math.min (chunkSize, count - i);
                                    chunk = (BigInteger[]) node_group_index.getData (offset, size);
                                }
                                int index = (int) (i - offset[0]);
                                if (chunk[index].longValueExact () != i)
                                {
                                    canUseHDF = false;
                                    break;
                                }
                            }
                        }
                        catch (HdfInvalidPathException e) {}  // Absence of "node_group_index" indicates no groups.
                    }

                    // Generate model(s) and auxiliary files.
                    if (canUseHDF)
                    {
                        // Create a single part for the entire "population".

                        MNode modelTree = nodeTypes.child (populationName).iterator ().next ();  // Retrieve first (and only) model.

                        // Handle input population
                        // We expect all model_types under the same model_template to match.
                        // However, this is not guaranteed by the SONATA specification.
                        String model_type = modelTree.iterator ().next ().get ("model_type");
                        if (model_type.equals ("virtual"))
                        {
                            connectInput (populationName, count);
                            continue;
                        }

                        // Handle regular population
                        String raw_model_template = modelTree.key ();
                        String pieces[] = raw_model_template.split (":", 2);
                        String schema         = pieces[0];
                        String model_template = pieces[1];
                        ImportSONATApart importer = backends.get (schema);
                        if (importer == null) throw new AbortRun ("No suitable importer found for schema: " + schema);

                        String partName = populationName + " " + model_template;
                        model.set ("dir+\"/" + nodes_file + "\"",                                                   partName, "hdfFile");
                        model.set ("table(hdfFile, $index, 0, hdf5=\"nodes/" + populationName + "/node_type_id\")", partName, "node_type_id");

                        List<String> groupColumnNames = null;
                        if (groupAttributes.isEmpty ())
                        {
                            groupColumnNames = new ArrayList<String> ();
                        }
                        else
                        {
                            Integer group_id = groupAttributes.keySet ().iterator ().next ();
                            groupColumnNames = groupAttributes.get (group_id).names;
                            model.set ("\"nodes/" + populationName + "/" + group_id + "\"", partName, "groupPath");
                        }

                        importer.processPart (this, partName, "node", populationName, raw_model_template, groupColumnNames);
                    }
                    else
                    {
                        // TODO: Process each node individually, sorting them into proper N2A parts.
                        // Create files: "{population} {model_type} {group_id}.csv"
                        // Keep a map from said combinations (the string name) to open files.
                        /*
                        Dataset node_type_id     = population.getDatasetByPath ("node_type_id");
                        Dataset node_group_id    = population.getDatasetByPath ("node_group_id");
                        Dataset node_group_index = population.getDatasetByPath ("node_group_index");
                        BigInteger chunkType [] = null;
                        Integer    chunkGroup[] = null;
                        BigInteger chunkIndex[] = null;
                        for (long i = 0; i < count; i++)
                        {
                            if (i % chunkSize == 0)
                            {
                                offset[0] = i;
                                size[0] = (int) Math.min (chunkSize, count - i);
                                chunkType  = (BigInteger[]) node_type_id    .getData (offset, size);
                                chunkGroup = (Integer   []) node_group_id   .getData (offset, size);
                                chunkIndex = (BigInteger[]) node_group_index.getData (offset, size);
                            }

                            String instanceFile
                            // TODO
                        }
                        */
                    }
                }
            }
        }

        for (MNode e : config.childOrEmpty ("networks", "edges"))
        {
            String edges_file = e.get ("edges_file");
            Path edgesPath = dir.resolve (edges_file);
            try (HdfFile file = new HdfFile (edgesPath))
            {
                Group edges = (Group) file.getChild ("edges");
                for (Entry<String,Node> entry : edges.getChildren ().entrySet ())
                {
                    Node   edge           = entry.getValue ();
                    if (! edge.isGroup ()) continue;  // This should never happen.
                    Group  population     = (Group) edge;
                    String populationName = entry.getKey ();

                    HashMap<Integer,GroupAttributes> groupAttributes = GroupAttributes.fromPopulation (population);

                    boolean canUseHDF = edgeTypes.child (populationName).size () == 1;
                    long count = 0;
                    if (canUseHDF)
                    {
                        try
                        {
                            Dataset edge_group_index = population.getDatasetByPath ("edge_group_index");
                            BigInteger chunk[] = null;
                            count = edge_group_index.getSize ();
                            for (long i = 0; i < count; i++)
                            {
                                if (i % chunkSize == 0)
                                {
                                    offset[0] = i;
                                    size[0] = (int) Math.min (chunkSize, count - i);
                                    chunk = (BigInteger[]) edge_group_index.getData (offset, size);
                                }
                                int index = (int) (i - offset[0]);
                                if (chunk[index].longValueExact () != i)
                                {
                                    canUseHDF = false;
                                    break;
                                }
                            }
                        }
                        catch (HdfInvalidPathException error) {}
                    }

                    if (canUseHDF)
                    {
                        MNode modelTree = edgeTypes.child (populationName).iterator ().next ();
                        String raw_model_template = modelTree.key ();
                        String pieces[] = raw_model_template.split (":", 2);
                        String schema         = pieces[0];
                        String model_template = pieces[1];
                        ImportSONATApart importer = backends.get (schema);
                        if (importer == null) throw new AbortRun ("No suitable importer found for schema: " + schema);

                        String partName = populationName + " " + model_template;
                        model.set ("dir+\"/" + edges_file + "\"",                                                   partName, "hdfFile");
                        model.set ("table(hdfFile, $index, 0, hdf5=\"edges/" + populationName + "/edge_type_id\")", partName, "edge_type_id");

                        List<String> groupColumnNames = null;
                        if (groupAttributes.isEmpty ())
                        {
                            groupColumnNames = new ArrayList<String> ();
                        }
                        else
                        {
                            Integer group_id = groupAttributes.keySet ().iterator ().next ();
                            groupColumnNames = groupAttributes.get (group_id).names;
                            model.set ("\"edges/" + populationName + "/" + group_id + "\"", partName, "groupPath");
                        }

                        importer.processPart (this, partName, "edge", populationName, raw_model_template, groupColumnNames);
                    }
                    else
                    {
                        /*
                        Map<Long,String> populationIndex = edgeTypeIndex.get (populationName);

                        Dataset edge_type_id     = population.getDatasetByPath ("edge_type_id");
                        Dataset edge_group_id    = population.getDatasetByPath ("edge_group_id");
                        Dataset edge_group_index = population.getDatasetByPath ("edge_group_index");
                        Dataset source_node_id   = population.getDatasetByPath ("source_node_id");
                        Dataset target_node_id   = population.getDatasetByPath ("target_node_id");
                        BigInteger chunkType  [] = null;
                        Integer    chunkGroup [] = null;
                        BigInteger chunkIndex [] = null;
                        BigInteger chunkSource[] = null;
                        BigInteger chunkTarget[] = null;
                        count = edge_type_id.getSize ();
                        for (long i = 0; i < count; i++)
                        {
                            if (i % chunkSize == 0)
                            {
                                offset[0] = i;
                                size[0] = (int) Math.min (chunkSize, count - i);
                                chunkType   = (BigInteger[]) edge_type_id    .getData (offset, size);
                                chunkGroup  = (Integer   []) edge_group_id   .getData (offset, size);
                                chunkIndex  = (BigInteger[]) edge_group_index.getData (offset, size);
                                chunkSource = (BigInteger[]) source_node_id  .getData (offset, size);
                                chunkTarget = (BigInteger[]) target_node_id  .getData (offset, size);
                            }
                            int ir = (int) (i - offset[0]);  // relative index

                            long type_id = chunkType[ir].longValue ();
                            String raw_model_template = populationIndex.get (type_id);
                            String pieces[] = raw_model_template.split (":", 2);
                            String schema         = pieces[0];
                            String model_template = pieces[1];
                            ImportSONATApart importer = backends.get (schema);
                            if (importer == null) throw new AbortRun ("No suitable importer found for schema: " + schema);

                            String partName = populationName + " " + model_template;
                            model.set ("dir+\"/" + nodes_file + "\"",                                                   partName, "hdfFile");
                            model.set ("table(hdfFile, $index, 0, hdf5=\"nodes/" + populationName + "/node_type_id\")", partName, "node_type_id");


                            // TODO
                        }
                        */
                    }
                }
            }
        }
    }

    public void connectInput (String populationName, long count)
    {
        // model_template is probably the empty string ("").
        MNode part = model.childOrCreate (populationName);
        part.set ("Spike Source", "$inherit");
        part.set (count, "$n");

        // Attempt to determine a concrete input file and set it up as input.
        MNode inputs = config.childOrEmpty ("inputs");
        MNode input = null;
        for (MNode i : inputs)
        {
            String node_set = i.get ("node_set");
            if (! node_set.equals (populationName)) continue;
            input = i;
            break;
        }
        if (input == null) return;

        if (input.get ("input_type").equals ("spikes"))
        {
            // "spike" files are sorted first by node_id, then by time (in ms).
            // Columns are "timestamps" and "node_ids".
            // Both N2A and NeuroML represent a spike array as a list of times.
            // To make this work, convert the file into a sparse matrix with node_ids in the columns
            // and timestamps in the rows. Each column should be terminated with infinity.
            // That will make Spike Array stop incrementing its index. This allows varying-length columns.

            // S2 TODO: special optimization to set up host-side spike sender?
            //   alt: sparse representation that can be buffered in DRAM.

            if (input.get ("module").equals ("h5"))  // Read spikes from HDF5 file.
            {
                //part.set ("table(hdfFile, $t/1ms, )", "fire");
            }
        }
    }

    public void generateTables (MNode collection) throws IOException
    {
        Path n2aDir = dir.resolve ("n2a");
        Files.createDirectories (n2aDir);

        for (MNode population : collection)
        {
            String populationName = population.key ();
            for (MNode model_template : population)
            {
                String modelName = model_template.key ().split (":", 2)[1];
                Path typesPath = n2aDir.resolve (populationName + " " + modelName + " types.csv");  // TODO: this code assumes that node population names and edge population names never overlap. The SONATA guide does not promise this.
                try (BufferedWriter writer = Files.newBufferedWriter (typesPath))
                {
                    // Write header
                    MNode partAttributes = model_template.childOrEmpty ("");
                    boolean first = true;
                    for (MNode a : partAttributes)
                    {
                        if (! first) writer.write (" ");
                        first = false;
                        writer.write (a.key ());
                    }

                    // Write data
                    for (MNode t : model_template)
                    {
                        if (t == partAttributes) continue;
                        first = true;
                        for (MNode a : partAttributes)
                        {
                            if (! first) writer.write (" ");
                            first = false;
                            writer.write (t.get (a.key ()));
                        }
                    }
                }
            }
        }
    }
}

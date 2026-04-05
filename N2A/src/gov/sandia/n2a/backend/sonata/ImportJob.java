/*
Copyright 2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.sonata;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import gov.sandia.n2a.db.JSON;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MNode.Visitor;
import gov.sandia.n2a.language.function.Table;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Backend.AbortRun;
import gov.sandia.n2a.plugins.extpoints.Import;
import gov.sandia.n2a.plugins.extpoints.ImportModel;
import gov.sandia.n2a.ui.eq.undo.AddDoc;
import io.jhdf.HdfFile;
import io.jhdf.api.Dataset;
import io.jhdf.api.Group;
import io.jhdf.api.Node;
import io.jhdf.exceptions.HdfInvalidPathException;

public class ImportJob
{
    public    MNode                        models        = new MVolatile ();
    protected String                       modelName     = "";
    public    MNode                        model;                                                   // The main model, inside "models", referenced by "modelName".
    public    Path                         dir;                                                     // Working directory, where config file is found.
    public    Path                         n2aDir;                                                  // Directory under working directory where we store our own resources (results of conversion).
    protected JSON                         json          = new JSON ();                             // We read a lot of JSON files.
    public    MNode                        config        = new MVolatile ();                        // The top-level config file for this SONATA model.
    public    MNode                        nodeTypes     = new MVolatile ();                        // {node name}/{model template}/{type id}/tree
    public    MNode                        edgeTypes     = new MVolatile ();                        // {edge name}/{model template}/{type id}/tree
    protected Map<String,Map<Long,String>> nodeTypeIndex = new HashMap<String,Map<Long,String>> (); // from (population, node_type_id) to model_template
    protected Map<String,Map<Long,String>> edgeTypeIndex = new HashMap<String,Map<Long,String>> (); // from (population, edge_type_id) to model_template
    protected String                       target_simulator;
    protected Map<String,ImportSONATApart> backends      = new HashMap<String,ImportSONATApart> ();

    public static final long chunkSize = 1000000;  // Size for partial reads of table. Prevents memory depletion.

    public void process (Path source) throws IOException
    {
        dir       = source.getParent ();
        n2aDir    = dir.resolve ("n2a");
        modelName = dir.getFileName ().toString ();
        int index = modelName.lastIndexOf ('.');
        if (index > 0) modelName = modelName.substring (0, index);
        modelName = AddDoc.uniqueName (modelName);
        model = models.childOrCreate (modelName);
        model.set ("\"" + dir + "\"", "dir");
        Files.createDirectories (n2aDir);

        // Build table of backends that support SONATA.
        for (ExtensionPoint ext : PluginManager.getExtensionsForPoint (Import.class))
        {
            if (! (ext instanceof ImportSONATApart)) continue;
            ImportModel im = (ImportModel) ext;
            String name = im.getName ().toLowerCase ();
            switch (name)
            {
                case "neuroml": name = "nml"; break;  // Even though the backend key is "lems", the importer is named "NeuroML".
                case "neuron":  name = "nrn"; break;
            }
            backends.put (name, (ImportSONATApart) ext);
        }

        try (BufferedReader reader = Files.newBufferedReader (source))
        {
            json.read (config, reader);
            substituteStrings (config);
            include ("network");
            include ("simulation");
        }

        target_simulator = config.get ("target_simulator").toLowerCase ();
        model.set (target_simulator, "$meta", "backend");  // TODO: may need to map some strings.

        collectTypes (nodeTypes, nodeTypeIndex, "node", dir.resolve (config.get ("components", "point_neuron_models_dir")));
        collectTypes (edgeTypes, nodeTypeIndex, "edge", dir.resolve (config.get ("components", "synaptic_models_dir")));
        generateModel ();
        generateTables (nodeTypes);
        generateTables (edgeTypes);
    }

    public static void substituteStrings (MNode config)
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

    public void include (String key) throws IOException
    {
        String fileName = config.get (key);
        if (fileName.isBlank ()) return;

        try (BufferedReader reader = Files.newBufferedReader (dir.resolve (fileName)))
        {
            MVolatile temp = new MVolatile ();
            json.read (temp, reader);
            substituteStrings (temp);  // Each include file has its own manifest.
            config.merge (temp);
        }
    }

    public void collectTypes (MNode collection, Map<String,Map<Long,String>> index, String type, Path modelsDir) throws IOException
    {
        // Collect the types.
        for (MNode n : config.childOrEmpty ("networks", type + "s"))
        {
            Path typesPath = dir.resolve (n.get (type + "_types_file"));
            Table.Holder H = new Table.HolderSheet (typesPath);

            int index_population      = H.getColumnIndex ("population");
            int index_type_id         = H.getColumnIndex (type + "_type_id");
            int index_model_template  = H.getColumnIndex ("model_template");
            int index_dynamics_params = H.getColumnIndex ("dynamics_params");
            int rows                  = H.rows ();
            int cols                  = H.columns ();

            String population      = "";
            String type_id         = "";
            String model_template  = "";
            String dynamics_params = "";

            // Verify that "population" column is actually present. Fall back if it isn't.
            if (index_population < 0)
            {
                // Our fallback assumption is that only one population is present,
                // and that the paired HDF file names this population.
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
                if (index_population      >= 0) population      = getString (H, r, index_population);
                if (index_type_id         >= 0) type_id         = getString (H, r, index_type_id);
                if (index_model_template  >= 0) model_template  = getString (H, r, index_model_template);
                if (index_dynamics_params >= 0) dynamics_params = getString (H, r, index_dynamics_params);

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
                    if (c == index_population  ||  c == index_model_template  ||  c == index_dynamics_params) continue;
                    String key   = H.getString (0, c);
                    String value = H.getString (r, c);
                    collection.set (value, population, model_template, type_id, key);
                }

                // Load dynamics_params
                if (dynamics_params.isEmpty ()) continue;
                Path modelPath = modelsDir.resolve (dynamics_params);
                try (BufferedReader reader = Files.newBufferedReader (modelPath))
                {
                    MNode params = new MVolatile ();
                    json.read (params, reader);
                    collection.childOrCreate (population, model_template, type_id).merge (params);
                }
            }
        }
        collection.visit (new Visitor ()
        {
            public boolean visit (MNode node)
            {
                // Here we are setting values, whereas the getString() function guards against "NULL" in keys.
                if (node.get ().equals ("NULL")) node.set ("");
                return true;
            }
        });

        // Create union of parameter names and identify constants.
        Map<String,String> templates = new HashMap<String,String> ();  // From raw model_template name to imported part name, if there is an import.
        for (MNode population : collection)
        {
            for (MNode model_template : population)
            {
                templates.put (model_template.key (), "");  // Part name will be filled in later. This just collects all the templates.

                // Step 0 -- Expand path-name attributes
                String prefixMorphology        = config.get ("components", "morphologies_dir");
                String prefixElectrophysiology = config.get ("components", "");  // TODO: What is the right key? Found "fit" files in shared_components/biophysical_neuron_templates/json
                for (MNode group : model_template)
                {
                    MNode attribute = group.child ("morphology");
                    if (attribute != null  &&  ! prefixMorphology.isBlank ())
                    {
                        String value = attribute.get ();
                        if (! value.isBlank ()) attribute.set (prefixMorphology + "/" + value);
                    }

                    attribute = group.child ("electrophysiology");
                    if (attribute != null  &&  ! prefixElectrophysiology.isBlank ())
                    {
                        String value = attribute.get ();
                        if (! value.isBlank ()) attribute.set (prefixElectrophysiology + "/" + value);
                    }
                }

                // Step 1 -- Create a union of model attributes.
                MNode result = new MVolatile ();
                for (MNode m : model_template) result.merge (m);
                result.clear ("model_type");  // Don't include this column in attributes applied to parts. Other key attributes (such as model_template itself) are removed above, but this is left in individual trees for ease of access.

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

        // Import model_template items that reference model files rather than parameter files.
        // Convert model_template name to imported part name.
        for (Entry<String,String> t : templates.entrySet ())
        {
            String key = t.getKey ();
            String pieces[] = key.split (":", 2);
            String schema         = pieces[0];
            String model_template = pieces[1];
            ImportSONATApart importer = backends.get (schema);
            String partName = model_template;
            if (importer != null) partName = importer.prepare (this, model_template);
            t.setValue (schema + ":" + partName);
        }

        // Apply name remaps to model_templates and populationIndex.
        for (MNode population : collection)
        {
            Map<Long,String> populationIndex = index.get (population.key ());
            for (MNode model_template : population)
            {
                String key = model_template.key ();
                String mappedName = templates.get (key);
                if (mappedName.isBlank ()) continue;

                population.move (key, mappedName);  // This assumes that mappedName never appears in the population's original model_template names. Otherwise, another part with get overwritten.
                model_template = population.child (mappedName);

                for (MNode n : model_template)
                {
                    key = n.key ();
                    if (key.isEmpty ()) continue;  // Skip the union-constants node created above.
                    Long node_type_id = Long.valueOf (key);
                    populationIndex.put (node_type_id, mappedName);
                }
            }
        }
    }

    public String getString (Table.Holder H, int row, int column)
    {
        String result = H.getString (row, column).trim ();
        if (result.equals ("NULL")) return "";
        return result;
    }

    /**
        Manages one attribute column, and returns its value as a double.
    **/
    public static class Attribute
    {
        public Dataset  dataset;
        public Class<?> type;
        public double[] chunk;

        public Attribute (Dataset dataset)
        {
            this.dataset = dataset;
            type = dataset.getJavaType ();
        }

        /**
            Retrieves the next chunk of data and converts to double.
            The caller is responsible for managing when this is done, and the size of the block.
        **/
        public void read (long index, int count)
        {
            long offset[] = {index};
            int  size  [] = {count};
            Object temp = dataset.getData (offset, size);
            if (type == double.class)
            {
                chunk = (double[]) temp;
            }
            else if (type == float.class)
            {
                float[] t = (float[]) temp;
                chunk = new double[count];
                for (int i = 0; i < count; i++) chunk[i] = t[i];
            }
            else if (type == long.class)
            {
                long[] t = (long[]) temp;
                chunk = new double[count];
                for (int i = 0; i < count; i++) chunk[i] = t[i];
            }
            else if (type == int.class)
            {
                int[] t = (int[]) temp;
                chunk = new double[count];
                for (int i = 0; i < count; i++) chunk[i] = t[i];
            }
            else if (type == BigInteger.class)
            {
                BigInteger[] t = (BigInteger[]) temp;
                chunk = new double[count];
                for (int i = 0; i < count; i++) chunk[i] = t[i].doubleValue ();
            }
            else throw new AbortRun ("Unsupported data type in attribute.");
        }
    }

    /**
        A flat list of all datasets associated with a population group.
        These have the same length, and each one is like a column in a table of attributes for the group.
    **/
    public static class GroupAttributes
    {
        public int             id;  // Of the group that contains these attributes.
        public List<String>    names   = new ArrayList<String> ();
        public List<Attribute> columns = new ArrayList<Attribute> ();
        public long            rows;
        public long            offset;

        public GroupAttributes (int id, Group group)
        {
            this.id = id;

            Group dynamics_params = null;
            for (Node node : group)
            {
                String name = node.getName ();
                if (name.equals ("dynamics_params"))
                {
                    dynamics_params = (Group) node;
                    continue;
                }
                names.add (name);
                Dataset d = (Dataset) node;
                columns.add (new Attribute (d));
                if (rows == 0) rows = d.getSize ();
            }

            if (dynamics_params == null) return;
            for (Node node : dynamics_params)
            {
                names.add ("dynamics_params/" + node.getName ());
                Dataset d = (Dataset) node;
                columns.add (new Attribute (d));
                if (rows == 0) rows = d.getSize ();
            }
        }

        /**
            Retrieve chunks of data for all columns.
            @param index Absolute position (current row) in columns.
            This function assumes that index increase monotonically and never skips a row.
            If either of those assumptions are violated, a more general approach is needed.
        **/
        public void read (long index)
        {
            if (index % chunkSize != 0) return;
            offset = index;
            int count = (int) Math.min (chunkSize, rows - index);
            for (Attribute a : columns) a.read (index, count);
        }

        public static HashMap<Integer,GroupAttributes> fromPopulation (Group population)
        {
            HashMap<Integer,GroupAttributes> result = new HashMap<Integer,GroupAttributes> ();
            for (Node node : population)
            {
                if (! node.isGroup ()) continue;

                int group_id = -1;
                try {group_id = Integer.valueOf (node.getName ());}
                catch (NumberFormatException error) {}
                if (group_id < 0) continue;

                result.put (group_id, new GroupAttributes (group_id, (Group) node));
            }
            return result;
        }
    }

    public void generateModel () throws IOException
    {
        long offset[] = new long[1];
        int  size  [] = new int [1];

        for (MNode n : config.childOrEmpty ("networks", "nodes"))
        {
            String nodes_file = n.get ("nodes_file");
            Path nodesPath = dir.resolve (nodes_file);
            try (HdfFile file = new HdfFile (nodesPath))
            {
                Group nodes = (Group) file.getChild ("nodes");
                for (Node node : nodes)
                {
                    if (! node.isGroup ()) continue;  // This should never happen.
                    Group  population     = (Group) node;
                    String populationName = node.getName ();

                    // Collect group column lists.
                    HashMap<Integer,GroupAttributes> attributeGroups = GroupAttributes.fromPopulation (population);

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

                    // The tag "simple" means that one N2A part represents the entire population, and that node_id maps 1-to-1 with $index.
                    // * Requires that there be only one model_template and one group.
                    // * Requires that node_id be zero-based and contiguous (checked above).
                    // * Requires that node_group_index be zero-based and contiguous (checked below).
                    // If the population is not simple, then it is necessary to build sorted table(s), along with possibly more than one N2A part.
                    boolean simple = nodeTypes.child (populationName).size () == 1;  // Only one mathematical model.
                    if (simple)
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
                                    simple = false;
                                    break;
                                }
                            }
                        }
                        catch (HdfInvalidPathException e) {}  // Absence of "node_group_index" indicates no groups.
                    }

                    // Generate model(s) and auxiliary files.
                    if (simple)
                    {
                        // Create a single part for the entire "population".
                        String partName = populationName;
                        MNode  part     = model.childOrCreate (partName);
                        part.set ("",                            "$meta", "backend", "sonata", "simple");
                        part.set ("dir+\"/" + nodes_file + "\"", "hdfFile");

                        MNode modelTree = nodeTypes.child (populationName).iterator ().next ();  // Retrieve first (and only) model.
                        GroupAttributes groupAttributes = null;
                        if (! attributeGroups.isEmpty ())
                        {
                            groupAttributes = attributeGroups.values ().iterator ().next ();  // Retrieve the first (and only) attribute group.
                            part.set ("\"nodes/" + populationName + "/" + groupAttributes.id + "\"", "groupPath");
                        }

                        // We expect all model_types under the same model_template to match.
                        // However, this is not guaranteed by the SONATA specification.
                        String model_type = modelTree.iterator ().next ().get ("model_type");  // Retrieve first type row under the current model template, and get its model_type field.
                        if (model_type.equals ("virtual"))
                        {
                            // Handle input population
                            // model_template is probably the empty string ("").
                            connectInput (part, populationName, count, groupAttributes);
                        }
                        else
                        {
                            // Handle regular population
                            part.set ("table(hdfFile, $index, 0, hdf=\"nodes/" + populationName + "/node_type_id\")", "node_type_id");

                            String raw_model_template = modelTree.key ();
                            String pieces[] = raw_model_template.split (":", 2);
                            String schema   = pieces[0];
                            ImportSONATApart importer = backends.get (schema);
                            if (importer == null) throw new AbortRun ("No suitable importer found for schema: " + schema);

                            List<String> groupColumnNames;  // The columns associated with the group.
                            if (groupAttributes == null) groupColumnNames = new ArrayList<String> ();
                            else                         groupColumnNames = groupAttributes.names;

                            importer.processPart (this, partName, populationName, raw_model_template, groupColumnNames);
                        }
                    }
                    else
                    {
                        // Process each node individually, sorting them into proper N2A parts.
                        // Part names are: "{population} {model_template} {group_id}"
                        // One configuration file per part, named: "{part name}.csv"
                        // The config file holds all data associated with each instance. That includes
                        // "node_" attributes and also attributes from each column in the specific group.

                        Map<Long,String>           populationTypeIndex = nodeTypeIndex.get (populationName);
                        Map<String,BufferedWriter> writers             = new HashMap<String,BufferedWriter> ();

                        Dataset datasetType  = population.getDatasetByPath ("node_type_id");
                        Dataset datasetGroup = population.getDatasetByPath ("node_group_id");
                        Dataset datasetIndex = population.getDatasetByPath ("node_group_index");
                        BigInteger chunkType [] = null;
                        long       chunkGroup[] = null;
                        BigInteger chunkIndex[] = null;
                        for (long i = 0; i < count; i++)
                        {
                            if (i % chunkSize == 0)
                            {
                                offset[0] = i;
                                size[0] = (int) Math.min (chunkSize, count - i);
                                chunkType  = (BigInteger[]) datasetType .getData (offset, size);
                                chunkGroup = (long      []) datasetGroup.getData (offset, size);
                                chunkIndex = (BigInteger[]) datasetIndex.getData (offset, size);
                            }
                            int index = (int) (i - offset[0]);
                            long node_type_id     = chunkType [index].longValue ();
                            long node_group_id    = chunkGroup[index];
                            long node_group_index = chunkIndex[index].longValue ();

                            String raw_model_template = populationTypeIndex.get (node_type_id);
                            MNode modelTree = nodeTypes.child (populationName, raw_model_template);
                            GroupAttributes groupAttributes = null;
                            if (! attributeGroups.isEmpty ())
                            {
                                groupAttributes = attributeGroups.get (node_group_id);
                            }

                            String pieces[] = raw_model_template.split (":", 2);
                            String schema         = pieces[0];
                            String model_template = pieces[1];
                            ImportSONATApart importer = backends.get (schema);
                            if (importer == null) throw new AbortRun ("No suitable importer found for schema: " + schema);

                            String partName = populationName + " " + model_template + " " + node_group_id;  // TODO: use simpler name if there is only one model_template and/or only one node_group_id.
                            BufferedWriter writer = writers.get (partName);
                            if (writer == null)
                            {
                                writer = Files.newBufferedWriter (n2aDir.resolve (partName + " instances.csv"));
                                writers.put (partName, writer);

                                writer.write ("node_type_id");
                                if (groupAttributes != null)
                                {
                                    for (String name : groupAttributes.names) writer.write (" " + name);
                                }
                                writer.write ("\n");

                                MNode part = model.childOrCreate (partName);

                                String model_type = modelTree.get (node_type_id, "model_type");
                                if (model_type.equals ("virtual"))
                                {
                                    // This assumes only one source of info for input spikes, with populationName as the node_set.
                                    // The specification does not clearly promise this, unless "node_set" is equal to population.
                                    connectInput (part, populationName, count, groupAttributes);
                                }
                                else
                                {
                                    List<String> groupColumnNames;
                                    if (groupAttributes == null) groupColumnNames = new ArrayList<String> ();
                                    else                         groupColumnNames = groupAttributes.names;

                                    importer.processPart (this, partName, populationName, raw_model_template, groupColumnNames);
                                }
                            }

                            // Add all columns to part info file.
                            writer.write (Long.toString (node_type_id));
                            if (groupAttributes != null)
                            {
                                groupAttributes.read (node_group_index);
                                int index2 = (int) (node_group_index - groupAttributes.offset);
                                for (Attribute a : groupAttributes.columns) writer.write (" " + a.chunk[index2]);
                            }
                            writer.write ("\n");
                        }

                        for (BufferedWriter w : writers.values ()) w.close ();
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
                for (Node edge : edges)
                {
                    if (! edge.isGroup ()) continue;  // This should never happen.
                    Group  population     = (Group) edge;
                    String populationName = edge.getName ();

                    HashMap<Integer,GroupAttributes> groupAttributes = GroupAttributes.fromPopulation (population);

                    Node source_node_id = population.getChild ("source_node_id");
                    Node target_node_id = population.getChild ("target_node_id");
                    String source_node_population = source_node_id.getAttribute ("node_population").getData ().toString ();
                    String target_node_population = target_node_id.getAttribute ("node_population").getData ().toString ();
                    boolean Asimple = model.getFlag (source_node_population, "$meta", "backend", "sonata", "simple");  // The part might not even exist, in which case the value is correctly false.
                    boolean Bsimple = model.getFlag (target_node_population, "$meta", "backend", "sonata", "simple");

                    long count = ((Dataset) source_node_id).getSize ();

                    boolean simple = Asimple  &&  Bsimple  &&  edgeTypes.child (populationName).size () == 1;
                    if (simple)
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
                                    simple = false;
                                    break;
                                }
                            }
                        }
                        catch (HdfInvalidPathException error) {}
                    }

                    if (simple)
                    {
                        MNode modelTree = edgeTypes.child (populationName).iterator ().next ();
                        String raw_model_template = modelTree.key ();
                        String pieces[] = raw_model_template.split (":", 2);
                        String schema         = pieces[0];
                        String model_template = pieces[1];
                        ImportSONATApart importer = backends.get (schema);
                        if (importer == null) throw new AbortRun ("No suitable importer found for schema: " + schema);

                        String partName = populationName;
                        if (model.child (partName) != null) partName += " edge";
                        MNode part = model.childOrCreate (partName);

                        String table = "table(hdfFile, $index, 0, hdf=\"edges/" + populationName + "/edge_type_id\")";
                        part.set ("dir+\"/" + edges_file + "\"", "hdfFile");
                        part.set (table,                         "edge_type_id");
                        part.set (source_node_population,        "A");
                        part.set (target_node_population,        "B");

                        List<String> groupColumnNames = null;
                        if (groupAttributes.isEmpty ())
                        {
                            groupColumnNames = new ArrayList<String> ();
                        }
                        else
                        {
                            Integer group_id = groupAttributes.keySet ().iterator ().next ();
                            groupColumnNames = groupAttributes.get (group_id).names;
                            part.set ("\"edges/" + populationName + "/" + group_id + "\"", "groupPath");
                        }

                        importer.processPart (this, partName, populationName, raw_model_template, groupColumnNames);
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
                            model.set ("dir+\"/" + nodes_file + "\"",                                                  partName, "hdfFile");
                            model.set ("table(hdfFile, $index, 0, hdf=\"nodes/" + populationName + "/node_type_id\")", partName, "node_type_id");


                            // TODO
                        }
                        */
                    }
                }
            }
        }
    }

    /**
        Construct an input population.
        This is similar to the job of ImportSONATApart.processPart().
        @param part Already created.
        @param node_set Name as understood by the "inputs" section of the configuration file.
        @param count Size of population.
        @param groupAttributes Non-null if there are group attributes associated with this population.
    **/
    public void connectInput (MNode part, String node_set, long count, GroupAttributes groupAttributes)
    {
        part.set ("Spike Array", "$inherit");
        part.set (count,         "$n");

        // Attempt to determine a concrete input file and set it up as input.
        MNode inputs = config.childOrEmpty ("inputs");
        MNode input = null;
        for (MNode i : inputs)
        {
            if (! i.get ("node_set").equals (node_set)) continue;
            input = i;
            break;
        }
        if (input == null) return;

        String input_type = input.get ("input_type");
        String module     = input.get ("module");
        String input_file = input.get ("input_file");
        switch (input_type)
        {
            case "spikes":
            {
                // "spike" files are sorted first by node_id, then by time (in ms).
                // Columns are "timestamps" and "node_ids".
                // Both N2A and NeuroML represent a spike array as a list of times.
                // To make this work, convert the file into a sparse matrix with node_ids in the columns
                // and timestamps in the rows. Each column should be terminated with infinity.
                // That will make Spike Array stop incrementing its index. This allows varying-length columns.
                // (For a sparse matrix, the default value could be infinity, so no need to explicitly add element.)
    
                // S2 TODO: special optimization to set up host-side spike sender?
                //   alt: sparse representation that can be buffered in DRAM.
    
                switch (module)
                {
                    case "h5":
                    case "sonata":
                        part.set ("dir+\"/" + input_file + "\"",    "hdfFile");
                        part.set ("\"spikes/" + node_set + "\"",    "inputPath");
                        part.set ("matrix(hdfFile, hdf=inputPath)", "times");
                        part.set ("$t>=times(index, $index)*1ms",   "fire");
                        break;
                    case "csv":
                        part.set ("dir+\"/" + input_file + "\"",                     "spikesFile");
                        part.set ("matrix(spikesFile, sonata=\"" + node_set + "\")", "times");  // TODO: implement SONATA CSV spikes special matrix in ReadMatrix, similar to one in Table.
                        part.set ("$t>=times(index, $index)*1ms",                    "fire");
                        break;
                    default:
                        throw new AbortRun ("Unrecognized input module: " + module);
                }
                break;
            }
        }

        if (groupAttributes == null) return;
        for (String name : groupAttributes.names)
        {
            boolean dynamics_params = name.startsWith ("dynamics_params/");
            if (dynamics_params) name = name.substring (16);

            if (part.getFlag ("hdfFile"))
            {
                String table = "table(hdfFile, $index, \"" + name + "\", hdf=groupPath";
                if (dynamics_params) table += "+\"/dynamics_params\"";
                table += ")";
                part.set (table, name);
            }
            else
            {
                part.set ("dir+\"/n2a/" + part.key () + " instances.csv\"", "instanceFile");
                part.set ("table(instanceFile, $index, \"" + name + "\")", name);
            }
        }
        ImportSONATA.processXYZ (part);
    }

    public void generateTables (MNode collection) throws IOException
    {
        for (MNode population : collection)
        {
            String populationName = population.key ();
            for (MNode model_template : population)
            {
                String modelName = model_template.key ().split (":", 2)[1];
                // TODO: sometimes modelName is empty. Do we still need to emit a file? What are parameters used for in that case?
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
                    writer.write ("\n");

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
                        writer.write ("\n");
                    }
                }
            }
        }
    }
}

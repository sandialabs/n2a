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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.JSON;
import gov.sandia.n2a.db.MCombo;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MNode.Visitor;
import gov.sandia.n2a.db.MPartRepo;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.language.function.Output;
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
    public    Map<String,Integer>          modelCount    = new HashMap<String,Integer> ();          // Number of model_template uses in "nodeTypes" and "edgeTypes". Used to cull entries from "models" that get eliminated by folding.
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
            String name = backendSchema (im.getName ());
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
        target_simulator = backendSchema (target_simulator);

        collectTypes (nodeTypes, nodeTypeIndex, "node", dir.resolve (config.get ("components", "point_neuron_models_dir")));
        collectTypes (edgeTypes, edgeTypeIndex, "edge", dir.resolve (config.get ("components", "synaptic_models_dir")));
        generateModel ();
        generateTables (nodeTypes);
        generateTables (edgeTypes);

        for (Entry<String,Integer> e : modelCount.entrySet ())
        {
            if (e.getValue () < 1) models.clear (e.getKey ());
        }
    }

    /**
        Takes a backend or simulator name and outputs the corresponding SONATA schema code.
    **/
    public static String backendSchema (String name)
    {
        name = name.toLowerCase ();
        switch (name)
        {
            case "neuroml": return "nml";  // Even though the backend key is "lems", the importer is named "NeuroML".
            case "neuron":  return "nrn";
        }
        return name;
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
            // Keep a separate count of references to imported parts, rather than storing the count in the part itself.
            // This simplifies the task of comparing structure below.
            if (models.child (partName) != null)
            {
                Integer count = modelCount.get (partName);
                if (count == null) count = 0;
                modelCount.put (partName, count + 1);
            }
        }

        // Apply name re-maps to model_templates.
        for (MNode population : collection)
        {
            for (MNode model_template : population)
            {
                String key = model_template.key ();
                String mappedName = templates.get (key);
                // This assumes that mappedName never appears in the population's original model_template names.
                // Otherwise, another part will get overwritten.
                if (! mappedName.isBlank ()) population.move (key, mappedName);
            }
        }

        // Fold models with the same structure.
        for (MNode population : collection)
        {
            // Collate all models. This ensures that comparisons are on actual model structure,
            // not just the collection of local overrides.
            Map<String,MNode> collated = new HashMap<String,MNode> ();
            MCombo repo = new MCombo (null, models, AppData.docs.child ("models"));
            for (MNode model_template : population)
            {
                String key      = model_template.key ();
                String pieces[] = key.split (":", 2);
                if (pieces.length < 2) continue;
                String name     = pieces[1];
                MNode  source   = models.child (name);  // We're only interested in new models that are part of this import.
                if (source == null) continue;
                collated.put (name, new MPartRepo (source, repo));
            }

            for (MNode model_template1 : population)
            {
                if (model_template1 == null) continue;  // Because a node could have been removed after this iteration started.
                String key1     = model_template1.key ();
                String pieces[] = key1.split (":", 2);
                if (pieces.length < 2) continue;
                String name1    = pieces[1];
                MNode collated1 = collated.get (name1);
                if (collated1 == null) continue;
                MNode structure1 = model_template1.child ("");

                for (MNode model_template2 : population)
                {
                    // We only want to compare unique combinations of models, so need to ensure no backtracking.
                    // The models are iterated in M order, so we simply check the key until the inner loop passes the outer loop.
                    // This involves n^2 key compares, but only n^2/2 model compares.
                    String key2 = model_template2.key ();
                    if (MNode.compare (key2, key1) <= 0) continue;

                    pieces          = key2.split (":", 2);
                    if (pieces.length < 2) continue;
                    String name2    = pieces[1];
                    MNode collated2 = collated.get (name2);
                    if (collated2 == null) continue;
                    if (! collated2.structureEquals (collated1)) continue;
                    MNode structure2 = model_template2.child ("");

                    // Compare values.
                    MNode diff1 = new MVolatile ();
                    diff1.merge        (collated1);
                    diff1.uniqueValues (collated2);
                    MNode diff2 = new MVolatile ();
                    diff2.merge        (collated2);
                    diff2.uniqueValues (collated1);

                    // Check for constants that differ between structure1 and structure2.
                    MNode diffC = new MVolatile ();
                    diffC.merge   (structure2);  // copy of structure2
                    diffC.changes (structure1);  // diffC nodes are defined only when structure1 is a constant (defined) and different that structure2
                    diff1.merge   (diffC);       // Augment diff1 with constants from structure1. The code below will fill in the corresponding value in diff2, the distribute it to all node types.

                    // Update structure trees.
                    diff1.visit (new Visitor ()
                    {
                        public boolean visit (MNode node1)
                        {
                            String key       = node1.key ();
                            String keypath[] = node1.keyPath ();

                            if (key.equals ("$meta"))  // Don't represent any differences in metadata. TODO: could something like "poll" be important?
                            {
                                diff1.clear (keypath);
                                diff2.clear (keypath);
                                return false;
                            }
                            // TODO: Should we also try to detect equations, as opposed to parameters? This would indicate that the models should not be merged.
                            if (! node1.data ()) return true;  // Skip inner nodes. Note that it could be undefined only in collated1, in which case we would still want to process the value from collated2. However, this case is unlikely.

                            MNode node2 = diff2.child (keypath);
                            if (node2 == null) node2 = diff2.set (null, keypath);  // Probably because diff1 gained a node from a constant in structure1 that didn't match structure2. This will get resolved below.
                            boolean isString = false;
                            if (structure1.data (keypath))  // node has an associated constant in structure1
                            {
                                node1.set (structure1.get (keypath));  // Constant overrides model value.
                                if (structure1.child (keypath).getFlag ("$tring")) isString = true;
                            }
                            if (structure2.data (keypath))  // node has an associated constant in structure2
                            {
                                node2.set (structure2.get (keypath));
                                if (structure2.child (keypath).getFlag ("$tring")) isString = true;
                            }
                            if (! isString)
                            {
                                // This is coming from a difference in models, so there may be units with the numbers.
                                // Need to convert units.
                                if (checkString (node1)) isString = true;
                                if (checkString (node2)) isString = true;
                            }
                            MNode snode = structure1.set (null, keypath);  // Attribute should no longer be constant, since it varies between model_template1 and 2.
                            if (isString) snode.set (null, "$tring");

                            return true;
                        }
                    });

                    // Update node_type trees for model_template1.
                    for (MNode n : model_template1)
                    {
                        if (n.key ().isEmpty ()) continue;  // Skip the structure node.
                        n.mergeUnder (diff1);
                    }

                    // Update node_type trees for model_template2, and merge into model_template1.
                    for (MNode n : model_template2)
                    {
                        String nkey = n.key ();
                        if (nkey.isEmpty ()) continue;
                        n.mergeUnder (diff2);
                        model_template1.set (n, nkey);
                    }

                    // Delete model_template2
                    population.clear (key2);
                    Integer count = modelCount.get (name2);
                    modelCount.put (name2, count - 1);
                }
            }
        }

        // Build populationIndex.
        for (MNode population : collection)
        {
            Map<Long,String> populationIndex = index.get (population.key ());
            for (MNode model_template : population)
            {
                String templateName = model_template.key ();
                for (MNode n : model_template)
                {
                    String key = n.key ();
                    if (key.isEmpty ()) continue;  // Skip the structure node.
                    Long node_type_id = Long.valueOf (key);
                    populationIndex.put (node_type_id, templateName);
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

    public boolean checkString (MNode node)
    {
        String value = node.get ().trim ();
        if (UnitValue.findUnits (value) < value.length ()) return true;  // Some part of value does not match a number with units, so must be treated as string.

        // TODO: Number, possibly with units, so consider conversion.
        //UnitValue uv = new UnitValue (value);

        return false;
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
        public void read (long position, int count)
        {
            chunk = readDouble (dataset, position, count);
        }
    }

    public static double[] readDouble (Dataset dataset, long position, int count)
    {
        long offset[] = {position};
        int  size  [] = {count};
        Object temp = dataset.getData (offset, size);
        Class<?> type = dataset.getJavaType ();
        if (type == double.class)
        {
            return (double[]) temp;
        }
        if (type == float.class)
        {
            float[] t = (float[]) temp;
            double[] result = new double[count];
            for (int i = 0; i < count; i++) result[i] = t[i];
            return result;
        }
        if (type == long.class)
        {
            long[] t = (long[]) temp;
            double[] result = new double[count];
            for (int i = 0; i < count; i++) result[i] = t[i];
            return result;
        }
        else if (type == int.class)
        {
            int[] t = (int[]) temp;
            double[] result = new double[count];
            for (int i = 0; i < count; i++) result[i] = t[i];
            return result;
        }
        else if (type == BigInteger.class)
        {
            BigInteger[] t = (BigInteger[]) temp;
            double[] result = new double[count];
            for (int i = 0; i < count; i++) result[i] = t[i].doubleValue ();
            return result;
        }
        else throw new AbortRun ("Unhandled data type");
    }

    public static long[] readLong (Dataset dataset, long position, int count)
    {
        long offset[] = {position};
        int  size  [] = {count};
        Object temp = dataset.getData (offset, size);
        Class<?> type = dataset.getJavaType ();
        if (type == long.class)
        {
            return (long[]) temp;
        }
        if (type == BigInteger.class)
        {
            BigInteger[] t = (BigInteger[]) temp;
            long[] result = new long[count];
            for (int i = 0; i < count; i++) result[i] = t[i].longValueExact ();
            return result;
        }
        if (type == int.class)
        {
            int[] t = (int[]) temp;
            long[] result = new long[count];
            for (int i = 0; i < count; i++) result[i] = t[i];
            return result;
        }
        if (type == short.class)
        {
            short[] t = (short[]) temp;
            long[] result = new long[count];
            for (int i = 0; i < count; i++) result[i] = t[i];
            return result;
        }
        throw new AbortRun ("Unhandled integer type");
    }

    public static int[] readInt (Dataset dataset, long position, int count)
    {
        long offset[] = {position};
        int  size  [] = {count};
        Object temp = dataset.getData (offset, size);
        Class<?> type = dataset.getJavaType ();
        if (type == int.class)
        {
            return (int[]) temp;
        }
        if (type == BigInteger.class)
        {
            BigInteger[] t = (BigInteger[]) temp;
            int[] result = new int[count];
            for (int i = 0; i < count; i++) result[i] = t[i].intValueExact ();
            return result;
        }
        if (type == long.class)
        {
            long[] t = (long[]) temp;
            int[] result = new int[count];
            for (int i = 0; i < count; i++) result[i] = (int) t[i];
            return result;
        }
        if (type == short.class)
        {
            short[] t = (short[]) temp;
            int[] result = new int[count];
            for (int i = 0; i < count; i++) result[i] = t[i];
            return result;
        }
        throw new AbortRun ("Unhandled integer type");
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
        List<String>      codeToPart = new ArrayList<String> ();      // For non-simple populations, this maps from integer code to N2A part name.
        Map<String,Short> partToCode = new HashMap<String,Short> ();  // Reverse lookup for "codeToPart".

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
                    boolean multiGroup = attributeGroups.size () > 1;  // Notice that the negative case includes zero groups.

                    // Determine if we need a map from node_id to $index.
                    long count = 0;
                    try
                    {
                        Dataset node_id = population.getDatasetByPath ("node_id");
                        long chunk[] = null;
                        long offset = 0;
                        count = node_id.getSize ();
                        for (long i = 0; i < count; i++)
                        {
                            if (i % chunkSize == 0)
                            {
                                offset = i;
                                int size = (int) Math.min (chunkSize, count - i);
                                chunk = readLong (node_id, offset, size);
                            }
                            int ir = (int) (i - offset);
                            if (chunk[ir] != i)
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
                    boolean multiTemplate = nodeTypes.child (populationName).size () > 1;
                    boolean simple = ! multiTemplate  &&  ! multiGroup;  // Only one mathematical model.
                    if (simple)
                    {
                        // One would hope that the node_group_index values are sorted in ascending order,
                        // but the specification does not promise this.
                        try
                        {
                            Dataset node_group_index = population.getDatasetByPath ("node_group_index");
                            long chunk[] = null;
                            long offset = 0;
                            count = node_group_index.getSize ();
                            for (long i = 0; i < count; i++)
                            {
                                if (i % chunkSize == 0)
                                {
                                    offset = i;
                                    int size = (int) Math.min (chunkSize, count - i);
                                    chunk = readLong (node_group_index, offset, size);
                                }
                                int ir = (int) (i - offset);
                                if (chunk[ir] != i)
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
                        boolean multiType = modelTree.size () > 2;  // Includes key "", which holds the merged attributes.
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

                            String raw_model_template = modelTree.key ();
                            String pieces[] = raw_model_template.split (":", 2);
                            String schema         = pieces[0];
                            String model_template = pieces[1];
                            ImportSONATApart importer = backends.get (schema);
                            if (importer == null) throw new AbortRun ("No suitable importer found for schema: " + schema);

                            if (multiType)
                            {
                                part.set ("table(hdfFile, $index, 0, hdf=\"nodes/" + populationName + "/node_type_id\")", "node_type_id");
                                part.set ("dir+\"/n2a/" + populationName + " " + model_template + " types.csv\"",         "typeFile");
                            }

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

                        // There is one configuration file per part, named: "{part name} instances.csv"
                        // The config file holds all data associated with each instance. That includes
                        // "node_" attributes and also attributes from each column in the specific group.

                        // An file named "{part name}.index" (not CSV) contains a mapping from node_id
                        // to N2A part, along with the $index value within the part. These are stored in
                        // raw binary format, with fixed-size records: {16-bit index into table of parts}{32-bit $index}
                        // The table of parts is temporary, local to this conversion process.

                        class InstanceWriter
                        {
                            BufferedWriter writer;
                            int            count;
                        }
                        Map<String,InstanceWriter> writers      = new HashMap<String,InstanceWriter> ();
                        ByteChannel                indexChannel = null;
                        try
                        {
                            indexChannel = Files.newByteChannel (n2aDir.resolve (populationName + ".index"), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                            ByteBuffer indexBuffer = ByteBuffer.allocate (6);  // For one record in index
                            indexBuffer.order (ByteOrder.nativeOrder ());

                            Map<Long,String> populationTypeIndex = nodeTypeIndex.get (populationName);

                            Dataset datasetType  = population.getDatasetByPath ("node_type_id");
                            Dataset datasetGroup = population.getDatasetByPath ("node_group_id");
                            Dataset datasetIndex = population.getDatasetByPath ("node_group_index");
                            long chunkType [] = null;
                            long chunkGroup[] = null;
                            long chunkIndex[] = null;
                            long offset = 0;
                            for (long i = 0; i < count; i++)
                            {
                                if (i % chunkSize == 0)
                                {
                                    offset = i;
                                    int size = (int) Math.min (chunkSize, count - i);
                                    chunkType  = readLong (datasetType,  offset, size);
                                    chunkGroup = readLong (datasetGroup, offset, size);
                                    chunkIndex = readLong (datasetIndex, offset, size);
                                }
                                int ir = (int) (i - offset);
                                long node_type_id     = chunkType [ir];
                                long node_group_id    = chunkGroup[ir];
                                long node_group_index = chunkIndex[ir];

                                String raw_model_template = populationTypeIndex.get (node_type_id);
                                MNode modelTree = nodeTypes.child (populationName, raw_model_template);
                                boolean multiType = modelTree.size () > 2;
                                GroupAttributes attributes = null;
                                if (! attributeGroups.isEmpty ()) attributes = attributeGroups.get ((int) node_group_id);

                                String pieces[] = raw_model_template.split (":", 2);
                                String schema         = pieces[0];
                                String model_template = pieces[1];
                                ImportSONATApart importer = backends.get (schema);
                                if (importer == null) throw new AbortRun ("No suitable importer found for schema: " + schema);

                                String partName = populationName;
                                if (multiTemplate) partName += " " + model_template;
                                if (multiGroup)    partName += " " + node_group_id;
                                InstanceWriter iw = writers.get (partName);
                                if (iw == null)
                                {
                                    iw = new InstanceWriter ();
                                    iw.writer = Files.newBufferedWriter (n2aDir.resolve (partName + " instances.csv"));
                                    writers.put (partName, iw);

                                    boolean first = true;
                                    if (multiType)
                                    {
                                        iw.writer.write ("node_type_id");
                                        first = false;
                                    }
                                    if (attributes != null)
                                    {
                                        for (String name : attributes.names)
                                        {
                                            if (! first) iw.writer.write (" ");
                                            first = false;
                                            iw.writer.write (name);
                                        }
                                    }
                                    if (! first) iw.writer.write ("\n");

                                    MNode part = model.childOrCreate (partName);
                                    partToCode.put (partName, (short) codeToPart.size ());
                                    codeToPart.add (partName);
                                    part.set ("dir+\"/n2a/" + partName + " instances.csv\"", "instanceFile");

                                    String model_type = modelTree.get (node_type_id, "model_type");
                                    if (model_type.equals ("virtual"))
                                    {
                                        // This assumes only one source of info for input spikes, with populationName as the node_set.
                                        // The specification does not clearly promise this, unless "node_set" is equal to population.
                                        connectInput (part, populationName, count, attributes);
                                    }
                                    else
                                    {
                                        if (multiType)
                                        {
                                            part.set ("table(instanceFile, $index, \"node_type_id\")",                        "node_type_id");
                                            part.set ("dir+\"/n2a/" + populationName + " " + model_template + " types.csv\"", "typeFile");
                                        }

                                        List<String> groupColumnNames;
                                        if (attributes == null) groupColumnNames = new ArrayList<String> ();
                                        else                    groupColumnNames = attributes.names;

                                        importer.processPart (this, partName, populationName, raw_model_template, groupColumnNames);
                                    }
                                }

                                // Add all columns to part info file.
                                boolean first = true;
                                if (multiType)
                                {
                                    iw.writer.write (Long.toString (node_type_id));
                                    first = false;
                                }
                                if (attributes != null)
                                {
                                    attributes.read (node_group_index);
                                    int index = (int) (node_group_index - attributes.offset);
                                    for (Attribute a : attributes.columns)
                                    {
                                        if (! first) iw.writer.write (" ");
                                        first = false;
                                        iw.writer.write (String.valueOf (a.chunk[index]));
                                    }
                                }
                                if (! first) iw.writer.write ("\n");

                                // Write node_id mapping to index file.
                                short code = partToCode.get (partName);
                                indexBuffer.rewind ();
                                indexBuffer.putShort (code);
                                indexBuffer.putInt (iw.count++);
                                indexBuffer.rewind ();
                                indexChannel.write (indexBuffer);
                            }
                        }
                        finally
                        {
                            for (InstanceWriter iw : writers.values ()) iw.writer.close ();
                            if (indexChannel != null) indexChannel.close ();
                        }
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
                    boolean multiGroup = groupAttributes.size () > 1;

                    Dataset datasetSource = (Dataset) population.getChild ("source_node_id");
                    Dataset datasetTarget = (Dataset) population.getChild ("target_node_id");
                    String source_node_population = datasetSource.getAttribute ("node_population").getData ().toString ();
                    String target_node_population = datasetTarget.getAttribute ("node_population").getData ().toString ();
                    boolean Asimple = model.getFlag (source_node_population, "$meta", "backend", "sonata", "simple");  // The part might not even exist, in which case the value is correctly false.
                    boolean Bsimple = model.getFlag (target_node_population, "$meta", "backend", "sonata", "simple");

                    long count = datasetSource.getSize ();

                    // N2A edges (connections) are distinguished by (source part, target part, edge population, edge model_template, edge group)
                    // Usually there would be just one connection between any (source, target).
                    // However, the SONATA format allows multiple models connecting the same pair of parts.
                    // A "simple" edge is one that only needs a single N2A connection part.
                    // Implicitly, it can load directly from the HDF file.
                    // To be simple, there must be only one model_template and one group in the edge population.
                    // Also, the source and target populations must be simple (only one N2A part, with direct mapping from $index).
                    boolean multiTemplate = edgeTypes.child (populationName).size () > 1;
                    boolean simple = Asimple  &&  Bsimple  &&  ! multiTemplate  &&  ! multiGroup;
                    // No need to check mapping from position in edge lists to position in group attributes.
                    // If the edge structure is simple, then we can always use edge_group_index while iterating through connections.

                    if (simple)
                    {
                        MNode modelTree = edgeTypes.child (populationName).iterator ().next ();
                        boolean multiType = modelTree.size () > 2;
                        String raw_model_template = modelTree.key ();
                        String pieces[] = raw_model_template.split (":", 2);
                        String schema = pieces[0];
                        ImportSONATApart importer = backends.get (schema);
                        if (importer == null) throw new AbortRun ("No suitable importer found for schema: " + schema);

                        String partName = populationName;
                        if (model.child (partName) != null) partName += " edge";  // In case there is a name collision with nodes. There should never be a name collision with another edge.
                        MNode part = model.childOrCreate (partName);

                        part.set ("dir+\"/" + edges_file + "\"",                           "hdfFile");
                        part.set (source_node_population,                                  "A");
                        part.set (target_node_population,                                  "B");
                        part.set ("Medge(A.$index, B.$index)!=0@$connect",                 "$p");
                        part.set ("matrix(hdfFile, hdf=\"edges/" + populationName + "\")", "Medge");
                        if (multiType)
                        {
                            part.set ("matrix(hdfFile, hdf=\"edges/" + populationName + "/edge_type_id\")", "Medge_type_id");
                            part.set ("Medge_type_id(A.$index, B.$index)",                                  "edge_type_id");
                        }

                        List<String> groupColumnNames;
                        if (groupAttributes.isEmpty ()) groupColumnNames = new ArrayList<String> ();
                        else                            groupColumnNames = groupAttributes.values ().iterator ().next ().names;

                        importer.processPart (this, partName, populationName, raw_model_template, groupColumnNames);
                    }
                    else
                    {
                        // Process each edge individually, sorting them into proper N2A connection parts.
                        // Part names are: "{population name} {model_template} {group_id}"
                        // Note that edge population implies source and target, so no need to include source or target names.

                        Map<String,BufferedWriter> writers  = new HashMap<String,BufferedWriter> ();
                        SeekableByteChannel        channelA = null;
                        SeekableByteChannel        channelB = null;
                        try
                        {
                            if (! Asimple) channelA = Files.newByteChannel (n2aDir.resolve (source_node_population + ".index"));
                            if (! Bsimple)
                            {
                                if (source_node_population.equals (target_node_population)) channelB = channelA;  // Asimple must also be false, since they are the same population.
                                else                                                        channelB = Files.newByteChannel (n2aDir.resolve (target_node_population + ".index"));
                            }
                            ByteBuffer indexBuffer = ByteBuffer.allocate (6);  // For one record in index
                            indexBuffer.order (ByteOrder.nativeOrder ());
                            class IndexReader
                            {
                                String partName;
                                long   index;
                                void read (SeekableByteChannel channel, long node_id) throws IOException
                                {
                                    channel.position (node_id * 6);  // code (2 bytes) + index (4 bytes)
                                    indexBuffer.rewind ();
                                    channel.read (indexBuffer);
                                    indexBuffer.rewind ();
                                    short code = indexBuffer.getShort ();
                                    index      = indexBuffer.getInt ();
                                    partName = codeToPart.get (code);
                                }
                            }
                            IndexReader indexReader = new IndexReader ();

                            Map<Long,String> populationTypeIndex = edgeTypeIndex.get (populationName);

                            Dataset datasetType  = population.getDatasetByPath ("edge_type_id");
                            Dataset datasetGroup = population.getDatasetByPath ("edge_group_id");
                            Dataset datasetIndex = population.getDatasetByPath ("edge_group_index");
                            long chunkType  [] = null;
                            long chunkGroup [] = null;
                            long chunkIndex [] = null;
                            long chunkSource[] = null;
                            long chunkTarget[] = null;
                            long offset = 0;
                            count = datasetType.getSize ();
                            for (long i = 0; i < count; i++)
                            {
                                if (i % chunkSize == 0)
                                {
                                    offset = i;
                                    int size = (int) Math.min (chunkSize, count - i);
                                    chunkType   = readLong (datasetType,   offset, size);
                                    chunkGroup  = readLong (datasetGroup,  offset, size);
                                    chunkIndex  = readLong (datasetIndex,  offset, size);
                                    chunkSource = readLong (datasetSource, offset, size);
                                    chunkTarget = readLong (datasetTarget, offset, size);
                                }
                                int ir = (int) (i - offset);  // relative index
                                long edge_type_id     = chunkType  [ir];
                                long edge_group_id    = chunkGroup [ir];
                                long edge_group_index = chunkIndex [ir];
                                long source_node_id   = chunkSource[ir];
                                long target_node_id   = chunkTarget[ir];

                                String raw_model_template = populationTypeIndex.get (edge_type_id);
                                MNode modelTree = edgeTypes.child (populationName, raw_model_template);
                                boolean multiType = modelTree.size () > 2;
                                GroupAttributes attributes = null;
                                if (! groupAttributes.isEmpty ()) attributes = groupAttributes.get ((int) edge_group_id);

                                String pieces[] = raw_model_template.split (":", 2);
                                String schema         = pieces[0];
                                String model_template = pieces[1];
                                ImportSONATApart importer = backends.get (schema);
                                if (importer == null) throw new AbortRun ("No suitable importer found for schema: " + schema);

                                // Translate source and target node IDs to N2A part/$index
                                String partA;
                                String partB;
                                long indexA;
                                long indexB;
                                if (Asimple)
                                {
                                    partA  = source_node_population;
                                    indexA = source_node_id;
                                }
                                else
                                {
                                    indexReader.read (channelA, source_node_id);
                                    partA  = indexReader.partName;
                                    indexA = indexReader.index;
                                }
                                if (Bsimple)
                                {
                                    partB  = target_node_population;
                                    indexB = target_node_id;
                                }
                                else
                                {
                                    indexReader.read (channelB, target_node_id);
                                    partB  = indexReader.partName;
                                    indexB = indexReader.index;
                                }

                                String partName = populationName;
                                if (multiTemplate) partName += " " + model_template;
                                if (multiGroup)    partName += " " + edge_group_id;
                                // At this point, the edge is mathematically unique, but could apply to several different combinations
                                // of source and target N2A parts. If we encounter an edge part already defined, we make it unique
                                // by including specific endpoint part names.
                                if (model.child (partName) != null)
                                {
                                    partName += " " + partA;
                                    partName += " " + partB;
                                }
                                BufferedWriter writer = writers.get (partName);
                                if (writer == null)
                                {
                                    writer = Files.newBufferedWriter (n2aDir.resolve (partName + " instances.csv"));
                                    writers.put (partName, writer);

                                    writer.write ("source_node_id target_node_id");
                                    if (multiType) writer.write (" node_type_id");
                                    if (attributes != null)
                                    {
                                        for (String name : attributes.names) writer.write (" " + name);
                                    }
                                    writer.write ("\n");

                                    MNode part = model.childOrCreate (partName);
                                    part.set ("dir+\"/n2a/" + partName + " instances.csv\"", "instanceFile");
                                    part.set (partA,                                         "A");
                                    part.set (partB,                                         "B");
                                    part.set ("Medge(A.$index, B.$index)!=0@$connect",       "$p");
                                    part.set ("matrix(instanceFile, sonata=\"\")",           "Medge");
                                    if (multiType)
                                    {
                                        part.set ("matrix(instanceFile, sonata=\"edge_type_id\")", "Medge_type_id");
                                        part.set ("Medge_type_id(A.$index, B.$index)",             "edge_type_id");
                                    }

                                    List<String> groupColumnNames;
                                    if (attributes == null) groupColumnNames = new ArrayList<String> ();
                                    else                    groupColumnNames = attributes.names;
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

                                // Add all columns to part info file.
                                writer.write (Long.toString (indexA) + " " + Long.toString (indexB));
                                if (multiType) writer.write (" " + Long.toString (edge_type_id));
                                if (attributes != null)
                                {
                                    attributes.read (edge_group_index);
                                    int index = (int) (edge_group_index - attributes.offset);
                                    for (Attribute a : attributes.columns) writer.write (" " + a.chunk[index]);
                                }
                                writer.write ("\n");
                            }

                            // TODO: sort individual instance files by A.$index, then by B.$index (for fast load on S2)
                        }
                        finally
                        {
                            for (BufferedWriter w : writers.values ()) w.close ();
                            if (channelA != null) channelA.close ();
                            if (channelB != null) channelB.close ();
                        }
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
                    partAttributes.visit (new Visitor ()
                    {
                        boolean first = true;
                        public boolean visit (MNode node)
                        {
                            boolean isString = node.getFlag ("$tring");
                            if (! isString  &&  ! node.isEmpty ()) return true;  // Don't output inner nodes, but do descend.
                            if (node.data ()) return false;  // Node is constant, so it has already been embedded in model.
                            try
                            {
                                if (! first) writer.write (" ");
                                first = false;
                                writer.write (node.keyPathString (partAttributes));
                            }
                            catch (IOException e) {}  // If the file goes bad, we will find out soon enough below.
                            return false;  // This should be a leaf node, so don't descend. (Prevents us from visiting $tring node.)
                        }
                    });
                    writer.write ("\n");

                    // Write data
                    for (MNode t : model_template)
                    {
                        if (t == partAttributes) continue;
                        partAttributes.visit (new Visitor ()
                        {
                            boolean first = true;
                            public boolean visit (MNode node)
                            {
                                boolean isString = node.getFlag ("$tring");
                                if (! isString  &&  ! node.isEmpty ()) return true;
                                if (node.data ()) return false;
                                try
                                {
                                    if (! first) writer.write (" ");
                                    first = false;
                                    String keyPath[] = node.keyPath (partAttributes);
                                    writer.write (Output.Holder.escape (t.get (keyPath)));
                                }
                                catch (IOException e) {}
                                return false;
                            }
                        });
                        writer.write ("\n");
                    }
                }
            }
        }
    }
}

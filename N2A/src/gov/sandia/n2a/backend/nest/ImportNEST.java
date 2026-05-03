/*
Copyright 2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.nest;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import gov.sandia.n2a.backend.PartMap;
import gov.sandia.n2a.backend.PartMap.NameMap;
import gov.sandia.n2a.backend.sonata.ImportSONATA;
import gov.sandia.n2a.backend.sonata.ImportSONATApart;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MNode.Visitor;
import gov.sandia.n2a.db.MPart;
import gov.sandia.n2a.db.MPartRepo;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Backend.AbortRun;
import gov.sandia.n2a.plugins.extpoints.ImportModel;

public class ImportNEST extends ImportModel implements ImportSONATApart
{
    @Override
    public String getName ()
    {
        return "NEST";
    }

    @Override
    public MNode extractModels (Path source, String name) throws Exception
    {
        throw new Exception ("Not implemented");
    }

    @Override
    public float matches (Path source)
    {
        return 0;
    }

    @Override
    public boolean accept (Path source)
    {
        return false;
    }

    @Override
    public void processPart (gov.sandia.n2a.backend.sonata.ImportJob job, String partName, String population, String model_template, List<String> instanceAttributes)
    {
        if (PluginNEST.partMap == null) PluginNEST.partMap = new PartMap ("nest");

        MNode part = job.model.childOrCreate (partName);
        String B = part.get ("B");
        MNode Bpart = job.model.child (B);
        boolean isSynapse =  Bpart != null;

        int pos = model_template.indexOf (':');
        String externalPartName = pos < 0 ? model_template : model_template.substring (pos + 1);
        if (externalPartName.isBlank ()) throw new AbortRun (population + " model_template is missing.");

        if (isSynapse)
        {
            // For now, make the simple assumption that the neuron class supports exactly one synapse type.
            // TODO: handle arbitrary variety of synapse targets.
            String Binherit = MPart.parseInheritOne (Bpart.get ("$inherit"));
            MNode BbasePart = new MPartRepo (AppData.docs.childOrEmpty ("models", Binherit));
            String synapseClass = BbasePart.childOrEmpty ("$meta", "backend", "nest", "ports").iterator ().next ().key ();  // "ports" must be defined for this part, or the system is not set up right.

            NameMap map = PluginNEST.partMap.exportMap (synapseClass); // synapseClass is an internal name, so using exportMap(). Synapse names are neutral because NEST doesn't really describe them separately. However, parameter names are mapped.
            MNode basePart = new MPartRepo (AppData.docs.child ("models", synapseClass));
            part.set (synapseClass,   "$inherit");
            part.set (population,     "$meta", "backend", "sonata", "population");     // Currently, population is not directly represented in the model structure, just in the part name. Need this info for synapses.
            part.set (model_template, "$meta", "backend", "sonata", "model_template"); // ditto

            // Direct attributes
            MNode partAttributes = job.edgeTypes.child (population, model_template, "");
            partAttributes.visit (new Visitor ()
            {
                public boolean visit (MNode node)
                {
                    boolean isString = node.getFlag ("$tring");
                    if (! isString  &&  ! node.isEmpty ()) return true;  // Descend past interior nodes. Only leaves contain attributes.

                    String keyPath      = node.keyPathString (partAttributes);
                    String internalName = map.importName (keyPath);
                    applyAttribute (node, keyPath, internalName, "edge_type_id", false, false, basePart, part);
                    return false;  // Don't descend past a leaf node. The only thing to be found there is "$tring".
                }
            });

            // Indirect attributes from target neuron (B)
            boolean receptor_type  = part.child ("receptor_type") != null;
            String Bpopulation     = Bpart.get ("$meta", "backend", "sonata", "population");
            String Bmodel_template = Bpart.get ("$meta", "backend", "sonata", "model_template");
            MNode Battributes = job.nodeTypes.child (Bpopulation, Bmodel_template, "");
            for (MNode b : BbasePart.childOrEmpty ("$meta", "backend", "nest", "ports", synapseClass))  // Iterate through the attributes associated with this port.
            {
                String Bkey       = b.key ();
                String BkeyPath[] = Bkey.split ("\\.");
                MNode  Battribute = Battributes.child (BkeyPath);
                if (Battribute == null) continue;  // Does B have data for this attribute?

                String externalName = b.getOrDefault (Bkey);  // Translate from namespace of neuron to namespace of synapse. This is still an external name. It allows for NEST parts to name their synapse parameters inconsistently.
                String internalName = map.importName (externalName);
                Bkey = "\"" + Bkey;
                if (receptor_type) Bkey += ".\"+(receptor_type-1)";
                else               Bkey += "\"";
                applyAttribute (Battribute, Bkey, internalName, "node_type_id", false, true, basePart, part);
            }

            // Instance values
            for (String key : instanceAttributes)
            {
                boolean dynamics_params = key.startsWith ("dynamics_params/");
                if (dynamics_params) key = key.substring (16);
                String internalName = map.importName (key);  // We assume that all instance values are legit model parameters.

                String unit = UnitValue.safeUnit (basePart.get (internalName, "$meta", "backend", "nest", "unit"));
                String one  = unit.isBlank () ? "" : "*1" + unit;

                String temp = "matrix(";
                if (part.getFlag ("hdfFile"))
                {
                    temp += "hdfFile, hdf=groupPath+\"";
                    if (dynamics_params) temp += "/dynamics_params";
                    temp += "/" + key + "\")";
                }
                else
                {
                    temp += "instanceFile, sonata=\"" + key + "\")";
                }
                String M = "M" + internalName;
                part.set (temp,                             M);
                part.set (M + "(A.$index, B.$index)" + one, internalName);
            }
        }
        else  // neuron
        {
            NameMap map = PluginNEST.partMap.importMap (externalPartName);
            MNode basePart = new MPartRepo (AppData.docs.child ("models", map.internalPart));
            part.set (map.internalPart, "$inherit");
            part.set (population,       "$meta", "backend", "sonata", "population");     // Currently, population is not directly represented in the model structure, just in the part name. Need this info for synapses.
            part.set (model_template,   "$meta", "backend", "sonata", "model_template"); // ditto

            // Build mapping from attribute to child model.
            // (This work gets repeated every time a given part is used. However, it should not present a big cost.)
            Map<String,String> children = new HashMap<String,String> ();
            for (MNode child : basePart.childOrEmpty ("$meta", "backend", "nest", "children"))
            {
                String childPartName = child.key ();
                for (MNode attribute : child)
                {
                    children.put (attribute.key (), childPartName);
                }
            }

            // Collect forbidden synapse parameters
            Set<String> portParams = new HashSet<String> ();
            for (MNode port : basePart.childOrEmpty ("$meta", "backend", "nest", "ports"))
            {
                for (MNode name : port)
                {
                    portParams.add (name.key ());
                }
            }

            // Apply parameter table and constants
            MNode partAttributes = job.nodeTypes.child (population, model_template, "");
            partAttributes.visit (new Visitor ()
            {
                public boolean visit (MNode node)
                {
                    boolean isString = node.getFlag ("$tring");
                    if (! isString  &&  ! node.isEmpty ()) return true;
                    String key     = node.key ();
                    String keyPath = node.keyPathString (partAttributes);

                    // Handle children
                    // Parameters that are arrays have integer indices as their final key:
                    //   bob.0, bob.1, bob.2, ...
                    int subpartIndex = -1;
                    try {subpartIndex = Integer.valueOf (key);}
                    catch (NumberFormatException e) {}
                    boolean isCollection =  subpartIndex >= 0  &&  node.depth () > 1;

                    String columnName = keyPath;
                    if (isCollection) keyPath = node.parent ().keyPathString (partAttributes);  // Remove last path element (the index). Usually, the remaining path will be a single element.
                    if (portParams.contains (keyPath)) return false;  // Skip synapse parameters.

                    MNode   p = part;
                    NameMap m = map;
                    MNode   b = basePart;
                    if (isCollection)
                    {
                        String subpartName = children.get (keyPath);  // Retrieve internal part name using external parameter name.
                        if (subpartName == null)
                        {
                            Backend.err.get ().println ("Array parameter does not map to a port or subpart: " + keyPath);
                            return false;
                        }
                        p = part.childOrCreate (subpartName + " " + subpartIndex);
                        p.set (subpartName, "$inherit");
                        m = PluginNEST.partMap.exportMap (subpartName);  // subpartName is an internal name, so using exportMap()
                        b = new MPartRepo (AppData.docs.child ("models", m.internalPart));
                    }

                    String internalName = m.importName (keyPath);
                    applyAttribute (node, columnName, internalName, "node_type_id", isCollection, false, b, p);
                    return false;  // Don't descend past a leaf node. The only thing to be found there is "$tring".
                }
            });

            // Apply instance values
            for (String key : instanceAttributes)
            {
                boolean dynamics_params = key.startsWith ("dynamics_params/");
                if (dynamics_params) key = key.substring (16);
                String internalName = map.importName (key);  // We assume that all instance values are legit model parameters.

                String unit = UnitValue.safeUnit (basePart.get (internalName, "$meta", "backend", "nest", "unit"));
                String one  = unit.isBlank () ? "" : "*1" + UnitValue.safeUnit (unit);

                if (part.getFlag ("hdfFile"))
                {
                    String table = "table(hdfFile, $index, \"" + key + "\", hdf=groupPath";
                    if (dynamics_params) table += "+\"/dynamics_params\"";
                    table += ")" + one;
                    part.set (table, internalName);
                }
                else
                {
                    part.set ("table(instanceFile, $index, \"" + key + "\")" + one, internalName);
                }
            }

            ImportSONATA.processXYZ (part);
        }
    }

    public void applyAttribute (MNode attribute, String columnName, String internalName, String type_id, boolean subpart, boolean indirect, MNode basePart, MNode part)
    {
        boolean isString = attribute.getFlag ("$tring");

        // Retrieve units
        //System.out.println ("applyUnit: " + internalName + " -- " + basePart.key ());
        String unit = UnitValue.safeUnit (basePart.get (internalName, "$meta", "backend", "nest", "unit"));
        String one  = unit.isBlank () ? "" : "*1" + unit;

        // Apply value
        if (attribute.data ())  // Value is defined, so emit constant.
        {
            String value = attribute.get ();
            if (isString)
            {
                if (value.isBlank ()) return;
                value = "\"" + value + "\"";
            }
            else
            {
                UnitValue uv = new UnitValue (value);
                if (uv.unit == null) value += unit;  // Only tack on default unit if value does not explicitly state one.
            }
            part.set (value, internalName);
        }
        else  // Value is undefined, so emit table lookup.
        {
            if (! indirect  &&  ! subpart)
            {
                String population     = part.get ("$meta", "backend", "sonata", "population");
                String model_template = part.get ("$meta", "backend", "sonata", "model_template");
                String modelName = model_template.split (":", 2)[1];
                part.set ("dir+\"/n2a/" + population + " " + modelName + " types.csv\"", "typeFile");
            }

            String table = "table(";
            if (indirect) table += "B.typeFile, B." + type_id + ", "   + columnName;
            else          table += "typeFile, "     + type_id + ", \"" + columnName + "\"";
            table += ", key=\"" + type_id + "\"";
            if (isString) table += ", string=1)";  // Force return value to be string.
            else          table += ")" + one;
            part.set (table, internalName);
        }
    }
}

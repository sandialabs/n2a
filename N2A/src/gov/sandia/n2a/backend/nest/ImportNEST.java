/*
Copyright 2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.nest;

import java.nio.file.Path;
import java.util.List;

import gov.sandia.n2a.backend.PartMap;
import gov.sandia.n2a.backend.PartMap.NameMap;
import gov.sandia.n2a.backend.sonata.ImportSONATApart;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MNode.Visitor;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.plugins.extpoints.Backend.AbortRun;
import gov.sandia.n2a.plugins.extpoints.ImportModel;

public class ImportNEST implements ImportModel, ImportSONATApart
{
    @Override
    public String getName ()
    {
        return "NEST";
    }

    @Override
    public void process (Path source, String name) throws Exception
    {
    }

    @Override
    public void processPart (gov.sandia.n2a.backend.sonata.ImportJob job, String partName, String type, String population, String model_template, List<String> instanceAttributes)
    {
        if (PluginNEST.partMap == null) PluginNEST.partMap = new PartMap ("nest");

        int pos = model_template.indexOf (':');
        String externalPart = pos < 0 ? model_template : model_template.substring (pos + 1);
        if (externalPart.isBlank ()) throw new AbortRun (population + " model_template is missing.");
        NameMap map = PluginNEST.partMap.importMap (externalPart);

        MNode part = job.model.childOrCreate (partName);
        part.set (map.internalPart, "$inherit");
        MNode basePart = AppData.docs.child ("models", map.internalPart);

        String type_id = type + "_type_id";
        MNode partAttributes = (type.equals ("node") ? job.nodeTypes : job.edgeTypes).child (population, model_template, "");

        // Apply parameter table and constants
        partAttributes.visit (new Visitor ()
        {
            public boolean visit (MNode node)
            {
                // Parameters that are arrays have integer indices as their final key:
                //   bob.0, bob.1, bob.2, ...
                // Each array parameter belongs to some N2A part. The mapping should be in key:
                //   $meta.backend.nest.children.<external parameter> = <internal part>
                //   (Further mapping information is found in the internal part.)

                boolean isString = node.getFlag ("$tring");
                if (! isString  &&  ! node.isEmpty ()) return true;  // Descend past interior nodes. Only leaves contain attributes.
                String key     = node.key ();
                String keyPath = node.keyPathString (partAttributes);
                int subpartIndex = -1;
                try {subpartIndex = Integer.valueOf (key);}
                catch (NumberFormatException e) {}
                boolean isCollection =  subpartIndex >= 0  &&  node.depth () > 1;  // TODO: determine the right depth

                MNode   p = part;
                NameMap m = map;
                MNode   b = basePart;
                String  columnName = keyPath;
                if (isCollection)
                {
                    keyPath = node.parent ().keyPathString (partAttributes);  // Remove last path element (the index). Usually, the remaining path will be a single element.
                    String subpartName = basePart.get ("$meta", "backend", "nest", "children", keyPath);  // Retrieve internal part name using external parameter name.
                    if (subpartName.isEmpty ())  // This is a synapse parameter (NEST port), rather than a sub-part.
                    {
                        p = part.childOrCreate ("$meta", "backend", "nest", "port", subpartIndex);
                        m = new NameMap ();  // Store external param name, because we don't know the synapse part yet.
                        // If the value ends of being a table lookup, then the synapse part will need to obtain the neuron's file path.
                        // This can be done with string rewriting.
                    }
                    else  // sub-part
                    {
                        p = part.childOrCreate (subpartName + " " + subpartIndex);
                        p.set (subpartName, "$inherit");
                        m = PluginNEST.partMap.exportMap (subpartName);
                        b = AppData.docs.child ("models", m.internalPart);
                    }
                }

                String internalName = m.importName (keyPath);

                // Retrieve units
                String unit = UnitValue.safeUnit (b.get (internalName, "$meta", "backend", "nest", "unit"));
                String one  = unit.isBlank () ? "" : "*1" + unit;

                // Apply value
                if (node.data ())  // Value is defined, so emit constant.
                {
                    String value = node.get ();
                    if (isString)
                    {
                        value = "\"" + value + "\"";
                    }
                    else
                    {
                        UnitValue uv = new UnitValue (value);
                        if (uv.unit == null) value += unit;  // Only tack on default unit if value does not explicitly state one.
                    }
                    p.set (value, internalName);
                }
                else  // Value is undefined, so emit table lookup.
                {
                    part.set ("dir+\"/n2a/" + partName + " types.csv\"", "typeFile");

                    String table = "table(typeFile, " + type_id + ", \"" + columnName + "\", key=\"" + type_id + "\"";
                    if (isString) table += ", string=1)";  // Force return value to be string.
                    else          table += ")" + one;
                    p.set (table, internalName);
                }

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
                String table = "table(hdfFile, $index, \"" + key + "\", hdf5=groupPath";
                if (dynamics_params) table += "+\"/dynamics_params\"";
                table += ")" + one;
                part.set (table, internalName);
            }
            else
            {
                part.set ("dir+\"/n2a/" + partName + " instances.csv\"", "instanceFile");
                part.set ("table(instanceFile, $index, \"" + key + "\")" + one, internalName);
            }
        }

        // Post-process $xyz
        MNode x = part.child ("x");
        MNode y = part.child ("y");
        MNode z = part.child ("z");
        if (x != null  ||  y != null  ||  z != null)
        {
            String xyz = "[";
            xyz +=  x == null ? "0" : "x";  // Effectively, treating variable "x" as a temporary that just reads input.
            xyz += ";";
            xyz +=  y == null ? "0" : "y";
            xyz += ";";
            xyz +=  z == null ? "0" : "z";
            xyz += "]";
            part.set (xyz, "$xyz");
        }
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
}

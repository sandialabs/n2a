/*
Copyright 2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.sonata;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import gov.sandia.n2a.backend.PartMap;
import gov.sandia.n2a.backend.PartMap.NameMap;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.JSON;
import gov.sandia.n2a.db.MCombo;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPartRepo;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.db.MNode.Visitor;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.plugins.extpoints.ImportModel;
import gov.sandia.n2a.plugins.extpoints.Backend.AbortRun;

public class ImportSONATA extends ImportModel
{
    @Override
    public String getName ()
    {
        return "SONATA";
    }

    @Override
    public MNode extractModels (Path source, String name) throws Exception
    {
        ImportJob job = new ImportJob ();
        job.process (source);
        job.models.set (job.modelName);
        return job.models;
    }

    @Override
    public float matches (Path source)
    {
        String name = source.getFileName ().toString ();
        int lastDot = name.lastIndexOf ('.');
        String suffix = "";
        if (lastDot >= 0) suffix = name.substring (lastDot + 1).toLowerCase ();

        if (suffix.equals ("json"))
        {
            float result = 0;
            try (BufferedReader reader = Files.newBufferedReader (source))
            {
                MVolatile config = new MVolatile ();
                JSON json = new JSON ();
                json.read (config, reader);

                // For a config that includes other configs, look for the two major keys.
                if (config.child ("network")          != null) result += 0.5;
                if (config.child ("simulation")       != null) result += 0.5;

                // For a self-contained config, look for the most important sections.
                if (config.child ("networks")         != null) result += 0.5;
                if (config.child ("components")       != null) result += 0.2;
                if (config.child ("manifest")         != null) result += 0.1;
                if (config.child ("target_simulator") != null) result += 0.1;
                if (config.child ("run")              != null) result += 0.1;
            }
            catch (IOException e) {}  // and fall through to return 0
            return result;
        }

        return 0;
    }

    @Override
    public boolean accept (Path source)
    {
        if (Files.isDirectory (source)) return true;
        String name = source.getFileName ().toString ();
        String suffix = "";
        int lastDot = name.lastIndexOf ('.');
        if (lastDot >= 0) suffix = name.substring (lastDot + 1).toLowerCase ();
        if (suffix.equals ("json")) return true;
        return false;
    }

    /**
        Generic procedure that other backends can use to implement their processPart().
        @param job sonata.ImportJob instance
        @param partName Of the specific neuron sub-population
        @param population That contains the sub-population
        @param model_template Name of the neuron class
        @param instanceAttributes Names of columns (HDF datasets) in the group associated with the sub-population
    **/
    public static void processPart (String backend, PartMap partMap, ImportJob job, String partName, String population, String model_template, List<String> instanceAttributes)
    {
        int pos = model_template.indexOf (':');
        String externalPartName = pos < 0 ? model_template : model_template.substring (pos + 1);
        if (externalPartName.isBlank ()) throw new AbortRun (population + " model_template is missing.");

        NameMap map = partMap.importMap (externalPartName);
        //partMap.dump ();
        MCombo repo = new MCombo (null, job.models, AppData.docs.child ("models"));
        MNode basePart = new MPartRepo (repo.childOrEmpty (map.internalPart), repo);

        MNode part = job.model.childOrCreate (partName);
        part.set (map.internalPart, "$inherit");

        String B = part.get ("B");
        MNode Bpart = job.model.child (B);
        boolean isSynapse =  Bpart != null;

        // Apply direct attributes.
        String type_id;
        MNode partAttributes;
        if (isSynapse)
        {
            type_id = "edge_type_id";
            partAttributes = job.edgeTypes.child (population, model_template, "");
        }
        else
        {
            type_id = "node_type_id";
            partAttributes = job.nodeTypes.child (population, model_template, "");
        }
        partAttributes.visit (new Visitor ()
        {
            public boolean visit (MNode node)
            {
                boolean isString = node.getFlag ("$tring");
                if (! isString  &&  ! node.isEmpty ()) return true;  // Descend past interior nodes. Only leaves contain attributes.

                String keyPath      = node.keyPathString (partAttributes);
                String internalName = map.importName (keyPath);

                // Retrieve units
                String unit = UnitValue.safeUnit (basePart.get (internalName, "$meta", "backend", backend, "unit"));
                String one  = unit.isBlank () ? "" : "*1" + unit;

                // Apply value
                if (node.data ())  // Value is defined, so emit constant.
                {
                    String value = node.get ();
                    if (isString)
                    {
                        if (value.isBlank ()) return false;
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
                    part.set ("dir+\"/n2a/" + population + " " + externalPartName + " types.csv\"", "typeFile");  // This value may be set several times, but that does no harm.

                    String table = "table(typeFile, " + type_id + ", \"" + keyPath + "\"";
                    table += ", key=\"" + type_id + "\"";
                    if (isString) table += ", string=1)";  // Force return value to be string.
                    else          table += ")" + one;
                    part.set (table, internalName);
                }

                return false;  // Don't descend past a leaf node. The only thing to be found there is "$tring".
            }
        });

        // Apply instance values.
        for (String key : instanceAttributes)
        {
            boolean dynamics_params = key.startsWith ("dynamics_params/");
            if (dynamics_params) key = key.substring (16);
            String internalName = map.importName (key);  // We assume that all instance values are legit model parameters.

            String unit = UnitValue.safeUnit (basePart.get (internalName, "$meta", "backend", backend, "unit"));
            String one  = unit.isBlank () ? "" : "*1" + unit;

            if (isSynapse)
            {
                // Values are consolidated and streamed by a sparse iterator rather then being queried.
                if (part.getFlag ("hdfFile"))
                {
                    String table = "table(hdfFile, A.$index, B.$index, \"" + key + "\", hdf=groupPath";
                    if (dynamics_params) table += "+\"/dynamics_params\"";
                    table += ")" + one;
                    part.set (table, internalName);
                }
                else
                {
                    part.set ("dir+\"/n2a/" + partName + " instances.csv\"", "instanceFile");
                    part.set ("table(instanceFile, A.$index, B.$index, \"" + key + "\")" + one, internalName);
                }
            }
            else  // neuron
            {
                if (part.getFlag ("hdfFile"))
                {
                    String table = "table(hdfFile, $index, \"" + key + "\", hdf=groupPath";
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
        }

        if (! isSynapse) processXYZ (part);
    }

    public static void processXYZ (MNode part)
    {
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
}

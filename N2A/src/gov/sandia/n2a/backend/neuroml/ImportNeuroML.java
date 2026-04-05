/*
Copyright 2017-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import gov.sandia.n2a.backend.sonata.ImportSONATA;
import gov.sandia.n2a.backend.sonata.ImportSONATApart;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDir;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.ImportModel;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ImportNeuroML extends ImportModel implements ImportSONATApart
{
    @Override
    public String getName ()
    {
        return "NeuroML";
    }

    @Override
    public MNode extractModels (Path source, String name)
    {
        if (PluginNeuroML.partMap == null) PluginNeuroML.partMap = new PartMapNeuroML ();

        ImportJob job = new ImportJob (PluginNeuroML.partMap);
        job.process (source);
        job.postprocess ();

        if (name == null) name = job.modelName;
        else              job.models.move (job.modelName, name);  // If they match, this does nothing.
        job.models.set (name);
        return job.models;
    }

    @Override
    public float matches (Path source)
    {
        String name = source.getFileName ().toString ();
        int lastDot = name.lastIndexOf ('.');
        if (lastDot >= 0  &&  name.substring (lastDot).equalsIgnoreCase (".nml")) return 1;

        try (BufferedReader reader = Files.newBufferedReader (source))
        {
            String line = "";
            while (line.isEmpty ())
            {
                line = reader.readLine ();
                if (line == null) return 0;
                line = line.trim ();
            }
            if (line.startsWith ("<Lems")) return 1.0f;
            if (line.startsWith ("<?xml")) return 0.8f;
            // To be absolutely certain, could check for top-level tags that normally start a NeuroML section.
        }
        catch (IOException e) {}
        return 0;
    }

    @Override
    public boolean accept (Path source)
    {
        if (Files.isDirectory (source)) return true;
        String name = source.getFileName ().toString ();
        int lastDot = name.lastIndexOf ('.');
        if (lastDot >= 0)
        {
            String suffix = name.substring (lastDot);
            if (suffix.equalsIgnoreCase (".nml")) return true;
            if (suffix.equalsIgnoreCase (".xml")) return true;
        }
        return false;
    }

    @Override
    public String prepare (gov.sandia.n2a.backend.sonata.ImportJob job, String model_template)
    {
        // model_template contains a file name, located in biophysical_neuron_models_dir
        // We extract the model, then check if it matches an already-imported model.
        Path   models_dir = job.dir.resolve (job.config.get ("components", "biophysical_neuron_models_dir"));
        Path   path       = models_dir.resolve (model_template);
        MNode  models     = extractModels (path, null);
        String primary    = models.get ();
        MNode  model      = models.child (primary);

        MNode cell = model;
        if (! model.isEmpty ()) cell = model.iterator ().next ();  // Get first child.
        String key = MDir.validFilenameFrom (cell.key ());  // Make sure the cell name has a chance of being a model name.
        // We blindly assume that Allen-assigned cell names are always unique in this DB.
        // That can be false if there are several different versions (say, cells at different GLIF levels).
        // To cope with that, we may need some systematic way to assign suffixes to distinguish different versions of the same model.
        MNode existing = AppData.docs.child ("models", key);
        if (existing == null)
        {
            cell.set ("SONATA", "$meta", "gui", "category");  // There could be a lot of cell types from the Allen atlas. Putting models in the SONATA group will reduce clutter in main list.
            // TODO: Allen models exported from NEURON currently don't have proper connections between 4 main compartments (soma, axon, dend, apic).
            // If connections are missing, and if there are 4 compartments with these names, then add basic connections.
            // Should be suitable for mapping to an SWC file. Individual connections should be contingent on the existence of a line in the SWC.
            job.models.set (cell, key);  // Auxiliary models will be added at end of import.
        }
        return key;
    }

    @Override
    public void processPart (gov.sandia.n2a.backend.sonata.ImportJob job, String partName, String population, String model_template, List<String> instanceAttributes)
    {
        if (PluginNeuroML.partMap == null) PluginNeuroML.partMap = new PartMapNeuroML ();
        ImportSONATA.processPart ("lems", PluginNeuroML.partMap, job, partName, population, model_template, instanceAttributes);
    }
}

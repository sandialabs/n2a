/*
Copyright 2017-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.ImportModel;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ImportNeuroML extends ImportModel
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
}

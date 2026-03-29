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

import gov.sandia.n2a.db.JSON;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.plugins.extpoints.ImportModel;

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
}

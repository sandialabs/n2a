/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import gov.sandia.n2a.plugins.extpoints.Importer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class ImportNeuroML implements Importer
{
    @Override
    public String getName ()
    {
        return "NeuroML";
    }

    @Override
    public void process (File source)
    {
        new ImportJob ().process (source);
    }

    @Override
    public float isIn (File source)
    {
        String name = source.getName ();
        int lastDot = name.lastIndexOf ('.');
        if (lastDot >= 0  &&  name.substring (lastDot).equalsIgnoreCase (".nml")) return 1;

        try (BufferedReader reader = new BufferedReader (new FileReader (source)))
        {
            String line = reader.readLine ();
            if (line.toLowerCase ().startsWith ("<?xml")) return 0.8f;
            // To be absolutely certain, could check for top-level tags that normally start a NeuroML section.
        }
        catch (IOException e)
        {
        }
        return 0;
    }

    @Override
    public boolean accept (File source)
    {
        if (source.isDirectory ()) return true;
        String name = source.getName ();
        int lastDot = name.lastIndexOf ('.');
        if (lastDot >= 0  &&  name.substring (lastDot).equalsIgnoreCase (".nml")) return true;
        return false;
    }
}

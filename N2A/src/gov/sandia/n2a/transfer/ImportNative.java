/*
Copyright 2016-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.transfer;

import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.plugins.extpoints.Importer;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.eq.undo.AddDoc;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ImportNative implements Importer
{
    @Override
    public String getName ()
    {
        return "N2A Native";
    }

    @Override
    public void process (Path source)
    {
        try (BufferedReader reader = Files.newBufferedReader (source))
        {
            MVolatile doc = new MVolatile ();
            Schema.readAll (doc, reader);
            MainFrame.instance.undoManager.add (new AddDoc (source.getFileName ().toString (), doc));
        }
        catch (IOException e)
        {
        }
    }

    @Override
    public float isIn (Path source)
    {
        try (BufferedReader reader = Files.newBufferedReader (source))
        {
            String line = reader.readLine ();
            if (line.startsWith ("N2A.schema")) return 1;
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
        if (lastDot >= 0  &&  name.substring (lastDot).equalsIgnoreCase (".n2a")) return true;
        return false;
    }
}

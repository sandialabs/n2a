/*
Copyright 2016-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.transfer;

import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.plugins.extpoints.Importer;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.undo.AddDoc;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ImportNative implements Importer
{
    @Override
    public String getName ()
    {
        return "N2A Native";
    }

    @Override
    public void process (File source)
    {
        try (BufferedReader reader = Files.newBufferedReader (source.toPath ()))
        {
            reader.readLine ();  // dispose of schema line
            MVolatile doc = new MVolatile ();
            doc.read (reader);
            PanelModel.instance.undoManager.add (new AddDoc (source.getName (), doc));
        }
        catch (IOException e)
        {
        }
    }

    @Override
    public float isIn (File source)
    {
        try (BufferedReader reader = Files.newBufferedReader (source.toPath ()))
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

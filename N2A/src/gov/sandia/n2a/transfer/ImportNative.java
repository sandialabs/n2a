/*
Copyright 2016-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.transfer;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.plugins.extpoints.ImportModel;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ImportNative extends ImportModel
{
    @Override
    public String getName ()
    {
        return "N2A Native";
    }

    @Override
    public MNode extractModels (Path source, String name) throws IOException
    {
        if (name == null)
        {
            name = source.getFileName ().toString ();
            if (name.endsWith (".n2a")) name = name.substring (0, name.length () - 4);
        }

        try (BufferedReader reader = Files.newBufferedReader (source))
        {
            MNode result = new MVolatile (name, "");
            MNode doc    = result.childOrCreate (name);
            Schema.readAll (doc, reader);
            return result;
        }
    }

    @Override
    public float matches (Path source)
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
    public boolean accept (Path source)
    {
        if (Files.isDirectory (source)) return true;
        String name = source.getFileName ().toString ();
        int lastDot = name.lastIndexOf ('.');
        if (lastDot >= 0  &&  name.substring (lastDot).equalsIgnoreCase (".n2a")) return true;
        return false;
    }
}

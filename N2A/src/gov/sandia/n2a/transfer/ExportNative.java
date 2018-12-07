/*
Copyright 2016-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.transfer;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.plugins.extpoints.Exporter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ExportNative implements Exporter
{
    @Override
    public String getName ()
    {
        return "N2A Native";
    }

    @Override
    public void export (MNode source, File destination)
    {
        try
        {
            // Write a standard repository file. See MDoc.save()
            BufferedWriter writer = Files.newBufferedWriter (destination.toPath ());
            Schema schema = Schema.latest ();
            schema.write (writer);
            for (MNode n : source) schema.write (n, writer, "");
            writer.close ();
        }
        catch (IOException e)
        {
        }
    }
}

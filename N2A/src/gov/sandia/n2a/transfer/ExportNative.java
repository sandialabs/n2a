/*
Copyright 2016-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.transfer;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.plugins.extpoints.ExportModel;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExportNative implements ExportModel
{
    @Override
    public String getName ()
    {
        return "N2A Native";
    }

    @Override
    public void process (MNode source, Path destination) throws IOException
    {
        // Write a standard repository file. See MDoc.save()
        BufferedWriter writer = Files.newBufferedWriter (destination);
        Schema schema = Schema.latest ();
        schema.write (writer);
        for (MNode n : source) schema.write (n, writer, "");
        writer.close ();
    }

    @Override
    public boolean accept (Path source)
    {
        if (Files.isDirectory (source)) return true;
        String name = source.getFileName ().toString ();
        int lastDot = name.lastIndexOf ('.');
        if (lastDot < 0) return true;  // Assume a file with no suffix could be an n2a file.
        String suffix = name.substring (lastDot);
        if (suffix.equalsIgnoreCase (".n2a")) return true;
        return false;
    }
}

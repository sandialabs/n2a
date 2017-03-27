/*
Copyright 2016,2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.transfer;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Exporter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

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
            BufferedWriter writer = new BufferedWriter (new FileWriter (destination));
            writer.write (String.format ("N2A.schema=1%n"));
            for (MNode n : source) n.write (writer, "");
            writer.close ();
        }
        catch (IOException e)
        {
        }
    }
}

/*
Copyright 2016,2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.transfer;

import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.plugins.extpoints.Importer;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.undo.AddDoc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

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
        try
        {
            BufferedReader reader = new BufferedReader (new FileReader (source));
            reader.readLine ();  // dispose of schema line
            MVolatile doc = new MVolatile ();
            doc.read (reader);
            reader.close ();
            ModelEditPanel.instance.undoManager.add (new AddDoc (source.getName (), doc));
        }
        catch (IOException e)
        {
        }
    }
}

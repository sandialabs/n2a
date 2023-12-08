/*
Copyright 2021-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import gov.sandia.n2a.db.MDir;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.plugins.extpoints.Import;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.ref.undo.AddEntry;

public abstract class ImportBibliography implements Import
{
    @Override
    public void process (Path path, String name)
    {
        try (BufferedReader reader = Files.newBufferedReader (path))
        {
            MNode data = new MVolatile ();
            parse (reader, data);

            for (MNode n : data)  // data can contain several entries
            {
                String key = MDir.validFilenameFrom (n.key ());
                MainFrame.undoManager.apply (new AddEntry (key, n));
            }
        }
        catch (IOException e)
        {
        }
    }

    @Override
    public float matches (Path source)
    {
        if (accept (source)  &&  ! Files.isDirectory (source)) return 1;

        try (BufferedReader reader = Files.newBufferedReader (source))
        {
            return matches (reader);
        }
        catch (IOException e) {}
        return 0;
    }

    public abstract float matches (BufferedReader reader) throws IOException;
    public abstract void parse (BufferedReader input, MNode output) throws IOException;
}

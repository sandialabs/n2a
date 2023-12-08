/*
Copyright 2019-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.vensim;

import gov.sandia.n2a.plugins.extpoints.ImportModel;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.eq.undo.AddDoc;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ImportVensim implements ImportModel
{
    @Override
    public String getName ()
    {
        return "Vensim";
    }

    @Override
    public void process (Path source, String name)
    {
        ImportJob job = new ImportJob ();
        job.process (source);
        if (job.model.size () > 0)
        {
            if (name == null) name = job.modelName;
            MainFrame.undoManager.apply (new AddDoc (name, job.model));
        }
    }

    @Override
    public float matches (Path source)
    {
        try (BufferedReader reader = Files.newBufferedReader (source))
        {
            String line = reader.readLine ();
            if (line.startsWith ("{UTF-8}")) return 1;  // This is a terrible string to search for, but it happens to start Vensim mdl files.
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
        if (lastDot >= 0  &&  name.substring (lastDot).equalsIgnoreCase (".mdl")) return true;
        return false;
    }
}

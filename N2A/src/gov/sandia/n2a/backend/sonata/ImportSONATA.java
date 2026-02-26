/*
Copyright 2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.sonata;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import gov.sandia.n2a.db.JSON;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.plugins.extpoints.ImportModel;
import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.UndoManager;
import gov.sandia.n2a.ui.eq.undo.AddDoc;

public class ImportSONATA implements ImportModel
{
    @Override
    public String getName ()
    {
        return "SONATA";
    }

    @Override
    public void process (Path source, String name) throws Exception
    {
        ImportJob job = new ImportJob ();
        job.process (source);

        MNode mainModel = job.models.child (job.modelName);
        job.models.clear (job.modelName);

        UndoManager um = MainFrame.undoManager;
        um.addEdit (new CompoundEdit ());
        for (MNode m : job.models)
        {
            AddDoc add = new AddDoc (m.key (), m);
            add.setSilent ();
            um.apply (add);
        }
        if (mainModel != null) um.apply (new AddDoc (job.modelName, mainModel));
        um.endCompoundEdit ();
    }

    @Override
    public float matches (Path source)
    {
        String name = source.getFileName ().toString ();
        int lastDot = name.lastIndexOf ('.');
        String suffix = "";
        if (lastDot >= 0) suffix = name.substring (lastDot + 1).toLowerCase ();

        if (suffix.equals ("json"))
        {
            float result = 0;
            try (BufferedReader reader = Files.newBufferedReader (source))
            {
                MVolatile config = new MVolatile ();
                JSON json = new JSON ();
                json.read (config, reader);
                if (config.child ("networks")         != null) result += 0.5;
                if (config.child ("components")       != null) result += 0.2;
                if (config.child ("manifest")         != null) result += 0.1;
                if (config.child ("target_simulator") != null) result += 0.1;
                if (config.child ("run")              != null) result += 0.1;
            }
            catch (IOException e) {}  // and fall through to return 0
            return result;
        }

        return 0;
    }

    @Override
    public boolean accept (Path source)
    {
        if (Files.isDirectory (source)) return true;
        String name = source.getFileName ().toString ();
        String suffix = "";
        int lastDot = name.lastIndexOf ('.');
        if (lastDot >= 0) suffix = name.substring (lastDot + 1).toLowerCase ();
        if (suffix.equals ("json")) return true;
        return false;
    }
}

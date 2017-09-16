/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Importer;
import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.UndoManager;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.undo.AddDoc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class ImportNeuroML implements Importer
{
    @Override
    public String getName ()
    {
        return "NeuroML";
    }

    @Override
    public void process (File source)
    {
        ImportJob job = new ImportJob ();
        job.process (source);
        job.postprocess ();

        UndoManager um = PanelModel.instance.undoManager;
        um.addEdit (new CompoundEdit ());
        for (MNode m : job.models)
        {
            String key = m.key ();
            if (key.equals (job.modelName)) continue;  // Save the best for last. That is, ensure that the main model is the one selected after all add operations are applied.
            um.add (new AddDoc (key, m));
        }
        um.add (new AddDoc (job.modelName, job.models.child (job.modelName)));
        um.endCompoundEdit ();
    }

    @Override
    public float isIn (File source)
    {
        String name = source.getName ();
        int lastDot = name.lastIndexOf ('.');
        if (lastDot >= 0  &&  name.substring (lastDot).equalsIgnoreCase (".nml")) return 1;

        try (BufferedReader reader = new BufferedReader (new FileReader (source)))
        {
            String line = reader.readLine ();
            if (line.startsWith ("<Lems")) return 1.0f;
            if (line.startsWith ("<?xml")) return 0.8f;
            // To be absolutely certain, could check for top-level tags that normally start a NeuroML section.
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
        if (lastDot >= 0)
        {
            String suffix = name.substring (lastDot);
            if (suffix.equalsIgnoreCase (".nml")) return true;
            if (suffix.equalsIgnoreCase (".xml")) return true;
        }
        return false;
    }
}

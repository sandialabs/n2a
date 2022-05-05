/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.ExportModel;
import gov.sandia.n2a.ui.jobs.NodeJob;
import gov.sandia.n2a.ui.jobs.PanelRun;

import java.awt.EventQueue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ExportCstatic implements ExportModel
{
    protected boolean shared;

    @Override
    public String getName ()
    {
        return "C static library";
    }

    @Override
    public void export (MNode source, Path destination) throws Exception
    {
        // Approach: Create a proper C job, then copy select resources to the destination.

        MDoc job = null;
        try
        {
            // Do everything normally involved in launching a C job.
            // This is adapted from PanelEquations.launchJob()
            String jobKey = new SimpleDateFormat ("yyyy-MM-dd-HHmmss", Locale.ROOT).format (new Date ());
            job = (MDoc) AppData.runs.childOrCreate (jobKey);
            NodeJob.collectJobParameters (source, source.key (), job);
            job.save ();
            NodeJob.saveSnapshot (source, job);

            JobC t = new JobC (job);
            t.lib    = true;  // library flag. Will build a library then return (model not executed).
            t.shared = shared;
            t.run ();  // Process on current thread rather than starting a new one.

            // Move library resources to destination
            Compiler.Factory factory = BackendC.getFactory (t.env);  // to get suffixes
            String stem        = destination.getFileName ().toString ();
            Path   parent      = destination.getParent ();
            String suffix      = factory.suffixLibraryStatic ();
            Path   libraryFrom = t.jobDir.resolve ("model" + suffix);  // hard-coded assumption that "model" is stem of source file
            Path   libraryTo   = parent  .resolve (stem    + suffix);
            Path   headerFrom  = t.jobDir.resolve ("model.h");
            Path   headerTo    = parent  .resolve (stem + ".h");
            Files.move (libraryFrom, libraryTo, StandardCopyOption.REPLACE_EXISTING);
            Files.move (headerFrom,  headerTo,  StandardCopyOption.REPLACE_EXISTING);
            if (shared)
            {
                suffix      = factory.suffixLibraryShared ();
                libraryFrom = t.jobDir.resolve ("model" + suffix);
                libraryTo   = parent  .resolve (stem    + suffix);
                Files.move (libraryFrom, libraryTo, StandardCopyOption.REPLACE_EXISTING);
            }

            // Cleanup
            AppData.runs.clear (jobKey);
        }
        catch (Exception e)
        {
            // Add job to UI so the user can diagnose C build problems.
            if (job != null)
            {
                final MDoc finalJob = job;
                EventQueue.invokeLater (new Runnable ()
                {
                    public void run ()
                    {
                        PanelRun.instance.addNewRun (finalJob, false);
                    }
                });
            }

            throw e;
        }
    }
}

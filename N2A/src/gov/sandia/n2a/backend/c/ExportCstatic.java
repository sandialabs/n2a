/*
Copyright 2022-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.host.Remote;
import gov.sandia.n2a.plugins.extpoints.ExportModel;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.MainTabbedPane;
import gov.sandia.n2a.ui.jobs.NodeBase;
import gov.sandia.n2a.ui.jobs.NodeJob;
import gov.sandia.n2a.ui.jobs.PanelRun;

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import javax.swing.tree.TreePath;

public class ExportCstatic implements ExportModel
{
    protected boolean shared;
    protected boolean lib = true;

    @Override
    public String getName ()
    {
        return "C library static";
    }

    @Override
    public void process (MNode source, Path destination) throws Exception
    {
        // Approach: Create a proper C job, then copy specific resources to the destination.

        MDoc job = null;
        try
        {
            // Do everything normally involved in launching a C job.
            // This is adapted from PanelEquations.launchJob()
            String jobKey = new SimpleDateFormat ("yyyy-MM-dd-HHmmss", Locale.ROOT).format (new Date ());
            job = (MDoc) AppData.runs.childOrCreate (jobKey);
            NodeJob.collectJobParameters (source, source.key (), job);
            job.save ();
            //NodeJob.saveSnapshot (source, job);  // Currently no use for snapshot in exports.

            boolean headless = AppData.properties.getFlag ("headless");
            if (! headless)
            {
                final MDoc finalJob = job;
                EventQueue.invokeLater (new Runnable ()
                {
                    public void run ()
                    {
                        MainTabbedPane mtp = (MainTabbedPane) MainFrame.instance.tabs;
                        mtp.setPreferredFocus (PanelRun.instance, PanelRun.instance.tree);
                        mtp.selectTab ("Runs");
                        NodeJob jobNode = PanelRun.instance.addNewRun (finalJob, true);
                        Host h = Host.get (finalJob);
                        h.monitor (jobNode);
                    }
                });
            }

            String stem = destination.getFileName ().toString ();
            int pos = stem.lastIndexOf ('.');
            if (pos > 0) stem = stem.substring (0, pos);  // In case user accidentally selected an existing file with wrong suffix.

            JobC t = new JobC (job);
            t.lib     = lib;  // library flag. Will build a library then return (model not executed).
            t.shared  = shared;
            t.libStem = stem;
            t.run ();  // Export is already on its own thread, so no need to start a new one for this.

            // No exception is thrown by t.run().
            // If anything goes wrong, it shows up in the "finished" file.
            String line = "";
            Path finished = t.localJobDir.resolve ("finished");
            try (BufferedReader reader = Files.newBufferedReader (finished))
            {
                line = reader.readLine ();
            }
            catch (IOException e) {}
            if (! line.isEmpty ()) throw new Exception ("Compile failed. See 'err' file for details.");

            // Move resources to destination
            // Notice that even though the export may be built on a remote host
            // (per host key in model), it will be copied to a local directory.
            CompilerFactory factory = BackendC.getFactory (t.env);  // to get suffixes
            Path parent = destination.getParent ();
            if (lib)
            {
                String prefix      = factory.prefixLibrary (shared);
                String suffix      = factory.suffixLibrary (shared);
                Path   libraryFrom = t.jobDir.resolve (prefix + stem + suffix);
                Path   libraryTo   = parent  .resolve (prefix + stem + suffix);
                Path   headerFrom  = t.jobDir.resolve (stem + ".h");
                Path   headerTo    = parent  .resolve (stem + ".h");
                Files.move (libraryFrom, libraryTo, StandardCopyOption.REPLACE_EXISTING);
                Files.move (headerFrom,  headerTo,  StandardCopyOption.REPLACE_EXISTING);
                if (shared)
                {
                    if (factory.wrapperRequired ())
                    {
                        String wrapper = factory.suffixLibraryWrapper ();
                        libraryFrom = t.jobDir.resolve (stem + wrapper);
                        libraryTo   = parent  .resolve (stem + wrapper);
                        Files.move (libraryFrom, libraryTo, StandardCopyOption.REPLACE_EXISTING);
                    }
                    if (t.debug  &&  factory.debugRequired ())
                    {
                        String debug = factory.suffixDebug ();
                        Path from = t.jobDir.resolve (stem + debug);
                        Path to   = parent  .resolve (stem + debug);
                        Files.move (from, to, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            else  // binary
            {
                // Kill job and wait for exit. On some platforms, the binary file is locked when the program is executing.
                t.env.killJob (job, true);  // force kill. No need for graceful shutdown.
                boolean live = t.env.isAlive (job);  // This test can potentially take a long time, even longer than 1 second.
                for (int i = 0; i < 10  &&  live; i++)
                {
                    Thread.sleep (100);
                    live = t.env.isAlive (job);
                }

                String suffixBinary = factory.suffixBinary ();
                String suffixShell  = t.env.shellSuffix ();
                Path binaryFrom = t.jobDir.resolve ("model"   + suffixBinary);
                Path binaryTo   = parent  .resolve (stem      + suffixBinary);
                Path shellFrom  = t.jobDir.resolve ("n2a_job" + suffixShell);
                Path shellTo    = parent  .resolve (stem      + suffixShell);
                Files.move (binaryFrom, binaryTo, StandardCopyOption.REPLACE_EXISTING);
                if (t.env instanceof Remote)
                {
                    Files.move (shellFrom, shellTo, StandardCopyOption.REPLACE_EXISTING);
                }
                else  // localhost. Rewrite paths to match destination dir
                {
                    String shellScript = Files.readString (shellFrom);
                    shellScript = shellScript.replace (t.jobDir.toString (), parent.toString ());
                    shellScript = shellScript.replace ("model" + suffixBinary, stem + suffixBinary);
                    Files.writeString (shellTo, shellScript, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
                }
            }
            if (t.cli)
            {
                Path from = t.jobDir.resolve ("params");
                Path to   = parent  .resolve (stem + ".params");
                Files.move (from, to, StandardCopyOption.REPLACE_EXISTING);
            }

            // Cleanup
            if (source.getFlag ("$meta", "backend", "c", "keepExportJob")) return;
            if (headless)
            {
                AppData.runs.clear (jobKey);
                return;
            }
            EventQueue.invokeLater (new Runnable ()
            {
                public void run ()
                {
                    NodeJob jobNode;
                    synchronized (PanelRun.jobNodes) {jobNode = PanelRun.jobNodes.get (jobKey);}
                    if (jobNode == null) return;

                    MainTabbedPane mtp = (MainTabbedPane) MainFrame.instance.tabs;
                    int tabIndex = mtp.getSelectedIndex ();
                    String tabName = "";
                    if (tabIndex >= 0) tabName = mtp.getTitleAt (tabIndex);
                    PanelRun pr = PanelRun.instance;
                    TreePath path = pr.tree.getLeadSelectionPath ();
                    NodeBase selectedNode = (NodeBase) path.getLastPathComponent ();
                    boolean wasShowing =  tabName.equals ("Runs")  &&  selectedNode != null  &&  selectedNode.isNodeAncestor (jobNode);

                    jobNode.complete = 1;  // Fix race condition between job monitor thread and this thread.
                    pr.delete (jobNode);
                    if (wasShowing) mtp.selectTab ("Models");
                }
            });
        }
        catch (Exception e)
        {
            if (job != null)
            {
                Path localJobDir = Host.getJobDir (Host.getLocalResourceDir (), job);
                try {Host.stringToFile ("failure", localJobDir.resolve ("finished"));}
                catch (Exception f) {}
            }

            throw e;
        }
    }

    @Override
    public boolean accept (Path source)
    {
        if (Files.isDirectory (source)) return true;
        String name = source.getFileName ().toString ();
        int lastDot = name.lastIndexOf ('.');
        if (! lib  &&  lastDot < 0) return true;  // File without suffix could be a binary.
        if (lastDot >= 0)
        {
            String suffix = name.substring (lastDot);
            // TODO: add MacOS suffixes here
            if (! lib)  // binary
            {
                if (suffix.equalsIgnoreCase (".exe")) return true;
                if (suffix.equalsIgnoreCase (".bin")) return true;
            }
            else if (shared)  // shared library
            {
                if (suffix.equalsIgnoreCase (".dll")) return true;
                if (suffix.equalsIgnoreCase (".so" )) return true;
            }
            else  // static library
            {
                if (suffix.equalsIgnoreCase (".lib")) return true;
                if (suffix.equalsIgnoreCase (".a"  )) return true;
            }
        }
        return false;
    }
}

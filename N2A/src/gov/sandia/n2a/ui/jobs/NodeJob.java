/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.host.Remote;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.Utility;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.studies.PanelStudy;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/**
    Tree node for holding simulation runs ("jobs").
    Notice that even though this is an important part of the GUI, it is really just a data object.
    This class does a number of utility functions outside of the GUI, and serves to keep track of jobs
    even when running headless. 
**/
@SuppressWarnings("serial")
public class NodeJob extends NodeBase
{
    public static final ImageIcon iconComplete  = ImageUtil.getImage ("complete.gif");
    public static final ImageIcon iconUnknown   = ImageUtil.getImage ("help.gif");
    public static final ImageIcon iconFailed    = ImageUtil.getImage ("remove.gif");
    public static final ImageIcon iconLingering = ImageUtil.getImage ("lingering.png");
    public static final ImageIcon iconStopped   = ImageUtil.getImage ("stop.gif");

    protected static List<String> imageFileSuffixes = Arrays.asList (ImageIO.getReaderFileSuffixes ());  // We don't expect to load image handling plugins after startup, so one-time initialization is fine.

    protected String  key;
    protected String  inherit         = "";
    public    float   complete        = -1; // A number between 0 and 1, where 0 means just started, and 1 means done. -1 means unknown. 2 means failed. 3 means killed-lingering. 4 means killed-dead.
    protected Date    dateStarted     = null;
    protected Date    dateFinished    = null;
    protected double  expectedSimTime = 0;  // If greater than 0, then we can use this to estimate percent complete.
    protected long    lastMonitored   = 0;
    protected long    lastActive      = 0;
    protected long    lastDisplay     = 0;
    public    boolean deleted;
    public    boolean old;                  // Indicates that the associated job existed before the current invocation of this app started. Used to limit which hosts are automatically enabled.
    protected boolean tryToSelectOutput;

    public static final long activeTimeout = 1000 * 1000;  // 1000 seconds, or about 20 minutes

    public NodeJob (MNode source, boolean newlyStarted)
    {
        key = source.key ();
        setUserObject (key);  // This is fast, but the task of loading the $inherit line is slow, so we do it on the first call to monitorProgress().
        old = ! newlyStarted;
        tryToSelectOutput = newlyStarted;
    }

    @Override
    public boolean isLeaf ()
    {
        return false;
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        if (complete >= 0  &&  complete < 1) return Utility.makeProgressIcon (complete);
        if (complete == 1) return iconComplete;
        if (complete == 2) return iconFailed;
        if (complete == 3) return iconLingering;
        if (complete == 4) return iconStopped;
        return iconUnknown;
    }

    public MNode getSource ()
    {
        return AppData.runs.child (key);
    }

    public MNode getModel ()
    {
        return getModel (getSource ());
    }

    public boolean hasSnapshot ()
    {
        MNode job = getSource ();
        if (job == null) return false;
        Path localJobDir = Host.getJobDir (Host.getLocalResourceDir (), job);
        if (Files.exists (localJobDir.resolve ("snapshot"))) return true;
        if (Files.exists (localJobDir.resolve ("model"))) return true;
        return false;
    }

    /**
        @return Path to the source file (not the containing directory).
    **/
    public Path getJobPath ()
    {
        return Paths.get (getSource ().get ());
    }

    /**
        Remove all files from the job dir except the main job file.
        This allows the job to restart cleanly, without file-creation conflicts.
    **/
    public synchronized void reset ()
    {
        // Reset variables to initial state.
        complete        = -1;
        dateStarted     = null;
        dateFinished    = null;
        expectedSimTime = 0;
        lastMonitored   = 0;
        lastActive      = 0;
        lastDisplay     = 0;

        // Purge files
        Host localhost = Host.get ();
        MNode source = getSource ();
        Path localJobDir = Host.getJobDir (Host.getLocalResourceDir (), source);
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream (localJobDir))
        {
            for (Path path : dirStream)
            {
                if (path.endsWith ("job")) continue;
                localhost.deleteTree (path);  // deleteTree() works for both files and dirs, and also absorbs most exceptions. 
            }
        }
        catch (IOException e) {e.printStackTrace ();}

        PanelRun panelRun = PanelRun.instance;
        if (! panelRun.tree.isCollapsed (new TreePath (getPath ()))) build (panelRun.tree);
    }

    /**
        Load job data in and decide which host thread should monitor it.
    **/
    public synchronized void distribute ()
    {
        MNode source = getSource ();
        if (source.size () == 0)  // TODO: remove this conversion by 3/17/2022 (one-year sunset date)
        {
            // Convert old-format job directories to new format, which has separate "job" and "model" files.
            System.err.println ("converting to new job format: " + source.key ());
            Path jobDir = Host.getJobDir (Host.getLocalResourceDir (), source);
            MDoc model = new MDoc (jobDir.resolve ("model"));
            collectJobParameters (model, model.get ("$inherit"), source);
            String temp = model.get ("$metadata", "pid");
            if (! temp.isEmpty ()) source.set (temp, "pid");
        }
        inherit = source.getOrDefault (key, "$inherit").split (",", 2)[0].replace ("\"", "");
        setUserObject (inherit);

        // Lightweight evaluation of local "finished" file.
        // This slows down the initial load, but also makes the user more comfortable by showing
        // status as soon as possible on the first screenful of the Runs tab.
        Path localJobDir = Host.getJobDir (Host.getLocalResourceDir (), source);
        Path finished = localJobDir.resolve ("finished");
        if (Files.exists (finished)) checkFinished (finished);

        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                PanelRun panel = PanelRun.instance;
                panel.model.nodeChanged (NodeJob.this);
            }
        });

        Path started = localJobDir.resolve ("started");
        if (Files.exists (started))
        {
            dateStarted = new Date (Host.lastModified (started));
            Host env = Host.get (source);
            env.monitor (this);
        }
        else  // Not started yet, so send to wait-for-host queue.
        {
            // However, only try to start jobs that still have enough info to actually run.
            // Sometimes a directory might be lingering from an incomplete delete,
            // so don't try to start a job in that case.
            Path model = localJobDir.resolve ("model");
            if (Files.exists (model)) Host.waitForHost (this);
        }
    }

    /**
        Examines a model submitted for simulation and extracts key metadata needed
        for the job record. The caller is responsible for assigning $inherit,
        because this is usually already lost from "model" by the time this function
        is called.
        @param model The input model. Should be already collated, and so generally
        not the original source model from the DB.
        @param inherit Key of the source model in the DB. 
        @param job The job record to be filled in.
    **/
    public static void collectJobParameters (MNode model, String inherit, MNode job)
    {
        if (! inherit.isEmpty ()) job.set (inherit, "$inherit");

        // Take the entire host subtree, in case there are host-specific configurations as subkeys.
        // For example: "nodes" and "cores" for HPC systems.
        MNode host = model.child ("$metadata", "host");
        if (host != null)
        {
            job.set (host, "host");
            job.clear ("host", "study");  // While we can't negate every unimportant thing, this is definitely one of them.
        }

        // Only need the main backend key, since the backend will always open the model, and thus can access detailed keys directly.
        String temp = model.get ("$metadata", "backend");
        if (! temp.isEmpty ()) job.set (temp, "backend");

        temp = model.get ("$metadata", "duration");
        if (! temp.isEmpty ()) job.set (temp, "duration");

        // Unlike everything above, "seed" is merely trivia.
        // However, it is useful for reproducing a run, so is frequently displayed to user.
        // To avoid constantly opening the model, we copy it over to the job record.
        temp = model.get ("$metadata", "seed");
        if (! temp.isEmpty ()) job.set (temp, "seed");
    }

    public synchronized void checkFinished (Path finished)
    {
        dateFinished = new Date (Host.lastModified (finished));
        String line = null;
        try (BufferedReader reader = Files.newBufferedReader (finished))
        {
            line = reader.readLine ();
        }
        catch (IOException e) {}
        if (line == null) line = "";
        else              line = line.trim ();  // Windows adds a space on end of line, due to the way it interprets echo in .bat files. Rather than hack the bat, just defend against it.

        if (line.length () >= 6  ||  Duration.between (dateFinished.toInstant (), Instant.now ()).abs ().getSeconds () > 10)
        {
            if      (line.equals ("success")) complete = 1;
            else if (line.equals ("killed" )) complete = 3;
            else                              complete = 2;  // includes "failure", "dead", "", and any other unknown string
        }
    }

    public synchronized void monitorProgress ()
    {
        if (deleted) return;
        if (complete >= 1  &&  complete != 3) return;

        // Limit monitoring to no more than once per second.
        long elapsed = System.currentTimeMillis () - lastMonitored;
        long wait = 1000 - elapsed;
        if (wait > 0)
        {
            try {Thread.sleep (wait);}
            catch (InterruptedException e) {}
        }
        lastMonitored = System.currentTimeMillis ();

        float oldComplete = complete;
        MNode source = getSource ();
        Host env = Host.get (source);
        Path localJobDir = Host.getJobDir (Host.getLocalResourceDir (), source);
        // If job is remote, attempt to grab its state files.
        // TODO: handle remote jobs waiting in queue. Plan is to update "started" file with queue status.
        Path finished = localJobDir.resolve ("finished");
        if (! Files.exists (finished)  &&  env instanceof Remote)
        {
            @SuppressWarnings("resource")
            Remote remote = (Remote) env;
            if (remote.isConnected ()  ||  remote.isEnabled ())
            {
                try
                {
                    Path remoteJobDir = Host.getJobDir (env.getResourceDir (), source);
                    Path remoteFinished = remoteJobDir.resolve ("finished");
                    Files.copy (remoteFinished, finished);  // throws an exception if the remote file does not exist
                }
                catch (Exception e) {}
            }
        }

        if (complete == -1)
        {
            Path started = localJobDir.resolve ("started");
            if (Files.exists (started))
            {
                complete = 0;
                dateStarted = new Date (Host.lastModified (started));
                lastActive = dateStarted.getTime ();
            }
        }
        Backend simulator = Backend.getBackend (source.get ("backend"));
        if (complete >= 0  &&  complete < 1)
        {
            float percentDone = 0;
            if (expectedSimTime == 0) expectedSimTime = new UnitValue (source.get ("duration")).get ();
            if (expectedSimTime > 0)  percentDone = (float) (simulator.currentSimTime (source) / expectedSimTime);

            if (Files.exists (finished))
            {
                checkFinished (finished);
            }
            else
            {
                try
                {
                    long currentTime = System.currentTimeMillis ();
                    if (simulator.isActive (source))
                    {
                        lastActive = currentTime;
                    }
                    else if (currentTime - lastActive > activeTimeout)
                    {
                        if (percentDone < 1)
                        {
                            // Give it up for dead.
                            Files.copy (new ByteArrayInputStream ("dead".getBytes ("UTF-8")), finished);
                            complete = 4;
                        }
                        else  // Job appears to be actually finished, even though "finished" hasn't been written yet.
                        {
                            // Fake the "finished" file. This might be overwritten later by the batch process.
                            // Most likely, it will also report that we succeeded, so presume that things are fine.
                            Files.copy (new ByteArrayInputStream ("success".getBytes ("UTF-8")), finished);
                            complete = 1;
                        }
                    }
                }
                catch (Exception e) {}
            }

            if (complete >= 0  &&  complete < 1) complete = Math.min (0.99999f, percentDone);
        }
        if (complete == 3)
        {
            // Check if process is still lingering
            if (! simulator.isActive (source)) complete = 4;
        }

        PanelRun   panelRun   = PanelRun.instance;
        PanelStudy panelStudy = PanelStudy.instance;
        if (panelRun == null) return;  // Probably running headless, so skip all UI updates.
        if (complete != oldComplete)
        {
            EventQueue.invokeLater (new Runnable ()
            {
                public void run ()
                {
                    panelRun.model.nodeChanged (NodeJob.this);
                    if (panelRun.displayNode == NodeJob.this)
                    {
                        panelRun.buttonStop.setEnabled (complete < 1  ||  complete == 3);
                        panelRun.viewJob (true);
                    }
                    else if (panelRun.displayNode instanceof NodeFile  &&  panelRun.displayNode.getParent () == NodeJob.this)
                    {
                        panelRun.buttonStop.setEnabled (complete < 1  ||  complete == 3);
                        // Update the display every 5 seconds during the run.
                        // Some displays, such as a chart, could take longer than 5s to construct, so don't interrupt those.
                        // Always update the display when a run finishes.
                        long currentTime = System.currentTimeMillis ();
                        if (complete >= 1  &&  complete != 3  ||  panelRun.displayThread == null  &&  currentTime - lastDisplay > 5000)
                        {
                            lastDisplay = currentTime;
                            panelRun.viewFile (true);
                        }
                    }

                    // Also update corresponding entry in Study panel, if visible.
                    if (panelStudy != null) panelStudy.tableSamples.updateJob (key);  // panelStudy could be null for a brief moment during startup
                }
            });
        }

        if (! panelRun.tree.isCollapsed (new TreePath (getPath ()))) build (panelRun.tree);
    }

    public void stop ()
    {
        MNode source = getSource ();
        Backend.getBackend (source.get ("backend")).kill (source, complete >= 3);
        if (complete < 3) complete = 3;
    }

    /**
        Construct the list of resources under this job node.
        This only called if this job node is actively monitored and open in the tree,
        or if this job node is about to be opened regardless of monitoring.

        This function may make blocking remote calls, so should not run on the EDT.
        It queues changes and inserts an EDT event to apply them.
    **/
    public synchronized void build (JTree tree)
    {
        NodeBase selected = null;
        TreePath path = tree.getLeadSelectionPath ();
        if (path != null) selected = (NodeBase) path.getLastPathComponent ();
        TreeMap<String,NodeFile> existing = new TreeMap<String,NodeFile> ();
        if (children != null)
        {
            for (Object c : children)
            {
                NodeFile nf = (NodeFile) c;
                nf.found = false;
                existing.put (nf.path.getFileName ().toString (), nf);
            }
        }

        // Scan local job dir
        boolean changed = false;
        MNode source = getSource ();
        Path localJobDir = Host.getJobDir (Host.getLocalResourceDir (), source);
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream (localJobDir))
        {
            for (Path file : dirStream) if (buildChild (file, existing)) changed = true;
        }
        catch (IOException e) {}
        if (changed) applyChanges (tree, selected, existing);  // Update display immediately, before waiting on remote.
        changed = false;

        // Scan remote job dir. Because this is done second, remote files take lower precedence than local files.
        Host env = Host.get (source);
        if (env instanceof Remote)
        {
            try
            {
                ((Remote) env).enable ();  // To get here, the use had to expand the node. This implies permission to prompt for login.
                Path remoteJobDir = Host.getJobDir (env.getResourceDir (), source);
                try (DirectoryStream<Path> dirStream = Files.newDirectoryStream (remoteJobDir))
                {
                    for (Path file : dirStream) if (buildChild (file, existing)) changed = true;
                }
            }
            catch (Exception e) {}
        }

        List<String> keys = new ArrayList<String> (existing.keySet ());
        for (String key : keys)
        {
            NodeFile nf = existing.get (key);
            if (! nf.found)
            {
                changed = true;
                existing.remove (key);
            }
        }
        if (changed) applyChanges (tree, selected, existing);
    }

    public synchronized void applyChanges (JTree tree, NodeBase selected, Map<String,NodeFile> existing)
    {
        if (MainFrame.instance.tabs.getSelectedComponent () != PanelRun.instance)
        {
            tryToSelectOutput = false;
            selected = null;
            if (getChildCount () == 0) tree.collapsePath (new TreePath (getPath ()));
        }

        if (selected == null) return;
        if (selected instanceof NodeFile)
        {
            NodeBase parent = (NodeBase) selected.getParent ();
            if (parent != NodeJob.this) selected = null;
            else if (! ((NodeFile) selected).found) selected = parent;
        }

        // Try to select an output file when it first appears for the newest job, but only if the job is still selected.
        if (tryToSelectOutput  &&  selected == NodeJob.this  &&  selected == tree.getPathForRow (0).getLastPathComponent ())
        {
            NodeFile bestFile = null;
            for (NodeFile nf : existing.values ())
            {
                if (nf.type.priority == 0) continue;
                if (bestFile == null  ||  nf.type.priority > bestFile.type.priority)
                {
                    bestFile = nf;
                }
            }
            if (bestFile != null)
            {
                selected = bestFile;
                tryToSelectOutput = false;
            }
        }

        final NodeBase finalSelected = selected;
        Runnable update = new Runnable ()
        {
            public void run ()
            {
                removeAllChildren ();
                for (NodeFile nf : existing.values ()) add (nf);
                DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();
                model.nodeStructureChanged (NodeJob.this);

                if (finalSelected != null)
                {
                    TreePath selectedPath = new TreePath (finalSelected.getPath ());
                    tree.setSelectionPath (selectedPath);
                    tree.scrollPathToVisible (selectedPath);
                }
            }
        };
        EventQueue.invokeLater (update);
    }

    /**
        Creates a new file node for the given path, but only if it passes a number of filters.
        It must be different than any file already known to us, and it must be a file we
        actually want to show to the user.
    **/
    public synchronized boolean buildChild (Path path, Map<String,NodeFile> existing)
    {
        String fileName = path.getFileName ().toString ();
        NodeFile oldNode = existing.get (fileName);
        if (oldNode != null)
        {
            oldNode.found = true;
            return false;
        }

        NodeFile newNode;
        if (Files.isDirectory (path))
        {
            // Check for image sequence.
            // It's an image sequence if a random file from the dir has the right form: an integer with a standard image-file suffix.
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream (path))
            {
                Iterator<Path> it = dirStream.iterator ();
                if (! it.hasNext ()) return false;
                Path p = it.next ();
                String[] pieces = p.getFileName ().toString ().split ("\\.");
                if (pieces.length != 2) return false;
                try {Integer.valueOf (pieces[0]);}
                catch (NumberFormatException e) {return false;}
                String suffix = pieces[1].toLowerCase ();
                if (imageFileSuffixes.indexOf (suffix) < 0) return false;
                newNode = new NodeFile (NodeFile.Type.Video, path);
            }
            catch (Exception e)
            {
                return false;
            }
        }
        else
        {
            try {if (Files.size (path) == 0) return false;}
            catch (IOException e) {return false;}

            if (fileName.startsWith ("n2a_job" )) return false;
            if (fileName.equals     ("job"     )) return false;  // The primary record for the jobs repo.
            if (fileName.equals     ("snapshot")) return false;  // copy of models as they existed at start of job
            if (fileName.equals     ("model"   )) return false;  // ditto (old style)
            if (fileName.equals     ("started" )) return false;
            if (fileName.equals     ("finished")) return false;
            if (fileName.startsWith ("compile" )) return false;  // Piped files for compilation process. These will get copied to appropriate places if necessary.
            if (fileName.endsWith   (".bin"    )) return false;  // Don't show generated binaries
            if (fileName.endsWith   (".aplx"   )) return false;
            if (fileName.endsWith   (".columns")) return false;  // Hint for column names when simulator doesn't output them.
            if (fileName.endsWith   (".mod"    )) return false;  // NEURON files

            String suffix = "";
            String[] pieces = fileName.split ("\\.");
            if (pieces.length > 1) suffix = pieces[pieces.length-1].toLowerCase ();

            if      (fileName.endsWith ("out"))               newNode = new NodeFile (NodeFile.Type.Output,  path);
            else if (fileName.endsWith ("err"))               newNode = new NodeFile (NodeFile.Type.Error,   path);
            else if (imageFileSuffixes.indexOf (suffix) >= 0) newNode = new NodeFile (NodeFile.Type.Picture, path);
            else                                              newNode = new NodeFile (NodeFile.Type.Other,   path);
        }

        existing.put (fileName, newNode);
        newNode.found = true;  // So it won't get deleted in build().
        return true;
    }

    // The following static functions are utilities for working with the snapshot model
    // in the job dir. They are not directly related to NodeJob, but this is as good a place
    // as any to collect these routines.

    public static MNode getModel (String jobKey)
    {
        return getModel (AppData.runs.child (jobKey));
    }

    /**
        @return A fully-collated model. The key of the root node is usable as main model name
        for the purposes of backend processing.
    **/
    public static MNode getModel (MNode job)
    {
        if (job == null) return null;
        Path localJobDir  = Host.getJobDir (Host.getLocalResourceDir (), job);
        Path snapshotPath = localJobDir.resolve ("snapshot");
        Path modelPath    = localJobDir.resolve ("model");
        String key        = job.get ("$inherit");

        MNode result;
        if (Files.exists (snapshotPath))  // mini-repo snapshot
        {
            MDoc snapshot = new MDoc (snapshotPath);
            result = MPart.fromSnapshot (key, snapshot);
        }
        else if (Files.exists (modelPath))  // collated snapshot
        {
            result = new MDoc (modelPath, key);
        }
        else  // no snapshot
        {
            // Retrieve directly from database.
            result = new MPart (AppData.models.child (key));
        }
        return result;
    }

    /**
        Writes the collated model to the local job directory.
        @param collated The fully-expanded model.
        @param job The job record, used only to determine name of job directory.
    **/
    public static void saveCollatedModel (MNode collated, MNode job)
    {
        String snapshotMode = AppData.state.get ("General", "snapshot");
        if (snapshotMode.startsWith ("No")) return;  // Save nothing

        // Save main model (all other snapshot modes)
        MVolatile snapshot = new MVolatile ();
        String inherit = job.get ("$inherit");  // name of main model
        MNode doc = AppData.models.child (inherit);
        snapshot.link (doc);

        // Save referenced models
        if (snapshotMode.startsWith ("All")) addInherits (doc, snapshot);

        // Write snapshot to disk
        // See MDoc.save () for similar code.
        // We don't create an MDoc here because it would require duplicating the collated model in memory,
        // and the model could be very large.
        Path localJobDir = Host.getJobDir (Host.getLocalResourceDir (), job);  // The path is also contained in the job MDoc node.
        Path modelPath   = localJobDir.resolve ("snapshot");
        try (BufferedWriter writer = Files.newBufferedWriter (modelPath))
        {
            Schema.latest ().writeAll (snapshot, writer);
        }
        catch (IOException e)
        {
            System.err.println ("Failed to write snapshot file.");
            e.printStackTrace ();
        }
    }

    public static void addInherits (MNode n, MVolatile snapshot)
    {
        String inherits = n.get ("$inherit");
        for (String inherit : inherits.split (","))
        {
            inherit = inherit.trim ().replace ("\"", "");
            if (snapshot.child (inherit) != null) continue;
            MNode p = AppData.models.child (inherit);
            if (p == null) continue;
            snapshot.link (p);
            addInherits (p, snapshot);
        }
        for (MNode c : n) addInherits (c, snapshot);
    }
}

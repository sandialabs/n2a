/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.execenvs.Host;
import gov.sandia.n2a.execenvs.Remote;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.ui.Utility;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

@SuppressWarnings("serial")
public class NodeJob extends NodeBase
{
    protected static ImageIcon iconComplete  = ImageUtil.getImage ("complete.gif");
    protected static ImageIcon iconUnknown   = ImageUtil.getImage ("help.gif");
    protected static ImageIcon iconFailed    = ImageUtil.getImage ("remove.gif");
    protected static ImageIcon iconLingering = ImageUtil.getImage ("lingering.png");
    protected static ImageIcon iconStopped   = ImageUtil.getImage ("stop.gif");

    protected static List<String> imageFileSuffixes = Arrays.asList (ImageIO.getReaderFileSuffixes ());  // We don't expect to load image handling plugins after startup, so one-time initialization is fine.

    protected String  key;
    protected String  inherit         = "";
    public    float   complete        = -1; // A number between 0 and 1, where 0 means just started, and 1 means done. -1 means unknown. 2 means failed. 3 means terminated.
    protected Date    dateStarted     = null;
    protected Date    dateFinished    = null;
    protected double  expectedSimTime = 0;  // If greater than 0, then we can use this to estimate percent complete.
    protected long    lastLiveCheck   = 0;
    protected long    lastDisplay     = 0;
    public    boolean deleted;
    protected boolean tryToSelectOutput;

    public NodeJob (MNode source, boolean newlyStarted)
    {
        key = source.key ();
        setUserObject (key);  // This is fast, but the task of loading the $inherit line is slow, so we do it on the first call to monitorProgress().
        if (newlyStarted)
        {
            lastLiveCheck = System.currentTimeMillis ();  // See below. Gives new jobs about 20 minutes to show some progress. Old jobs have no grace period because their last live check is 0.
            tryToSelectOutput = true;
        }
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

    /**
        @return Path to the source file (not the containing directory).
    **/
    public Path getJobPath ()
    {
        return Paths.get (getSource ().get ());
    }

    /**
        Load job data in and decide which host thread should monitor it.
    **/
    public synchronized void distribute ()
    {
        MNode source = getSource ();
        inherit = source.getOrDefault (key, "$inherit").split (",", 2)[0].replace ("\"", "");
        setUserObject (inherit);
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                PanelRun panel = PanelRun.instance;
                panel.model.nodeChanged (NodeJob.this);
                panel.tree.paintImmediately (panel.tree.getPathBounds (new TreePath (NodeJob.this.getPath ())));
            }
        });

        // Lightweight evaluation of local "finished" file.
        // This slows down the initial load, but also makes the user more comfortable by showing status
        // as soon as possible on the first screenful of the Runs tab.
        Path localJobDir = Host.getJobDir (Host.getLocalResourceDir (), source);
        Path finished = localJobDir.resolve ("finished");
        if (Files.exists (finished)) checkFinished (finished);

        Host env = Host.get (source);
        synchronized (env.running) {env.running.add (this);};
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
        else              line = line.trim ();  // Windows tacks a space on end of line, due to the way it interprets echo in .bat files. Rather than hack the bat, just defend against it.

        if (line.length () >= 6  ||  Duration.between (dateFinished.toInstant (), Instant.now ()).abs ().getSeconds () > 10)
        {
            if      (line.equals ("success")) complete = 1;
            else if (line.equals ("killed" )) complete = 3;
            else                              complete = 2;  // includes "failure", "", and any other unknown string
        }
    }

    public synchronized void monitorProgress ()
    {
        if (deleted) return;
        if (complete >= 1  &&  complete != 3) return;

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
                    Path resourceDir  = env.getResourceDir ();
                    Path remoteJobDir = Host.getJobDir (resourceDir, source);
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
                dateStarted = new Date (started.toFile ().lastModified ());
            }
        }
        if (complete < 1)
        {
            if (Files.exists (finished))
            {
                checkFinished (finished);
            }
            else
            {
                long currentTime = System.currentTimeMillis ();
                if (currentTime - lastLiveCheck > 1000000)  // 1000 seconds, about 20 minutes
                {
                    try
                    {
                        if (! env.isActive (source))
                        {
                            Files.copy (new ByteArrayInputStream ("killed".getBytes ("UTF-8")), finished);
                            complete = 4;
                        }
                    }
                    catch (Exception e) {}
                    lastLiveCheck = currentTime;
                }
            }
        }

        if (complete >= 0  &&  complete < 1)
        {
            if (expectedSimTime == 0) expectedSimTime = source.getOrDefault (0.0, "$metadata", "duration");
            if (expectedSimTime > 0)
            {
                Backend simulator = Backend.getBackend (source.get ("$metadata", "backend"));
                double t = simulator.currentSimTime (source);
                if (t != 0) complete = Math.min (0.99999f, (float) (t / expectedSimTime));
            }
        }

        if (complete == 3)
        {
            // Check if process is still lingering
            try
            {
                if (! env.isActive (source)) complete = 4;
            }
            catch (Exception e) {}
        }

        PanelRun panel = PanelRun.instance;
        if (complete != oldComplete)
        {
            EventQueue.invokeLater (new Runnable ()
            {
                public void run ()
                {
                    panel.model.nodeChanged (NodeJob.this);
                    if (panel.displayNode == NodeJob.this)
                    {
                        panel.buttonStop.setEnabled (complete < 1  ||  complete == 3);
                        panel.viewJob ();
                    }
                    else if (panel.displayNode instanceof NodeFile  &&  panel.displayNode.getParent () == NodeJob.this)
                    {
                        panel.buttonStop.setEnabled (complete < 1  ||  complete == 3);
                        // Update the display every 5 seconds during the run.
                        // Some displays, such as a chart, could take longer than 5s to construct, so don't interrupt those.
                        // Always update the display when a run finishes.
                        long currentTime = System.currentTimeMillis ();
                        if (complete >= 1  &&  complete != 3  ||  panel.displayThread == null  &&  currentTime - lastDisplay > 5000)
                        {
                            lastDisplay = currentTime;
                            panel.viewFile (false);
                        }
                    }
                }
            });
        }

        if (! panel.tree.isCollapsed (new TreePath (getPath ()))) build (panel.tree);
    }

    public void stop ()
    {
        MNode source = getSource ();
        Backend.getBackend (source.get ("$metadata", "backend")).kill (source, complete >= 3);
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
        Path localJobDir = Paths.get (source.get ()).getParent ();
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream (localJobDir))
        {
            for (Path file : dirStream) if (buildChild (file, existing)) changed = true;
        }
        catch (IOException e) {}

        // Scan remote job dir. Because this is done second, it takes lower precedence relative to local files.
        Host env = Host.get (source);
        if (env instanceof Remote)
        {
            try
            {
                ((Remote) env).enable ();  // To get here, the use had to expand the node. This implies permission to prompt for login.
                Path resourceDir  = env.getResourceDir ();
                Path remoteJobDir = Host.getJobDir (resourceDir, source);
                try (DirectoryStream<Path> dirStream = Files.newDirectoryStream (remoteJobDir))
                {
                    for (Path file : dirStream) if (buildChild (file, existing)) changed = true;
                }
            }
            catch (Exception e) {}
        }

        for (NodeFile nf : existing.values ())
        {
            if (! nf.found)
            {
                remove (nf);
                changed = true;
            }
        }

        if (changed)
        {
            if (selected != null)
            {
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
    }

    /**
        Creates a new file node for the given path, but only if it passes a number of filters.
        It must be different than any file already known to us, and it must be a file we
        actually want to show to the user.
    **/
    public synchronized boolean buildChild (Path file, Map<String,NodeFile> existing)
    {
        String fileName = file.getFileName ().toString ();
        NodeFile oldNode = existing.get (fileName);
        if (oldNode != null)
        {
            oldNode.found = true;
            return false;
        }

        NodeFile newNode;
        if (Files.isDirectory (file))
        {
            // Check for image sequence.
            // It's an image sequence if a random file from the dir has the right form: an integer with an standard image-file suffix.
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream (file))
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
                newNode = new NodeFile (NodeFile.Type.Video, file);
            }
            catch (Exception e)
            {
                return false;
            }
        }
        else
        {
            try {if (Files.size (file) == 0) return false;}
            catch (IOException e) {return false;}

            if (fileName.startsWith ("n2a_job" )) return false;
            if (fileName.equals     ("model"   )) return false;  // This is the file associated with our own "source"
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

            if      (fileName.endsWith ("out"    ))           newNode = new NodeFile (NodeFile.Type.Output,  file);
            else if (fileName.endsWith ("err"    ))           newNode = new NodeFile (NodeFile.Type.Error,   file);
            else if (fileName.endsWith ("result" ))           newNode = new NodeFile (NodeFile.Type.Result,  file);
            else if (fileName.endsWith ("console"))           newNode = new NodeFile (NodeFile.Type.Console, file);
            else if (imageFileSuffixes.indexOf (suffix) >= 0) newNode = new NodeFile (NodeFile.Type.Picture, file);
            else                                              newNode = new NodeFile (NodeFile.Type.Other,   file);
        }

        existing.put (fileName, newNode);
        newNode.found = true;  // So it won't get deleted below.
        return true;
    }
}

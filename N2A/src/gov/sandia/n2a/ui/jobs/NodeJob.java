/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.execenvs.HostSystem;
import gov.sandia.n2a.plugins.extpoints.Backend;
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
    protected static ImageIcon iconComplete = ImageUtil.getImage ("complete.gif");
    protected static ImageIcon iconUnknown  = ImageUtil.getImage ("help.gif");
    protected static ImageIcon iconFailed   = ImageUtil.getImage ("remove.gif");
    protected static ImageIcon iconStopped  = ImageUtil.getImage ("stop.gif");

    protected static List<String> imageFileSuffixes = Arrays.asList (ImageIO.getReaderFileSuffixes ());  // We don't expect to load image handling plugins after startup, so one-time initialization is fine.

    protected String  key;
    protected String  inherit         = "";
    protected float   complete        = -1; // A number between 0 and 1, where 0 means just started, and 1 means done. -1 means unknown. 2 means failed. 3 means terminated.
    protected Date    dateStarted     = null;
    protected Date    dateFinished    = null;
    protected double  expectedSimTime = 0;  // If greater than 0, then we can use this to estimate percent complete.
    protected long    lastLiveCheck   = 0;
    protected boolean deleted;
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
        if (complete == -1) return iconUnknown;
        if (complete ==  1) return iconComplete;
        if (complete ==  2) return iconFailed;
        if (complete ==  3) return iconStopped;

        // Create an icon on the fly which represents percent complete as a pie-chart
        BufferedImage inProgress = new BufferedImage (16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = inProgress.createGraphics ();
        g.setBackground (new Color (0, 0, 0, 1));
        g.clearRect (0, 0, 16, 16);
        g.setColor (new Color (0.3f, 0.5f, 1));
        g.drawOval (0, 0, 14, 14);
        g.setColor (Color.black);
        g.fillArc (0, 0, 14, 14, 90, - Math.round (complete * 360));
        return new ImageIcon (inProgress);
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

    public synchronized void monitorProgress (final PanelRun panel)
    {
        if (deleted) return;

        MNode source = getSource ();
        if (inherit.isEmpty ())
        {
            inherit = source.getOrDefault (key, "$inherit").split (",", 2)[0].replace ("\"", "");
            setUserObject (inherit);
            if (complete >= 1)
            {
                // Since we won't reach the display update below, do a simple one here.
                EventQueue.invokeLater (new Runnable ()
                {
                    public void run ()
                    {
                        panel.model.nodeChanged (NodeJob.this);
                        panel.tree.paintImmediately (panel.tree.getPathBounds (new TreePath (NodeJob.this.getPath ())));
                    }
                });
            }
        }

        if (complete >= 1) return;

        float oldComplete = complete;
        Path jobDir = Paths.get (source.get ()).getParent ();
        if (complete == -1)
        {
            Path started = jobDir.resolve ("started");
            if (Files.exists (started))
            {
                complete = 0;
                dateStarted = new Date (started.toFile ().lastModified ());
            }
        }
        if (complete < 1)
        {
            Path finished = jobDir.resolve ("finished");
            if (Files.exists (finished))
            {
                dateFinished = new Date (finished.toFile ().lastModified ());
                String line = null;
                try
                {
                    BufferedReader reader = Files.newBufferedReader (finished);
                    line = reader.readLine ();
                    reader.close ();
                }
                catch (IOException e) {}
                if (line == null) line = "";

                if (line.length () >= 6  ||  Duration.between (dateFinished.toInstant (), Instant.now ()).abs ().getSeconds () > 10)
                {
                    if      (line.equals ("success")) complete = 1;
                    else if (line.equals ("killed" )) complete = 3;
                    else                              complete = 2;  // includes "failure", "", and any other unknown string
                }
            }
            else
            {
                long currentTime = System.currentTimeMillis ();
                if (currentTime - lastLiveCheck > 1000000)  // about 20 minutes
                {
                    HostSystem env = HostSystem.get (source.get ("$metadata", "host"));
                    long pid = source.getOrDefault (0l, "$metadata", "pid");
                    try
                    {
                        Set<Long> pids = env.getActiveProcs ();
                        boolean dead;
                        if (pid == 0) dead = pids.isEmpty ();
                        else          dead = ! pids.contains (pid);
                        if (dead)
                        {
                            Files.copy (new ByteArrayInputStream ("killed".getBytes ("UTF-8")), finished);
                            complete = 3;
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

        if (complete != oldComplete)
        {
            EventQueue.invokeLater (new Runnable ()
            {
                public void run ()
                {
                    if (panel.displayNode == NodeJob.this)
                    {
                        panel.buttonStop.setEnabled (complete < 1);
                        panel.viewJob ();
                    }
                    panel.model.nodeChanged (NodeJob.this);
                }
            });
        }

        if (! panel.tree.isCollapsed (new TreePath (getPath ()))) build (panel.tree);
    }

    public void stop ()
    {
        MNode source = getSource ();
        Backend.getBackend (source.get ("$metadata", "backend")).kill (source);
    }

    public void build (JTree tree)
    {
        // Only handle local resources.
        // If a job runs remotely, then we need to fetch its files to view them, so assume they will be downloaded to local dir when requested.

        NodeBase selected = (NodeBase) tree.getLeadSelectionPath ().getLastPathComponent ();
        TreeMap<Path,NodeFile> existing = new TreeMap<Path,NodeFile> ();
        if (children != null)
        {
            for (Object c : children)
            {
                NodeFile nf = (NodeFile) c;
                nf.found = false;
                existing.put (nf.path, nf);
            }
        }

        class FileConsumer implements Consumer<Path>
        {
            boolean changed;
            public void accept (Path file)
            {
                NodeFile newNode;
                if (Files.isDirectory (file))
                {
                    // Check for image sequence.
                    // It's an image sequence if a random file from the dir has the right form: an integer with an standard image-file suffix.
                    try (Stream<Path> dirStream = Files.list (file))
                    {
                        Optional<Path> someFile = dirStream.findAny ();
                        if (! someFile.isPresent ()) return;
                        Path p = someFile.get ();
                        String[] pieces = p.getFileName ().toString ().split ("\\.");
                        if (pieces.length != 2) return;
                        try {Integer.valueOf (pieces[0]);}
                        catch (NumberFormatException e) {return;}
                        String suffix = pieces[1].toLowerCase ();
                        if (imageFileSuffixes.indexOf (suffix) < 0) return;
                        newNode = new NodeFile (NodeFile.Type.Video, file);
                    }
                    catch (Exception e) {return;}
                }
                else
                {
                    try {if (Files.size (file) == 0) return;}
                    catch (IOException e) {return;}

                    String fileName = file.getFileName ().toString ();
                    if (fileName.startsWith ("n2a_job" )) return;
                    if (fileName.equals     ("model"   )) return;  // This is the file associated with our own "source"
                    if (fileName.equals     ("started" )) return;
                    if (fileName.equals     ("finished")) return;
                    if (fileName.startsWith ("compile" )) return;  // Piped files for compilation process. These will get copied to appropriate places if necessary.
                    if (fileName.endsWith   (".bin"    )) return;  // Don't show generated binaries
                    if (fileName.endsWith   (".aplx"   )) return;
                    if (fileName.endsWith   (".columns")) return;  // Hint for column names when simulator doesn't output them.
                    if (fileName.endsWith   (".mod"    )) return;  // NEURON files

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

                NodeFile oldNode = existing.get (newNode.path);
                if (oldNode == null)
                {
                    add (newNode);
                    changed = true;
                }
                else
                {
                    oldNode.found = true;
                }
            }
        };
        FileConsumer consumer = new FileConsumer ();
        MNode source = getSource ();
        Path dir = Paths.get (source.get ()).getParent ();
        try (Stream<Path> dirStream = Files.list (dir))
        {
            dirStream.forEach (consumer);
        }
        catch (IOException e) {}

        for (NodeFile nf : existing.values ())
        {
            if (! nf.found)
            {
                remove (nf);
                consumer.changed = true;
            }
        }

        if (consumer.changed)
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
                    for (Object c : children)
                    {
                        NodeFile nf = (NodeFile) c;
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
            final TreePath selectedPath = (selected == null) ? null : new TreePath (selected.getPath ());
            Runnable update = new Runnable ()
            {
                public void run ()
                {
                    DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();
                    model.nodeStructureChanged (NodeJob.this);
                    if (selectedPath != null)
                    {
                        tree.setSelectionPath (selectedPath);
                        tree.scrollPathToVisible (selectedPath);
                    }
                }
            };
            if (EventQueue.isDispatchThread ()) update.run ();
            else                                EventQueue.invokeLater (update);
        }
    }
}

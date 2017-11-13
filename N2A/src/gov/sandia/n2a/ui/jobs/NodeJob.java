/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


package gov.sandia.n2a.ui.jobs;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

public class NodeJob extends NodeBase
{
    protected static ImageIcon iconComplete = ImageUtil.getImage ("complete.gif");
    protected static ImageIcon iconUnknown  = ImageUtil.getImage ("help.gif");
    protected static ImageIcon iconFailed   = ImageUtil.getImage ("remove.gif");
    protected static ImageIcon iconStopped  = ImageUtil.getImage ("stop.gif");

    protected MNode source;
    protected float complete = -1;  // A number between 0 and 1, where 0 means just started, and 1 means done. -1 means unknown. 2 means failed. 3 means terminated.
    protected Date dateStarted = null;
    protected Date dateFinished = null;
    protected double expectedSimTime = 0;  // If greater than 0, then we can use this to estimate percent complete.

    public NodeJob (MNode source)
    {
        this.source = source;
        setUserObject (source.getOrDefault ("$inherit", source.key ()).split (",", 2)[0].replace ("\"", ""));
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

    public void monitorProgress (final PanelRun panel)
    {
        if (complete >= 1) return;

        float oldComplete = complete;
        File path = new File (source.get ()).getParentFile ();
        if (complete == -1)
        {
            File started = new File (path, "started"); 
            if (started.exists ())
            {
                complete = 0;
                dateStarted = new Date (started.lastModified ());
            }
        }
        if (complete < 1)
        {
            File finished = new File (path, "finished");
            if (finished.exists ())
            {
                dateFinished = new Date (finished.lastModified ());
                String line = "";
                try
                {
                    BufferedReader reader = new BufferedReader (new FileReader (finished));
                    line = reader.readLine ();
                    reader.close ();
                }
                catch (IOException e) {}

                if (line.length () >= 6  ||  Duration.between (dateFinished.toInstant (), Instant.now ()).abs ().getSeconds () > 10)
                {
                    if      (line.equals ("success")) complete = 1;
                    else if (line.equals ("killed" )) complete = 3;
                    else                              complete = 2;
                }
            }
        }

        if (complete >= 0  &&  complete < 1)
        {
            if (expectedSimTime == 0) expectedSimTime = source.getOrDefaultDouble ("$metadata", "duration", "0");
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
                    if (panel.displayNode == NodeJob.this) panel.viewJob ();
                    panel.model.nodeChanged (NodeJob.this);
                    panel.tree.paintImmediately (panel.tree.getPathBounds (new TreePath (NodeJob.this.getPath ())));
                }
            });
        }
    }

    public void stop ()
    {
        Backend.getBackend (source.get ("$metadata", "backend")).kill (source);
    }

    public void build (JTree tree)
    {
        removeAllChildren ();

        // Only handle local resources.
        // If a job runs remotely, then we need to fetch its files to view them, so assume they will be downloaded to local dir when requested.

        File path = new File (source.get ()).getParentFile ();
        for (File file : path.listFiles ())
        {
            NodeBase newNode;
            String fileName = file.getName ();

            if (fileName.startsWith ("n2a_job" )) continue;
            if (fileName.equals     ("model"   )) continue;  // This is the file associated with our own "source"
            if (fileName.equals     ("started" )) continue;
            if (fileName.equals     ("finished")) continue;
            if (fileName.endsWith   (".bin"    )) continue;  // Don't show generated binaries
            if (fileName.endsWith   (".aplx"   )) continue;

            if      (fileName.endsWith ("out"    )) newNode = new NodeFile (NodeFile.Type.Output,  file);
            else if (fileName.endsWith ("err"    )) newNode = new NodeFile (NodeFile.Type.Error,   file);
            else if (fileName.endsWith ("result" )) newNode = new NodeFile (NodeFile.Type.Result,  file);
            else if (fileName.endsWith ("console")) newNode = new NodeFile (NodeFile.Type.Console, file);
            else                                    newNode = new NodeFile (NodeFile.Type.Other,   file);
            add (newNode);
        }
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();
        model.nodeStructureChanged (this);
    }
}

/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.umf.platform.ui.jobs;

import java.awt.EventQueue;
import java.io.File;

import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;

public class NodeJob extends NodeBase
{
    protected static ImageIcon iconInProgress = ImageUtil.getImage ("inprogress.gif");
    protected static ImageIcon iconComplete   = ImageUtil.getImage ("complete.gif");
    protected static ImageIcon iconUnknown    = ImageUtil.getImage ("help.gif");
    protected static ImageIcon iconFailed     = ImageUtil.getImage ("remove.gif");

    protected MNode source;
    protected float complete = -1;  // A number between 0 and 1, where 0 means just started, and 1 means done. -1 means unknown. 2 means failed

    public NodeJob (MNode source)
    {
        this.source = source;
        setUserObject (source.getOrDefault (source.key (), "$inherit"));
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
        // TODO: create an icon on the fly which represents percent complete as a pie-chart, similar to inprogress.gif
        return iconInProgress;
    }

    public void monitorProgress (final DefaultTreeModel model)
    {
        if (complete == -2) return;

        float oldComplete = complete;
        File path = new File (source.get ()).getParentFile ();
        if (complete == -1  &&  new File (path, "started" ).exists ()) complete = 0;
        if (complete == 0   &&  new File (path, "finished").exists ()) complete = 1;
        // TODO: add feature to backend to monitor progress
        // We can determine backend from the model file
        // time stamps on "started" and "finished" convey information about job
        // contents of those files may convey additional detail

        if (complete != oldComplete)
        {
            EventQueue.invokeLater (new Runnable ()
            {
                public void run ()
                {
                    model.nodeChanged (NodeJob.this);
                }
            });
        }
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

            if (fileName.startsWith ("n2a_job")) continue;
            if (fileName.endsWith   ("model"  )) continue;  // This is the file associated with our own "source"

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

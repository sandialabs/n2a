/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.EventQueue;
import java.nio.file.Path;

import javax.swing.ImageIcon;

import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.jobs.PanelRun.DisplayThread;

@SuppressWarnings("serial")
public class NodeVideo extends NodeFile
{
    public static final ImageIcon iconVideo = ImageUtil.getImage ("fileVideo.png");

    public NodeVideo (Path path)
    {
        super (path);
        priority = 2;
        icon     = iconVideo;
    }

    @Override
    public boolean couldHaveColumns ()
    {
        return false;
    }

    @Override
    public boolean isGraphable ()
    {
        return false;
    }

    @Override
    public boolean render (DisplayThread dt)
    {
        // If refresh is true, then the player is already busy-waiting for more frames, so there is nothing to do.
        if (dt.refresh) return true;

        final Video v = new Video (this);
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                if (dt.stop) return;
                PanelRun.instance.displayChart.buttonBar.setVisible (false);
                PanelRun.instance.displayPane.setViewportView (v);
                v.play ();
            }
        });
        return true;
    }
}

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
public class NodeImage extends NodeFile
{
    public static final ImageIcon iconImage = ImageUtil.getImage ("fileImage-16.png");

    public NodeImage (Path path)
    {
        super (path);
        priority = 2;
        icon     = iconImage;
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
        final Picture p = new Picture (path);
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                if (dt.stop) return;
                PanelRun.instance.displayChart.buttonBar.setVisible (false);
                PanelRun.instance.displayPane.setViewportView (p);
            }
        });
        return true;
    }
}

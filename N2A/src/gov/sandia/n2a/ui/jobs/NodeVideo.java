/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.EventQueue;
import java.nio.file.Path;

import javax.swing.ImageIcon;

import gov.sandia.n2a.backend.c.VideoIn;
import gov.sandia.n2a.host.Host;
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

        // Attempt to acquire FFmpeg JNI
        // Since we are on the display thread, we can take our time.
        PanelRun pr = PanelRun.instance;
        Host localhost = Host.get ("localhost");
        if (! localhost.objects.containsKey ("ffmpegJNI"))
        {
            EventQueue.invokeLater (new Runnable ()
            {
                public void run ()
                {
                    pr.showStatus ("Compiling C runtime to support video I/O. This can take up to a minute.");
                }
            });
            VideoIn.prepareJNI ();
        }

        // Start video
        Video v = new Video (this);
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                synchronized (pr.displayPane)
                {
                    if (dt != pr.displayThread) return;
                    if (! v.canPlay)
                    {
                        if (localhost.objects.containsKey ("ffmpegJNI"))
                        {
                            pr.showStatus
                            (
                                "Failed to open video or image sequence.\n" +
                                "There could be a bug in this program or its supporting libraries.\n" +
                                "Please report this to the developers. If possible, send a copy of the file(s)."
                            );
                        }
                        else
                        {
                            pr.showStatus
                            (
                                "Video I/O is not available. Possible causes include:\n" +
                                " * FFmpeg library is missing.\n" +
                                " * C++ compiler is missing.\n" +
                                " * Path to JNI headers is incorrect.\n" +
                                "Go to Settings:Backend C and update 'localhost'.\n" +
                                "Directions on how to obtain FFmpeg and a C++ compiler are on the wiki page."
                            );
                        }
                        return;
                    }
                    pr.displayChart.buttonBar.setVisible (false);
                    pr.displayPane.setViewportView (v);
                }
                v.play ();
            }
        });
        return true;
    }
}

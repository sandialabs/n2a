/*
Copyright 2019-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.JScrollBar;

import gov.sandia.n2a.backend.c.VideoIn;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.ui.Lay;

@SuppressWarnings("serial")
public class Video extends JPanel
{
    public    boolean       canPlay;  // file is either an image sequence or FFmpeg is available to play the video. Set by ctor.
    protected NodeJob       job;
    protected Path          path;
    protected String        suffix;
    protected int           index = -1;
    protected int           first;
    protected int           last;
    protected VideoIn       vin;
    protected BufferedImage image;
    protected PlayThread    thread;

    protected JPanel     panelImage;
    protected JScrollBar scrollbar;

    public Video (NodeFile node)
    {
        job = (NodeJob) node.getParent ();
        path = node.path;

        if (Files.isDirectory (path))
        {
            try (Stream<Path> stream = Files.list (path);)
            {
                last = (int) stream.count () - 1;  // Terminal operation, so stream really does need to be reopened below.
            }
            catch (Exception e)
            {
                return;
            }

            try (Stream<Path> stream = Files.list (path);)
            {
                Optional<Path> someFile = stream.findAny ();
                Path p = someFile.get ();
                String[] pieces = p.getFileName ().toString ().split ("\\.");
                suffix = pieces[1].toLowerCase ();

                // Determine whether sequence is 0-based or 1-based
                if (last >= 0  &&  ! Files.exists (path.resolve ("0." + suffix)))
                {
                    index = 0;
                    first = 1;
                    last++;
                }
            }
            catch (Exception e)
            {
                return;
            }
        }
        else  // Single file, so we must use FFmpeg.
        {
            Host localhost = Host.get ("localhost");
            if (! localhost.objects.containsKey ("ffmpegJNI")) return;
            vin = new VideoIn (path);
            if (! vin.good ())
            {
                vin.close ();
                vin = null;
                return;
            }
            AppData.cleaner.register (this, vin);
            double duration    = Double.valueOf (vin.get ("duration"));
            double framePeriod = Double.valueOf (vin.get ("framePeriod"));
            last = (int) (duration / framePeriod);
        }
        // If we get this far, then we can play the video or image sequence.
        canPlay = true;

        // Build GUI

        MouseAdapter adapter = new MouseAdapter ()
        {
            public void mouseClicked (MouseEvent e)
            {
                if (thread != null  &&  thread.playing) pause ();
                else                                    play ();
            }

            public void mouseDragged (MouseEvent e)
            {
                if (thread != null  &&  thread.playing) pause ();
                scratch ((float) e.getX () / panelImage.getWidth ());
                panelImage.paintImmediately (panelImage.getBounds ());
                scrollbar.setValue (index);
            }

            public void mouseWheelMoved (MouseWheelEvent e)
            {
                if (thread != null  &&  thread.playing) pause ();
                if (e.getWheelRotation () > 0) nextImage ();
                else                           previousImage ();
                panelImage.paintImmediately (panelImage.getBounds ());
            }
        };
        panelImage = new PanelImage ();
        panelImage.addMouseListener (adapter);
        panelImage.addMouseMotionListener (adapter);
        panelImage.addMouseWheelListener (adapter);

        scrollbar = new JScrollBar (JScrollBar.HORIZONTAL, first, 1, first, last + 1);
        scrollbar.addAdjustmentListener (new AdjustmentListener ()
        {
            public void adjustmentValueChanged (AdjustmentEvent e)
            {
                int newIndex = e.getValue ();
                if (newIndex != index)
                {
                    if (thread != null  &&  thread.playing) pause ();
                    scratch ((float) newIndex / last);
                    panelImage.paintImmediately (panelImage.getBounds ());
                }
            }
        });

        Lay.BLtg (this,
            "C", panelImage,
            "S", scrollbar
        );
    }

    public void checkReopenVideo ()
    {
        if (vin.good ()) return;
        vin.closeFile ();
        vin.openFile (path);
    }

    public synchronized void nextImage ()
    {
        BufferedImage temp = null;
        int nextIndex = index + 1;

        if (vin != null)
        {
            if (vin.getHandle () == 0) return;  // For coordination between play thread and EDT.
            checkReopenVideo ();
            vin.seekFrame (nextIndex);
            temp = vin.readNext ();
        }
        else if (suffix != null)
        {
            try {temp = ImageIO.read (path.resolve (nextIndex + "." + suffix).toFile ());}
            catch (Exception e) {}
        }
        else return;

        if (temp == null) return;  // Failed parse a proper image. This can happen if we read a partially-written image file.
        image = temp;
        if (nextIndex > last)
        {
            last = nextIndex;
            scrollbar.setMaximum (last + 1);
        }
        index = nextIndex;
        scrollbar.setValue (index);
    }

    public void previousImage ()
    {
        BufferedImage temp = null;
        int nextIndex = index - 1;
        if (nextIndex < first) return;

        if (vin != null)
        {
            checkReopenVideo ();
            vin.seekFrame (nextIndex);
            temp = vin.readNext ();
        }
        else if (suffix != null)
        {
            try {temp = ImageIO.read (path.resolve (nextIndex + "." + suffix).toFile ());}
            catch (IOException e) {}
        }
        else return;

        if (temp == null) return;
        image = temp;
        index = nextIndex;
        scrollbar.setValue (index);
    }

    public void scratch (float position)
    {
        if (last < 0) return;
        BufferedImage temp = null;
        int nextIndex = (int) Math.round (position * last);
        if      (nextIndex < first) nextIndex = first;
        else if (nextIndex > last ) nextIndex = last;

        if (vin != null)
        {
            checkReopenVideo ();
            vin.seekFrame (nextIndex);
            temp = vin.readNext ();
        }
        else if (suffix != null)
        {
            try {temp = ImageIO.read (path.resolve (nextIndex + "." + suffix).toFile ());}
            catch (IOException e) {}
        }
        else return;

        if (temp == null) return;
        image = temp;
        index = nextIndex;
    }

    public class PlayThread extends Thread
    {
        public boolean playing;

        public void run ()
        {
            playing = true;
            while (playing)
            {
                int oldIndex = index;
                nextImage ();
                if (index == oldIndex)
                {
                    if (job.complete < 1)  // Busy-wait for new images to come from simulation.
                    {
                        try
                        {
                            Thread.sleep (100);  // Balance between jerkiness and load on processor.
                            continue;
                        }
                        catch (InterruptedException e) {}
                    }
                    break;
                }
                CountDownLatch latch = new CountDownLatch (1);
                EventQueue.invokeLater (new Runnable ()
                {
                    public void run ()
                    {
                        if (playing) panelImage.paintImmediately (panelImage.getBounds ());
                        latch.countDown ();
                    }
                });
                try
                {
                    latch.await ();
                }
                catch (InterruptedException e)
                {
                    break;
                }
            }
            playing = false;
        }
    }

    public void play ()
    {
        if (panelImage == null) return;
        if (thread != null  &&  thread.playing) return;
        if (index >= last) index = -1;
        thread = new PlayThread ();
        thread.setDaemon (true);
        thread.start ();
    }

    /**
        Variant of play() called by PanelRun display thread when downloading remote image sequence.
        @param last Expected index of final image, based on size of remote dir.
    **/
    public void refresh (int last)
    {
        if (panelImage == null) return;
        if (thread != null  &&  thread.playing) return;
        if (last > this.last)
        {
            this.last = last;
            scrollbar.setMaximum (last + 1);
        }
        thread = new PlayThread ();
        thread.setDaemon (true);
        thread.start ();
    }

    public void pause ()
    {
        if (thread != null) thread.playing = false;
        thread = null;
    }

    @Override
    public void removeNotify ()
    {
        super.removeNotify ();
        pause ();
        if (vin != null) synchronized (this) {vin.close ();}  // Runs on EDT, but generally takes very little time.
    }

    public class PanelImage extends JPanel
    {
        public void paintComponent (Graphics g)
        {
            super.paintComponent (g);
            if (index < first) return;

            int pw = getWidth ();
            int ph = getHeight ();
            int iw = image.getWidth ();
            int ih = image.getHeight ();
            double ratioW = (double) pw / iw;
            double ratioH = (double) ph / ih;

            int w;
            int h;
            if (ratioW < ratioH)
            {
                w = pw;
                h = (int) Math.round (ih * ratioW);
            }
            else
            {
                w = (int) Math.round (iw * ratioH);
                h = ph;
            }
            int x = (pw - w) / 2;
            int y = (ph - h) / 2;
            g.drawImage (image, x, y, w, h, this);
        }
    }
}

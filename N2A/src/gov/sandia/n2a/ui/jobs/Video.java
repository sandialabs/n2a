/*
Copyright 2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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

import gov.sandia.n2a.ui.Lay;

@SuppressWarnings("serial")
public class Video extends JPanel
{
    protected Path          dir;
    protected String        suffix;
    protected int           index = -1;
    protected int           last;
    protected BufferedImage image;
    protected PlayThread    thread;

    protected JPanel     panelImage;
    protected JScrollBar scrollbar;

    public Video (Path dir)
    {
        this.dir = dir;

        try (Stream<Path> stream = Files.list (dir);)
        {
            last = (int) stream.count () - 1;
        }
        catch (Exception e)
        {
            return;
        }

        try (Stream<Path> stream = Files.list (dir);)
        {
            Optional<Path> someFile = stream.findAny ();
            Path p = someFile.get ();
            String[] pieces = p.getFileName ().toString ().split ("\\.");
            suffix = pieces[1].toLowerCase ();
        }
        catch (Exception e)
        {
            return;
        }

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

        scrollbar = new JScrollBar (JScrollBar.HORIZONTAL, 0, 1, 0, last + 1);
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

    public void nextImage ()
    {
        if (suffix == null) return;
        int nextIndex = index + 1;
        try
        {
            image = ImageIO.read (dir.resolve (nextIndex + "." + suffix).toFile ());
            if (nextIndex > last)
            {
                last = nextIndex;
                scrollbar.setMaximum (last + 1);
            }
            index = nextIndex;
            scrollbar.setValue (index);
        }
        catch (Exception e) {}
    }

    public void previousImage ()
    {
        if (suffix == null) return;
        int nextIndex = index - 1;
        if (nextIndex < 0) return;
        try
        {
            image = ImageIO.read (dir.resolve (nextIndex + "." + suffix).toFile ());
            index = nextIndex;
            scrollbar.setValue (index);
        }
        catch (IOException e) {}
    }

    public void scratch (float position)
    {
        if (suffix == null  ||  last < 0) return;
        int nextIndex = (int) Math.round (position * last);
        try
        {
            image = ImageIO.read (dir.resolve (nextIndex + "." + suffix).toFile ());
            index = nextIndex;
        }
        catch (IOException e) {}
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
                if (index == oldIndex) break;
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
    }

    public class PanelImage extends JPanel
    {
        public void paintComponent (Graphics g)
        {
            super.paintComponent (g);
            if (index < 0) return;

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

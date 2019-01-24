/*
Copyright 2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.JPanel;

@SuppressWarnings("serial")
public class Picture extends JPanel
{
    protected BufferedImage image;

    public Picture (Path path)
    {
        try {image = ImageIO.read (path.toFile ());}
        catch (IOException e) {}
    }

    public Dimension getPreferredSize ()
    {
        if (image == null) return new Dimension (100, 100);
        return new Dimension (image.getWidth (), image.getHeight ());
    }

    public void paintComponent (Graphics g)
    {
        super.paintComponent (g);
        if (image == null) return;
        int w = image.getWidth ();
        int h = image.getHeight ();
        g.drawImage (image, 0, 0, w, h, this);
    }
}

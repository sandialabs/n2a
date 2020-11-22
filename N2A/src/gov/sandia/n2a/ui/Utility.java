package gov.sandia.n2a.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;

public class Utility
{
    /**
        Create an icon on the fly which represents percent complete as a pie-chart
    **/
    public static ImageIcon makeProgressIcon (float percent)
    {
        BufferedImage result = new BufferedImage (16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics ();
        g.setBackground (new Color (0, 0, 0, 0));
        g.clearRect (0, 0, 16, 16);
        g.setColor (new Color (0.3f, 0.5f, 1));
        g.drawOval (0, 0, 14, 14);
        g.setColor (Color.black);
        g.fillArc (0, 0, 14, 14, 90, - Math.round (percent * 360));
        return new ImageIcon (result);
    }
}

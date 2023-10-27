/*
Copyright 2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import gov.sandia.n2a.db.MNode;

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

    /**
        Returns the custom icon stored in a part, or null if one does not exist.
    **/
    public static ImageIcon extractIcon (MNode part)
    {
        String base64 = part.get ("$meta", "gui", "icon");
        if (base64.isEmpty ()) return null;
    
        byte[] bytes = Base64.getDecoder ().decode (base64);
        try
        {
            return new ImageIcon (ImageIO.read (new ByteArrayInputStream (bytes)));
        }
        catch (IOException e)
        {
            return null;
        }
    }

    /**
        Forces an image to fit within a given size.
    **/
    public static ImageIcon rescale (ImageIcon icon, int maxWidth, int maxHeight)
    {
        // Create scaled instance, if needed.
        Image image = icon.getImage ();
        double w = icon.getIconWidth ();
        double h = icon.getIconHeight ();
        if (w > maxWidth)
        {
            h *= maxWidth / w;
            w  = maxWidth;
        }
        if (h > maxHeight)
        {
            w *= maxHeight / h;
            h  = maxHeight;
        }
        int width  = (int) Math.round (w);
        int height = (int) Math.round (h);
        return new ImageIcon (image.getScaledInstance (width, height, Image.SCALE_SMOOTH));
    }

    public static ImageIcon overlay (ImageIcon foreground, ImageIcon background)
    {
        return overlay (foreground, background, 1, 1);
    }

    public static ImageIcon overlay (ImageIcon foreground, ImageIcon background, float alphaForeground, float alphaBackground)
    {
        int w = foreground.getIconWidth ();
        int h = foreground.getIconHeight ();
        BufferedImage combined = new BufferedImage (w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = combined.createGraphics ();
        g.setComposite (AlphaComposite.getInstance (AlphaComposite.SRC_OVER, alphaBackground));
        g.drawImage (background.getImage (), 0, 0, null);
        g.setComposite (AlphaComposite.getInstance (AlphaComposite.SRC_OVER, alphaForeground));
        g.drawImage (foreground.getImage (), 0, 0, null);
        g.dispose ();
        return new ImageIcon (combined);
    }

    public static float[] HSLfromColor (Color c)
    {
        return HSLfromRGB (c.getRGBColorComponents (null));
    }

    public static float[] HSLfromRGB (float[] rgb)
    {
        float r = rgb[0];
        float g = rgb[1];
        float b = rgb[2];

        // Lightness
        float rgbmax = Math.max (r, Math.max (g, b));
        float rgbmin = Math.min (r, Math.min (g, b));
        float l = (rgbmax + rgbmin) / 2.0f;

        // Hue and Saturation
        float h;
        float s;
        if (rgbmax == rgbmin)
        {
            h = 0;
            s = 0;
        }
        else
        {
            float mmm = rgbmax - rgbmin;  // "max minus min"
            float mpm = rgbmax + rgbmin;  // "max plus min"

            // Saturation
            if (l <= 0.5f) s = mmm / mpm;
            else           s = mmm / (2.0f - mpm);

            // Hue
            float TwoPi    = (float) Math.PI * 2;
            float onethird = 1.0f / 3;
            float root32   = (float) Math.sqrt (3) / 2;
            float x =  -0.5f * r -   0.5f * g + b;
            float y = root32 * r - root32 * g;
            h = (float) Math.atan2 (y, x) / TwoPi - onethird;
            if (h < 0) h += 1;
        }

        return new float[] {h, s, l};
    }

    public static Color HSLtoColor (float[] hsl)
    {
        float h = hsl[0];
        float s = hsl[1];
        float l = hsl[2];

        float r;
        float g;
        float b;

        if (s == 0)
        {
            r = l;
            g = l;
            b = l;
        }
        else
        {
            float m2;
            if (l <= 0.5f) m2 = l + l * s;
            else           m2 = l + s - l * s;
            float m1 = 2.0f * l - m2;

            h -= Math.floor (h);

            float onethird = 1.0f / 3;
            r = HS (m1, m2, h + onethird);
            g = HS (m1, m2, h);
            b = HS (m1, m2, h - onethird);
        }

        return new Color (r, g, b);
    }

    protected static float HS (float n1, float n2, float h)
    {
        if (h > 1.0f) h -= 1.0f;
        if (h < 0)    h += 1.0f;

        float onesixth  = 1.0f / 6;
        float twothirds = 2.0f / 3;
        if (h < onesixth)  return n1 + (n2 - n1) * h * 6.0f;
        if (h < 0.5f)      return n2;
        if (h < twothirds) return n1 + (n2 - n1) * (twothirds - h) * 6.0f;
        return n1;
    }
}

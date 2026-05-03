/*
Copyright 2023-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Backend.AbortRun;
import gov.sandia.n2a.plugins.extpoints.Export;
import gov.sandia.n2a.plugins.extpoints.Import;

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

    /**
        Returns a color with maximum possible saturation, given the lightness level.
        The lightness is treated as a perceptual value in [0,1].
        To maximize color contrast on either a full-white or full-black background,
        use lightness 0.5.
    **/
    public static Color hueWithTrueLightness (float h, float l)
    {
        float[] hsl = new float[3];
        hsl[0] = h;
        hsl[1] = 1;
        hsl[2] = l;
        float[] rgb = HSLtoRGB (hsl);
        float y = 0.299f * rgb[0] + 0.587f * rgb[1] + 0.114f * rgb[2];
        float max = 0;
        for (int i = 0; i < 3; i++)
        {
            rgb[i] *= l / y;
            max = Math.max (max, rgb[i]);
        }
        if (max > 1)  // Can't reach full lightness, because some primaries (particularly G) are brighter than others.
        {
            for (int i = 0; i < 3; i++) rgb[i] /= max;
        }

        return new Color (rgb[0], rgb[1], rgb[2]);
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
        float[] rgb = HSLtoRGB (hsl);
        return new Color (rgb[0], rgb[1], rgb[2]);
    }

    public static float[] HSLtoRGB (float[] hsl)
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

        return new float[]{r, g, b};
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

    /**
        Import based only on file name and contents.
    **/
    public static void importFile (Path path)
    {
        Thread t = new Thread ()
        {
            public void run ()
            {
                Exception error = null;
                Backend.Capture.attach ();
                try
                {
                    importFile (path, null, null);
                }
                catch (Exception e)
                {
                    error = e;
                }
                fileImportExportException ("Import", error);
            }
        };
        t.setDaemon (true);
        t.start ();
    }

    /**
        Imports multiple files.
    **/
    public static void importFiles (List<File> files)
    {
        Thread t = new Thread ()
        {
            public void run ()
            {
                Exception error = null;
                Backend.Capture.attach ();
                UndoManager um = MainFrame.undoManager;
                EventQueue.invokeLater (new Runnable ()
                {
                    public void run ()
                    {
                        um.addEdit (new CompoundEdit ());  // in case there is more than one file
                    }
                });
                for (File file : files)
                {
                    try
                    {
                        importFile (file.toPath (), null, null);
                    }
                    catch (Exception e)
                    {
                        error = e;
                    }
                }
                EventQueue.invokeLater (new Runnable ()
                {
                    public void run ()
                    {
                        um.endCompoundEdit ();
                    }
                });
                fileImportExportException ("Import", error);
            }
        };
        t.setDaemon (true);
        t.start ();
    }

    /**
        Import with hints.
        Can be called from CLI (headless) or from GUI.
        Threading and Backend.err should be configured by caller.
        The Import class checks whether it is headless or UI.
        If UI, the Import class creates Undoable transactions for UI update.
        @param format Name of the format, as given by Import.getName()
        @param name Suggested name for resulting DB record.
    **/
    public static void importFile (Path path, String format, String name) throws Exception
    {
        Import bestImporter = null;
        float  bestP        = 0;
        for (ExtensionPoint exp : PluginManager.getExtensionsForPoint (Import.class))
        {
            Import imp = (Import) exp;
            if (format != null  &&  imp.getName ().equalsIgnoreCase (format))
            {
                bestImporter = imp;
                break;
            }
            float P = imp.matches (path);
            if (P > bestP)
            {
                bestP        = P;
                bestImporter = (Import) exp;
            }
        }
        if (bestImporter != null) bestImporter.process (path, name);
    }

    /**
        Import based only on file name and contents.
    **/
    public static void exportFile (Export exporter, MNode document, Path path)
    {
        Thread t = new Thread ()
        {
            public void run ()
            {
                Exception error = null;
                Backend.Capture.attach ();
                try
                {
                    exporter.process (document, path);
                }
                catch (Exception e)
                {
                    error = e;
                }
                fileImportExportException ("Export", error);
            }
        };
        t.setDaemon (true);
        t.start ();
    }

    /**
        Report an import error to the user via UI and crashdump file.
        If Backend.err currently maps to a string printer, its contents is assumed to be
        warnings associated with the current operation. Removes the string printer from err.
        @param error A exception that was thrown during the process. May be null if no
        exception was thrown.
    **/
    public static void fileImportExportException (String direction, Exception error)
    {
        // Collect strings and determine level of success/failure.
        PrintStream ps = Backend.err.get ();  // Backend.err should always be a Capture at this point.
        if      (error instanceof AbortRun) ps.println (error.getLocalizedMessage ());
        else if (error != null)             error.printStackTrace (ps);
        String message = Backend.Capture.finish ();
        if (message.isEmpty ()) return;  // Nothing to report

        Path resourceDir = Paths.get (AppData.properties.get ("resourceDir"));
        try (BufferedWriter writer = Files.newBufferedWriter (resourceDir.resolve ("crashdump")))
        {
            writer.write (message);
        }
        catch (IOException e) {}

        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                boolean failed =  error != null;
                JTextArea textArea = new JTextArea (message);
                JScrollPane scrollPane = new JScrollPane (textArea);
                scrollPane.setPreferredSize (new java.awt.Dimension (640, 480));
                JOptionPane.showMessageDialog
                (
                    MainFrame.instance,
                    scrollPane,
                    direction + (failed ? " failed" : " completed with warnings"),
                    failed ? JOptionPane.ERROR_MESSAGE :  JOptionPane.WARNING_MESSAGE
                );
            }
        });
    }
}

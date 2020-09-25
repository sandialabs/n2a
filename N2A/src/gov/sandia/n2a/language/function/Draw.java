/*
Copyright 2019-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.plugins.extpoints.Backend;
import tech.units.indriya.AbstractUnit;

public class Draw extends Function
{
    public boolean isOutput ()
    {
        return true;
    }

    public boolean canBeConstant ()
    {
        return false;
    }

    public void determineExponent (ExponentContext context)
    {
        updateExponent (context, MSB, 0);
    }

    public void determineExponentNext ()
    {
        for (int i = 0; i < operands.length; i++)
        {
            Operator op = operands[i];
            op.exponentNext = op.exponent;
            op.determineExponentNext ();
        }
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        unit = AbstractUnit.ONE;
    }

    public Type getType ()
    {
        return new Scalar ();
    }

    public static class Holder implements gov.sandia.n2a.backend.internal.Holder
    {
        public Path    path;
        public boolean single;              // Store a single frame rather than an image sequence.
        public String  format     = "png";  // name of format as recognized by supporting libraries
        public boolean dirCreated;

        public boolean raw;
        public int     width      = 1024;
        public int     height     = 1024;
        public Color   clearColor = Color.BLACK;

        // TODO: handle video streams. Probably use FFMPEG JNI wrapper.
        public double           t;
        public int              frameCount; // Number of frames actually written so far.
        public BufferedImage    image;      // Current image being built. Null if nothing has been drawn since last write to disk.
        public Graphics2D       graphics;   // for drawing on current image
        public Line2D.Double    line;       // Re-usable Shape object
        public Ellipse2D.Double disc;       // ditto

        public Holder (Simulator simulator, String filename)
        {
            Path file = simulator.jobDir.resolve (filename);
            Path parent = file.getParent ();
            filename = file.getFileName ().toString ();
            String[] pieces = filename.split ("\\.");
            if (pieces.length > 1)
            {
                format = pieces[pieces.length - 1].toLowerCase ();
                filename = filename.substring (0, filename.length () - format.length () - 1);
            }
            path = parent.resolve (filename);
        }

        public void close ()
        {
            writeImage ();
        }

        public void next (double now)
        {
            if (now > t)
            {
                writeImage ();
                t = now;
            }
            if (image == null)
            {
                image = new BufferedImage (width, height, BufferedImage.TYPE_INT_ARGB);
                graphics = image.createGraphics ();
                graphics.setColor (clearColor);
                graphics.fillRect (0, 0, width, height);
            }
        }

        public void drawDisc (double now, double x, double y, double radius, int color)
        {
            next (now);

            if (! raw)
            {
                x      *= width;
                y      *= width;
                radius *= width;
            }
            if (radius < 0.5) radius = 0.5;  // 1px diameter

            double w = 2 * radius;
            if (disc == null) disc = new Ellipse2D.Double (x - radius, y - radius, w, w);
            else              disc.setFrame               (x - radius, y - radius, w, w);

            graphics.setColor (new Color (color));
            graphics.fill (disc);
        }

        public void drawSegment (double now, double x, double y, double x2, double y2, double thickness, int color)
        {
            next (now);

            if (! raw)
            {
                x         *= width;
                y         *= width;
                x2        *= width;
                y2        *= width;
                thickness *= width;
            }
            if (width < 1) width = 1;

            if (line == null) line = new Line2D.Double (x, y, x2, y2);
            else              line.setLine             (x, y, x2, y2);
            graphics.setStroke (new BasicStroke ((float) thickness));
            graphics.setColor (new Color (color));
            graphics.draw (line);
        }

        public void writeImage ()
        {
            if (image == null) return;

            String filename;
            if (single)
            {
                filename = path.toString () + "." + format;
            }
            else
            {
                if (! dirCreated)
                {
                    path.toFile ().getAbsoluteFile ().mkdirs ();
                    dirCreated = true;
                }
                filename = path.resolve (String.format ("%d.%s", frameCount, format)).toString ();
            }
            // Path.toAbsolutePath() does not resolve against job directory the same way File.getAbsoluteFile() does.
            try {ImageIO.write (image, format, new File (filename).getAbsoluteFile ());}
            catch (IOException e) {e.printStackTrace ();}

            if (! single) image = null;
            frameCount++;
        }
    }

    public Holder getHolder (Simulator simulator, Instance context)
    {
        String path = ((Text) operands[0].eval (context)).value;
        Object o = simulator.holders.get (path);
        if (o == null)
        {
            Holder H = new Holder (simulator, path);
            simulator.holders.put (path, H);

            if (operands.length > 3)
            {
                String mode = operands[operands.length-1].getString ();  // mode should not require eval, just retrieval
                H.raw    = mode.contains ("raw");
                H.single = mode.contains ("single");
                String[] pieces = mode.split (",");
                for (String p : pieces)
                {
                    String[] pieces2 = p.split ("=");
                    if (pieces2.length == 1) continue;
                    switch (pieces2[0])
                    {
                        case "size":
                            String[] pieces3 = pieces2[1].split ("x");
                            try
                            {
                                H.width = Integer.valueOf (pieces3[0]);
                                H.height = H.width;
                            }
                            catch (NumberFormatException e) {}
                            if (pieces3.length > 1)
                            {
                                try {H.height = Integer.valueOf (pieces3[1]);}
                                catch (NumberFormatException e) {}
                            }
                            break;
                        case "clear":
                            try {H.clearColor = Color.decode (pieces2[1]);}
                            catch (NumberFormatException e) {}
                            break;
                    }
                }
            }
            return H;
        }
        if (! (o instanceof Holder))
        {
            Backend.err.get ().println ("ERROR: Reopening file as a different resource type.");
            throw new Backend.AbortRun ();
        }
        return (Holder) o;
    }
}

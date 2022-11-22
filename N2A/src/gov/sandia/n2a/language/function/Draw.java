/*
Copyright 2019-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

import gov.sandia.n2a.backend.c.VideoIn;
import gov.sandia.n2a.backend.c.VideoOut;
import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Backend.AbortRun;
import tech.units.indriya.AbstractUnit;

public class Draw extends Function
{
    public String  name;     // For C backend, the name of the ImageOutput object.
    public String  fileName; // For C backend, the name of the string variable holding the file name, if any.

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "draw";
            }

            public Operator createInstance ()
            {
                return new Draw ();
            }
        };
    }

    /**
        Marks a drawX() that actually has output, as distinct from generic draw().
        Simplifies analysis code in EquationSet.addDrawDependencies.
    **/
    public interface Shape {}

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
        for (Operator op : operands)
        {
            op.determineExponent (context);
        }
        updateExponent (context, MSB, 0);  // Our output is always an integer (1).

        if (keywords == null) return;
        for (Operator op : keywords.values ())
        {
            op.determineExponent (context);
        }
    }

    public void determineExponentNext ()
    {
        // First arg is fileName, so we don't care about it.
        Operator op0 = operands[0];
        op0.exponentNext = op0.exponent;
        op0.determineExponentNext ();

        // Last arg is color, which is always a raw integer.
        int last = operands.length - 1;
        Operator c = operands[last];
        c.exponentNext = MSB;
        c.determineExponentNext ();

        // All pixel-valued operands must agree on exponent.
        if (last > 1)
        {
            int avg = 0;
            for (int i = 1; i < last; i++) avg += operands[i].exponent;
            avg /= last - 1;
            for (int i = 1; i < last; i++)
            {
                Operator op = operands[i];
                op.exponentNext = avg;
                op.determineExponentNext ();
            }
        }

        if (keywords == null) return;
        for (Operator op : keywords.values ())
        {
            op.exponentNext = MSB;  // Currently, all keyword args have integer (or boolean) values.
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

    public static class Holder implements AutoCloseable
    {
        public Path     path;
        public boolean  hold;         // Store a single frame rather than an image sequence.
        public String   format = "";  // name of format as recognized by supporting libraries
        public String   codec  = "";  // nmae of codec for video file
        public VideoOut vout;         // If null, then output an image sequence instead.
        public boolean  opened;
        public boolean  dirCreated;

        public int   width      = 1024;
        public int   height     = 1024;
        public Color clearColor = Color.BLACK;

        public double             t;
        public double             timeScale;
        public int                frameCount; // Number of frames actually written so far.
        public BufferedImage      image;      // Current image being built. Null if nothing has been drawn since last write to disk.
        public Graphics2D         graphics;   // for drawing on current image
        public Line2D.Double      line;       // Re-usable Shape object
        public Ellipse2D.Double   disc;       // ditto
        public Rectangle2D.Double rect;       // ditto

        public Holder (Path path)
        {
            this.path = path;
        }

        public void open ()
        {
            opened = true;

            Path parent = path.getParent ();
            String fileName = path.getFileName ().toString ();
            String prefix = fileName;
            String suffix = "";
            String[] pieces = fileName.split ("\\.");
            if (pieces.length > 1)
            {
                suffix = pieces[pieces.length - 1].toLowerCase ();
                prefix = fileName.substring (0, fileName.length () - suffix.length () - 1);
            }
            int posPercent = prefix.lastIndexOf ('%');
            if (posPercent >= 0) prefix = prefix.substring (0, posPercent);
            if (prefix.isBlank ()) prefix = "frame";

            Host localhost = Host.get ("localhost");
            if (! localhost.objects.containsKey ("ffmpegJNI")) VideoIn.prepareJNI ();
            if (localhost.objects.containsKey ("ffmpegJNI"))
            {
                // Check if this is an image sequence, in which case modify file name to go into subdir.
                Path videoPath;
                if (posPercent < 0)  // Single video file
                {
                    videoPath  = path;
                    dirCreated = true;  // Do't create the dir when writing.
                }
                else  // Image sequence, so create a subdirectory. This is more user-friendly for Runs tab.
                {
                    String temp = "%d";
                    if (! suffix.isBlank ()) temp += "." + suffix;
                    path      = parent.resolve (prefix);  // Even though we use FFmpeg, we still want to pre-create the directory.
                    videoPath = path.resolve (temp);
                }
                vout = new VideoOut (videoPath, format, codec);
                if (vout.good ()) return;

                // Fall through image sequence code below ...
                vout = null;
            }

            if (format.isBlank ()) format = suffix;
            if (format.isBlank ()) format = "png";
            path = parent.resolve (prefix);
        }

        public void close ()
        {
            hold = false;
            writeImage ();
            if (vout != null) vout.close ();
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

        public void drawDisc (double now, boolean raw, double x, double y, double radius, int color)
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

        public void drawBlock (double now, boolean raw, double x, double y, double w, double h, int color)
        {
            next (now);

            if (! raw)
            {
                x *= width;
                y *= width;
                w *= width;
                h *= width;
            }
            if (w < 0.5) w = 0.5;  // 1px
            if (h < 0.5) h = 0.5;

            if (rect == null) rect = new Rectangle2D.Double (x - w/2, y - h/2, w, h);
            else              rect.setFrame                 (x - w/2, y - h/2, w, h);

            graphics.setColor (new Color (color));
            graphics.fill (rect);
        }

        public void drawSegment (double now, boolean raw, double x, double y, double x2, double y2, double thickness, int color)
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
            if (hold) return;

            if (! opened) open ();
            if (! dirCreated)
            {
                path.toFile ().getAbsoluteFile ().mkdirs ();
                dirCreated = true;
            }

            if (vout != null)
            {
                double timestamp;
                if (timeScale == 0) timestamp = 1e6;  // Exceeds 95443, the threshold at which VideoOut stops using the timestamp as PTS.
                else                timestamp = timeScale * t;
                vout.writeNext (image, timestamp);
            }
            else
            {
                String filename = path.resolve (String.format ("%d.%s", frameCount, format)).toString ();
                // Path.toAbsolutePath() does not resolve against job directory the same way File.getAbsoluteFile() does.
                try
                {
                    boolean success = ImageIO.write (image, format, new File (filename).getAbsoluteFile ());
                    if (! success)
                    {
                        format = "png";  // This should always be available in JVM. Preferable over JPEG because it is lossless.
                        filename = path.resolve (String.format ("%d.%s", frameCount, format)).toString ();
                        success = ImageIO.write (image, format, new File (filename).getAbsoluteFile ());
                        if (! success) throw new AbortRun ("Failed to write images because format was not available.");
                    }
                }
                catch (IOException e) {e.printStackTrace ();}
            }

            image = null;
            frameCount++;
        }
    }

    public Holder getHolder (Simulator simulator, Instance context)
    {
        String path = ((Text) operands[0].eval (context)).value;
        Object o = simulator.holders.get (path);
        if (o == null)
        {
            Holder H = new Holder (simulator.jobDir.resolve (path));
            simulator.holders.put (path, H);
            return H;
        }
        if (! (o instanceof Holder))
        {
            Backend.err.get ().println ("ERROR: Reopening file as a different resource type.");
            throw new Backend.AbortRun ();
        }
        return (Holder) o;
    }

    public boolean applyKeywords (Instance context, Holder H)
    {
        if (keywords == null) return false;

        // Don't apply a keyword unless it is explicitly present.
        // For compactness, use utility routines from Function, even though this is a little more expensive.

        boolean raw = false;
        int width  = 0;
        int height = 0;
        for (String key : keywords.keySet ())
        {
            switch (key)
            {
                case "width":     width        = evalKeyword     (context, "width",     0);           break;
                case "height":    height       = evalKeyword     (context, "height",    0);           break;
                case "timeScale": H.timeScale  = evalKeyword     (context, "timeScale", 0.0);         break;
                case "format":    H.format     = evalKeyword     (context, "format",    "");          break;
                case "codec":     H.codec      = evalKeyword     (context, "codec",     "");          break;
                case "clear":     H.clearColor = evalKeyword     (context, "clear",     Color.black); break;
                case "hold":      H.hold       = evalKeywordFlag (context, "hold");                   break;
                case "raw":       raw          = true;                                                break;
            }
        }

        if      (width <= 0  &&  height >  0) width  = height;
        else if (width >  0  &&  height <= 0) height = width;
        if (width  > 0) H.width  = width;
        if (height > 0) H.height = height;

        return raw;
    }

    public Type eval (Instance context)
    {
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return new Scalar (0);

        Holder H = getHolder (simulator, context);
        applyKeywords (context, H);

        // We don't do any actual drawing, nor do we advance the clock.
        // All this function does is stage canvas and camera configurations
        // for the next time the clock advances by a drawX() that makes a mark.

        return new Scalar (1);
    }

    public String toString ()
    {
        return "draw";
    }
}

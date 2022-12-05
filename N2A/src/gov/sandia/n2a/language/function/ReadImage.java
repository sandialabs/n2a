/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import gov.sandia.n2a.backend.c.VideoIn;
import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.linear.MatrixDense;
import gov.sandia.n2a.plugins.extpoints.Backend;
import tech.units.indriya.AbstractUnit;

public class ReadImage extends Function
{
    public String name;     // For C backend, the name of the ImageInput object.
    public String fileName; // For C backend, the name of the string variable holding the file name, if any.

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "image";
            }

            public Operator createInstance ()
            {
                return new ReadImage ();
            }
        };
    }

    public boolean canBeConstant ()
    {
        return false;
    }

    public boolean canBeInitOnly ()
    {
        return true;
    }

    // Not a matrix input, so no need to define isMatrixInput().

    public void determineExponent (ExponentContext context)
    {
        // Color channels are generally in [0,1]
        updateExponent (context, 0, MSB - 1);

        // operands[0] is the file string
        // operands[1] is the color string
        // operands[2] is PTS, which should be scaled the same as $t.
        if (operands.length > 2)
        {
            operands[2].determineExponent (context);
            operands[2].exponentNext = context.exponentTime;  // Stash this now, since it won't be available during determineExponentNext().
        }
    }

    public void determineExponentNext ()
    {
        exponent = exponentNext;  // Conversion done while reading.
        operands[2].determineExponentNext ();
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        unit = AbstractUnit.ONE;
    }

    public Type getType ()
    {
        return new MatrixDense ();
    }

    public static class Holder implements AutoCloseable
    {
        public VideoIn                 video;
        public BufferedImage           image;
        public Path                    path;     // Directory that contains image sequence. Null if not an image sequence.
        public String                  pattern;  // printf string for generating image sequence file names
        public int                     index;    // of current image in sequence
        public double                  t = Double.NEGATIVE_INFINITY;
        public double                  framePeriod;
        public Map<String,MatrixDense> channels = new HashMap<String,MatrixDense> ();
        public boolean                 haveFFmpeg;
        public boolean                 haveJNI;
        public boolean                 JNIwarning;

        public Holder (Simulator simulator, String pathString)
        {
            Host localhost = Host.get ("localhost");
            if (! localhost.objects.containsKey ("ffmpegJNI")) VideoIn.prepareJNI ();
            haveFFmpeg = localhost.objects.containsKey ("ffmpegJNI");
            haveJNI    = localhost.objects.containsKey ("JNI");

            // See similar code in jobs.Video
            Path path = simulator.jobDir.resolve (pathString);  // The simulation thread can't easily set a working directory, so instead resolve relative to job dir.
            if (Files.isDirectory (path))
            {
                this.path = path;  // Indicate that we have an image sequence handled internally.
                try (Stream<Path> stream = Files.list (path);)
                {
                    Optional<Path> someFile = stream.findAny ();
                    Path p = someFile.get ();
                    String[] pieces = p.getFileName ().toString ().split ("\\.");
                    pattern = "%d." + pieces[1];

                    // Detect if sequence is 1-based rather than 0-based.
                    if (! Files.exists (path.resolve (pattern.formatted (0)))) index = 1;
                }
                catch (Exception e)
                {
                    return;
                }
            }
            else  // File or pattern
            {
                pattern = path.getFileName ().toString ();
                boolean sequence = pattern.contains ("%");

                // Try to use FFmpeg.
                if (haveFFmpeg)
                {
                    video = new VideoIn (path);
                    if (video.good ())
                    {
                        AppData.cleaner.register (this, video);
                        if (sequence) framePeriod = Double.valueOf (video.get ("framePeriod"));
                        return;
                    }
                    video.close ();
                    video = null;
                }

                // Fall back on Java image I/O.
                if (sequence)
                {
                    this.path = path.getParent ();
                    if (! Files.exists (this.path.resolve (pattern.formatted (0)))) index = 1;
                }
                else  // single image
                {
                    try {image = ImageIO.read (path.toFile ());}
                    catch (Exception e) {}
                }
            }
        }

        public void close () throws Exception
        {
            if (video == null) return;
            video.close ();
            video = null;
        }

        public MatrixDense get (String channelName, double now)
        {
            // Fetch next image, if needed.
            if (video != null  ||  path != null)
            {
                if (Double.doubleToLongBits (now) < 0)  // also detects negative zero
                {
                    if (now != t)
                    {
                        t = now;
                        BufferedImage temp = null;
                        if (video != null)
                        {
                            temp = video.readNext ();
                        }
                        else if (path != null)
                        {
                            try {temp = ImageIO.read (path.resolve (pattern.formatted (index++)).toFile ());}
                            catch (Exception e) {}
                        }
                        if (temp != null)
                        {
                            image = temp;  // Keep last image when we reach end of file.
                            channels.clear ();
                        }
                    }
                }
                else
                {
                    if (now >= t)
                    {
                        BufferedImage temp = null;
                        if (video != null)
                        {
                            // TODO: seek to frame that contains "now", rather than assuming that we progress in single steps.
                            temp = video.readNext ();
                            if (temp != null)
                            {
                                t = Double.valueOf (video.get ("nextPTS"));
                                if (framePeriod != 0) t = Math.round (t / framePeriod);
                            }
                        }
                        else if (path != null)
                        {
                            index = (int) Math.floor (now);
                            try {temp = ImageIO.read (path.resolve (pattern.formatted (index)).toFile ());}
                            catch (Exception e) {}
                            if (temp != null) t = index + 1;
                        }
                        if (temp != null)
                        {
                            image = temp;
                            channels.clear ();
                        }
                    }
                }
            }
            if (image == null) return new MatrixDense ();

            // Create converted channels, if needed.
            int colorSpace;
            switch (channelName)
            {
                case "R",  "G",  "B":  colorSpace = 0; break;
                case "R'", "G'", "B'": colorSpace = 1; break;
                default: channelName = "Y";
                case "X",  "Y",  "Z":  colorSpace = 2; break;
                case "H",  "S",  "V":  colorSpace = 3; break;
            }
            MatrixDense A = channels.get (channelName);
            if (A != null) return A;

            String c0 = null;
            String c1 = null;
            String c2 = null;
            switch (colorSpace)
            {
                case 0: c0 = "R";  c1 = "G";  c2 = "B";  break;
                case 1: c0 = "R'"; c1 = "G'"; c2 = "B'"; break;
                case 2: c0 = "X";  c1 = "Y";  c2 = "Z";  break;
                case 3: c0 = "H";  c1 = "S";  c2 = "V";  break;
            }
            int width  = image.getWidth ();
            int height = image.getHeight ();

            if (haveJNI)
            {
                A = VideoIn.convert (image, colorSpace);
                double[] data = A.getData ();
                channels.put (c0, new MatrixDense (data, 0, width, height, 3, 3 * width));
                channels.put (c1, new MatrixDense (data, 1, width, height, 3, 3 * width));
                channels.put (c2, new MatrixDense (data, 2, width, height, 3, 3 * width));
            }
            else  // simple image processing
            {
                if (colorSpace != 1  &&  ! JNIwarning)
                {
                    Backend.err.get ().println ("WARNING: JNI image processing not available, so there is limited support for color channels.");
                    JNIwarning = true;
                }

                if (colorSpace == 0  ||  colorSpace == 1)  // We won't do the work to create linear channels, but still offer an approximation.
                {
                    // Extract RGB channels from image.
                    MatrixDense R = new MatrixDense (width, height);
                    MatrixDense G = new MatrixDense (width, height);
                    MatrixDense B = new MatrixDense (width, height);
                    for (int y = 0; y < height; y++)
                    {
                        for (int x = 0; x < width; x++)
                        {
                            int rgb = image.getRGB (x, y);
                            R.set (x, y, ((rgb & 0xFF0000) >> 16) / 255.0);
                            G.set (x, y, ((rgb &   0xFF00) >>  8) / 255.0);
                            B.set (x, y, ( rgb &     0xFF       ) / 255.0);
                        }
                    }
                    channels.put (c0, R);
                    channels.put (c1, G);
                    channels.put (c2, B);
                }
                else  // Force to gray channel "Y"
                {
                    // Approximate gray channel
                    channelName = "Y";
                    MatrixDense Y = new MatrixDense (width, height);
                    for (int y = 0; y < height; y++)
                    {
                        for (int x = 0; x < width; x++)
                        {
                            int rgb = image.getRGB (x, y);
                            double r = ((rgb & 0xFF0000) >> 16) / 255.0;
                            double g = ((rgb &   0xFF00) >>  8) / 255.0;
                            double b = ( rgb &     0xFF       ) / 255.0;
                            Y.set (x, y, 0.2989 * r + 0.5866 *g + 0.1145 * b);  // Standard conversion factors for non-linear sRGB to gray (Y in YUV, but not exactly Y in XYZ).
                        }
                    }
                    channels.put ("Y", Y);
                }
            }
            return channels.get (channelName);
        }
    }

    public Holder get (Simulator simulator, String path)
    {
        Holder result;
        Object o = simulator.holders.get (path);
        if (o == null)
        {
            result = new Holder (simulator, path);
            simulator.holders.put (path, result);
        }
        else if (! (o instanceof Holder))
        {
            Backend.err.get ().println ("ERROR: Reopening file as a different resource type.");
            throw new Backend.AbortRun ();
        }
        else result = (Holder) o;
        return result;
    }

    public Type eval (Instance context)
    {
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return new MatrixDense ();  // absence of simulator indicates analysis phase, so opening files is unnecessary

        String path = ((Text) operands[0].eval (context)).value;
        Holder H = get (simulator, path);

        String channelName;
        if (operands.length > 1) channelName = ((Text) operands[1].eval (context)).value;
        else                     channelName = "Y";

        double t;
        if (operands.length > 2) t = ((Scalar) operands[2].eval (context)).value;
        else                     t = - simulator.currentEvent.t;

        return H.get (channelName, t);
    }

    public String toString ()
    {
        return "image";
    }
}

/*
Copyright 2019-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import com.jogamp.opengl.DefaultGLCapabilitiesChooser;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.GLUniformData;
import com.jogamp.opengl.math.FloatUtil;
import com.jogamp.opengl.util.GLArrayDataServer;
import com.jogamp.opengl.util.PMVMatrix;
import com.jogamp.opengl.util.awt.AWTGLReadBufferUtil;
import com.jogamp.opengl.util.glsl.ShaderCode;
import com.jogamp.opengl.util.glsl.ShaderProgram;
import com.jogamp.opengl.util.glsl.ShaderState;

import gov.sandia.n2a.backend.c.VideoIn;
import gov.sandia.n2a.backend.c.VideoOut;
import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.linear.MatrixDense;
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

    public Type eval (Instance context)
    {
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return new Scalar (0);

        Holder H = getHolder (simulator, context);
        applyKeywords (context, H);

        // We don't do any actual drawing, nor do we advance the clock.
        // All this function does is stage canvas and camera configurations
        // for the next time the clock advances by a drawX() that makes a mark.

        return new Scalar (0);
    }

    public String toString ()
    {
        return "draw";
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
                case "width":      width        =          evalKeyword     (context, "width",     0);           break;
                case "height":     height       =          evalKeyword     (context, "height",    0);           break;
                case "timeScale":  H.timeScale  =          evalKeyword     (context, "timeScale", 0.0);         break;
                case "format":     H.format     =          evalKeyword     (context, "format",    "");          break;
                case "codec":      H.codec      =          evalKeyword     (context, "codec",     "");          break;
                case "clear":      H.clearColor =          evalKeyword     (context, "clear",     Color.black); break;
                case "view":       H.view       = (Matrix) evalKeyword     (context, key);                      break;
                case "projection": H.projection = (Matrix) evalKeyword     (context, key);                      break;
                case "hold":       H.hold       =          evalKeywordFlag (context, "hold");                   break;
                case "raw":        raw          =          evalKeywordFlag (context, "raw");                    break;
            }
        }

        if      (width <= 0  &&  height >  0) width  = height;
        else if (width >  0  &&  height <= 0) height = width;
        if (width  > 0) H.width  = width;
        if (height > 0) H.height = height;

        return raw;
    }

    public static class Holder implements AutoCloseable
    {
        public Path     path;
        public boolean  hold;         // Store a single frame rather than an image sequence.
        public String   format = "";  // name of format as recognized by supporting libraries
        public String   codec  = "";  // name of codec for video file
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

        // OpenGL support
        public GLAutoDrawable         drawable;
        public ShaderState            st;
        public PMVMatrix              pv;
        public Matrix                 view;
        public Matrix                 projection;
        public TreeMap<Integer,Light> lights;
        public boolean                have3D;  // 3D objects were drawn during the current cycle

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
            if (drawable != null)
            {
                GL2ES2 gl = drawable.getGL ().getGL2ES2 ();
                if (st != null) st.destroy (gl);
                drawable.destroy ();
            }
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
            }
        }

        // Called immediately after next() to prepare for 3D drawing.
        public void next3D ()
        {
            // One-time setup of GL for entire simulation
            int w = image.getWidth ();
            int h = image.getHeight ();
            if (drawable != null  &&  (drawable.getSurfaceWidth () != w  ||  drawable.getSurfaceHeight () != h))
            {
                GL2ES2 gl = drawable.getGL ().getGL2ES2 ();
                if (st != null) st.destroy (gl);
                st = null;
                drawable.destroy ();
                drawable = null;
            }
            if (drawable == null)
            {
                GLProfile glp = GLProfile.getDefault ();
                GLCapabilities caps = new GLCapabilities (glp);
                caps.setHardwareAccelerated (true);
                caps.setDoubleBuffered (false);
                caps.setAlphaBits (8);
                caps.setRedBits (8);
                caps.setBlueBits (8);
                caps.setGreenBits (8);
                caps.setOnscreen (false);
                GLDrawableFactory factory = GLDrawableFactory.getFactory (glp);

                drawable = factory.createOffscreenAutoDrawable (factory.getDefaultDevice(), caps, new DefaultGLCapabilitiesChooser(), w, h);
                drawable.display ();
                drawable.getContext ().makeCurrent();
            }
            GL2ES2 gl = drawable.getGL ().getGL2ES2 ();
            if (st == null)
            {
                ShaderCode vp = ShaderCode.create (gl, GL2ES2.GL_VERTEX_SHADER,   this.getClass(), "", "", "Shader", "vp", null, true);
                ShaderCode fp = ShaderCode.create (gl, GL2ES2.GL_FRAGMENT_SHADER, this.getClass(), "", "", "Shader", "fp", null, true);
                ShaderProgram sp = new ShaderProgram ();
                vp.defaultShaderCustomization (gl, true, true);
                fp.defaultShaderCustomization (gl, true, true);
                sp.add (gl, vp, System.err);
                sp.add (gl, fp, System.err);

                st = new ShaderState ();
                st.attachShaderProgram (gl, sp, false);
                st.bindAttribLocation (gl, 0, "vertexPosition");
                st.bindAttribLocation (gl, 1, "vertexNormal");
                st.useProgram (gl, true);
            }

            // Setup for current cycle
            if (! have3D)
            {
                gl.glEnable (GL.GL_DEPTH_TEST);
                //gl.glEnable (GL.GL_CULL_FACE);
                float[] cv = clearColor.getRGBComponents (null);  // always RGBA
                gl.glClearColor (cv[0], cv[1], cv[2], cv[3]);
                gl.glClear (GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

                if (pv == null) pv = new PMVMatrix ();
                pv.glMatrixMode (PMVMatrix.GL_MODELVIEW);
                if (view == null) pv.glLoadIdentity ();
                else              pv.glLoadMatrixf (getMatrix (view), 0);
                pv.glMatrixMode (PMVMatrix.GL_PROJECTION);
                if (projection == null)  // Use default projection
                {
                    pv.glLoadIdentity ();  // because glOrtho multiplies the current matrix.
                    float s = 50e-6f;
                    if (w <= h)
                    {
                        float r = (float) h / w;
                        pv.glOrthof (-s, s, -s*r, s*r, -s, s);
                    }
                    else
                    {
                        float r = (float) w / h;
                        pv.glOrthof (-s*r, s*r, -s, s, -s, s);
                    }
                }
                else  // Use given projection
                {
                    pv.glLoadMatrixf (getMatrix (projection), 0);
                }

                st.uniform (gl, new GLUniformData ("modelViewMatrix",  4, 4, pv.glGetMvMatrixf ()));
                st.uniform (gl, new GLUniformData ("normalMatrix",     4, 4, pv.glGetMvitMatrixf ()));
                st.uniform (gl, new GLUniformData ("projectionMatrix", 4, 4, pv.glGetPMatrixf ()));

                if (lights == null) lights = new TreeMap<Integer,Light> ();
                if (lights.isEmpty ()) lights.put (0, new Light ());
                int i = 0;
                for (Light l : lights.values ())
                {
                    l.setUniform (i++, this);
                    if (i >= 8) break;
                }
                st.uniform (gl, new GLUniformData ("enabled", i));
            }
            have3D = true;
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

            BufferedImage background;
            Graphics2D g2;
            int w = image.getWidth ();
            int h = image.getHeight ();
            if (have3D)  // Composite 2D and 3D outputs.
            {
                background = new AWTGLReadBufferUtil (drawable.getGLProfile (), true).readPixelsToBufferedImage (drawable.getGL (), 0, 0, w, h, true);
                try
                {
                    ImageIO.write(background, "png", new File("/Users/frothga/gltest.png"));
                }
                catch (IOException e)
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                g2 = background.createGraphics ();
            }
            else  // Fill background with clear color, since this won't be provided by the 3D scene.
            {
                background = new BufferedImage (w, h, BufferedImage.TYPE_INT_ARGB);
                g2 = background.createGraphics ();
                g2.setColor (clearColor);
                g2.fillRect (0, 0, w, h);
            }
            g2.drawImage (image, 0, 0, null);
            image = background;
            g2.dispose ();

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
            have3D = false;
            frameCount++;
        }
    }

    public static float[] getMatrix (Matrix A)
    {
        float[] result = new float[16];
        int i = 0;
        for (int c = 0; c < 4; c++)
        {
            for (int r = 0; r < 4; r++)
            {
                result[i++] = (float) A.get (r, c);
            }
        }
        return result;
    }

    public static GLUniformData uniformVector (String name, float x, float y, float z)
    {
        float[] b = new float[3];
        b[0] = x;
        b[1] = y;
        b[2] = z;
        FloatBuffer buffer = FloatBuffer.wrap (b);
        return new GLUniformData (name, 3, buffer);
    }

    public static GLUniformData uniformVector (String name, float[] data)
    {
        FloatBuffer buffer = FloatBuffer.wrap (data);
        return new GLUniformData (name, data.length, buffer);
    }

    public static void extractColor (Type from, float[] to)
    {
        if (from instanceof Matrix)
        {
            MatrixDense A = (MatrixDense) from;
            int count = Math.min (to.length, A.rows () * A.columns ());
            for (int i = 0; i < count; i++) to[i] = (float) A.get (i);
        }
        else if (from instanceof Scalar)
        {
            long c = (long) ((Scalar) from).value;
            to[0] = (c >> 16 & 0xFF) / 255.0f;
            to[1] = (c >>  8 & 0xFF) / 255.0f;
            to[2] = (c       & 0xFF) / 255.0f;
            if (to.length > 3) to[3] = (c >> 24 & 0xFF) / 255.0f;
        }
    }

    public static void extractVector (Type from, float[] to)
    {
        if (! (from instanceof Matrix)) return;
        Matrix A = (Matrix) from;
        int count = Math.max (A.rows (), to.length);
        for (int i = 0; i < count; i++) to[i] = (float) A.get (i);
    }

    public static class Light
    {
        public int     index;
        public float[] position     = {0, 0, 1};  // In world coordinates, not eye coordinates.
        public float[] direction    = {0, 0, -1}; // Ditto
        public float[] ambient      = {0, 0, 0};
        public float[] diffuse      = {1, 1, 1};
        public float[] specular     = {1, 1, 1};
        public float   spotExponent;
        public float   spotCutoff   = 180;        // In degrees
        public float   attenuation0 = 1;          // The suffix digit refers to power of r in 1 / (a + b*r + c*r^2).
        public float   attenuation1;
        public float   attenuation2;

        public void extract (Function f, Instance context)
        {
            if (f.keywords == null) return;
            for (String key : f.keywords.keySet ())
            {
                // Rather than preemptively evaluating every keyword, we explicitly evaluate each one that's relevant.
                switch (key)
                {
                    case "direction":    extractVector (f.evalKeyword (context, key), direction);    break;
                    case "ambient":      extractColor  (f.evalKeyword (context, key), ambient);      break;
                    case "diffuse":      extractColor  (f.evalKeyword (context, key), diffuse);      break;
                    case "specular":     extractColor  (f.evalKeyword (context, key), specular);     break;
                    case "spotExponent": spotExponent = (float) f.evalKeyword (context, key,   0.0); break;
                    case "spotCutoff":   spotCutoff   = (float) f.evalKeyword (context, key, 180.0); break;
                    case "attenuation0": attenuation0 = (float) f.evalKeyword (context, key,   1.0); break;
                    case "attenuation1": attenuation1 = (float) f.evalKeyword (context, key,   0.0); break;
                    case "attenuation2": attenuation2 = (float) f.evalKeyword (context, key,   0.0); break;
                    case "position":
                        Type t = f.evalKeyword (context, key);
                        if (t instanceof Scalar) position[0] = Float.POSITIVE_INFINITY;
                        else                     extractVector (t, position);
                        break;
                }
            }
        }

        public void setUniform (int index, Holder H)
        {
            ShaderState st = H.st;
            GL2ES2 gl = H.drawable.getGL ().getGL2ES2 ();

            // Preemptively transform the position and direction vectors, since it is
            // a waste to do that for every pixel in the fragment shader, and this
            // doesn't really belong in the vertex shader.
            float[] P = new float[4];
            P[0] = position[0];
            P[1] = position[1];
            P[2] = position[2];
            P[3] = 1;
            FloatBuffer Mv = H.pv.glGetMvMatrixf ();
            float[] temp = new float[4];
            FloatUtil.multMatrixVec (Mv, P, temp);
            P = new float[3];
            P[0] = temp[0];
            P[1] = temp[1];
            P[2] = temp[2];

            float[] D = new float[4];
            D[0] = direction[0];
            D[1] = direction[1];
            D[2] = direction[2];
            D[3] = 1;
            FloatBuffer Mvit = H.pv.glGetMvitMatrixf ();
            FloatUtil.multMatrixVec (Mvit, D, temp);
            D = new float[3];
            D[0] = temp[0];
            D[1] = temp[1];
            D[2] = temp[2];

            st.uniform (gl, uniformVector     ("light[" + index + "].position",     P));
            st.uniform (gl, uniformVector     ("light[" + index + "].direction",    D));
            st.uniform (gl, uniformVector     ("light[" + index + "].ambient",      ambient));
            st.uniform (gl, uniformVector     ("light[" + index + "].diffuse",      diffuse));
            st.uniform (gl, uniformVector     ("light[" + index + "].specular",     specular));
            st.uniform (gl, new GLUniformData ("light[" + index + "].spotExponent", spotExponent));
            st.uniform (gl, new GLUniformData ("light[" + index + "].spotCutoff",   spotCutoff));
            st.uniform (gl, new GLUniformData ("light[" + index + "].attenuation0", attenuation0));
            st.uniform (gl, new GLUniformData ("light[" + index + "].attenuation1", attenuation1));
            st.uniform (gl, new GLUniformData ("light[" + index + "].attenuation2", attenuation2));
        }
    }

    public static class Material
    {
        public float[] ambient   = {0.2f, 0.2f, 0.2f};
        public float[] diffuse   = {0.8f, 0.8f, 0.8f, 1};
        public float[] emission  = {0, 0, 0};
        public float[] specular  = {0, 0, 0};
        public float   shininess = 0;

        public void extract (Function f, Instance context)
        {
            if (f.keywords == null) return;

            for (String key : f.keywords.keySet ())
            {
                switch (key)
                {
                    case "ambient":   extractColor       (f.evalKeyword (context, key), ambient);  break;
                    case "diffuse":   extractColor       (f.evalKeyword (context, key), diffuse);  break;
                    case "emission":  extractColor       (f.evalKeyword (context, key), emission); break;
                    case "specular":  extractColor       (f.evalKeyword (context, key), specular); break;
                    case "shininess": shininess = (float) f.evalKeyword (context, key, 0.0);       break;
                }
            }
        }

        public void setUniform (ShaderState st, GL2ES2 gl)
        {
            st.uniform (gl, uniformVector     ("material.ambient",   ambient));
            st.uniform (gl, uniformVector     ("material.diffuse",   diffuse));
            st.uniform (gl, uniformVector     ("material.emission",  emission));
            st.uniform (gl, uniformVector     ("material.specular",  specular));
            st.uniform (gl, new GLUniformData ("material.shininess", shininess));
        }
    }

    public static class SharedVertex implements Comparable<SharedVertex>
    {
        public float x;
        public float y;
        public float z;
        public float nx;
        public float ny;
        public float nz;
        public int index = -1;  // in vertices array. -1 means not added yet.

        public SharedVertex (float x, float y, float z, float nx, float ny, float nz)
        {
            this.x  = x;
            this.y  = y;
            this.z  = z;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }

        public int compareTo (SharedVertex o)
        {
            if (o == this) return 0;
            if (x < o.x) return -1;
            if (x > o.x) return 1; 
            if (y < o.y) return -1;
            if (y > o.y) return 1; 
            if (z < o.z) return -1;
            if (z > o.z) return 1; 
            return 0;
        }

        public String toString ()
        {
            return index + " = " + x + " " + y + " " + z + " " + nx + " " + ny + " " + nz;
        }
    }

    public static class SharedVertexSet
    {
        protected ArrayList<SharedVertex> vertices = new ArrayList<SharedVertex> ();
        protected TreeSet<SharedVertex>   lookup   = new TreeSet<SharedVertex> ();

        public int add (float x, float y, float z, float nx, float ny, float nz)
        {
            return add (new SharedVertex (x, y, z, nx, ny, nz));
        }

        public int add (SharedVertex v)
        {
            SharedVertex result = lookup.floor (v);
            if (result != null  &&  result.compareTo (v) == 0) return result.index;

            v.index = vertices.size ();
            vertices.add (v);
            lookup.add (v);
            return v.index;
        }

        public GLArrayDataServer vertexArray (ShaderState st)
        {
            int count = vertices.size ();
            GLArrayDataServer result = GLArrayDataServer.createGLSLInterleaved (6, GL.GL_FLOAT, false, count, GL.GL_STATIC_DRAW);
            result.addGLSLSubArray ("vertexPosition", 3, GL.GL_ARRAY_BUFFER);
            result.addGLSLSubArray ("vertexNormal",   3, GL.GL_ARRAY_BUFFER);

            for (SharedVertex v : vertices)
            {
                result.putf (v.x);
                result.putf (v.y);
                result.putf (v.z);
                result.putf (v.nx);
                result.putf (v.ny);
                result.putf (v.nz);
            }

            result.seal (true);
            result.associate (st, true);
            return result;
        }

        public static GLArrayDataServer indexArray (List<Integer> indices, ShaderState st)
        {
            int count = indices.size ();
            GLArrayDataServer result = GLArrayDataServer.createData (1, GL.GL_UNSIGNED_INT, count, GL.GL_STATIC_DRAW, GL.GL_ELEMENT_ARRAY_BUFFER);
            for (int i : indices) result.puti (i);
            result.seal (true);
            result.associate (st, true);
            return result;
        }
    }
}

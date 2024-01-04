/*
Copyright 2023-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.util.Map.Entry;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GLUniformData;
import com.jogamp.opengl.util.GLArrayDataServer;
import com.jogamp.opengl.util.glsl.ShaderState;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.linear.MatrixDense;

public class DrawCylinder extends Draw3D
{
    protected MatrixDense t; // temporary used by put(). For efficiency, we don't want to keep creating every time.

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "drawCylinder";
            }

            public Operator createInstance ()
            {
                return new DrawCylinder ();
            }
        };
    }

    public Operator simplify (Variable from, boolean evalOnly)
    {
        for (int i = 0; i < operands.length; i++)
        {
            operands[i] = operands[i].simplify (from, evalOnly);
        }
        if (keywords != null)
        {
            for (Entry<String,Operator> k : keywords.entrySet ())
            {
                k.setValue (k.getValue ().simplify (from, evalOnly));
            }
        }
        return this;
    }

    public void determineExponentNext ()
    {
        super.determineExponentNext ();

        Operator p1 = operands[1];
        Operator r1 = operands[2];
        Operator p2 = operands[3];
        Operator r2 = r1;
        if (operands.length > 4) r2 = operands[4];

        int exponent = (p1.exponent + p2.exponent) / 2;
        p1.exponentNext = exponent;
        p1.determineExponentNext ();
        p2.exponentNext = exponent;
        p2.determineExponentNext ();

        exponent = (r1.exponent + r2.exponent) / 2;
        r1.exponentNext = exponent;
        r1.determineExponentNext ();
        if (r2 != r1)
        {
            r2.exponentNext = exponent;
            r2.determineExponentNext ();
        }
    }

    @Override
    public boolean needModelMatrix ()
    {
        return false;
    }

    public Type eval (Instance context)
    {
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return new Scalar (0);

        Holder H = getHolder (simulator, context);
        applyKeywords (context, H);

        double now;
        if (simulator.currentEvent == null) now = 0;
        else                                now = (float) simulator.currentEvent.t;

        MatrixDense p1 = (MatrixDense)     operands[1].eval (context);
        float       r1 = (float) ((Scalar) operands[2].eval (context)).value;
        MatrixDense p2 = (MatrixDense)     operands[3].eval (context);
        if (p1.compareTo (p2) == 0) return new Scalar (0);
        float r2 = r1;
        if (operands.length > 4) r2 = (float) ((Scalar) operands[4].eval (context)).value;
        if (r1 == 0  &&  r2 == 0) return new Scalar (0);
        if (p1.columns () > 1) p1 = p1.transpose ();
        if (p2.columns () > 1) p2 = p2.transpose ();

        int steps    = 6;
        int stepsCap = -1;  // number of rings to insert for cap. If 0, then rounded cap becomes a cone.
        int cap1     = 0;
        int cap2     = 0;
        if (keywords != null)
        {
            for (String key : keywords.keySet ())
            {
                switch (key)
                {
                    case "cap1":     cap1     = evalKeyword (context, "cap1",      0); break;
                    case "cap2":     cap2     = evalKeyword (context, "cap2",      0); break;
                    case "steps":    steps    = evalKeyword (context, "steps",     6); break;
                    case "stepsCap": stepsCap = evalKeyword (context, "stepsCap", -1); break;
                }
            }
        }
        if (steps < 3) steps = 3;
        if (stepsCap < 0) stepsCap = steps / 4;  // Integer division, so 3/4==0, 4/4==1, etc.

        H.next (now);
        H.next3D ();
        Material m = new Material ();
        m.extract (this, context);
        GL2ES2 gl = H.drawable.getGL ().getGL2ES2 ();
        ShaderState st = H.st;
        m.setUniform (st, gl);

        GLArrayDataServer vertices = H.buffers.get ("cylinderVertices");
        GLArrayDataServer indices  = H.buffers.get ("cylinderIndices");
        if (vertices == null)
        {
            int count = 2 + steps * (2 + stepsCap);  // estimate based on rounded caps at both ends
            vertices = GLArrayDataServer.createGLSLInterleaved (6, GL.GL_FLOAT, false, count, GL.GL_STATIC_DRAW);
            vertices.addGLSLSubArray ("vertexPosition", 3, GL.GL_ARRAY_BUFFER);
            vertices.addGLSLSubArray ("vertexNormal",   3, GL.GL_ARRAY_BUFFER);
            indices  = GLArrayDataServer.createData (1, GL.GL_UNSIGNED_INT, count * 3, GL.GL_STATIC_DRAW, GL.GL_ELEMENT_ARRAY_BUFFER);
            vertices.associate (st, true);
            indices .associate (st, true);
            H.buffers.put ("cylinderVertices", vertices);
            H.buffers.put ("cylinderIndices",  indices);
        }
        else
        {
            vertices.reset ();
            indices.reset ();
        }

        // Construct a local coordinate frame.
        // This frame will be anchored first at one end, then at the other.
        // Its scale will be determined by the radius of the current end.
        //   The z vector runs along axis of the cylinder.
        //   Positive direction is toward p1 just because that's how all the
        //   geometry code was developed before moving to flexible coordinates.
        MatrixDense fz = p1.subtract (p2);
        double length = fz.norm (2);
        fz = fz.divide (length);
        //   Create x vector along the axis that z has smallest extent.
        //   This is an arbitrary choice, but it should be the best conitioned when computing cross product.
        MatrixDense fx = new MatrixDense (3, 1);
        int dimension = 0;
        for (int i = 1; i < 3; i++) if (Math.abs (fz.get (i)) < Math.abs (fz.get (dimension))) dimension = i;
        fx.set (dimension, 1);
        //   Create y vector by cross product.
        MatrixDense fy = fz.cross (fx).normalize ();
        fx = fy.cross (fz).normalize ();
        MatrixDense f = new MatrixDense (3, 4);
        f.getColumn (0).set (fx);
        f.getColumn (1).set (fy);
        f.getColumn (2).set (fz);
        f.getColumn (3).set (p1);
        if (t == null) t = new MatrixDense (4, 1);

        // Determine extra z component for sloped tube (r1 different from r2).
        // Suppose r1 is longer than r2. A triangle is formed by the tip of r1,
        // the tip of r2, and the projection of r1 onto the extension of r2.
        // This triangle is similar to the triangle formed by the tip of r1,
        // the tip of the normal added onto r1, and the slopeZ value we are
        // computing. The ratio between the triangles sizes is
        // 1 (for the normal) / (distance between p1 and p2).
        // This also works when r1 is shorter than r2.
        float slopeZ = (r2 - r1) / (float) length;

        double angleStep    = Math.PI * 2 / steps;
        double angleStep2   = Math.PI     / steps;  // half of a regular step
        double angleStepCap = Math.PI / 2 / (stepsCap + 1);

        int     rowsBegin = 0;     // Index of first vertex in first ring.
        int     rowsEnd   = 0;     // Index of first vertex after last ring.
        int     tip;               // Index of last vertex.
        boolean cone1     = false; // Close cone with vertex 0 and the ring that immediately follows it.
        boolean cone2     = false; // Close cone with vertex at rowBase1 and the ring the immediately precedes it.

        // Cap 1
        if (r1 > 0)
        {
            if (cap1 == 1)
            {
                cone1 = true;
                rowsBegin = rowsEnd = 1 + steps;  // So we can have separate normals for the disc.
                put (vertices, f, 0, 0, 0, 0, 0, 1);
                for (int i = 0; i < steps; i++)
                {
                    double a = i * angleStep;
                    float x = (float) Math.cos (a) * r1;
                    float y = (float) Math.sin (a) * r1;
                    put (vertices, f, x, y, 0, 0, 0, 1);
                }
            }
            else if (cap1 == 2)
            {
                cone1 = true;
                rowsBegin = 1;
                rowsEnd   = 1 + stepsCap * steps;
                put (vertices, f, 0, 0, r1, 0, 0, 1);
                for (int s = stepsCap; s > 0; s--)
                {
                    double a = s * angleStepCap;
                    float z = (float) Math.sin (a) * r1;
                    float r = (float) Math.cos (a) * r1;
                    for (int i = 0; i < steps; i++)
                    {
                        a = i * angleStep;
                        float x = (float) Math.cos (a) * r;
                        float y = (float) Math.sin (a) * r;
                        float l = (float) Math.sqrt (x * x + y * y + z * z);
                        put (vertices, f, x, y, z, x/l, y/l, z/l);
                    }
                }
            }
        }

        // Row 1
        rowsEnd += steps;
        for (int i = 0; i < steps; i++)
        {
            double a = i * angleStep;
            float c = (float) Math.cos (a);
            float s = (float) Math.sin (a);
            float nc = c;
            float ns = s;
            float l = (float) Math.sqrt (1 + slopeZ * slopeZ);  // 1 comes from c*c+s*s
            if (r1 == 0)  // Advance by half a step to get the average norm.
            {
                nc = (float) Math.cos (a + angleStep2);
                ns = (float) Math.sin (a + angleStep2);
                l *= 1000;  // Make the tip normal a lot smaller than the other two. This trick makes smoother shading on the cone.
            }
            put (vertices, f, c*r1, s*r1, 0, nc/l, ns/l, slopeZ/l);
        }

        // Move frame to end point
        f.getColumn (3).set (p2);

        // Row 2
        rowsEnd += steps;
        for (int i = 0; i < steps; i++)
        {
            double a = i * angleStep;
            float c = (float) Math.cos (a);
            float s = (float) Math.sin (a);
            float nc = c;
            float ns = s;
            float l = (float) Math.sqrt (1 + slopeZ * slopeZ);
            if (r2 == 0)
            {
                nc = (float) Math.cos (a - angleStep2);
                ns = (float) Math.sin (a - angleStep2);
                l *= 1000;
            }
            put (vertices, f, c*r2, s*r2, 0, nc/l, ns/l, slopeZ/l);
        }

        // Cap 2
        tip = rowsEnd;
        if (r2 > 0)
        {
            if (cap2 == 1)
            {
                cone2 = true;
                tip += steps;
                for (int i = 0; i < steps; i++)
                {
                    double a = i * angleStep;
                    float x = (float) Math.cos (a) * r2;
                    float y = (float) Math.sin (a) * r2;
                    put (vertices, f, x, y, 0, 0, 0, -1);
                }
                put (vertices, f, 0, 0, 0, 0, 0, -1);
            }
            else if (cap2 == 2)
            {
                cone2 = true;
                rowsEnd += stepsCap * steps;
                tip = rowsEnd;
                for (int s = 1; s <= stepsCap; s++)
                {
                    double a = s * angleStepCap;
                    float z = (float) -Math.sin (a) * r2;
                    float r = (float)  Math.cos (a) * r2;
                    for (int i = 0; i < steps; i++)
                    {
                        a = i * angleStep;
                        float x = (float) Math.cos (a) * r;
                        float y = (float) Math.sin (a) * r;
                        float l = (float) Math.sqrt (x * x + y * y + z * z);
                        put (vertices, f, x, y, z, x/l, y/l, z/l);
                    }
                }
                put (vertices, f, 0, 0, -r2, 0, 0, -1);
            }
        }

        // Connect vertices into triangles
        if (cone1)
        {
            for (int i = 0; i < steps; i++)
            {
                int j = (i + 1) % steps;
                indices.puti (0);
                indices.puti (i + 1);
                indices.puti (j + 1);
            }
        }
        if (r1 == 0)
        {
            for (int i = 0; i < steps; i++)
            {
                int i1 = rowsBegin + i;               // first vertex in row 1
                int j1 = rowsBegin + (i + 1) % steps; // second vertex in row 1
                int i2 = i1 + steps;                  // first vertex in row 2
                int j2 = j1 + steps;                  // second vertex in row 2

                // Only generate the lower triangle
                indices.puti (i1);
                indices.puti (i2);
                indices.puti (j2);
            }
            rowsBegin += steps;
        }
        else if (r2 == 0)
        {
            rowsEnd -= steps;
            int base = rowsEnd - steps;
            for (int i = 0; i < steps; i++)
            {
                int i1 = base + i;
                int j1 = base + (i + 1) % steps;
                int j2 = j1 + steps;

                // Only generate the upper triangle
                indices.puti (i1);
                indices.puti (j2);
                indices.puti (j1);
            }
        }
        for (int base = rowsBegin; base < rowsEnd - steps; base += steps)
        {
            for (int i = 0; i < steps; i++)
            {
                int i1 = base + i;
                int j1 = base + (i + 1) % steps;
                int i2 = i1 + steps;
                int j2 = j1 + steps;

                indices.puti (i1);
                indices.puti (i2);
                indices.puti (j2);

                indices.puti (i1);
                indices.puti (j2);
                indices.puti (j1);
            }
        }
        if (cone2)
        {
            int ring = tip - steps;
            for (int i = 0; i < steps; i++)
            {
                int j = (i + 1) % steps;
                indices.puti (tip);
                indices.puti (ring + j);
                indices.puti (ring + i);
            }
        }

        st.uniform (gl, new GLUniformData ("modelViewMatrix", 4, 4, H.pv.glGetMvMatrixf ()));
        st.uniform (gl, new GLUniformData ("normalMatrix",    4, 4, H.pv.glGetMvitMatrixf ()));

        vertices.seal (true);
        indices.seal (true);
        vertices.enableBuffer (gl, true);
        indices .bindBuffer   (gl, true);
        int count = indices.getElementCount ();
        gl.glDrawElements (GL.GL_TRIANGLES, count, GL.GL_UNSIGNED_INT, 0);

        return new Scalar (0);
    }

    public void put (GLArrayDataServer vertices, MatrixDense f, float x, float y, float z, float nx, float ny, float nz)
    {
        t.set (0, x);
        t.set (1, y);
        t.set (2, z);
        t.set (3, 1);
        MatrixDense P = f.multiply (t);
        vertices.putf ((float) P.get (0));
        vertices.putf ((float) P.get (1));
        vertices.putf ((float) P.get (2));

        t.set (0, nx);
        t.set (1, ny);
        t.set (2, nz);
        t.set (3, 0);
        P = f.multiply (t);
        vertices.putf ((float) P.get (0));
        vertices.putf ((float) P.get (1));
        vertices.putf ((float) P.get (2));
    }

    public String toString ()
    {
        return "drawCylinder";
    }
}

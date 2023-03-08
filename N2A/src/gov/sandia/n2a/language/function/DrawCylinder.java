/*
Copyright 2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.util.GLArrayDataServer;
import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;

public class DrawCylinder extends Draw implements Draw.Shape
{
    public GLArrayDataServer vertices;
    public GLArrayDataServer indices;

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

    public Type eval (Instance context)
    {
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return new Scalar (0);

        Holder H = getHolder (simulator, context);
        applyKeywords (context, H);

        double now;
        if (simulator.currentEvent == null) now = 0;
        else                                now = (float) simulator.currentEvent.t;

        Matrix p1 = (Matrix)          operands[1].eval (context);
        float  r1 = (float) ((Scalar) operands[2].eval (context)).value;
        Matrix p2 = (Matrix)          operands[3].eval (context);
        if (p1.compareTo (p2) == 0) return new Scalar (0);
        float r2 = r1;
        if (operands.length > 4) r2 = (float) ((Scalar) operands[4].eval (context)).value;

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
        m.setUniform (H.st, gl);

        if (vertices == null)
        {
            int count = 2 + steps * (2 + stepsCap);  // estimate based on rounded caps at both ends
            vertices = GLArrayDataServer.createGLSLInterleaved (6, GL.GL_FLOAT, false, count, GL.GL_STATIC_DRAW);
            vertices.addGLSLSubArray ("vertexPosition", 3, GL.GL_ARRAY_BUFFER);
            vertices.addGLSLSubArray ("vertexNormal",   3, GL.GL_ARRAY_BUFFER);
            indices  = GLArrayDataServer.createData (1, GL.GL_UNSIGNED_INT, count * 3, GL.GL_STATIC_DRAW, GL.GL_ELEMENT_ARRAY_BUFFER);
            vertices.associate (H.st, true);
            indices .associate (H.st, true);
        }
        else
        {
            vertices.reset ();
            indices.reset ();
        }

        double angleStep    = Math.PI * 2 / steps;
        double angleStepCap = Math.PI / 2 / (stepsCap + 1);

        int     rowsBegin = 0;     // Index of first vertex in first ring.
        int     rowsEnd;           // Index of first vertex after last ring.
        int     tip;               // Index of last vertex.
        boolean cone1     = false; // Close cone with vertex 0 and the ring that immediately follows it.
        boolean cone2     = false; // Close cone with vertex at rowBase1 and the ring the immediately precedes it.

        // Cap 1
        if (cap1 > 0  ||  r1 == 0)
        {
            rowsBegin = 1;
            cone1 = true;
            if (cap1 == 2) put (0, 0, 1+r1, 0, 0, 1);
            else           put (0, 0, 1, 0, 0, 1);
        }
        rowsEnd = rowsBegin;
        if (r1 > 0)
        {
            if (cap1 == 1)
            {
                rowsBegin += steps;  // So we can have separate normals for the disc.
                rowsEnd   += steps;
                for (int i = 0; i < steps; i++)
                {
                    double a = i * angleStep;
                    float x = (float) Math.cos (a) * r1;
                    float y = (float) Math.sin (a) * r1;
                    put (x, y, 1, 0, 0, 1);
                }
            }
            else if (cap1 == 2)
            {
                rowsEnd += stepsCap * steps;
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
                        float nx = x / l;
                        float ny = y / l;
                        float nz = z / l;
                        put (x, y, z+1, nx, ny, nz);
                    }
                }
            }
        }

        // Row 1
        if (r1 > 0)
        {
            rowsEnd += steps;
            for (int i = 0; i < steps; i++)
            {
                double a = i * angleStep;
                float x = (float) Math.cos (a) * r1;
                float y = (float) Math.sin (a) * r1;
                put (x, y, 1, x, y, 0);
            }
        }

        // Row 2
        if (r2 > 0)
        {
            rowsEnd += steps;
            for (int i = 0; i < steps; i++)
            {
                double a = i * angleStep;
                float x = (float) Math.cos (a) * r2;
                float y = (float) Math.sin (a) * r2;
                put (x, y, -1, x, y, 0);
            }
        }
        else
        {
            cone2 = true;
        }

        // Cap 2
        tip = rowsEnd;
        if (r2 > 0)
        {
            if (cap2 == 1)
            {
                tip += steps;
                for (int i = 0; i < steps; i++)
                {
                    double a = i * angleStep;
                    float x = (float) Math.cos (a) * r2;
                    float y = (float) Math.sin (a) * r2;
                    put (x, y, -1, 0, 0, -1);
                }
            }
            else if (cap2 == 2)
            {
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
                        float nx = x / l;
                        float ny = y / l;
                        float nz = z / l;
                        put (x, y, z-1, nx, ny, nz);
                    }
                }
            }
        }
        if (cap2 > 0  ||  r2 == 0)
        {
            cone2 = true;
            if (cap2 == 2) put (0, 0, -1-r2, 0, 0, -1);
            else           put (0, 0, -1, 0, 0, -1);
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
        for (int base = rowsBegin; base < rowsEnd - steps; base += steps)
        {
            for (int i = 0; i < steps; i++)
            {
                int i1 = base + i;               // first vertex in row 0
                int j1 = base + (i + 1) % steps; // second vertex in row 0
                int i2 = i1 + steps;             // first vertex in row 1
                int j2 = j1 + steps;             // second vertex in row 1

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

        vertices.seal (true);
        indices.seal (true);
        vertices.enableBuffer (gl, true);
        indices .bindBuffer   (gl, true);
        int count = indices.getElementCount ();
        gl.glDrawElements (GL.GL_TRIANGLES, count, GL.GL_UNSIGNED_INT, 0);

        return new Scalar (0);
    }

    public void put (float x, float y, float z, float nx, float ny, float nz)
    {
        vertices.putf (x);
        vertices.putf (y);
        vertices.putf (z);
        vertices.putf (nx);
        vertices.putf (ny);
        vertices.putf (nz);
    }

    public String toString ()
    {
        return "drawCylinder";
    }
}

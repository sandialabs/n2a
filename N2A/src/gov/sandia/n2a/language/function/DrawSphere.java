/*
Copyright 2023-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.util.ArrayList;
import java.util.List;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GLUniformData;
import com.jogamp.opengl.util.GLArrayDataServer;
import com.jogamp.opengl.util.PMVMatrix;
import com.jogamp.opengl.util.glsl.ShaderState;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.operator.Multiply;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.linear.MatrixDense;

public class DrawSphere extends Draw3D
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "drawSphere";
            }

            public Operator createInstance ()
            {
                return new DrawSphere ();
            }
        };
    }

    public Operator simplify (Variable from, boolean evalOnly)
    {
        if (operands.length < 2) return super.simplify (from, evalOnly);

        // Positional parameters are present, so fold them into model matrix.
        Operator center = operands[1];
        glTranslate t = new glTranslate ();
        t.operands = new Operator[1];
        t.operands[0] = center;
        center.parent = t;

        glScale s = null;
        if (operands.length > 2)
        {
            Operator radius = operands[2];
            s = new glScale ();
            s.operands = new Operator[1];
            s.operands[0] = radius;
            radius.parent = s;
        }

        Operator model = getKeyword ("model");
        if (s != null)
        {
            if (model == null)
            {
                model = s;
            }
            else
            {
                Multiply m = new Multiply ();
                m.operand0 = model;
                m.operand1 = s;
                s.parent = m;
                model.parent = m;
                model = m;
            }
        }

        if (model == null)
        {
            addKeyword ("model", t);
        }
        else
        {
            Multiply m = new Multiply ();
            m.operand0 = t;
            m.operand1 = model;
            t.parent = m;
            model.parent = m;
            addKeyword ("model", m);
        }

        Operator[] nextOperands = new Operator[1];
        nextOperands[0] = operands[0];
        operands = nextOperands;

        from.changed = true;
        return this;
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

        int steps = evalKeyword (context, "steps", 1);
        if (steps > 10) steps = 10;  // defensive limit. ~20 million faces (20 * 4^10)
        MatrixDense model = (MatrixDense) evalKeyword (context, "model");
        Material m = new Material ();
        m.extract (this, context);

        H.next (now);
        H.next3D ();
        GL2ES2 gl = H.drawable.getGL ().getGL2ES2 ();
        ShaderState st = H.st;
        m.setUniform (st, gl);

        // Manage cached geometry.
        SharedVertexSet    vertexSet = (SharedVertexSet)    H.objects.get ("sphereVertexSet");
        @SuppressWarnings("unchecked")
        ArrayList<Integer> indexSet  = (ArrayList<Integer>) H.objects.get ("sphereIndexSet");
        if (vertexSet == null)
        {
            vertexSet = new SharedVertexSet ();
            indexSet  = new ArrayList<Integer> ();
            icosphere (vertexSet, indexSet);
            H.objects.put ("sphereVertexSet", vertexSet);
            H.objects.put ("sphereIndexSet",  indexSet);
            H.objects.put ("sphereCount",     1);
            H.buffers.put ("sphereVertices0", vertexSet.vertexArray (st));
            H.buffers.put ("sphereIndices0",  SharedVertexSet.indexArray (indexSet, st));
        }
        int stepCount = (Integer) H.objects.get ("sphereCount");
        while (stepCount <= steps)
        {
            subdivide (vertexSet, indexSet);
            H.buffers.put ("sphereVertices" + stepCount, vertexSet.vertexArray (st));
            H.buffers.put ("sphereIndices"  + stepCount, SharedVertexSet.indexArray (indexSet, st));
            stepCount++;
            H.objects.put ("sphereCount", stepCount);
        }
        GLArrayDataServer vertices = H.buffers.get ("sphereVertices" + steps);
        GLArrayDataServer indices  = H.buffers.get ("sphereIndices"  + steps);

        PMVMatrix pv = H.pv;
        pv.glMatrixMode (PMVMatrix.GL_MODELVIEW);
        pv.glPushMatrix ();
        if (model != null) pv.glMultMatrixf (getMatrix (model), 0);
        st.uniform (gl, new GLUniformData ("modelViewMatrix", 4, 4, pv.glGetMvMatrixf ()));
        st.uniform (gl, new GLUniformData ("normalMatrix",    4, 4, pv.glGetMvitMatrixf ()));
        pv.glPopMatrix ();

        vertices.enableBuffer (gl, true);
        indices .bindBuffer   (gl, true);
        int count = indices.getElementCount ();
        gl.glDrawElements (GL.GL_TRIANGLES, count, GL.GL_UNSIGNED_INT, 0);
        vertices.enableBuffer (gl, false);
        indices .bindBuffer   (gl, false);

        return new Scalar (0);
    }

    public String toString ()
    {
        return "drawSphere";
    }

    public void icosphere (SharedVertexSet vs, List<Integer> indices)
    {
        float PI     = (float) Math.PI;
        float angleH = 2 * PI / 5;               // 72 degrees
        float angleV = (float) Math.atan (0.5);  // elevation = 26.565 degree

        float angleH1 = -PI / 2 - angleH / 2;  // start from -126 deg at 2nd row
        float angleH2 = -PI / 2;               // start from -90  deg at 3rd row
        float z       = (float) Math.sin (angleV);

        // top
        vert (vs, 0, 0, 1);

        // 2nd row
        for (int i = 0; i < 5; i++)
        {
            float xy = (float) Math.cos (angleV);
            float a = angleH1 + i * angleH;
            vert (vs, xy * Math.cos (a), xy * Math.sin (a), z);
        }

        // 3rd row
        for (int i = 0; i < 5; i++)
        {
            float xy = (float) Math.cos (angleV);
            float a = angleH2 + i * angleH;
            vert (vs, xy * Math.cos (a), xy * Math.sin (a), -z);
        }

        // bottom vertex
        vert (vs, 0, 0, -1);

        // Indices
        for (int i = 0; i < 5; i++)
        {
            int i2 = i + 1;
            int i3 = i2 + 5;
            int j2 = (i + 1) % 5 + 1;
            int j3 = j2 + 5;

            // top triangle
            indices.add (0);
            indices.add (i2);
            indices.add (j2);

            // 2nd row
            indices.add (i2);
            indices.add (i3);
            indices.add (j2);

            // 3rd row
            indices.add (i3);
            indices.add (j3);
            indices.add (j2);

            // bottom triangle
            indices.add (11);
            indices.add (j3);
            indices.add (i3);
        }
    }

    public void vert (SharedVertexSet vs, double x, double y, double z)
    {
        // Since radius is 1, norm is same as position.
        vs.add (new SharedVertex ((float) x, (float) y, (float) z, (float) x, (float) y, (float) z));
    }

    public void subdivide (SharedVertexSet vs, List<Integer> indices)
    {
        int count = indices.size ();
        List<Integer> old = new ArrayList<Integer> (indices);
        indices.clear ();

        for (int j = 0; j < count; j += 3)
        {
            // Get current triangle.
            int j0 = old.get (j);
            int j1 = old.get (j+1);
            int j2 = old.get (j+2);
            SharedVertex v0 = vs.vertices.get (j0);
            SharedVertex v1 = vs.vertices.get (j1);
            SharedVertex v2 = vs.vertices.get (j2);

            // Create 3 new vertices by splitting each edge.
            int c01 = split (vs, v0, v1);
            int c12 = split (vs, v1, v2);
            int c20 = split (vs, v2, v0);

            // Add 4 new triangles
            indices.add (j0);
            indices.add (c01);
            indices.add (c20);

            indices.add (j1);
            indices.add (c12);
            indices.add (c01);

            indices.add (j2);
            indices.add (c20);
            indices.add (c12);

            indices.add (c01);
            indices.add (c12);
            indices.add (c20);
        }
    }

    public int split (SharedVertexSet vs, SharedVertex v0, SharedVertex v1)
    {
        float x = v0.x + v1.x;
        float y = v0.y + v1.y;
        float z = v0.z + v1.z;
        float l = (float) Math.sqrt (x * x + y * y + z * z);
        x /= l;
        y /= l;
        z /= l;
        return vs.add (x, y, z, x, y, z);
    }
}

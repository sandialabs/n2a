/*
Copyright 2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GLUniformData;
import com.jogamp.opengl.util.GLArrayDataServer;
import com.jogamp.opengl.util.PMVMatrix;
import com.jogamp.opengl.util.glsl.ShaderState;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.linear.MatrixDense;

public class DrawCube extends Draw implements Draw.Shape
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "drawCube";
            }

            public Operator createInstance ()
            {
                return new DrawCube ();
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

        Matrix p = null;
        if (operands.length > 1) p = (Matrix) operands[1].eval (context);

        MatrixDense model = (MatrixDense) evalKeyword (context, "model");
        Material m = new Material ();
        m.extract (this, context);

        H.next (now);
        H.next3D ();
        GL2ES2 gl = H.drawable.getGL ().getGL2ES2 ();
        ShaderState st = H.st;
        m.setUniform (st, gl);

        // Manage cached geometry.
        GLArrayDataServer vertices = H.buffers.get ("cubeVertices");
        GLArrayDataServer indices  = H.buffers.get ("cubeIndices");
        if (vertices == null)
        {
            // Each face is independent so that its normals can be independent.
            // Thus we have 6*4 = 24 faces.
            vertices = GLArrayDataServer.createGLSLInterleaved (6, GL.GL_FLOAT, false, 24, GL.GL_STATIC_DRAW);
            vertices.addGLSLSubArray ("vertexPosition", 3, GL.GL_ARRAY_BUFFER);
            vertices.addGLSLSubArray ("vertexNormal",   3, GL.GL_ARRAY_BUFFER);

            // There are 2 triangles per face, so 6*6 = 36 indices.
            indices = GLArrayDataServer.createData (1, GL.GL_UNSIGNED_INT, 36, GL.GL_STATIC_DRAW, GL.GL_ELEMENT_ARRAY_BUFFER);

            makeCube (vertices, indices);
            vertices.seal (true);
            vertices.associate (st, true);
            indices.seal (true);
            indices.associate (st, true);

            H.buffers.put ("cubeVertices", vertices);
            H.buffers.put ("cubeIndices",  indices);
        }

        PMVMatrix pv = H.pv;
        pv.glMatrixMode (PMVMatrix.GL_MODELVIEW);
        pv.glPushMatrix ();
        if (p != null) pv.glMultMatrixf (getMatrix (glTranslate.make (p)), 0);
        if (model != null)  pv.glMultMatrixf (getMatrix (model), 0);
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
        return "drawCube";
    }

    public void makeCube (GLArrayDataServer vertices, GLArrayDataServer indices)
    {
        // All vertices are specified in CCW order.

        // Top face, y=1
        float[] n = new float[3];
        n[1] = 1;
        put (vertices,  0.5f, 0.5f, -0.5f, n);
        put (vertices, -0.5f, 0.5f, -0.5f, n);
        put (vertices, -0.5f, 0.5f,  0.5f, n);
        put (vertices,  0.5f, 0.5f,  0.5f, n);

        // Bottom face, y=-1
        n[1] = -1;
        put (vertices,  0.5f, -0.5f,  0.5f, n);
        put (vertices, -0.5f, -0.5f,  0.5f, n);
        put (vertices, -0.5f, -0.5f, -0.5f, n);
        put (vertices,  0.5f, -0.5f, -0.5f, n);

        // Front face, z=1
        n[1] = 0;
        n[2] = 1;
        put (vertices,  0.5f,  0.5f,  0.5f, n);
        put (vertices, -0.5f,  0.5f,  0.5f, n);
        put (vertices, -0.5f, -0.5f,  0.5f, n);
        put (vertices,  0.5f, -0.5f,  0.5f, n);

        // Back face, z=-1
        n[2] = -1;
        put (vertices,  0.5f, -0.5f,  -0.5f, n);
        put (vertices, -0.5f, -0.5f,  -0.5f, n);
        put (vertices, -0.5f,  0.5f,  -0.5f, n);
        put (vertices,  0.5f,  0.5f,  -0.5f, n);

        // Left face, x=-1
        n[0] = -1;
        n[2] = 0;
        put (vertices, -0.5f,  0.5f,  0.5f, n);
        put (vertices, -0.5f,  0.5f, -0.5f, n);
        put (vertices, -0.5f, -0.5f, -0.5f, n);
        put (vertices, -0.5f, -0.5f,  0.5f, n);

        // Right face, x=1
        n[0] = 1;
        put (vertices, 0.5f,  0.5f, -0.5f, n);
        put (vertices, 0.5f,  0.5f,  0.5f, n);
        put (vertices, 0.5f, -0.5f,  0.5f, n);
        put (vertices, 0.5f, -0.5f, -0.5f, n);

        for (int v = 0; v < 24; v += 4)
        {
            // first triangle
            indices.puti (v + 0);
            indices.puti (v + 1);
            indices.puti (v + 2);
            // second triangle
            indices.puti (v + 0);
            indices.puti (v + 2);
            indices.puti (v + 3);
        }
    }
}

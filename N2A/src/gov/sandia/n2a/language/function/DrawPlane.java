/*
Copyright 2023-2025 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.nio.FloatBuffer;

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
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.linear.MatrixDense;

public class DrawPlane extends Draw3D
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "drawPlane";
            }

            public Operator createInstance ()
            {
                return new DrawPlane ();
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

        MatrixDense model = (MatrixDense) evalKeyword (context, "model");
        Material m = new Material ();
        m.extract (this, context);

        H.next (now);
        H.next3D ();
        GL2ES2 gl = H.drawable.getGL ().getGL2ES2 ();
        ShaderState st = H.st;
        m.setUniform (st, gl);

        // Manage cached geometry.
        GLArrayDataServer vertices = H.buffers.get ("planeVertices");
        GLArrayDataServer indices  = H.buffers.get ("planeIndices");
        if (vertices == null)
        {
            vertices = GLArrayDataServer.createGLSLInterleaved (6, GL.GL_FLOAT, false, 4, GL.GL_STATIC_DRAW);
            vertices.addGLSLSubArray ("vertexPosition", 3, GL.GL_ARRAY_BUFFER);
            vertices.addGLSLSubArray ("vertexNormal",   3, GL.GL_ARRAY_BUFFER);
            indices = GLArrayDataServer.createData (1, GL.GL_UNSIGNED_INT, 6, GL.GL_STATIC_DRAW, GL.GL_ELEMENT_ARRAY_BUFFER);

            float[] n = new float[3];
            n[2] = 1;
            put (vertices,  0.5f,  0.5f, 0, n);
            put (vertices, -0.5f,  0.5f, 0, n);
            put (vertices, -0.5f, -0.5f, 0, n);
            put (vertices,  0.5f, -0.5f, 0, n);

            // first triangle
            indices.puti (0);
            indices.puti (1);
            indices.puti (2);

            // second triangle
            indices.puti (0);
            indices.puti (2);
            indices.puti (3);

            vertices.seal (true);
            vertices.associate (st, true);
            indices.seal (true);
            indices.associate (st, true);

            H.buffers.put ("planeVertices", vertices);
            H.buffers.put ("planeIndices",  indices);
        }

        PMVMatrix pv = H.pv;
        pv.glMatrixMode (PMVMatrix.GL_MODELVIEW);
        pv.glPushMatrix ();
        if (model != null)  pv.glMultMatrixf (getMatrix (model), 0);
        FloatBuffer Mv   = pv.getMv   ().get (FloatBuffer.wrap (new float[16])).rewind ();
        FloatBuffer Mvit = pv.getMvit ().get (FloatBuffer.wrap (new float[16])).rewind ();
        st.uniform (gl, new GLUniformData ("modelViewMatrix", 4, 4, Mv));
        st.uniform (gl, new GLUniformData ("normalMatrix",    4, 4, Mvit));
        pv.glPopMatrix ();

        vertices.enableBuffer (gl, true);
        indices .bindBuffer   (gl, true);
        int count = indices.getElemCount ();
        gl.glDrawElements (GL.GL_TRIANGLES, count, GL.GL_UNSIGNED_INT, 0);
        vertices.enableBuffer (gl, false);
        indices .bindBuffer   (gl, false);

        return new Scalar (0);
    }

    public String toString ()
    {
        return "drawPlane";
    }
}

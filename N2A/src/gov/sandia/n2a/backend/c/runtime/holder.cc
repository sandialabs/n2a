/*
Copyright 2018-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#include "holder.tcc"
#include "Matrix.tcc"
#include "MatrixFixed.tcc"
#include "MatrixSparse.tcc"

#ifdef HAVE_JNI
#  include "image.h"
#  include <jni.h>
#endif

using namespace n2a;
using namespace std;


// Matrix library ------------------------------------------------------------

template class MatrixAbstract<n2a_T>;
template class MatrixStrided<n2a_T>;
template class Matrix<n2a_T>;
template class MatrixFixed<n2a_T,3,1>;
template class MatrixSparse<n2a_T>;

// Most functions and operators are defined outside the matrix classes.
// These must be individually instantiated.

template SHARED void          clear      (      MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED n2a_T         sumSquares (const MatrixAbstract<n2a_T> & A);
template SHARED Matrix<n2a_T> visit      (const MatrixAbstract<n2a_T> & A, n2a_T (*function) (const n2a_T &));
template SHARED Matrix<n2a_T> visit      (const MatrixAbstract<n2a_T> & A, n2a_T (*function) (const n2a_T));

template SHARED Matrix<n2a_T> operator == (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator == (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator != (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator != (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator <  (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator <  (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator <= (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator <= (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator >  (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator >  (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator >= (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator >= (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator && (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator && (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator || (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator || (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);

template SHARED Matrix<n2a_T> operator & (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator * (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator / (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator / (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator / (const n2a_T scalar,              const MatrixAbstract<n2a_T> & A);
template SHARED Matrix<n2a_T> operator + (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator + (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator - (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator - (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator - (const n2a_T scalar,              const MatrixAbstract<n2a_T> & A);

template SHARED void operator *= (MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED void operator *= (MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED void operator /= (MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED void operator /= (MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED void operator += (MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED void operator += (MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED void operator -= (MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED void operator -= (MatrixAbstract<n2a_T> & A, const n2a_T scalar);

template SHARED Matrix<n2a_T> min (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> min (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> max (const MatrixAbstract<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> max (const MatrixAbstract<n2a_T> & A, const n2a_T scalar);

template SHARED ostream & operator << (ostream & stream, const MatrixAbstract<n2a_T> & A);

template SHARED void          clear (      MatrixStrided<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> visit (const MatrixStrided<n2a_T> & A, n2a_T (*function) (const n2a_T &));
template SHARED Matrix<n2a_T> visit (const MatrixStrided<n2a_T> & A, n2a_T (*function) (const n2a_T));

template SHARED Matrix<n2a_T> operator & (const MatrixStrided<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator * (const MatrixStrided<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator * (const MatrixStrided<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator / (const MatrixStrided<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator / (const MatrixStrided<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator / (const n2a_T scalar,             const MatrixStrided<n2a_T> & A);
template SHARED Matrix<n2a_T> operator + (const MatrixStrided<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator + (const MatrixStrided<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator - (const MatrixStrided<n2a_T> & A, const MatrixAbstract<n2a_T> & B);
template SHARED Matrix<n2a_T> operator - (const MatrixStrided<n2a_T> & A, const n2a_T scalar);
template SHARED Matrix<n2a_T> operator - (const n2a_T scalar,             const MatrixStrided<n2a_T> & A);

template SHARED Matrix<n2a_T> operator ~ (const Matrix<n2a_T> & A);

#ifndef n2a_FP
template SHARED n2a_T norm (const MatrixAbstract<n2a_T> & A, n2a_T n);
template SHARED n2a_T norm (const MatrixStrided<n2a_T>  & A, n2a_T n);
#endif


// I/O library ---------------------------------------------------------------

Holder::Holder (const String & fileName)
:   fileName (fileName)
{
}

Holder::~Holder ()
{
}

template class Parameters<n2a_T>;
template class IteratorNonzero<n2a_T>;
template class IteratorSkip<n2a_T>;
template class IteratorSparse<n2a_T>;
template class MatrixInput<n2a_T>;
template class Mfile<n2a_T>;
template class ImageInput<n2a_T>;
template class ImageOutput<n2a_T>;
template class InputHolder<n2a_T>;
template class OutputHolder<n2a_T>;
template SHARED IteratorNonzero<n2a_T> * getIterator (MatrixAbstract<n2a_T> * A);
#ifdef n2a_FP
template SHARED MatrixInput <n2a_T> * matrixHelper     (const String & fileName, int exponent, MatrixInput <n2a_T> * oldHandle);
template SHARED InputHolder <n2a_T> * inputHelper      (const String & fileName, int exponent, InputHolder <n2a_T> * oldHandle);
#else
template SHARED MatrixInput <n2a_T> * matrixHelper     (const String & fileName,               MatrixInput <n2a_T> * oldHandle);
template SHARED InputHolder <n2a_T> * inputHelper      (const String & fileName,               InputHolder <n2a_T> * oldHandle);
#endif
template SHARED Mfile       <n2a_T> * MfileHelper      (const String & fileName,               Mfile       <n2a_T> * oldHandle);
template SHARED OutputHolder<n2a_T> * outputHelper     (const String & fileName,               OutputHolder<n2a_T> * oldHandle);
template SHARED ImageInput  <n2a_T> * imageInputHelper (const String & fileName,               ImageInput  <n2a_T> * oldHandle);
template SHARED ImageOutput <n2a_T> * imageOutputHelper(const String & fileName,               ImageOutput <n2a_T> * oldHandle);

#ifdef HAVE_JNI

PixelFormat2BufferedImage pixelFormat2BufferedImageMap[] =
{
    {&BGRxChar,   TYPE_INT_RGB,        4},
    {&BGRAChar,   TYPE_INT_ARGB,       4},
    {&BGRAChar,   TYPE_INT_ARGB_PRE,   4},
    {&RGBxChar,   TYPE_INT_BGR,        4},
    {&BGRChar,    TYPE_3BYTE_BGR,      3},
    {&ABGRChar,   TYPE_4BYTE_ABGR,     4},
    {&ABGRChar,   TYPE_4BYTE_ABGR_PRE, 4},
    {&B5G6R5,     TYPE_USHORT_565_RGB, 2},
    {&B5G5R5,     TYPE_USHORT_555_RGB, 2},
    {&GrayChar,   TYPE_BYTE_GRAY,      1},  // TODO: Comes out dark. Probably should be treated as linear.
    {&GrayShort,  TYPE_USHORT_GRAY,    2},
    {0}
};

// Subroutine of convert{type} routines below
static void convert (jint width, jint height, jint format, void * cbuffer, jint colorSpace, double * cmatrix)
{
    PixelFormat2BufferedImage * m = pixelFormat2BufferedImageMap;
    while (m->pf)
    {
        if (m->bi == format) break;
        m++;
    }
    if (m->pf == 0) return;  // TODO: throw an error

    Image image (cbuffer, width, height, *m->pf);
    Image image2;
    switch (colorSpace)
    {
        case 0: image2 = image * RGBFloat;  break;
        case 1: image2 = image * sRGBFloat; break;
        case 2: image2 = image * XYZFloat;  break;
        case 3: image2 = image * HSVFloat;  break;
    }

    float *  from = (float *) image2.buffer->pixel (0, 0);
    float *  end  = from + width * height * 3;
    double * to   = cmatrix;
    while (from < end) *to++ = *from++;
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_VideoIn_convertByte (JNIEnv * env, jclass obj, jint width, jint height, jint format, jbyteArray buffer, jint colorSpace, jdoubleArray matrix)
{
    void * cbuffer = env->GetPrimitiveArrayCritical (buffer, 0);
    void * cmatrix = env->GetPrimitiveArrayCritical (matrix, 0);
    convert (width, height, format, cbuffer, colorSpace, (double *) cmatrix);
    env->ReleasePrimitiveArrayCritical (buffer, cbuffer, 0);
    env->ReleasePrimitiveArrayCritical (buffer, cmatrix, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_VideoIn_convertShort (JNIEnv * env, jclass obj, jint width, jint height, jint format, jshortArray buffer, jint colorSpace, jdoubleArray matrix)
{
    void * cbuffer = env->GetPrimitiveArrayCritical (buffer, 0);
    void * cmatrix = env->GetPrimitiveArrayCritical (matrix, 0);
    convert (width, height, format, cbuffer, colorSpace, (double *) cmatrix);
    env->ReleasePrimitiveArrayCritical (buffer, cbuffer, 0);
    env->ReleasePrimitiveArrayCritical (buffer, cmatrix, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_gov_sandia_n2a_backend_c_VideoIn_convertInt (JNIEnv * env, jclass obj, jint width, jint height, jint format, jintArray buffer, jint colorSpace, jdoubleArray matrix)
{
    void * cbuffer = env->GetPrimitiveArrayCritical (buffer, 0);
    void * cmatrix = env->GetPrimitiveArrayCritical (matrix, 0);
    convert (width, height, format, cbuffer, colorSpace, (double *) cmatrix);
    env->ReleasePrimitiveArrayCritical (buffer, cbuffer, 0);
    env->ReleasePrimitiveArrayCritical (buffer, cmatrix, 0);
}

#endif


#ifdef HAVE_GL

LightLocation::LightLocation (GLuint program, int index)
{
    char buffer[32];
    sprintf (buffer, "light[%i].infinite",     index);
    infinite     = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].position",     index);
    position     = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].direction",    index);
    direction    = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].ambient",      index);
    ambient      = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].diffuse",      index);
    diffuse      = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].specular",     index);
    specular     = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].spotExponent", index);
    spotExponent = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].Cutoff",       index);
    spotCutoff   = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].attenuation0", index);
    attenuation0 = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].attenuation1", index);
    attenuation1 = glGetUniformLocation (program, buffer);
    sprintf (buffer, "light[%i].attenuation2", index);
    attenuation2 = glGetUniformLocation (program, buffer);
}

Light::Light ()
:   position {0, 0, 1},
    direction{0, 0, -1},
    ambient  {0, 0, 0},
    diffuse  {1, 1, 1},
    specular {1, 1, 1}
{
    infinite = false;
    spotExponent = 0;
    spotCutoff   = -1;
    attenuation0 = 1;
    attenuation1 = 0;
    attenuation2 = 0;
}

void
Light::setUniform (const LightLocation & l, const Matrix<float> view)
{
    // Transform the position and direction vectors.
    Matrix<float> P = view * position + column (view, 3);  // Ignore fourth row, since view should not have perspective scaling.
    Matrix<float> normal = view;  // TODO: create inverse transpose of view
    Matrix<float> D = normal * direction;

    glUniform1i  (l.infinite,     infinite);
    glUniform3fv (l.position,  1, P);
    glUniform3fv (l.direction, 1, D);
    glUniform3fv (l.ambient,   1, ambient);
    glUniform3fv (l.diffuse,   1, diffuse);
    glUniform3fv (l.specular,  1, specular);
    glUniform1f  (l.spotExponent, spotExponent);
    glUniform1f  (l.spotCutoff,   spotCutoff);
    glUniform1f  (l.attenuation0, attenuation0);
    glUniform1f  (l.attenuation1, attenuation1);
    glUniform1f  (l.attenuation2, attenuation2);
}

Material::Material ()
:   ambient{0.2, 0.2, 0.2},
    diffuse{0.8, 0.8, 0.8, 1},
    emission{0, 0, 0},
    specular{0, 0, 0}
{
    shininess = 16;
}

void
Material::setUniform ()
{
    glUniform3fv (locAmbient,  1, ambient);
    glUniform4fv (locDiffuse,  1, diffuse);
    glUniform3fv (locEmission, 1, emission);
    glUniform3fv (locSpecular, 1, specular);
    glUniform1f  (locShininess,   shininess);
}

void
put (std::vector<GLfloat> & vertices, float x, float y, float z, float[3] n)
{
    vertices.push_back (x);
    vertices.push_back (y);
    vertices.push_back (z);
    vertices.push_back (n[0]);
    vertices.push_back (n[1]);
    vertices.push_back (n[2]);
}

void
put (std::vector<GLfloat> & vertices, Matrix<float> f, float x, float y, float z, float nx, float ny, float nz)
{
    MatrixFixed<float,4,1> t;
    t[0] = x;
    t[1] = y;
    t[2] = z;
    t[3] = 1;
    Matrix<float> P = f * t;
    vertices.push_back (P[0]);
    vertices.push_back (P[1]);
    vertices.push_back (P[2]);

    t[0] = nx;
    t[1] = ny;
    t[2] = nz;
    t[3] = 0;
    P = f * t;
    vertices.push_back (P[0]);
    vertices.push_back (P[1]);
    vertices.push_back (P[2]);
}

int
putUnique (std::vector<GLfloat> & vertices, float x, float y, float z)
{
    int count = vertices.size ();
    for (int i = 0; i < count; i += 6)
    {
        if (vertics[i] == x  &&  vertices[i+1] == y  &&  vertices[i+2] == z) return i / 6;
    }

    vertices.push_back (x);
    vertices.push_back (y);
    vertices.push_back (z);
    vertices.push_back (x);
    vertices.push_back (y);
    vertices.push_back (z);

    return count / 6;
}

void
icosphere (std::vector<GLfloat> & vertices, std::vector<GLuint> & indices)
{
    float angleH = 2 * M_PI / 5; // 72 degrees
    float angleV = atan (0.5);   // elevation = 26.565 degree

    float angleH1 = -M_PI / 2 - angleH / 2;  // start from -126 deg at 2nd row
    float angleH2 = -M_PI / 2;               // start from -90  deg at 3rd row
    float z       = sin (angleV);

    // top
    putUnique (vertices, 0, 0, 1);

    // 2nd row
    for (int i = 0; i < 5; i++)
    {
        float xy = cos (angleV);
        float a = angleH1 + i * angleH;
        putUnique (vertices, xy * cos (a), xy * sin (a), z);
    }

    // 3rd row
    for (int i = 0; i < 5; i++)
    {
        float xy = cos (angleV);
        float a = angleH2 + i * angleH;
        putUnique (vertices, xy * cos (a), xy * sin (a), -z);
    }

    // bottom vertex
    putUnique (vertices, 0, 0, -1);

    // Indices
    for (int i = 0; i < 5; i++)
    {
        int i2 = i + 1;
        int i3 = i2 + 5;
        int j2 = (i + 1) % 5 + 1;
        int j3 = j2 + 5;

        // top triangle
        indices.push_back (0);
        indices.push_back (i2);
        indices.push_back (j2);

        // 2nd row
        indices.push_back (i2);
        indices.push_back (i3);
        indices.push_back (j2);

        // 3rd row
        indices.push_back (i3);
        indices.push_back (j3);
        indices.push_back (j2);

        // bottom triangle
        indices.push_back (11);
        indices.push_back (j3);
        indices.push_back (i3);
    }
}

void
icosphereSubdivide (std::vector<GLfloat> & vertices, std::vector<GLuint> & indices);
{
    int count = indices.size ();
    std::vector<GLuint> next (count * 4);

    for (int j = 0; j < count; j += 3)
    {
        // Get current triangle.
        int j0 = indices[j];
        int j1 = indices[j+1];
        int j2 = indices[j+2];

        // Create 3 new vertices by splitting each edge.
        int c01 = split (vertices, j0, j1);
        int c12 = split (vertices, j1, j2);
        int c20 = split (vertices, j2, j0);

        // Add 4 new triangles
        next.push_back (j0);
        next.push_back (c01);
        next.push_back (c20);

        next.push_back (j1);
        next.push_back (c12);
        next.push_back (c01);

        next.push_back (j2);
        next.push_back (c20);
        next.push_back (c12);

        next.push_back (c01);
        next.push_back (c12);
        next.push_back (c20);
    }

    indices = next;
}

int
split (std::vector<GLfloat> & vertices, int v0, int v1)
{
    v0 *= 6;
    v1 *= 6;
    float x = vertices[v0  ] + vertices[v1  ];
    float y = vertices[v0+1] + vertices[v1+1];
    float z = vertices[v0+2] + vertices[v1+2];
    float l = sqrt (x * x + y * y + z * z);
    x /= l;
    y /= l;
    z /= l;
    return putUnique (vertices, x, y, z);
}


#endif

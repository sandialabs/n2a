/*
Copyright 2018-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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

/*
Author: Fred Rothganger
Created 2/29/08 to replace Matrix2x2.tcc and Matrix3x3.tcc

Copyright 2009-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_matrix_fixed_tcc
#define n2a_matrix_fixed_tcc


#include "matrix.h"


// class MatrixFixed<T,2,2> -------------------------------------------------

template <class T>
inline T
det (const MatrixFixed<T,2,2> & A)
{
    return A.data[0][0] * A.data[1][1] - A.data[0][1] * A.data[1][0];
}

template <class T>
MatrixFixed<T,2,2>
operator ! (const MatrixFixed<T,2,2> & A)
{
    MatrixFixed<T,2,2> result;

    T q = det (A);
    if (q == 0) N2A_THROW ("invert: Matrix is singular!");

    result.data[0][0] = A.data[1][1] /  q;
    result.data[0][1] = A.data[0][1] / -q;
    result.data[1][0] = A.data[1][0] / -q;
    result.data[1][1] = A.data[0][0] /  q;

    return result;
}


// class MatrixFixed<T,3,3> -------------------------------------------------

template<class T>
inline T
det (const MatrixFixed<T,3,3> & A)
{
    return   A.data[0][0] * A.data[1][1] * A.data[2][2]
           - A.data[0][0] * A.data[2][1] * A.data[1][2]
           - A.data[1][0] * A.data[0][1] * A.data[2][2]
           + A.data[1][0] * A.data[2][1] * A.data[0][2]
           + A.data[2][0] * A.data[0][1] * A.data[1][2]
           - A.data[2][0] * A.data[1][1] * A.data[0][2];
}

template<class T>
MatrixFixed<T,3,3>
operator ! (const MatrixFixed<T,3,3> & A)
{
    MatrixFixed<T,3,3> result;

    T q = det (A);
    if (q == 0) N2A_THROW ("invert: Matrix is singular!");

    // Ugly, but ensures we actually inline this code.
#   define det22(data,r0,r1,c0,c1) (data[c0][r0] * data[c1][r1] - data[c1][r0] * data[c0][r1])

    result.data[0][0] = det22 (A.data, 1, 2, 1, 2) / q;
    result.data[0][1] = det22 (A.data, 1, 2, 2, 0) / q;
    result.data[0][2] = det22 (A.data, 1, 2, 0, 1) / q;
    result.data[1][0] = det22 (A.data, 0, 2, 2, 1) / q;
    result.data[1][1] = det22 (A.data, 0, 2, 0, 2) / q;
    result.data[1][2] = det22 (A.data, 0, 2, 1, 0) / q;
    result.data[2][0] = det22 (A.data, 0, 1, 1, 2) / q;
    result.data[2][1] = det22 (A.data, 0, 1, 2, 0) / q;
    result.data[2][2] = det22 (A.data, 0, 1, 0, 1) / q;

    return result;
}


// class MatrixFixed<T,R,C> -------------------------------------------------

template<class T, int R, int C>
MatrixFixed<T,R,C>::MatrixFixed ()
{
}

template<class T, int R, int C>
MatrixFixed<T,R,C>::MatrixFixed (std::initializer_list<T> elements)
{
    T * d = data[0];
    for (auto e : elements) *d++ = e;
    // No guard against overrun or underrun. Of the two, overrun is worse.
}

template<class T, int R, int C>
uint32_t
MatrixFixed<T,R,C>::classID () const
{
    return MatrixStridedID | MatrixFixedID;
}

template<class T, int R, int C>
MatrixFixed<T,C,R>
operator ~ (const MatrixFixed<T,R,C> & A)
{
    MatrixFixed<T,C,R> result;
    for (int c = 0; c < C; c++)
    {
        for (int r = 0; r < R; r++)
        {
            result.data[r][c] = A.data[c][r];
        }
    }
    return result;
}

template<class T, int R, int C>
MatrixFixed<T,R,C>
operator & (const MatrixFixed<T,R,C> & A, const MatrixFixed<T,R,C> & B)
{
    MatrixFixed<T,R,C> result;
    const T * a   = A.data[0];
    const T * b   = B.data[0];
    T *       r   = result.data[0];
    T *       end = r + R * C;
    while (r < end) *r++ = *a++ * *b++;
    return result;
}

template<class T, int R, int C, int O>
MatrixFixed<T,R,C>
operator * (const MatrixFixed<T,R,O> & A, const MatrixFixed<T,O,C> & B)
{
    MatrixFixed<T,R,C> result;
    const T * aa  = A.data[0];
    const T * b   = B.data[0];
    T *       r   = result.data[0];
    T *       end = r + R * C;
    while (r < end)
    {
        const T * a = aa;
        T * columnEnd = r + R;
        while (r < columnEnd)
        {
            T element = (T) 0;
            const T * i = a;
            const T * j = b;
            const T * rowEnd = j + O;
            while (j != rowEnd)
            {
                element += (*i) * (*j++);
                i += R;
            }
            *r++ = element;
            a++;
        }
        b += O;
    }
    return result;
}

template<class T, int R, int C>
MatrixFixed<T,R,C>
operator * (const MatrixFixed<T,R,C> & A, const T scalar)
{
    MatrixFixed<T,R,C> result;
    const T * a   = A.data[0];
    T *       r   = result.data[0];
    T *       end = r + R * C;
    while (r < end) *r++ = *a++ * scalar;
    return result;
}

template<class T, int R, int C>
MatrixFixed<T,R,C>
operator / (const MatrixFixed<T,R,C> & A, const MatrixFixed<T,R,C> & B)
{
    MatrixFixed<T,R,C> result;
    const T * a   = A.data[0];
    const T * b   = B.data[0];
    T *       r   = result.data[0];
    T *       end = r + R * C;
    while (r < end) *r++ = *a++ / *b++;
    return result;
}

template<class T, int R, int C>
MatrixFixed<T,R,C>
operator / (const MatrixFixed<T,R,C> & A, const T scalar)
{
    MatrixFixed<T,R,C> result;
    const T * a   = A.data[0];
    T *       r   = result.data[0];
    T *       end = r + R * C;
    while (r < end) *r++ = *a++ / scalar;
    return result;
}

template<class T, int R, int C>
MatrixFixed<T,R,C>
operator / (const T scalar, const MatrixFixed<T,R,C> & A)
{
    MatrixFixed<T,R,C> result;
    const T * a   = A.data[0];
    T *       r   = result.data[0];
    T *       end = r + R * C;
    while (r < end) *r++ = scalar / *a++;
    return result;
}

template<class T, int R, int C>
MatrixFixed<T,R,C>
operator + (const MatrixFixed<T,R,C> & A, const MatrixFixed<T,R,C> & B)
{
    MatrixFixed<T,R,C> result;
    const T * a   = A.data[0];
    const T * b   = B.data[0];
    T *       r   = result.data[0];
    T *       end = r + R * C;
    while (r < end) *r++ = *a++ + *b++;
    return result;
}

template<class T, int R, int C>
MatrixFixed<T,R,C>
operator + (const MatrixFixed<T,R,C> & A, const T scalar)
{
    MatrixFixed<T,R,C> result;
    const T * a   = A.data[0];
    T *       r   = result.data[0];
    T *       end = r + R * C;
    while (r < end) *r++ = *a++ + scalar;
    return result;
}

template<class T, int R, int C>
MatrixFixed<T,R,C>
operator - (const MatrixFixed<T,R,C> & A, const MatrixFixed<T,R,C> & B)
{
    MatrixFixed<T,R,C> result;
    const T * a   = A.data[0];
    const T * b   = B.data[0];
    T *       r   = result.data[0];
    T *       end = r + R * C;
    while (r < end) *r++ = *a++ - *b++;
    return result;
}

template<class T, int R, int C>
MatrixFixed<T,R,C>
operator - (const MatrixFixed<T,R,C> & A, const T scalar)
{
    MatrixFixed<T,R,C> result;
    const T * a   = A.data[0];
    T *       r   = result.data[0];
    T *       end = r + R * C;
    while (r < end) *r++ = *a++ - scalar;
    return result;
}

template<class T, int R, int C>
MatrixFixed<T,R,C>
operator - (const T scalar, const MatrixFixed<T,R,C> & A)
{
    MatrixFixed<T,R,C> result;
    const T * a   = A.data[0];
    T *       r   = result.data[0];
    T *       end = r + R * C;
    while (r < end) *r++ = scalar - *a++;
    return result;
}

template<class T, int R, int C>
void
operator *= (MatrixFixed<T,R,C> & A, const T scalar)
{
    T * a   = A.data[0];
    T * end = a + R * C;
    while (a < end) *a++ *= scalar;
}

#ifdef n2a_FP

template<int R, int C>
MatrixFixed<int,R,C>
shift (const MatrixFixed<int,R,C> & A, int shift)
{
    MatrixFixed<int,R,C> result;
    const int * a   = A.data[0];
    int *       r   = result.data[0];
    int *       end = r + R * C;
    if (shift >= 0) while (r < end) *r++ = *a++ << shift;
    else            while (r < end) *r++ = *a++ >> -shift;
    return result;
}

template<int R, int C>
MatrixFixed<int,R,C>
multiplyElementwise (const MatrixFixed<int,R,C> & A, const MatrixFixed<int,R,C> & B, int shift)
{
    MatrixFixed<int,R,C> result;
    const int * a   = A.data[0];
    const int * b   = B.data[0];
    int *       r   = result.data[0];
    int *       end = r + R * C;
    while (r < end) *r++ = (int64_t) *a++ * *b++ >> shift;
    return result;
}

template<int R, int C, int O>
MatrixFixed<int,R,C>
multiply (const MatrixFixed<int,R,O> & A, const MatrixFixed<int,O,C> & B, int shift)
{
    MatrixFixed<int,R,C> result;
    const int * aa  = A.data[0];
    const int * b   = B.data[0];
    int *       r   = result.data[0];
    int *       end = r + R * C;
    while (r < end)
    {
        const int * a = aa;
        int * columnEnd = r + R;
        while (r < columnEnd)
        {
            register int64_t element = 0;
            const int * i = a;
            const int * j = b;
            const int * rowEnd = j + O;
            while (j != rowEnd)
            {
                element += (int64_t) (*i) * (*j++);
                i += R;
            }
            *r++ = element >> shift;
            a++;
        }
        b += O;
    }
    return result;
}

template<int R, int C>
MatrixFixed<int,R,C>
multiply (const MatrixFixed<int,R,C> & A, int scalar, int shift)
{
    MatrixFixed<int,R,C> result;
    const int * a   = A.data[0];
    int *       r   = result.data[0];
    int *       end = r + R * C;
    while (r < end) *r++ = (int64_t) scalar * *a++ >> shift;
    return result;
}

template<int R, int C>
MatrixFixed<int,R,C>
divide (const MatrixFixed<int,R,C> & A, const MatrixFixed<int,R,C> & B, int shift)
{
    MatrixFixed<int,R,C> result;
    const int * a   = A.data[0];
    const int * b   = B.data[0];
    int *       r   = result.data[0];
    int *       end = r + R * C;
    while (r < end) *r++ = ((int64_t) *a++ << shift) / *b++;
    return result;
}

template<int R, int C>
MatrixFixed<int,R,C>
divide (const MatrixFixed<int,R,C> & A, int scalar, int shift)
{
    MatrixFixed<int,R,C> result;
    const int * a   = A.data[0];
    int *       r   = result.data[0];
    int *       end = r + R * C;
    while (r < end) *r++ = ((int64_t) *a++ << shift) / scalar;
    return result;
}

template<int R, int C>
MatrixFixed<int,R,C>
divide (int scalar, const MatrixFixed<int,R,C> & A, int shift)
{
    MatrixFixed<int,R,C> result;
    const int * a   = A.data[0];
    int *       r   = result.data[0];
    int *       end = r + R * C;
    while (r < end) *r++ = ((int64_t) scalar << shift) / *a++;
    return result;
}

#endif  // n2a_FP

#endif  // n2a_matrix_fixed_tcc

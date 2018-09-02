/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_fixedpoint_tcc
#define n2a_fixedpoint_tcc


#include "fixedpoint.h"


template<int R, int C>
MatrixFixed<int,R,C>
shiftUp (const MatrixFixed<int,R,C> & A, int shift)
{
    MatrixFixed<int,R,C> result;
    const int * a   = A.data[0];
    int *       r   = result.data[0];
    int *       end = r + R * C;
    while (r < end) *r++ = *a++ << shift;
    return result;
}

template<int R, int C>
MatrixFixed<int,R,C>
shiftDown (const MatrixFixed<int,R,C> & A, int shift)
{
    MatrixFixed<int,R,C> result;
    const int * a   = A.data[0];
    int *       r   = result.data[0];
    int *       end = r + R * C;
    while (r < end) *r++ = *a++ >> shift;
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


#endif

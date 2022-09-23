/*
Copyright 2018-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_fixedpoint_h
#define n2a_fixedpoint_h

#ifndef n2a_FP


#define _USE_MATH_DEFINES
#include <cmath>

#define TWOPI  6.283185307179586476925286766559
#define TWOPIf 6.283185307179586476925286766559f

namespace std
{
    /// Four-way max
    template<class T>
    inline const T &
    max (const T & a, const T & b, const T & c, const T & d)
    {
        return max (max (a, b), max (c, d));
    }

    /// Four-way min
    template<class T>
    inline const T &
    min (const T & a, const T & b, const T & c, const T & d)
    {
        return min (min (a, b), min (c, d));
    }
}

namespace n2a
{
    using std::isnan;
    using std::isinf;

    /**
        Same as round(), but when <code>|a - roundp(a)| = 0.5</code> the result will
        be the more positive integer.
    **/
    inline float
    roundp (float a)
    {
        return floorf (a + 0.5f);
    }

    /**
        Same as round(), but when <code>|a - roundp(a)| = 0.5</code> the result will
        be the more positive integer.
    **/
    inline double
    roundp (double a)
    {
        return floor (a + 0.5);
    }
}


#else


#include "nosys.h"
#include "matrix.h"

#define FP_MSB    30
#define FP_MSB2   15
#define M_LOG2E   1549082004  // log_2(e) = 1.4426950408889634074; exponent=0
#define M_E       1459366444  // exponent=1
#define M_PI      1686629713  // exponent=1
#undef NAN
#undef INFINITY
#define NAN       0x80000000
#define INFINITY  0x7FFFFFFF

namespace n2a
{
    inline bool isnan (int a)
    {
        return a == NAN;
    }

    inline bool isinf (int a)
    {
        return abs (a) == INFINITY;
    }
}


// Transcendental functions --------------------------------------------------

int atan2    (int y,                        int x);                                                    // returns angle in [-pi,pi], exponentResult=1; exponentA must match exponentB, but it may be arbitrary.
int cos      (int a,                               int exponentA);
int exp      (int a,                                                             int exponentResult);  // exponentA=7
int log      (int a,                               int exponentA,                int exponentResult);
int log2     (int a,                               int exponentA,                int exponentResult);  // Use as a subroutine. Not directly available to user.
int modFloor (int a,                        int b, int exponentA, int exponentB);                      // exponentResult is promised to be min(exponentA,exponentB)
int norm     (const MatrixStrided<int> & A, int n, int exponentA,                int exponentResult);  // exponentN=15
int pow      (int a,                        int b, int exponentA,                int exponentResult);  // exponentB=15
int sin      (int a,                               int exponentA);
int sqrt     (int a,                               int exponentA,                int exponentResult);
int tan      (int a,                               int exponentA,                int exponentResult);
int tanh     (int a,                               int exponentA);                                     // exponentResult=0


// Extended operations on MatrixFixed<int,R,C> -------------------------------

                              Matrix<int>          shift               (const MatrixAbstract<int>  & A,                                 int shift);
template<int R, int C>        MatrixFixed<int,R,C> shift               (const MatrixFixed<int,R,C> & A,                                 int shift);

template<int R, int C>        MatrixFixed<int,R,C> multiplyElementwise (const MatrixFixed<int,R,C> & A, const MatrixFixed<int,R,C> & B, int shift);
template<int R, int C, int O> MatrixFixed<int,R,C> multiply            (const MatrixFixed<int,R,O> & A, const MatrixFixed<int,O,C> & B, int shift);
template<int R, int C>        MatrixFixed<int,R,C> multiply            (const MatrixFixed<int,R,C> & A, int b,                          int shift);

template<int R, int C>        MatrixFixed<int,R,C> divide              (const MatrixFixed<int,R,C> & A, const MatrixFixed<int,R,C> & B, int shift);
template<int R, int C>        MatrixFixed<int,R,C> divide              (const MatrixFixed<int,R,C> & A, int b,                          int shift);
template<int R, int C>        MatrixFixed<int,R,C> divide              (int a,                          const MatrixFixed<int,R,C> & B, int shift);


#endif  // n2a_FP
#endif  // n2a_fixedpoint_h

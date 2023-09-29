/*
Copyright 2018-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_math_h
#define n2a_math_h

#define _USE_MATH_DEFINES
#include <cmath>
#include <limits>

#include "shared.h"

#define TWOPI  6.283185307179586476925286766559
#define TWOPIf 6.283185307179586476925286766559f

#ifdef _MSC_VER
namespace std
{
    inline bool
    isnan (double a)
    {
        return _isnan (a);
    }

    inline bool
    isinf (double a)
    {
        return ! _finite (a);
    }
}
#endif

namespace n2a
{
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

    template<typename T>
    inline T
    sgn (const T a)
    {
        if (a < (T) 0) return (T) -1;
        if (a > (T) 0) return (T) 1;
        return (T) 0;
    }
}


#ifdef n2a_FP

#include "nosys.h"

#undef M_LOG2E
#undef M_E
#undef M_PI
#undef NAN
#undef INFINITY

#define FP_MSB    30
#define FP_MSB2   15
#define M_LOG2E   1549082004  // log_2(e) = 1.4426950408889634074; exponent=0
#define M_E       1459366444  // exponent=1
#define M_PI      1686629713  // exponent=1
#define NAN       0x80000000
#define INFINITY  0x7FFFFFFF

namespace std
{
    inline bool
    isnan (int a)
    {
        return a == NAN;
    }

    inline bool
    isinf (int a)
    {
        return abs (a) == INFINITY;
    }

    // MSVC chokes on the generic template modFloor() in runtime.h unless this function
    // is defined in std, even though it is not used in fixed-point. We create a poisoned
    // version here, just to get past compile.
    inline int
    floor (int a)
    {
        throw "std::floor(int) not implemented";
    }
}

SHARED int atan2    (int y, int x);                                                    // returns angle in [-pi,pi], exponentResult=1; exponentA must match exponentB, but it may be arbitrary.
SHARED int ceil     (int a,        int exponentA,                int exponentResult);  // Only needed for matrices. For scalars, an immediate implementation is emitted.
SHARED int cos      (int a,        int exponentA);
SHARED int exp      (int a,                                      int exponentResult);  // exponentA=7
SHARED int floor    (int a,        int exponentA,                int exponentResult);  // Only needed for matrices.
SHARED int log      (int a,        int exponentA,                int exponentResult);
SHARED int log2     (int a,        int exponentA,                int exponentResult);  // Use as a subroutine. Not directly available to user.
SHARED int modFloor (int a, int b, int exponentA, int exponentB);                      // exponentResult is promised to be min(exponentA,exponentB)
SHARED int pow      (int a, int b, int exponentA,                int exponentResult);  // exponentB=15
SHARED int round    (int a,        int exponentA,                int exponentResult);  // Only needed for matrices.
SHARED int sgn      (int a,                                      int exponentResult);  // Only needed for matrices.
SHARED int sin      (int a,        int exponentA);
SHARED int sqrt     (int a,        int exponentA,                int exponentResult);
SHARED int tan      (int a,        int exponentA,                int exponentResult);
SHARED int tanh     (int a,        int exponentA);                                     // exponentResult=0

#endif  // n2a_FP

#endif  // n2a_math_h

#ifndef n2a_fixedpoint_h
#define n2a_fixedpoint_h


#include "nosys.h"
#include "matrix.h"


#define FP_MSB    30
#define FP_MSB2   15
#define FP_LOG2E  1549082004  // log_2(e) = 1.4426950408889634074; exponent=0
#define FP_E      1459366444  // exponent=1
#define FP_PI     1686629713  // exponent=1
#undef NAN
#undef INFINITY
#define NAN       0x80000000
#define INFINITY  0x7FFFFFFF


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

template<int R, int C>        MatrixFixed<int,R,C> shiftUp             (const MatrixFixed<int,R,C> & A,                                 int shift);
template<int R, int C>        MatrixFixed<int,R,C> shiftDown           (const MatrixFixed<int,R,C> & A,                                 int shift);

template<int R, int C>        MatrixFixed<int,R,C> multiplyElementwise (const MatrixFixed<int,R,C> & A, const MatrixFixed<int,R,C> & B, int shift);
template<int R, int C, int O> MatrixFixed<int,R,C> multiply            (const MatrixFixed<int,R,O> & A, const MatrixFixed<int,O,C> & B, int shift);
template<int R, int C>        MatrixFixed<int,R,C> multiply            (const MatrixFixed<int,R,C> & A, int b,                          int shift);

template<int R, int C>        MatrixFixed<int,R,C> divide              (const MatrixFixed<int,R,C> & A, const MatrixFixed<int,R,C> & B, int shift);
template<int R, int C>        MatrixFixed<int,R,C> divide              (const MatrixFixed<int,R,C> & A, int b,                          int shift);
template<int R, int C>        MatrixFixed<int,R,C> divide              (int a,                          const MatrixFixed<int,R,C> & B, int shift);


#endif

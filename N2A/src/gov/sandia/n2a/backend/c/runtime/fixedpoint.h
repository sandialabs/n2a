#ifndef n2a_fixedpoint_h
#define n2a_fixedpoint_h


#include "matrix.h"


#define FP_MSB    30
#define FP_MSB2   15
#define FP_LOG2E  1549082004  // log_2(e) = 1.4426950408889634074; exponent=0
#define FP_E      1459366444  // exponent=1
#define FP_PI     1686629713  // exponent=1
#define FP_INF    0x7FFFFFFF
#undef FP_NAN   // From math.h; a floating-point attribute.
#define FP_NAN    0x80000000


// Transcendental functions --------------------------------------------------

int cos   (int a,        int exponentA);
int exp   (int a,                                      int exponentResult);
int log   (int a,        int exponentA,                int exponentResult);
int log2  (int a,        int exponentA,                int exponentResult);  // Use as a subroutine. Not directly available to user.
int mod   (int a, int b, int exponentA, int exponentB); // exponentResult is promised to be min(exponentA,exponentB)
int pow   (int a, int b, int exponentA,                int exponentResult);
int sin   (int a,        int exponentA);
int sqrt  (int a,        int exponentA,                int exponentResult);
int tan   (int a,        int exponentA,                int exponentResult);


// Extended operations on Matrix<int> ----------------------------------------

Matrix<int> shift               (int shift, Matrix<int> A);
int         norm                (int n,     Matrix<int> A, int exponentA, int exponentResult);

Matrix<int> multiply            (Matrix<int> A, Matrix<int> B, int shift);
Matrix<int> multiply            (Matrix<int> A, int b,         int shift);
Matrix<int> multiply            (int a,         Matrix<int> B, int shift);
Matrix<int> multiplyElementwise (Matrix<int> A, Matrix<int> B, int shift);

Matrix<int> divide              (Matrix<int> A, Matrix<int> B, int shift);
Matrix<int> divide              (Matrix<int> A, int b,         int shift);
Matrix<int> divide              (int a,         Matrix<int> B, int shift);


#endif

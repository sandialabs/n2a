#ifndef n2a_fixedpoint_h
#define n2a_fixedpoint_h


#include "fl/matrix.h"


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

fl::MatrixResult<int> shift               (int shift, fl::Matrix<int> A);
int                   norm                (int n,     fl::Matrix<int> A, int exponentA, int exponentResult);

fl::MatrixResult<int> multiply            (fl::Matrix<int> A, fl::Matrix<int> B, int shift);
fl::MatrixResult<int> multiply            (fl::Matrix<int> A, int b,             int shift);
fl::MatrixResult<int> multiply            (int a,             fl::Matrix<int> B, int shift);
fl::MatrixResult<int> multiplyElementwise (fl::Matrix<int> A, fl::Matrix<int> B, int shift);

fl::MatrixResult<int> divide              (fl::Matrix<int> A, fl::Matrix<int> B, int shift);
fl::MatrixResult<int> divide              (fl::Matrix<int> A, int b,             int shift);
fl::MatrixResult<int> divide              (int a,             fl::Matrix<int> B, int shift);


#endif

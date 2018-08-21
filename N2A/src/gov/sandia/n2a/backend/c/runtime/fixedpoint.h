#ifndef n2a_fixedpoint_h
#define n2a_fixedpoint_h


#include "fl/matrix.h"


// Transcendental functions --------------------------------------------------

int cos   (int a);
int exp   (int a,        int exponentA,                int exponentResult);
int log   (int a,        int exponentA,                int exponentResult);
int log2  (int a,        int exponentA,                int exponentResult);  // Use as a subroutine. Not directly available to user.
int mod   (int a, int b, int exponentA, int exponentB, int exponentResult);
int pow   (int a, int b, int exponentA, int exponentB, int exponentResult);
int sin   (int a);
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

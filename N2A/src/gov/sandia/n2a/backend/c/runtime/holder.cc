/*
Copyright 2018-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#include "holder.tcc"
#include "Matrix.tcc"
#include "MatrixFixed.tcc"
#include "MatrixSparse.tcc"


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
template SHARED n2a_T         norm       (const MatrixAbstract<n2a_T> & A, n2a_T n);
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
template SHARED n2a_T         norm  (const MatrixStrided<n2a_T> & A, n2a_T n);
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

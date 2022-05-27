/*
Copyright 2018-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#include "io.tcc"
#include "Matrix.tcc"
#include "MatrixSparse.tcc"


using namespace std;


#ifdef n2a_TLS
// Hack to work around GCC 11.x. Should do no harm. Should also not be necessary.
template<class T> thread_local Simulator<T> * Simulator<T>::instance = 0;
#endif

template class MatrixAbstract<n2a_T>;
template class Matrix<n2a_T>;
template class MatrixSparse<n2a_T>;
template ostream & operator << (ostream & stream, const MatrixAbstract<n2a_T> & A);

template class Parameters<n2a_T>;
template class IteratorNonzero<n2a_T>;
template class IteratorSkip<n2a_T>;
template class IteratorSparse<n2a_T>;
template class MatrixInput<n2a_T>;
template class Mfile<n2a_T>;
template class InputHolder<n2a_T>;
template class OutputHolder<n2a_T>;
template IteratorNonzero<n2a_T> * getIterator (MatrixAbstract<n2a_T> * A);
#ifdef n2a_FP
template MatrixInput<n2a_T> *  matrixHelper (const String & fileName, int exponent, MatrixInput<n2a_T> *  oldHandle);
template InputHolder<n2a_T> *  inputHelper  (const String & fileName, int exponent, InputHolder<n2a_T> *  oldHandle);
#else
template MatrixInput<n2a_T> *  matrixHelper (const String & fileName,               MatrixInput<n2a_T> *  oldHandle);
template InputHolder<n2a_T> *  inputHelper  (const String & fileName,               InputHolder<n2a_T> *  oldHandle);
#endif
template Mfile       <n2a_T> * MfileHelper  (const String & fileName,               Mfile       <n2a_T> * oldHandle);
template OutputHolder<n2a_T> * outputHelper (const String & fileName,               OutputHolder<n2a_T> * oldHandle);


// Non-templated functions ---------------------------------------------------

Holder::Holder (const String & fileName)
:   fileName (fileName)
{
}

Holder::~Holder ()
{
}

#include "io.tcc"

#include "fl/Matrix.tcc"
#include "fl/MatrixFixed.tcc"
#include "fl/MatrixSparse.tcc"


using namespace fl;
using namespace std;


template class MatrixAbstract<n2a_T>;
template class Matrix<n2a_T>;
template class MatrixSparse<n2a_T>;
template class MatrixFixed<n2a_T,3,1>;
namespace fl
{
    template SHARED std::istream & operator >> (std::istream & stream, MatrixAbstract<n2a_T>  & A);
}

template class IteratorNonzero<n2a_T>;
template class IteratorSkip<n2a_T>;
template class IteratorSparse<n2a_T>;
template class MatrixInput<n2a_T>;
template class InputHolder<n2a_T>;
template class OutputHolder<n2a_T>;
template MatrixInput<n2a_T> *  matrixHelper (const String & fileName, MatrixInput<n2a_T> *  oldHandle);
template InputHolder<n2a_T> *  inputHelper  (const String & fileName, InputHolder<n2a_T> *  oldHandle);
template OutputHolder<n2a_T> * outputHelper (const String & fileName, OutputHolder<n2a_T> * oldHandle);


// Non-templated functions ---------------------------------------------------

Holder::Holder (const String & fileName)
:   fileName (fileName)
{
}

Holder::~Holder ()
{
}

void
outputClose ()
{
    for (auto it : outputMap) delete it;
    // No need to clear collection, because this function is only called during shutdown.
}

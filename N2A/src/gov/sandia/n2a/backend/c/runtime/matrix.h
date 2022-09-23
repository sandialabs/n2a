/*
Author: Fred Rothganger
Copyright (c) 2001-2004 Dept. of Computer Science and Beckman Institute,
                        Univ. of Illinois.  All rights reserved.
Distributed under the UIUC/NCSA Open Source License.


Copyright 2005-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_matrix_h
#define n2a_matrix_h


#include "pointer.h"

#include <vector>
#include <map>
#ifndef N2A_SPINNAKER
# include <iostream>
# include <sstream>
#endif


// Matrix class ID constants
// This is a hack to avoid the cost of RTTI.
#define MatrixStridedID   0x1
#define MatrixID          0x2
#define MatrixFixedID     0x4
#define MatrixSparseID    0x8


// Matrix general interface -------------------------------------------------

/**
    We reserve the name "Matrix" for a dense matrix, rather than for the
    abstract type.  This makes coding a little prettier, since dense
    matrices are the most common case.
**/
template<class T>
class MatrixAbstract
{
public:
    virtual ~MatrixAbstract ();
    virtual uint32_t classID () const = 0;  ///< @return A bitvector indicating all the classes to which this object can be cast.  Hack to avoid the cost of dynamic_cast.

    virtual T   get         (const int row, const int column) const = 0;  ///< Safe element accesss. Returns 0 if indices are out of range.
    virtual T & operator () (const int row, const int column) const = 0;  ///< Raw element accesss. No range checking. More efficient.
    virtual int rows        () const = 0;
    virtual int columns     () const = 0;
};

template<class T> class Matrix;

template<class T> void      clear      (      MatrixAbstract<T> & A, const T scalar = (T) 0);    ///< Set all elements to given value.
template<class T> T         norm       (const MatrixAbstract<T> & A, T n);                       ///< Generalized Frobenius norm: (sum_elements (element^n))^(1/n).  Effectively: INFINITY is max, 1 is sum, 2 is standard Frobenius norm.  n==0 is technically undefined, but we treat is as the count of non-zero elements.
template<class T> T         sumSquares (const MatrixAbstract<T> & A);                            ///< Equivalent to norm(A,2)^2, but without taking the square root.
template<class T> Matrix<T> visit      (const MatrixAbstract<T> & A, T (*function) (const T &)); ///< Apply function() to each element, and return the results in a new Matrix of equal size.
template<class T> Matrix<T> visit      (const MatrixAbstract<T> & A, T (*function) (const T));   ///< Apply function() to each element, and return the results in a new Matrix of equal size.

template<class T> bool operator == (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B);  ///< Two matrices are equal if they have the same shape and the same elements.
template<class T> bool operator != (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    return ! (A == B);
}

template<class T> Matrix<T> operator & (const MatrixAbstract<T> & A, const MatrixAbstract<T>  & B); ///< Elementwise multiplication.  The prettiest name for this operator would be ".*", but that is not overloadable.
template<class T> Matrix<T> operator * (const MatrixAbstract<T> & A, const MatrixAbstract<T>  & B); ///< Multiply matrices: this * B
template<class T> Matrix<T> operator * (const MatrixAbstract<T> & A, const T scalar);               ///< Multiply each element by scalar
template<class T> Matrix<T> operator / (const MatrixAbstract<T> & A, const MatrixAbstract<T>  & B); ///< Elementwise division.  Could mean this * !B, but such expressions are done other ways in linear algebra.
template<class T> Matrix<T> operator / (const MatrixAbstract<T> & A, const T scalar);               ///< Divide each element by scalar
template<class T> Matrix<T> operator + (const MatrixAbstract<T> & A, const MatrixAbstract<T>  & B); ///< Elementwise sum.
template<class T> Matrix<T> operator + (const MatrixAbstract<T> & A, const T scalar);               ///< Add scalar to each element
template<class T> Matrix<T> operator - (const MatrixAbstract<T> & A, const MatrixAbstract<T>  & B); ///< Elementwise difference.
template<class T> Matrix<T> operator - (const MatrixAbstract<T> & A, const T scalar);               ///< Subtract scalar from each element

#ifndef N2A_SPINNAKER
template<class T> std::ostream & operator << (std::ostream & stream, const MatrixAbstract<T> & A);  ///< Print human readable matrix to stream.
#endif


// Concrete matrices  -------------------------------------------------------

/**
    Provides several kinds of view specifically for the Matrix class.
    Handles these efficiently by using special combinations of start
    address and row and column stride.

    <p>This is the superclass for both Matrix and Vector, because those are
    really just more constrained forms of the general memory access pattern
    described by this class.  However, the semantics of functions in this
    class follow those of MatrixRegion in cases where they differ from
    Matrix and Vector.  For example, resize() will change the bounds, but
    will not allocate memory if they exceed the current storage size.
**/
template<class T>
class MatrixStrided : public MatrixAbstract<T>
{
public:
    virtual T * base    () const = 0; ///< Address of first element.
    virtual int strideR () const = 0; ///< Number of elements between start of each row in memory.
    virtual int strideC () const = 0; ///< Number of elements between start of each column in memory. Equivalent to "leading dimension" in LAPACK parlance.
};

template<class T> void      clear (      MatrixStrided<T> & A, const T scalar = (T) 0);
template<class T> T         norm  (const MatrixStrided<T> & A, T n);
template<class T> Matrix<T> visit (const MatrixStrided<T> & A, T (*function) (const T &));
template<class T> Matrix<T> visit (const MatrixStrided<T> & A, T (*function) (const T));

template<class T> Matrix<T> operator & (const MatrixStrided<T> & A, const MatrixAbstract<T> & B);
template<class T> Matrix<T> operator * (const MatrixStrided<T> & A, const MatrixAbstract<T> & B);
template<class T> Matrix<T> operator * (const MatrixStrided<T> & A, const T scalar);
template<class T> Matrix<T> operator * (const T scalar,             const MatrixStrided<T> & A) {return A * scalar;}
template<class T> Matrix<T> operator / (const MatrixStrided<T> & A, const MatrixAbstract<T> & B);
template<class T> Matrix<T> operator / (const MatrixStrided<T> & A, const T scalar);
template<class T> Matrix<T> operator / (const T scalar,             const MatrixStrided<T> & A);
template<class T> Matrix<T> operator + (const MatrixStrided<T> & A, const MatrixAbstract<T> & B);
template<class T> Matrix<T> operator + (const MatrixStrided<T> & A, const T scalar);
template<class T> Matrix<T> operator + (const T scalar,             const MatrixStrided<T> & A) {return A + scalar;}
template<class T> Matrix<T> operator - (const MatrixStrided<T> & A, const MatrixAbstract<T> & B);
template<class T> Matrix<T> operator - (const MatrixStrided<T> & A, const T scalar);
template<class T> Matrix<T> operator - (const T scalar,             const MatrixStrided<T> & A);

template<class T>
class Matrix : public MatrixStrided<T>
{
public:
    n2a::Pointer data;
    int offset;
    int rows_;
    int columns_;
    int strideR_;
    int strideC_;

    Matrix ();
    Matrix (const int rows, const int columns = 1);
    Matrix (const MatrixAbstract<T> & that);
    Matrix (const n2a::Pointer & that, const int offset, const int rows, const int columns, const int strideR, const int strideC);
    virtual uint32_t classID () const;

    void resize (const int rows, const int columns = 1);  ///< Subroutine of constructors.

    virtual T get (const int row, const int column) const
    {
        if (row < 0     ||  row    >= rows_   ) return (T) 0;
        if (column < 0  ||  column >= columns_) return (T) 0;
        return ((T *) data)[offset + column * strideC_ + row * strideR_];
    }
    virtual T & operator () (const int row, const int column) const
    {
        return ((T *) data)[offset + column * strideC_ + row * strideR_];
    }
    T & operator [] (const int row) const  ///< Only works for the first column, unless rows == strideC.
    {
        return ((T *) data)[offset + row * strideR_];
    }
    virtual int rows () const
    {
        return rows_;
    }
    virtual int columns () const
    {
        return columns_;
    }
    virtual T * base () const
    {
        return (T *) data + offset;
    }
    virtual int strideR () const
    {
        return strideR_;
    }
    virtual int strideC () const
    {
        return strideC_;
    }
};

template<class T> Matrix<T> operator ~ (const Matrix<T> & A);

template<class T, int R, int C>
class MatrixFixed : public MatrixStrided<T>
{
public:
    T data[C][R];

    MatrixFixed ();
    MatrixFixed (std::initializer_list<T> elements);  ///< In column-major order
    MatrixFixed (const MatrixAbstract<T> & that);
    virtual uint32_t classID () const;

    virtual T get (const int row, const int column) const
    {
        if (row < 0     ||  row    >= R) return (T) 0;
        if (column < 0  ||  column >= C) return (T) 0;
        return data[column][row];
    }
    virtual T & operator () (const int row, const int column) const
    {
        return (T &) data[column][row];
    }
    T & operator [] (const int row) const
    {
        return ((T *) data)[row];
    }
    virtual int rows () const
    {
        return R;
    }
    virtual int columns () const
    {
        return C;
    }
    virtual T * base () const
    {
        return const_cast<T *> (data[0]);
    }
    virtual int strideR () const
    {
        return 1;
    }
    virtual int strideC () const
    {
        return R;
    }
};

template<class T> T det (const MatrixFixed<T,2,2> & A);  ///< Subroutine for operator !
template<class T> T det (const MatrixFixed<T,3,3> & A);

template<class T>                      MatrixFixed<T,2,2> operator ! (const MatrixFixed<T,2,2> & A);
template<class T>                      MatrixFixed<T,3,3> operator ! (const MatrixFixed<T,3,3> & A);
template<class T, int R, int C>        MatrixFixed<T,C,R> operator ~ (const MatrixFixed<T,R,C> & A);
template<class T, int R, int C>        MatrixFixed<T,R,C> operator & (const MatrixFixed<T,R,C> & A, const MatrixFixed<T,R,C> & B);
template<class T, int R, int C, int O> MatrixFixed<T,R,C> operator * (const MatrixFixed<T,R,O> & A, const MatrixFixed<T,O,C> & B);
template<class T, int R, int C>        MatrixFixed<T,R,C> operator * (const MatrixFixed<T,R,C> & A, const T scalar);
template<class T, int R, int C>        MatrixFixed<T,R,C> operator * (const T scalar,               const MatrixFixed<T,R,C> & A) {return A * scalar;}
template<class T, int R, int C>        MatrixFixed<T,R,C> operator / (const MatrixFixed<T,R,C> & A, const MatrixFixed<T,R,C> & B);
template<class T, int R, int C>        MatrixFixed<T,R,C> operator / (const MatrixFixed<T,R,C> & A, const T scalar);
template<class T, int R, int C>        MatrixFixed<T,R,C> operator / (const T scalar,               const MatrixFixed<T,R,C> & A);
template<class T, int R, int C>        MatrixFixed<T,R,C> operator + (const MatrixFixed<T,R,C> & A, const MatrixFixed<T,R,C> & B);
template<class T, int R, int C>        MatrixFixed<T,R,C> operator + (const MatrixFixed<T,R,C> & A, const T scalar);
template<class T, int R, int C>        MatrixFixed<T,R,C> operator + (const T scalar,               const MatrixFixed<T,R,C> & A) {return A + scalar;}
template<class T, int R, int C>        MatrixFixed<T,R,C> operator - (const MatrixFixed<T,R,C> & A, const MatrixFixed<T,R,C> & B);
template<class T, int R, int C>        MatrixFixed<T,R,C> operator - (const MatrixFixed<T,R,C> & A, const T scalar);
template<class T, int R, int C>        MatrixFixed<T,R,C> operator - (const T scalar,               const MatrixFixed<T,R,C> & A);

/**
    Stores only nonzero elements.  Assumes that every column has at least
    one non-zero entry, so stores a structure for every column.  This is
    a trade-off between time and space (as always).  If the matrix is
    extremely sparse (not all columns used), then a sparse structure for
    holding the column structures would be better.
**/
template<class T>
class MatrixSparse : public MatrixAbstract<T>
{
public:
    int rows_;
    n2a::PointerStruct<std::vector<std::map<int,T>>> data;

    MatrixSparse ();
    MatrixSparse (const int rows, const int columns);
    MatrixSparse (const MatrixAbstract<T> & that);
    virtual uint32_t classID () const;

    void        set         (const int row, const int column, const T value);  ///< If value is non-zero, creates element if not already there; if value is zero, removes element if it exists.
    virtual T   get         (const int row, const int column) const {return operator() (row, column);}
    virtual T & operator () (const int row, const int column) const;
    virtual int rows        () const;
    virtual int columns     () const;
};


#endif

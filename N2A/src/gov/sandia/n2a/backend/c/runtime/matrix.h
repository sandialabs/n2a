/*
Author: Fred Rothganger
Copyright (c) 2001-2004 Dept. of Computer Science and Beckman Institute,
                        Univ. of Illinois.  All rights reserved.
Distributed under the UIUC/NCSA Open Source License.


Copyright 2005-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_matrix_h
#define n2a_matrix_h


#include "pointer.h"

#include <vector>
#include <map>
#include <memory>
#ifndef N2A_SPINNAKER
# include <iostream>
# include <sstream>
#endif

#include "shared.h"

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
class SHARED MatrixAbstract
{
public:
    virtual ~MatrixAbstract () = 0;
    virtual uint32_t classID () const = 0;  ///< @return A bitvector indicating all the classes to which this object can be cast.  Hack to avoid the cost of dynamic_cast.

    virtual T   get         (const int row, const int column = 0) const = 0;  ///< Safe element accesss. Returns 0 if indices are out of range.
    virtual T & operator () (const int row, const int column = 0) const = 0;  ///< Raw element accesss. No range checking. More efficient.
    virtual int rows        () const = 0;
    virtual int columns     () const = 0;
};

template<class T> class SHARED Matrix;

template<class T> SHARED void      clear      (const MatrixAbstract<T> & A, const T scalar = (T) 0);      ///< Set all elements to given value. const on A is a lie to allow transient matrix regions.
template<class T> SHARED void      identity   (const MatrixAbstract<T> & A);                              ///< Set diagonal to 1 and all other elements to 0. Does not have to be a square matrix. const on A is a lie to allow transient matrix regions.
template<class T> SHARED void      copy       (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B); ///< Copy overlap region from B into A. const on A is a lie to allow transient matrix regions.
template<class T> SHARED T         norm       (const MatrixAbstract<T> & A, T n = (T) 2);                 ///< (sum_elements (element^n))^(1/n). Effectively: INFINITY is max, 1 is sum, 2 is standard Frobenius norm. n==0 is technically undefined, but we treat is as the count of non-zero elements.
template<class T> SHARED T         sumSquares (const MatrixAbstract<T> & A);                              ///< Equivalent to norm(A,2)^2, but without taking the square root.
template<class T> SHARED Matrix<T> normalize  (const MatrixAbstract<T> & A);                              ///< shortcut for A / norm (A, 2)
template<class T> SHARED Matrix<T> cross      (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED Matrix<T> visit      (const MatrixAbstract<T> & A, T (*function) (const T &));   ///< Apply function() to each element, and return the results in a new Matrix of equal size.
template<class T> SHARED Matrix<T> visit      (const MatrixAbstract<T> & A, T (*function) (const T));     ///< Apply function() to each element, and return the results in a new Matrix of equal size.
template<class T> SHARED bool      equal      (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B); ///< Checks that matrices are exactly the same shape and have exactly the same element values.

// Elementwise logical operators
template<class T> SHARED Matrix<T> operator == (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED Matrix<T> operator == (const MatrixAbstract<T> & A, const T scalar);
template<class T>        Matrix<T> operator == (const T scalar,              const MatrixAbstract<T> & A) {return A == scalar;}
template<class T> SHARED Matrix<T> operator != (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED Matrix<T> operator != (const MatrixAbstract<T> & A, const T scalar);
template<class T>        Matrix<T> operator != (const T scalar,              const MatrixAbstract<T> & A) {return A != scalar;}
template<class T> SHARED Matrix<T> operator <  (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED Matrix<T> operator <  (const MatrixAbstract<T> & A, const T scalar);
template<class T>        Matrix<T> operator <  (const T scalar,              const MatrixAbstract<T> & A) {return A > scalar;}
template<class T> SHARED Matrix<T> operator <= (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED Matrix<T> operator <= (const MatrixAbstract<T> & A, const T scalar);
template<class T>        Matrix<T> operator <= (const T scalar,              const MatrixAbstract<T> & A) {return A >= scalar;}
template<class T> SHARED Matrix<T> operator >  (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED Matrix<T> operator >  (const MatrixAbstract<T> & A, const T scalar);
template<class T>        Matrix<T> operator >  (const T scalar,              const MatrixAbstract<T> & A) {return A < scalar;}
template<class T> SHARED Matrix<T> operator >= (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED Matrix<T> operator >= (const MatrixAbstract<T> & A, const T scalar);
template<class T>        Matrix<T> operator >= (const T scalar,              const MatrixAbstract<T> & A) {return A <= scalar;}
template<class T> SHARED Matrix<T> operator && (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED Matrix<T> operator && (const MatrixAbstract<T> & A, const T scalar);
template<class T>        Matrix<T> operator && (const T scalar,              const MatrixAbstract<T> & A) {return A && scalar;}
template<class T> SHARED Matrix<T> operator || (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED Matrix<T> operator || (const MatrixAbstract<T> & A, const T scalar);
template<class T>        Matrix<T> operator || (const T scalar,              const MatrixAbstract<T> & A) {return A || scalar;}

template<class T> SHARED Matrix<T> operator & (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B); ///< Elementwise multiplication. The prettiest name for this operator would be ".*", but that is not overloadable.
template<class T>        Matrix<T> operator * (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B) {return Matrix<T> (A) * B;} ///< Multiply whole matrices
template<class T> SHARED Matrix<T> operator * (const MatrixAbstract<T> & A, const T scalar);              ///< Multiply each element by scalar
template<class T>        Matrix<T> operator * (const T scalar,              const MatrixAbstract<T> & A) {return A * scalar;}
template<class T> SHARED Matrix<T> operator / (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B); ///< Elementwise division.  Could mean this * !B, but such expressions are done other ways in linear algebra.
template<class T> SHARED Matrix<T> operator / (const MatrixAbstract<T> & A, const T scalar);              ///< Divide each element by scalar
template<class T> SHARED Matrix<T> operator / (const T scalar,              const MatrixAbstract<T> & A);
template<class T> SHARED Matrix<T> operator + (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B); ///< Elementwise sum.
template<class T> SHARED Matrix<T> operator + (const MatrixAbstract<T> & A, const T scalar);              ///< Add scalar to each element
template<class T>        Matrix<T> operator + (const T scalar,              const MatrixAbstract<T> & A) {return A + scalar;}
template<class T> SHARED Matrix<T> operator - (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B); ///< Elementwise difference.
template<class T> SHARED Matrix<T> operator - (const MatrixAbstract<T> & A, const T scalar);              ///< Subtract scalar from each element
template<class T> SHARED Matrix<T> operator - (const T scalar,              const MatrixAbstract<T> & A);
template<class T>        Matrix<T> operator - (const MatrixAbstract<T> & A) {return A * (T) -1;}          ///< Unary minus, that is, negation

template<class T> SHARED void operator *= (MatrixAbstract<T> & A, const MatrixAbstract<T> & B);  ///< As a hack to make C code generation simpler, this does elementwise multiply rather than whole-matrix multiply.
template<class T> SHARED void operator *= (MatrixAbstract<T> & A, const T scalar);
template<class T> SHARED void operator /= (MatrixAbstract<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED void operator /= (MatrixAbstract<T> & A, const T scalar);
template<class T> SHARED void operator += (MatrixAbstract<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED void operator += (MatrixAbstract<T> & A, const T scalar);
template<class T> SHARED void operator -= (MatrixAbstract<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED void operator -= (MatrixAbstract<T> & A, const T scalar);

template<class T> SHARED Matrix<T> min (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED Matrix<T> min (const MatrixAbstract<T> & A, const T scalar);
template<class T>        Matrix<T> min (const T scalar,              const MatrixAbstract<T> & A) {return min (A, scalar);}
template<class T> SHARED Matrix<T> max (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED Matrix<T> max (const MatrixAbstract<T> & A, const T scalar);
template<class T>        Matrix<T> max (const T scalar,              const MatrixAbstract<T> & A) {return max (A, scalar);}

#ifndef N2A_SPINNAKER
template<class T> SHARED std::ostream & operator << (std::ostream & stream, const MatrixAbstract<T> & A);  ///< Print human readable matrix to stream.
#endif

#ifdef n2a_FP
SHARED Matrix<int> shift (const MatrixAbstract<int> & A, int shift);  // Defined in fixedpoint.cc
#endif


// Concrete matrices  -------------------------------------------------------

/**
    Provides several kinds of view specifically for the Matrix class.
    Handles these efficiently by using special combinations of start
    address and row and column stride.
**/
template<class T>
class SHARED MatrixStrided : public MatrixAbstract<T>
{
public:
    virtual T * base    () const = 0; ///< Address of first element.
    virtual int strideR () const = 0; ///< Number of elements between start of each row in memory.
    virtual int strideC () const = 0; ///< Number of elements between start of each column in memory. Equivalent to "leading dimension" in LAPACK parlance.
};

template<class T> SHARED void      clear (      MatrixStrided<T> & A, const T scalar = (T) 0);
template<class T> SHARED T         norm  (const MatrixStrided<T> & A, T n);  // For fixed-point, used alternate function declared below.
template<class T> SHARED Matrix<T> visit (const MatrixStrided<T> & A, T (*function) (const T &));
template<class T> SHARED Matrix<T> visit (const MatrixStrided<T> & A, T (*function) (const T));

template<class T> SHARED Matrix<T> operator & (const MatrixStrided<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED Matrix<T> operator * (const MatrixStrided<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED Matrix<T> operator * (const MatrixStrided<T> & A, const T scalar);
template<class T>        Matrix<T> operator * (const T scalar,             const MatrixStrided<T> & A) {return A * scalar;}
template<class T> SHARED Matrix<T> operator / (const MatrixStrided<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED Matrix<T> operator / (const MatrixStrided<T> & A, const T scalar);
template<class T> SHARED Matrix<T> operator / (const T scalar,             const MatrixStrided<T> & A);
template<class T> SHARED Matrix<T> operator + (const MatrixStrided<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED Matrix<T> operator + (const MatrixStrided<T> & A, const T scalar);
template<class T>        Matrix<T> operator + (const T scalar,             const MatrixStrided<T> & A) {return A + scalar;}
template<class T> SHARED Matrix<T> operator - (const MatrixStrided<T> & A, const MatrixAbstract<T> & B);
template<class T> SHARED Matrix<T> operator - (const MatrixStrided<T> & A, const T scalar);
template<class T> SHARED Matrix<T> operator - (const T scalar,             const MatrixStrided<T> & A);

#ifdef n2a_FP
// Fixed-point operations on MatrixStrided<int>
// These are not templates. Their implementations are in fixedpoint.cc
SHARED void        identity            (const MatrixStrided<int> & A, int one);  // "one" is passed explicitly, already scaled with the right exponent
SHARED int         norm                (const MatrixStrided<int> & A, int n, int exponentA, int exponentResult); // exponentN=-MSB/2
SHARED Matrix<int> normalize           (const MatrixStrided<int> & A,        int exponentA);                     // result has exponent=-MSB
SHARED Matrix<int> cross               (const MatrixStrided<int> & A, const MatrixStrided<int> & B, int shift);

SHARED Matrix<int> visit               (const MatrixStrided<int> & A, int (*function) (int, int),      int exponent1);
SHARED Matrix<int> visit               (const MatrixStrided<int> & A, int (*function) (int, int, int), int exponent1, int exponent2);

SHARED Matrix<int> multiplyElementwise (const MatrixStrided<int> & A, const MatrixStrided<int> & B, int shift);  // Don't know the exponent of B, so missing elements in B produce a 0 in the result.
SHARED Matrix<int> multiply            (const MatrixStrided<int> & A, const MatrixStrided<int> & B, int shift);  // "shift" is always down-shift, and is always positive or zero
SHARED Matrix<int> multiply            (const MatrixStrided<int> & A, int b,                        int shift);

SHARED Matrix<int> divide              (const MatrixStrided<int> & A, const MatrixStrided<int> & B, int shift);  // Don't know the exponent of B, so missing elements in B produce a 0 in the result.
SHARED Matrix<int> divide              (const MatrixStrided<int> & A, int b,                        int shift);  // "shift" is always up-shift, and is always positive or zero
SHARED Matrix<int> divide              (int a,                        const MatrixStrided<int> & B, int shift);
#endif

template<class T>
class SHARED Matrix : public MatrixStrided<T>
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
    Matrix (const Matrix<T> & that);
    Matrix (const n2a::Pointer & that, const int offset, const int rows, const int columns, const int strideR, const int strideC);
    virtual uint32_t classID () const;

    template<class T2>
    Matrix (const MatrixAbstract<T2> & that)
    {
        int h = that.rows ();
        int w = that.columns ();
        resize (h, w);
        T * i = (T *) this->data;
        for (int c = 0; c < w; c++)
        {
            for (int r = 0; r < h; r++)
            {
                *i++ = (T) that(r,c);
            }
        }
    }

    void resize (const int rows, const int columns = 1);  ///< Subroutine of constructors.

    virtual T get (const int row, const int column = 0) const
    {
        if (row < 0     ||  row    >= rows_   ) return (T) 0;
        if (column < 0  ||  column >= columns_) return (T) 0;
        return ((T *) data)[offset + column * strideC_ + row * strideR_];
    }
    virtual T & operator () (const int row, const int column = 0) const
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

template<class T> SHARED Matrix<T> operator ~ (const Matrix<T> & A);
template<class T> SHARED Matrix<T> row        (const Matrix<T> & A, int row);
template<class T> SHARED Matrix<T> column     (const Matrix<T> & A, int column);

// MatrixFixed and its associated functions are not SHARED, because there's no
// way to predict which instantiations will be used by which modules.
template<class T, int R, int C>
class MatrixFixed : public MatrixStrided<T>
{
public:
    T data[C][R];

    MatrixFixed ();
    MatrixFixed (std::initializer_list<T> elements);  ///< In column-major order
    virtual uint32_t classID () const;

    template<class T2>
    MatrixFixed (const MatrixAbstract<T2> & that)
    {
        int h = std::min (R, that.rows ());
        int w = std::min (C, that.columns ());
        for (int c = 0; c < w; c++)
        {
            for (int r = 0; r < h; r++) data[c][r] = (T) that(r,c);
            for (int r = h; r < R; r++) data[c][r] = (T) 0;
        }
        for (int c = w; c < C; c++)
        {
            for (int r = 0; r < R; r++) data[c][r] = (T) 0;
        }
    }

    virtual T get (const int row, const int column = 0) const
    {
        if (row < 0     ||  row    >= R) return (T) 0;
        if (column < 0  ||  column >= C) return (T) 0;
        return data[column][row];
    }
    virtual T & operator () (const int row, const int column = 0) const
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

template<class T, int R, int C> void operator *= (MatrixFixed<T,R,C> & A, const T scalar);

// Fixed-point operations on MatrixFixed<int,R,C>
// Since these are templates, their implementations are in MatrixFixed.tcc
#ifdef n2a_FP
template<int R, int C>        MatrixFixed<int,R,C> shift               (const MatrixFixed<int,R,C> & A,                                 int shift);

template<int R, int C>        MatrixFixed<int,R,C> multiplyElementwise (const MatrixFixed<int,R,C> & A, const MatrixFixed<int,R,C> & B, int shift);
template<int R, int C, int O> MatrixFixed<int,R,C> multiply            (const MatrixFixed<int,R,O> & A, const MatrixFixed<int,O,C> & B, int shift);
template<int R, int C>        MatrixFixed<int,R,C> multiply            (const MatrixFixed<int,R,C> & A, int b,                          int shift);

template<int R, int C>        MatrixFixed<int,R,C> divide              (const MatrixFixed<int,R,C> & A, const MatrixFixed<int,R,C> & B, int shift);
template<int R, int C>        MatrixFixed<int,R,C> divide              (const MatrixFixed<int,R,C> & A, int b,                          int shift);
template<int R, int C>        MatrixFixed<int,R,C> divide              (int a,                          const MatrixFixed<int,R,C> & B, int shift);
#endif

/**
    Stores only nonzero elements.  Assumes that every column has at least
    one non-zero entry, so stores a structure for every column.  This is
    a trade-off between time and space (as always).  If the matrix is
    extremely sparse (not all columns used), then a sparse structure for
    holding the column structures would be better.
**/
template<class T>
class SHARED MatrixSparse : public MatrixAbstract<T>
{
public:
    int rows_;
    std::shared_ptr<std::vector<std::map<int,T>>> data;

    MatrixSparse ();
    MatrixSparse (const int rows, const int columns);
    MatrixSparse (const MatrixAbstract<T> & that);
    virtual uint32_t classID () const;

    void        set         (const int row, const int column, const T value);  ///< If value is non-zero, creates element if not already there; if value is zero, removes element if it exists.
    virtual T   get         (const int row, const int column = 0) const {return operator() (row, column);}
    virtual T & operator () (const int row, const int column = 0) const;       ///< If element does not exist, this returns a dummy element. Assigning to it will have no effect. Elements must be created with set().
    virtual int rows        () const;
    virtual int columns     () const;
};


#endif

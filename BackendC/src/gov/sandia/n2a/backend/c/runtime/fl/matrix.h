/*
Author: Fred Rothganger
Copyright (c) 2001-2004 Dept. of Computer Science and Beckman Institute,
                        Univ. of Illinois.  All rights reserved.
Distributed under the UIUC/NCSA Open Source License.  See the file LICENSE
for details.


Copyright 2005, 2009, 2010 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the GNU Lesser General Public License.  See the file LICENSE
for details.
*/


#ifndef fl_matrix_h
#define fl_matrix_h


#include "fl/math.h"
#include "fl/pointer.h"
#include "fl/archive.h"

#include <iostream>
#include <sstream>
#include <vector>
#include <map>
#include <complex>

#undef SHARED
#ifdef _MSC_VER
#  ifdef flNumeric_EXPORTS
#    define SHARED __declspec(dllexport)
#  else
#    define SHARED __declspec(dllimport)
#  endif
#else
#  define SHARED
#endif


/**
   @file
   The linear algebra package has the following goals:
   <ul>
   <li>Be simple and straightforward for a programmer to use. It should be
   easy to express most common linear algebra calculations using
   overloaded operators.
   <li>Work seamlessly with LAPACK. To this end, storage is always column
   major.
   <li>Be lightweight to compile. We avoid putting much implementation in
   the headers, even though we are using templates.
   <li>Be lightweight at run-time.  Eg: shallow copy semantics, and only a
   couple of variables that need to be copied.  Should limit total copying
   to 16 bytes or less.
   </ul>

   In general, the implementation does not protect you from shooting yourself
   in the foot. Specifically, there is no range checking or verification
   that memory addresses are valid. All these do is make a bug easier to
   find (rather than eliminate it), and they cost at runtime. In cases
   where there is some legitimate interpretation of bizarre parameter values,
   we assume the programmer meant that interpretation and plow on.
**/


namespace fl
{
  // Forward declarations
  template<class T> class MatrixResult;
  template<class T> class Matrix;
  template<class T> class MatrixTranspose;
  template<class T> class MatrixRegion;
  template<class T> class MatrixStrided;

  // Matrix class ID constants
  // This is a hack to avoid the cost of dynamic_cast.
  #define MatrixAbstractID  0x001
  #define MatrixResultID    0x002
  #define MatrixStridedID   0x004
  #define MatrixID          0x008
  #define MatrixPackedID    0x010
  #define MatrixSparseID    0x020
  #define MatrixIdentityID  0x040
  #define MatrixDiagonalID  0x080
  #define MatrixFixedID     0x100
  #define MatrixBlockID     0x200


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
	virtual ~MatrixAbstract ();
	virtual uint32_t classID () const;  ///< @return A bitvector indicating all the classes to which this object can be cast.  Hack to avoid the cost of dynamic_cast.

	/**
	   Make a new instance of self on the heap.
	   Used for views.  Since this is class sensitive, it must be overridden.
	   @param deep Indicates that all levels of associated data must be
	   duplicated.  If false, then only the main object needs to be
	   duplicated (shallow copy), but deep copy is still permitted.
	**/
	virtual MatrixAbstract * clone (bool deep = false) const = 0;
	/**
	   Copy data from another matrix.
	   @param deep Indicates that all levels of associated data must be
	   duplicated.  There are some exceptions to this for views.
	   If false, then the copy is equivalent to operator =.
	   Generally that is a shallow copy, but each class is free to determine
	   semantics, and those semantics may vary depending on the class of the
	   source matrix.
	**/
	virtual void copyFrom (const MatrixAbstract<T> & that, bool deep = true);

	// Structural functions
	// These are the core functions in terms of which most other functions can
	// be implemented.  To some degree, they abstract away the actual storage
	// structure of the matrix.
	virtual T &  operator () (const int row, const int column) const = 0;  ///< Element accesss
	virtual T &  operator [] (const int row) const;  ///< Element access, treating us as a vector.
	virtual int  rows        () const;
	virtual int  columns     () const;
	virtual void resize      (const int rows, const int columns = 1) = 0;  ///< Change number of rows and columns.  Does not preserve data.

	// Higher level functions
	virtual void            clear           (const T scalar = (T) 0);  ///< Set all elements to given value.
	virtual T               norm            (float n) const;  ///< Generalized Frobenius norm: (sum_elements (element^n))^(1/n).  Effectively: INFINITY is max, 1 is sum, 2 is standard Frobenius norm.  n==0 is technically undefined, but we treat is as the count of non-zero elements.
	virtual T               sumSquares      () const;  ///< Similar to norm(2), but without taking the square root.
	virtual MatrixResult<T> transposeSquare () const;  ///< Returns the upper-triangular part of the symmetric matrix (~this * this).  Lower-triangular part is undefined.
	virtual MatrixResult<T> transposeTimes  (const MatrixAbstract & B) const;  ///< Return full result of ~this * B
	virtual void            normalize       (const T scalar = 1.0);  ///< View matrix as vector and adjust so norm (2) == scalar.
	virtual MatrixResult<T> visit           (T (*function) (const T &)) const;  ///< Apply function() to each of our elements, and return the results in a new Matrix of equal size.
	virtual MatrixResult<T> visit           (T (*function) (const T)) const;  ///< Apply function() to each of our elements, and return the results in a new Matrix of equal size.
	virtual T               dot             (const MatrixAbstract & B) const;  ///< Return the dot product of the first columns of the respective matrices.
	virtual void            identity        (const T scalar = 1.0);  ///< Set main diagonal to scalar and everything else to zero.
	virtual MatrixResult<T> row             (const int r) const;  ///< Returns a view row r.  The matrix is oriented "horizontal".
	virtual MatrixResult<T> column          (const int c) const;  ///< Returns a view of column c.
	virtual MatrixResult<T> region          (const int firstRow = 0, const int firstColumn = 0, int lastRow = -1, int lastColumn = -1) const;  ///< Same as call to MatrixRegion<T> (*this, firstRow, firstColumn, lastRow, lastColumn)
	const char *            toString        (std::string & buffer) const;  ///< Convenience funtion.  Same output as operator <<

	// Basic operations

	virtual bool operator == (const MatrixAbstract & B) const;  ///< Two matrices are equal if they have the same shape and the same elements.
	bool operator != (const MatrixAbstract & B) const
	{
	  return ! ((*this) == B);
	}

	virtual MatrixResult<T> operator ! () const;  ///< Invert matrix if square, otherwise create pseudo-inverse.
	virtual MatrixResult<T> operator ~ () const;  ///< Transpose matrix.

	virtual MatrixResult<T> operator ^ (const MatrixAbstract & B) const;  ///< View both matrices as vectors and return cross product.  (Is there a better definition that covers 2D matrices?)
	virtual MatrixResult<T> operator & (const MatrixAbstract & B) const;  ///< Elementwise multiplication.  The prettiest name for this operator would be ".*", but that is not overloadable.
	virtual MatrixResult<T> operator * (const MatrixAbstract & B) const;  ///< Multiply matrices: this * B
	virtual MatrixResult<T> operator * (const T scalar)           const;  ///< Multiply each element by scalar
	virtual MatrixResult<T> operator / (const MatrixAbstract & B) const;  ///< Elementwise division.  Could mean this * !B, but such expressions are done other ways in linear algebra.
	virtual MatrixResult<T> operator / (const T scalar)           const;  ///< Divide each element by scalar
	virtual MatrixResult<T> operator + (const MatrixAbstract & B) const;  ///< Elementwise sum.
	virtual MatrixResult<T> operator + (const T scalar)           const;  ///< Add scalar to each element
	virtual MatrixResult<T> operator - (const MatrixAbstract & B) const;  ///< Elementwise difference.
	virtual MatrixResult<T> operator - (const T scalar)           const;  ///< Subtract scalar from each element

	virtual MatrixAbstract & operator ^= (const MatrixAbstract & B);  ///< View both matrices as vectors and compute cross product, stored back to this
	virtual MatrixAbstract & operator &= (const MatrixAbstract & B);  ///< Elementwise multiply, stored back to this
	virtual MatrixAbstract & operator *= (const MatrixAbstract & B);  ///< Standard matrix multiplication, stored back to this
	virtual MatrixAbstract & operator *= (const T scalar);            ///< Multiply each element by scalar
	virtual MatrixAbstract & operator /= (const MatrixAbstract & B);  ///< Elementwise divide and store back to this
	virtual MatrixAbstract & operator /= (const T scalar);            ///< Divide each element by scalar
	virtual MatrixAbstract & operator += (const MatrixAbstract & B);  ///< Elementwise sum, stored back to this
	virtual MatrixAbstract & operator += (const T scalar);            ///< Increase each element by scalar
	virtual MatrixAbstract & operator -= (const MatrixAbstract & B);  ///< Elementwise difference, stored back to this
	virtual MatrixAbstract & operator -= (const T scalar);            ///< Decrease each element by scalar

	// Serialization
	void serialize (Archive & archive, uint32_t version);
	static uint32_t serializeVersion;

	// Global Data
	static int displayWidth;  ///< Number of character positions per cell to use when printing out matrix.
	static int displayPrecision;  ///< Number of significant digits to output.
  };

  template<class T>
  MatrixResult<T> operator * (const T scalar, const MatrixAbstract<T> & A)
  {
	return A * scalar;
  }

  template<class T>
  MatrixResult<T> operator - (const MatrixAbstract<T> & A)
  {
	return A * (T) -1;
  }

  template<class T>
  SHARED std::string elementToString (const T & value);

  template<class T>
  SHARED T elementFromString (const std::string & value);

  /**
	 Print human readable matrix to stream.  Formatted to be readable by
	 operator >> (istream, Matrix).
  **/
  template<class T>
  SHARED std::ostream &
  operator << (std::ostream & stream, const MatrixAbstract<T> & A);

  /**
	 Load human-readable matrix from stream.  Format rules:
	 <ul>
	 <li>All matrices begin with "[" and end with "]".  Everything before the
	 first "[" is ignored.  However, if a "~" occurs anywhere before the
	 first "[", then the matrix is transposed.
	 <li>Rows end with a LF character or a ";" (or both).
	 <li>The number of columns equals the longest row.
	 <li>Rows containing less than the full number of columns will be filled
	 out with zeros.
	 <li>All characters between a "#" and a LF character are ignored.
	 <li>Empty lines are ignored.  Equivalently, rows containing no elements
	 are ignored.  Note that the value zero counts as an element, so a row of
	 zeros can be created by simply putting a "0" on a line by itself.
	 </ul>
  **/
  template<class T>
  SHARED std::istream &
  operator >> (std::istream & stream, MatrixAbstract<T> & A);

  /**
	 Load human-readable matrix from string.  Follows same rules as
	 operator >> (istream, Matrix).
	 @todo This function may prove to be unecessay, given the existence of
	 Matrix<T>::Matrix(const string &).
  **/
  template<class T>
  SHARED MatrixAbstract<T> &
  operator << (MatrixAbstract<T> & A, const std::string & source);

  /**
	 An adapter that allows a function to return a matrix created on the heap
	 and guarantee that it will be destroyed in the calling function.
   **/
  template<class T>
  class MatrixResult : public MatrixAbstract<T>
  {
  public:
	MatrixResult (MatrixAbstract<T> * result) : result (result)                {}
	MatrixResult (const MatrixResult<T> & that) : result (that.result)         {const_cast<MatrixResult &> (that).result = 0;}
	virtual ~MatrixResult ()                                                   {if (result) delete result;}
	virtual uint32_t classID () const                                          {return MatrixAbstractID | MatrixResultID;}

	operator MatrixAbstract<T> & () const                                      {return *result;}
	MatrixAbstract<T> * relinquish ()
	{
	  MatrixAbstract<T> * temp = result;
	  result = 0;
	  return temp;
	}

	virtual MatrixAbstract<T> * clone (bool deep = false) const                {return result->clone (deep);}
	virtual void copyFrom (const MatrixAbstract<T> & that, bool deep = true)   {       result->copyFrom (that, deep);}
	MatrixAbstract<T> & operator = (const MatrixResult & that)                 {       result->copyFrom (*that.result, true); return *result;}
	MatrixAbstract<T> & operator = (const MatrixAbstract<T> & that)            {       result->copyFrom (that,         true); return *result;}
	template<class T2>
	MatrixAbstract<T> & operator = (const MatrixAbstract<T2> & that)
	{
	  int h = that.rows ();
	  int w = that.columns ();
	  result->resize (h, w);
	  for (int c = 0; c < w; c++)
	  {
		for (int r = 0; r < h; r++)
		{
		  (*result)(r,c) = (T) that(r,c);
		}
	  }
	  return *result;
	}

	virtual T & operator () (const int row, const int column) const            {return (*result)(row,column);}
	virtual T & operator [] (const int row) const                              {return (*result)[row];}
	virtual int rows () const                                                  {return result->rows ();}
	virtual int columns () const                                               {return result->columns ();}
	virtual void resize (const int rows, const int columns = 1)                {       result->resize (rows, columns);}

	virtual void clear (const T scalar = (T) 0)                                {       result->clear (scalar);}
	virtual T norm (float n) const                                             {return result->norm (n);}
	virtual T sumSquares () const                                              {return result->sumSquares ();}
	virtual MatrixResult<T> transposeSquare () const                           {return result->transposeSquare ();}
	virtual MatrixResult<T> transposeTimes (const MatrixAbstract<T> & B) const {return result->transposeTimes  (B);}
	virtual void normalize (const T scalar = 1.0)                              {       result->normalize (scalar);}
	virtual MatrixResult<T> visit (T (*function) (const T &)) const            {return result->visit (function);}
	virtual MatrixResult<T> visit (T (*function) (const T)) const              {return result->visit (function);}
	virtual T dot (const MatrixAbstract<T> & B) const                          {return result->dot (B);}
	virtual void identity (const T scalar = 1.0)                               {       result->identity (scalar);}
	virtual MatrixResult<T> row (const int r) const                            {return result->row (r);}
	virtual MatrixResult<T> column (const int c) const                         {return result->column (c);}
	virtual MatrixResult<T> region (const int firstRow = 0, const int firstColumn = 0, int lastRow = -1, int lastColumn = -1) const {return result->region (firstRow, firstColumn, lastRow, lastColumn);}

	virtual MatrixResult<T> operator ! () const                                {return !(*result);}
	virtual MatrixResult<T> operator ~ () const                                {return ~(*result);}

	virtual MatrixResult<T> operator ^ (const MatrixAbstract<T> & B) const     {return *result ^ B;}
	virtual MatrixResult<T> operator & (const MatrixAbstract<T> & B) const     {return *result & B;}
	virtual MatrixResult<T> operator * (const MatrixAbstract<T> & B) const     {return *result * B;}
	virtual MatrixResult<T> operator * (const T scalar) const                  {return *result * scalar;}
	virtual MatrixResult<T> operator / (const MatrixAbstract<T> & B) const     {return *result / B;}
	virtual MatrixResult<T> operator / (const T scalar) const                  {return *result / scalar;}
	virtual MatrixResult<T> operator + (const MatrixAbstract<T> & B) const     {return *result + B;}
	virtual MatrixResult<T> operator + (const T scalar) const                  {return *result + scalar;}
	virtual MatrixResult<T> operator - (const MatrixAbstract<T> & B) const     {return *result - B;}
	virtual MatrixResult<T> operator - (const T scalar) const                  {return *result - scalar;}

	virtual MatrixAbstract<T> & operator ^= (const MatrixAbstract<T> & B)      {return *result ^= B;}
	virtual MatrixAbstract<T> & operator &= (const MatrixAbstract<T> & B)      {return *result &= B;}
	virtual MatrixAbstract<T> & operator *= (const MatrixAbstract<T> & B)      {return *result *= B;}
	virtual MatrixAbstract<T> & operator *= (const T scalar)                   {return *result *= scalar;}
	virtual MatrixAbstract<T> & operator /= (const MatrixAbstract<T> & B)      {return *result /= B;}
	virtual MatrixAbstract<T> & operator /= (const T scalar)                   {return *result /= scalar;}
	virtual MatrixAbstract<T> & operator += (const MatrixAbstract<T> & B)      {return *result += B;}
	virtual MatrixAbstract<T> & operator += (const T scalar)                   {return *result += scalar;}
	virtual MatrixAbstract<T> & operator -= (const MatrixAbstract<T> & B)      {return *result -= B;}
	virtual MatrixAbstract<T> & operator -= (const T scalar)                   {return *result -= scalar;}

	void serialize (Archive & archive, uint32_t version)                       {throw "Attempt to serialize a MatrixResult";}

	MatrixAbstract<T> * result;  ///< We always take responsibility for destroying "result".
  };


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
  class SHARED MatrixStrided : public MatrixAbstract<T>
  {
  public:
	MatrixStrided ();  ///< Mainly for the convenience of Matrix constructors.  Generally, you will not directly construct an instance of this class.
	MatrixStrided (const MatrixAbstract<T> & that);
	template<class T2>
	MatrixStrided (const MatrixAbstract<T2> & that)
	{
	  int h = that.rows ();
	  int w = that.columns ();

	  // Equivalent to Matrix::resize(h,w).  We can't use our own resize(),
	  // because it does not actually allocate memory.
	  data.grow (w * h * sizeof (T));
	  offset   = 0;
	  rows_    = h;
	  columns_ = w;
	  strideR  = 1;
	  strideC  = h;

	  T * i = (T *) data;
	  for (int c = 0; c < w; c++)
	  {
		for (int r = 0; r < h; r++)
		{
		  *i++ = (T) that(r,c);
		}
	  }
	}
	MatrixStrided (const Pointer & that, const int offset, const int rows, const int columns, const int strideR, const int strideC);
	void detach ();  ///< Set the state of this matrix as if it has no data.  Releases (but only frees if appropriate) any memory.
	virtual uint32_t classID () const;

	virtual MatrixAbstract<T> * clone (bool deep = false) const;
	virtual void copyFrom (const MatrixAbstract<T> & that, bool deep = true);

	virtual T & operator () (const int row, const int column) const
	{
	  return ((T *) this->data)[offset + column * this->strideC + row * this->strideR];
	}
	/**
	   Guarantees correctness only for the first column, unless rows() == strideC.
	 **/
	virtual T & operator [] (const int row) const
	{
	  return ((T *) this->data)[offset + row * strideR];
	}
	virtual int rows () const;
	virtual int columns () const;
	virtual void resize (const int rows, const int columns = 1);  ///< Always sets strideC = rows.

	virtual void clear (const T scalar = (T) 0);
	virtual T norm (float n) const;
	virtual T sumSquares () const;
	virtual MatrixResult<T> transposeSquare () const;
	virtual MatrixResult<T> visit (T (*function) (const T &)) const;
	virtual MatrixResult<T> visit (T (*function) (const T)) const;
	virtual T dot (const MatrixAbstract<T> & B) const;
	virtual MatrixResult<T> row (const int r) const;
	virtual MatrixResult<T> column (const int c) const;
	virtual MatrixResult<T> region (const int firstRow = 0, const int firstColumn = 0, int lastRow = -1, int lastColumn = -1) const;

	virtual MatrixResult<T> operator ~ () const;

	virtual MatrixResult<T> operator & (const MatrixAbstract<T> & B) const;
	virtual MatrixResult<T> operator * (const MatrixAbstract<T> & B) const;
	virtual MatrixResult<T> operator * (const T scalar) const;
	virtual MatrixResult<T> operator / (const MatrixAbstract<T> & B) const;
	virtual MatrixResult<T> operator / (const T scalar) const;
	virtual MatrixResult<T> operator + (const MatrixAbstract<T> & B) const;
	virtual MatrixResult<T> operator + (const T scalar) const;
	virtual MatrixResult<T> operator - (const MatrixAbstract<T> & B) const;
	virtual MatrixResult<T> operator - (const T scalar) const;

	virtual MatrixAbstract<T> & operator &= (const MatrixAbstract<T> & B);
	virtual MatrixAbstract<T> & operator *= (const MatrixAbstract<T> & B);
	virtual MatrixAbstract<T> & operator *= (const T scalar);
	virtual MatrixAbstract<T> & operator /= (const MatrixAbstract<T> & B);
	virtual MatrixAbstract<T> & operator /= (const T scalar);
	virtual MatrixAbstract<T> & operator += (const MatrixAbstract<T> & B);
	virtual MatrixAbstract<T> & operator += (const T scalar);
	virtual MatrixAbstract<T> & operator -= (const MatrixAbstract<T> & B);
	virtual MatrixAbstract<T> & operator -= (const T scalar);

	// Operators on a different type.  These are syntactic sugar for
	// typecasting the second operand.
	template<class T2> MatrixResult<T> operator * (const MatrixAbstract<T2> & B) const {return operator * ((MatrixStrided<T>) B);}
	template<class T2> MatrixResult<T> operator - (const MatrixAbstract<T2> & B) const {return operator - ((MatrixStrided<T>) B);}

	void serialize (Archive & archive, uint32_t version);

	// Data
	Pointer data;
	int offset;
	int rows_;
	int columns_;
	int strideR;  ///< Number of elements between start of each row in memory.
	int strideC;  ///< Number of elements between start of each column in memory.  Equivalent to "leading dimension" in LAPACK parlance.  Could be in terms of bytes, like PixelBufferPacked::stride, but the need for this is very unlikely, so not worth the programming effort.
  };

  template<class T>
  class SHARED Matrix : public MatrixStrided<T>
  {
  public:
	Matrix ();
	Matrix (const int rows, const int columns = 1);
	Matrix (const MatrixAbstract<T> & that);
	template<class T2> Matrix (const MatrixAbstract<T2> & that) : MatrixStrided<T> (that) {}
	Matrix (const std::string & source);
	Matrix (T * that, const int rows, const int columns = 1);  ///< Attach to memory block pointed to by that
	Matrix (Pointer & that, const int rows = -1, const int columns = 1);  ///< Share memory block with that.  rows == -1 or columns == -1 means infer number from size of memory.  At least one of {rows, columns} must be positive.
	virtual uint32_t classID () const;

	virtual MatrixAbstract<T> * clone (bool deep = false) const;
	virtual void copyFrom (const MatrixAbstract<T> & that, bool deep = true);

	virtual T & operator () (const int row, const int column) const
	{
	  return ((T *) this->data)[column * this->strideC + row];
	}
	virtual T & operator [] (const int row) const
	{
	  return ((T *) this->data)[row];
	}
	virtual void resize (const int rows, const int columns = 1);  ///< Always sets strideC = rows.

	virtual Matrix reshape (const int rows, const int columns = 1, bool inPlace = false) const;

	virtual void clear (const T scalar = (T) 0);
  };

  /**
	 Vector is "syntactic sugar" for a Matrix with only one column.
  **/
  template<class T>
  class SHARED Vector : public Matrix<T>
  {
  public:
	Vector ();
	Vector (const int rows);
	Vector (const MatrixAbstract<T> & that);
	template<class T2> Vector (const MatrixAbstract<T2> & that) : Matrix<T> (that) {this->strideC = this->rows_ = this->rows_ * this->columns_; this->columns_ = 1;}
	Vector (const Matrix<T> & that);
	Vector (const std::string & source);
	Vector (T * that, const int rows);  ///< Attach to memory block pointed to by that
	Vector (Pointer & that, const int rows = -1);  ///< Share memory block with that.  rows == -1 means infer number from size of memory

	virtual void resize (const int rows, const int columns = 1);  ///< Converts all requests to a single column with height of requested rows * requested columns.
  };

  /**
	 This Matrix is presumed to be Symmetric.  It could also be Hermitian
	 or Triangular, but these require more specialization.  The whole point
	 of having this class is to take advantage of symmetry to cut down on
	 memory accesses.

	 For purpose of calls to LAPACK, this matrix stores the upper triangular
	 portion.
  **/
  template<class T>
  class SHARED MatrixPacked : public MatrixAbstract<T>
  {
  public:
	MatrixPacked ();
	MatrixPacked (const int rows);  ///< columns = rows
	MatrixPacked (const MatrixAbstract<T> & that);
	void detach ();  ///< Set the state of this matrix as if it has no data.  Releases any memory.
	virtual uint32_t classID () const;

	virtual MatrixAbstract<T> * clone (bool deep = false) const;
	virtual void copyFrom (const MatrixAbstract<T> & that, bool deep = true);

	virtual T & operator () (const int row, const int column) const;
	virtual T & operator [] (const int row) const;
	virtual int rows () const;
	virtual int columns () const;
	virtual void resize (const int rows, const int columns = -1);

	virtual void clear (const T scalar = (T) 0);

	virtual MatrixResult<T> operator ~ () const;

	void serialize (Archive & archive, uint32_t version);

	// Data
	Pointer data;
	int rows_;  ///< columns = rows
  };

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
	MatrixSparse ();
	MatrixSparse (const int rows, const int columns);
	MatrixSparse (const MatrixAbstract<T> & that);
	virtual ~MatrixSparse ();
	virtual uint32_t classID () const;

	virtual MatrixAbstract<T> * clone (bool deep = false) const;
	virtual void copyFrom (const MatrixAbstract<T> & that, bool deep = true);

	void set (const int row, const int column, const T value);  ///< If value is non-zero, creates element if not already there; if value is zero, removes element if it exists.
	virtual T & operator () (const int row, const int column) const;
	virtual int rows () const;
	virtual int columns () const;
	virtual void resize (const int rows, const int columns = 1);  ///< Changing number of rows has no effect at all.  Changing number of columns resizes column list.

	virtual void clear (const T scalar = (T) 0);  ///< Completely ignore the value of scalar, and simply delete all data.
	virtual T norm (float n) const;
	virtual MatrixResult<T> transposeSquare () const;
	virtual MatrixResult<T> transposeTimes (const MatrixAbstract<T> & B) const;

	virtual MatrixResult<T> operator * (const MatrixAbstract<T> & B) const;
	virtual MatrixResult<T> operator - (const MatrixAbstract<T> & B) const;

	void serialize (Archive & archive, uint32_t version);

	int rows_;
	fl::PointerStruct< std::vector< std::map<int, T> > > data;
  };

  /**
	 A matrix where the elements themselves are matrices. Each element is represented
	 as a pointer to a MatrixAbstract, and can be null. A null entry acts as a block
	 of zeros for the purpose of matrix operations.

	 All matrices that share a block-column must have the same number of columns,
	 and all matrices that share the same block-row must have the same number of rows.
	 In general, the row or column count is the max over all the relevant entries,
	 and in some cases you may get away with having some undersized matrices.
  **/
  template<class T>
  class SHARED MatrixBlock : public MatrixAbstract<T>
  {
  public:
	MatrixBlock ();
	MatrixBlock (const int blockRows, const int blockColumns = 1);
	MatrixBlock (const MatrixAbstract<T> & that);
	virtual ~MatrixBlock ();
	void detach ();  ///< reset to empty and delete all pointers we own
	virtual uint32_t classID () const;

	virtual MatrixAbstract<T> * clone (bool deep = false) const;
	virtual void copyFrom (const MatrixAbstract<T> & that, bool deep = true);
	MatrixAbstract<T> & operator = (const MatrixBlock & that);  ///< Transfers ownership of that contents (in violation of const semantics).

	void                blockSet       (const int blockRow, const int blockColumn,       MatrixAbstract<T> * A);  ///< If A is zero, deletes block. If non-zero, takes ownership of pointer.
	void                blockSet       (const int blockRow, const int blockColumn, const MatrixAbstract<T> & A);  ///< Clones A.
	MatrixAbstract<T> * blockGet       (const int blockRow, const int blockColumn) const;
	void                blockUpdate    (const int blockRow, const int blockColumn);  ///< Maintain row and column counts. Call when a block element has changed size.
	void                blockUpdateAll ();
	int                 blockRows      () const;
	int                 blockColumns   () const;
	void                blockResize    (const int blockRows, const int blockColumns = 1);  ///< Preserves any existing blocks inside new shape. All new elements are set to null.
	void                blockDump      () const;  ///< print a structural trace to stderr

	virtual T & operator () (const int row, const int column) const;  ///< Extremely expensive to look up an entry!
	virtual int rows () const;
	virtual int columns () const;
	virtual void resize (const int rows, const int columns = 1);  ///< On reduction, trims any existing blocks that boundary cuts through. On expansion, extends any existing blocks at perimeter.

	virtual void clear                      (const T scalar = (T) 0);  ///< Calls clear() on all blocks. If scalar is anything besides zero, only clears blocks that exist.
	virtual T norm                          (float n) const;  ///< Calls norm() on all blocks, and combines results using proper norm.
	virtual MatrixResult<T> transposeSquare () const;
	virtual MatrixResult<T> transposeTimes  (const MatrixAbstract<T> & B) const;
	virtual MatrixResult<T> row             (const int r) const;
	virtual MatrixResult<T> column          (const int c) const;

	virtual MatrixResult<T> operator * (const MatrixAbstract<T> & B) const;
	virtual MatrixResult<T> operator / (const T scalar)              const;
	virtual MatrixResult<T> operator - (const MatrixAbstract<T> & B) const;

	void serialize (Archive & archive, uint32_t version);

	std::vector<int> startRows;
	std::vector<int> startColumns;
	int blockStride;
	Pointer data;
  };

  /**
	 A square matrix that always returns the same value for a diagonal
	 element and zero for any other element.
  **/
  template<class T>
  class SHARED MatrixIdentity : public MatrixAbstract<T>
  {
  public:
	MatrixIdentity ();
	MatrixIdentity (int size, T value = 1);
	virtual uint32_t classID () const;

	virtual MatrixAbstract<T> * clone (bool deep = false) const;

	virtual T & operator () (const int row, const int column) const;
	virtual int rows () const;
	virtual int columns () const;
	virtual void resize (const int rows, const int columns = -1);

	virtual void clear (const T scalar = (T) 0);

	int size;
	T value;
  };

  /**
	 A square matrix that only stores values for the diagonal entries
	 and returns zero for any other element.
  **/
  template<class T>
  class SHARED MatrixDiagonal : public MatrixAbstract<T>
  {
  public:
	MatrixDiagonal ();
	MatrixDiagonal (const int rows, const int columns = -1);
	MatrixDiagonal (const Vector<T> & that, const int rows = -1, const int columns = -1);
	virtual uint32_t classID () const;

	virtual MatrixAbstract<T> * clone (bool deep = false) const;

	virtual T & operator () (const int row, const int column) const;
	virtual T & operator [] (const int row) const;
	virtual int rows () const;
	virtual int columns () const;
	virtual void resize (const int rows, const int columns = -1);

	virtual void clear (const T scalar = (T) 0);

	int rows_;
	int columns_;
	Pointer data;
  };


  // Views --------------------------------------------------------------------

  /*
	These matrices wrap other matrices and provide a different interpretation
	of the row and column coordinates.  Views are always "dense" in the sense
	that every element maps to an element in the wrapped Matrix.  However,
	they have no storage of their own.

	Views do two jobs:
	1) Make it convenient to access a Matrix in manners other than dense.
	   E.g.: You could do strided access without having to write out the
	   index offsets and multipliers.
	2) Act as a proxy for some rearrangement of the Matrix in the next
	   stage of calculation in order to avoid copying elements.
	   E.g.: A Transpose view allows one to multiply a transposed Matrix
	   without first moving all the elements to their tranposed positions.

	Because it does not have its own storage, a view depends on the continued
	existence of the wrapped matrix while the view exists.  For this
	reason, one should not generally return a view from a function.
  */

  /// this(i,j) maps to that(j,i)
  template<class T>
  class SHARED MatrixTranspose : public MatrixAbstract<T>
  {
  public:
	MatrixTranspose (MatrixAbstract<T> * that);
	virtual ~MatrixTranspose ();

	virtual MatrixAbstract<T> * clone (bool deep = false) const;

	virtual T & operator () (const int row, const int column) const
	{
	  return (*wrapped)(column,row);
	}
	virtual T & operator [] (const int row) const
	{
	  return (*wrapped)[row];
	}
	virtual int rows () const;
	virtual int columns () const;
	virtual void resize (const int rows, const int columns);

	virtual void clear (const T scalar = (T) 0);

	virtual MatrixResult<T> operator * (const MatrixAbstract<T> & B) const;
	virtual MatrixResult<T> operator * (const T scalar) const;

	// It is the job of the matrix being transposed to make another instance
	// of itself.  It is our responsibility to delete this object when we
	// are destroyed.
	MatrixAbstract<T> * wrapped;
  };

  template<class T>
  class SHARED MatrixRegion : public MatrixAbstract<T>
  {
  public:
	MatrixRegion (const MatrixAbstract<T> & that, const int firstRow = 0, const int firstColumn = 0, int lastRow = -1, int lastColumn = -1);

	virtual MatrixAbstract<T> * clone (bool deep = false) const;
	template<class T2>
	MatrixRegion & operator = (const MatrixAbstract<T2> & that)
	{
	  int h = that.rows ();
	  int w = that.columns ();
	  resize (h, w);
	  for (int c = 0; c < w; c++)
	  {
		for (int r = 0; r < h; r++)
		{
		  (*this)(r,c) = (T) that(r,c);
		}
	  }
	  return *this;
	}
	MatrixRegion & operator = (const MatrixRegion & that);  ///< deep copy semantics, that is, actually copies elements from "that" into wrapped

	virtual T & operator () (const int row, const int column) const
	{
	  return (*wrapped)(row + firstRow, column + firstColumn);
	}
	virtual T & operator [] (const int row) const
	{
	  return (*wrapped)(row % rows_ + firstRow, row / rows_ + firstColumn);
	}
	virtual int rows () const;
	virtual int columns () const;
	virtual void resize (const int rows, const int columns = 1);

	virtual void clear (const T scalar = (T) 0);

	virtual MatrixResult<T> operator * (const MatrixAbstract<T> & B) const;
	virtual MatrixResult<T> operator * (const T scalar) const;

	const MatrixAbstract<T> * wrapped;
	int firstRow;
	int firstColumn;
	int rows_;
	int columns_;
  };


  // Small Matrix classes -----------------------------------------------------

  // There are two reasons for making small Matrix classes.
  // 1) Avoid overhead of managing memory.
  // 2) Certain numerical operations (such as computing eigenvalues) have
  //    direct implementations in small matrix sizes (particularly 2x2).

  template<class T, int R, int C>
  class MatrixFixed : public MatrixAbstract<T>
  {
  public:
	MatrixFixed ();
	template<class T2>
	MatrixFixed (const MatrixAbstract<T2> & that)
	{
	  const int h = std::min (that.rows (),    R);
	  const int w = std::min (that.columns (), C);
	  for (int c = 0; c < w; c++)
	  {
		for (int r = 0; r < h; r++)
		{
		  data[c][r] = (T) that(r,c);
		}
	  }
	}
	virtual uint32_t classID () const;

	virtual MatrixAbstract<T> * clone (bool deep = false) const;
	virtual void copyFrom (const MatrixAbstract<T> & that, bool deep = true);

	virtual T & operator () (const int row, const int column) const
	{
	  return (T &) data[column][row];
	}
	virtual T & operator [] (const int row) const
	{
	  return ((T *) data)[row];
	}
	virtual int rows () const;
	virtual int columns () const;
	virtual void resize (const int rows = R, const int columns = C);
	virtual MatrixResult<T> row (const int r) const;
	virtual MatrixResult<T> column (const int c) const;
	virtual MatrixResult<T> region (const int firstRow = 0, const int firstColumn = 0, int lastRow = -1, int lastColumn = -1) const;

	virtual MatrixResult<T> operator ! () const;
	virtual MatrixResult<T> operator ~ () const;

	virtual MatrixResult<T> operator * (const MatrixAbstract<T> & B) const;
	virtual MatrixResult<T> operator * (const T scalar) const;
	virtual MatrixResult<T> operator / (const T scalar) const;

	virtual MatrixAbstract<T> & operator *= (const T scalar);
	virtual MatrixAbstract<T> & operator /= (const T scalar);

	void serialize (Archive & archive, uint32_t version);

	// Data
	T data[C][R];
  };

  // For MS DLLs, declare that explicit specializations will be available
  // that are exported.
# ifndef flNumeric_MS_EVIL
  extern template class SHARED MatrixFixed<float, 2,2>;
  extern template class SHARED MatrixFixed<float, 3,3>;
  extern template class SHARED MatrixFixed<double,2,2>;
  extern template class SHARED MatrixFixed<double,3,3>;
# endif

  template<class T>
  SHARED T det (const MatrixFixed<T,2,2> & A);

  template<class T>
  SHARED void geev (const MatrixFixed<T,2,2> & A, Matrix<T> & eigenvalues, bool destroyA = false);

  template<class T>
  SHARED void geev (const MatrixFixed<T,2,2> & A, Matrix<T> & eigenvalues, Matrix<T> & eigenvectors, bool destroyA = false);

  template<class T>
  SHARED T det (const MatrixFixed<T,3,3> & A);
}


#endif

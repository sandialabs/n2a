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


#ifndef fl_matrix_tcc
#define fl_matrix_tcc


#include "fl/matrix.h"
#include "fl/string.h"
#include "fl/blasproto.h"

#include <algorithm>
#include <typeinfo>


namespace fl
{
  // class MatrixAbstract<T> --------------------------------------------------

  template<class T>
  int MatrixAbstract<T>::displayWidth = 10;

  template<class T>
  int MatrixAbstract<T>::displayPrecision = 6;

  template<class T>
  uint32_t MatrixAbstract<T>::serializeVersion = 0;

  template<class T>
  MatrixAbstract<T>::~MatrixAbstract ()
  {
  }

  template<class T>
  uint32_t
  MatrixAbstract<T>::classID () const
  {
	return MatrixAbstractID;
  }

  template<class T>
  void
  MatrixAbstract<T>::copyFrom (const MatrixAbstract<T> & that, bool deep)
  {
	int h = that.rows ();
	int w = that.columns ();
	resize (h, w);
	for (int c = 0; c < w; c++)
	{
	  for (int r = 0; r < h; r++)
	  {
		(*this)(r,c) = that(r,c);
	  }
	}
  }

  template<class T>
  T &
  MatrixAbstract<T>::operator [] (const int row) const
  {
	const int h = rows ();
	return (*this) (row / h, row % h);
  }

  template<class T>
  int
  MatrixAbstract<T>::rows () const
  {
	return 1;
  }

  template<class T>
  int
  MatrixAbstract<T>::columns () const
  {
	return 1;
  }

  template<class T>
  void
  MatrixAbstract<T>::clear (const T scalar)
  {
	int h = rows ();
	int w = columns ();
	for (int c = 0; c < w; c++)
	{
	  for (int r = 0; r < h; r++)
	  {
		(*this)(r,c) = scalar;
	  }
	}
  }

  template<class T>
  T
  MatrixAbstract<T>::norm (float n) const
  {
	int h = rows ();
	int w = columns ();
	if (n == INFINITY)
	{
	  T result = (T) 0;
	  for (int c = 0; c < w; c++)
	  {
		for (int r = 0; r < h; r++)
		{
		  result = std::max (std::abs ((*this)(r,c)), result);
		}
	  }
	  return result;
	}
	else if (n == 0.0f)
	{
	  unsigned int result = 0;
	  for (int c = 0; c < w; c++)
	  {
		for (int r = 0; r < h; r++)
		{
		  if ((*this)(r,c) != (T) 0) result++;
		}
	  }
	  return (T) result;
	}
	else if (n == 1.0f)
	{
	  T result = (T) 0;
	  for (int c = 0; c < w; c++)
	  {
		for (int r = 0; r < h; r++)
		{
		  result += (*this) (r, c);
		}
	  }
	  return result;
	}
	else if (n == 2.0f)
	{
	  T result = (T) 0;
	  for (int c = 0; c < w; c++)
	  {
		for (int r = 0; r < h; r++)
		{
		  T t = (*this) (r, c);
		  result += t * t;
		}
	  }
	  return (T) std::sqrt (result);
	}
	else
	{
	  T result = (T) 0;
	  for (int c = 0; c < w; c++)
	  {
		for (int r = 0; r < h; r++)
		{
		  result += (T) std::pow ((*this) (r, c), (T) n);
		}
	  }
	  return (T) std::pow (result, (T) (1.0 / n));
	}
  }

  template<class T>
  T
  MatrixAbstract<T>::sumSquares () const
  {
	int h = rows ();
	int w = columns ();
	T result = 0;
	for (int c = 0; c < w; c++)
	{
	  for (int r = 0; r < h; r++)
	  {
		T t = (*this) (r, c);
		result += t * t;
	  }
	}
	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixAbstract<T>::transposeSquare () const
  {
	int w = columns ();
	Matrix<T> * result = new Matrix<T> (w, w);
	for (int c = 0; c < w; c++)
	{
	  for (int r = 0; r <= c; r++)
	  {
		(*result)(r,c) = this->column (r).dot (this->column (c));
	  }
	}
	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixAbstract<T>::transposeTimes (const MatrixAbstract<T> & B) const
  {
	return ~(*this) * B;
  }

  template<class T>
  void
  MatrixAbstract<T>::normalize (const T scalar)
  {
	T length = norm (2);
	if (length != (T) 0)
	{
	  (*this) /= length;
	  // It is less efficient to separate these operations, but more
	  // numerically stable.
	  if (scalar != (T) 1)
	  {
		(*this) *= scalar;
	  }
	}
  }

  template<class T>
  MatrixResult<T>
  MatrixAbstract<T>::visit (T (*function) (const T &)) const
  {
	return MatrixStrided<T> (*this).visit (function);
  }

  template<class T>
  MatrixResult<T>
  MatrixAbstract<T>::visit (T (*function) (const T)) const
  {
	return MatrixStrided<T> (*this).visit (function);
  }

  template<class T>
  T
  MatrixAbstract<T>::dot (const MatrixAbstract<T> & B) const
  {
	int h = std::min (rows (), B.rows ());
	register T result = (T) 0;
	for (int r = 0; r < h; r++) result += (*this)(r,0) * B(r,0);
	return result;
  }

  template<class T>
  void
  MatrixAbstract<T>::identity (const T scalar)
  {
	clear ();
	int last = std::min (rows (), columns ());
	for (int i = 0; i < last; i++)
	{
	  (*this)(i,i) = scalar;
	}
  }

  template<class T>
  MatrixResult<T>
  MatrixAbstract<T>::row (const int r) const
  {
	return new MatrixRegion<T> (*this, r, 0, r, columns () - 1);
  }

  template<class T>
  MatrixResult<T>
  MatrixAbstract<T>::column (const int c) const
  {
	return new MatrixRegion<T> (*this, 0, c, rows () - 1, c);
  }

  template<class T>
  MatrixResult<T>
  MatrixAbstract<T>::region (const int firstRow, const int firstColumn, int lastRow, int lastColumn) const
  {
	return new MatrixRegion<T> (*this, firstRow, firstColumn, lastRow, lastColumn);
  }

  template<class T>
  const char *
  MatrixAbstract<T>::toString (std::string & buffer) const
  {
	std::ostringstream stream;
	stream << *this;
	buffer = stream.str ();
	return buffer.c_str ();
  }

  template<class T>
  bool
  MatrixAbstract<T>::operator == (const MatrixAbstract<T> & B) const
  {
	int h = rows ();
	int w = columns ();
	if (B.rows () != h  ||  B.columns () != w)
	{
	  return false;
	}
	for (int c = 0; c < w; c++)
	{
	  for (int r = 0; r < h; r++)
	  {
		if (B(r,c) != (*this)(r,c))
		{
		  return false;
		}
	  }
	}
	return true;
  }

  template<class T>
  MatrixResult<T>
  MatrixAbstract<T>::operator ~ () const
  {
	return new MatrixTranspose<T> (this->clone ());
  }

  template<class T>
  MatrixResult<T>
  MatrixAbstract<T>::operator ^ (const MatrixAbstract<T> & B) const
  {
	// This version is only good for 3 element vectors.  Need to choose
	// a cross-product hack for higher dimensions

	Matrix<T> * result = new Matrix<T> (3);
	(*result)[0] = (*this)[1] * B[2] - (*this)[2] * B[1];
	(*result)[1] = (*this)[2] * B[0] - (*this)[0] * B[2];
	(*result)[2] = (*this)[0] * B[1] - (*this)[1] * B[0];

	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixAbstract<T>::operator & (const MatrixAbstract<T> & B) const
  {
	int h = rows ();
	int w = columns ();
	int oh = std::min (h, B.rows ());
	int ow = std::min (w, B.columns ());
	Matrix<T> * result = new Matrix<T> (h, w);
	for (int c = 0; c < ow; c++)
	{
	  for (int r = 0;  r < oh; r++) (*result)(r,c) = (*this)(r,c) * B(r,c);
	  for (int r = oh; r < h;  r++) (*result)(r,c) = (*this)(r,c);
	}
	for (int c = ow; c < w; c++)
	{
	  for (int r = 0; r < h; r++)   (*result)(r,c) = (*this)(r,c);
	}
	return result;
  }

  /**
	 It is more time efficient to realize both operands into dense
	 matrices, because otherwise each element is retrieved multiple times.
	 However, this is not space efficient, and may not always be the most
	 desirable action.  If a class exists primarily to represent a very
	 large (probably sparse) matrix in a space efficient way, it should
	 override this implementation.
  **/
  template<class T>
  MatrixResult<T>
  MatrixAbstract<T>::operator * (const MatrixAbstract<T> & B) const
  {
	return MatrixStrided<T> (*this) * MatrixStrided<T> (B);
  }

  template<class T>
  MatrixResult<T>
  MatrixAbstract<T>::operator * (const T scalar) const
  {
	int h = rows ();
	int w = columns ();
	Matrix<T> * result = new Matrix<T> (h, w);
	for (int c = 0; c < w; c++)
	{
	  for (int r = 0; r < h; r++)
	  {
		(*result)(r,c) = (*this)(r,c) * scalar;
	  }
	}
	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixAbstract<T>::operator / (const MatrixAbstract<T> & B) const
  {
	int h = rows ();
	int w = columns ();
	int oh = std::min (h, B.rows ());
	int ow = std::min (w, B.columns ());
	Matrix<T> * result = new Matrix<T> (h, w);
	for (int c = 0; c < ow; c++)
	{
	  for (int r = 0;  r < oh; r++) (*result)(r,c) = (*this)(r,c) / B(r,c);
	  for (int r = oh; r < h;  r++) (*result)(r,c) = (*this)(r,c);
	}
	for (int c = ow; c < w; c++)
	{
	  for (int r = 0; r < h; r++)   (*result)(r,c) = (*this)(r,c);
	}
	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixAbstract<T>::operator / (const T scalar) const
  {
	int h = rows ();
	int w = columns ();
	Matrix<T> * result = new Matrix<T> (h, w);
	for (int c = 0; c < w; c++)
	{
	  for (int r = 0; r < h; r++)
	  {
		(*result)(r,c) = (*this)(r,c) / scalar;
	  }
	}
	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixAbstract<T>::operator + (const MatrixAbstract<T> & B) const
  {
	int h = rows ();
	int w = columns ();
	int oh = std::min (h, B.rows ());
	int ow = std::min (w, B.columns ());
	Matrix<T> * result = new Matrix<T> (h, w);
	for (int c = 0; c < ow; c++)
	{
	  for (int r = 0;  r < oh; r++) (*result)(r,c) = (*this)(r,c) + B(r,c);
	  for (int r = oh; r < h;  r++) (*result)(r,c) = (*this)(r,c);
	}
	for (int c = ow; c < w; c++)
	{
	  for (int r = 0; r < h; r++)   (*result)(r,c) = (*this)(r,c);
	}
	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixAbstract<T>::operator + (const T scalar) const
  {
	int h = rows ();
	int w = columns ();
	Matrix<T> * result = new Matrix<T> (h, w);
	for (int c = 0; c < w; c++)
	{
	  for (int r = 0; r < h; r++)
	  {
		(*result)(r,c) = (*this)(r,c) + scalar;
	  }
	}
	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixAbstract<T>::operator - (const MatrixAbstract<T> & B) const
  {
	int h = rows ();
	int w = columns ();
	int oh = std::min (h, B.rows ());
	int ow = std::min (w, B.columns ());
	Matrix<T> * result = new Matrix<T> (h, w);
	for (int c = 0; c < ow; c++)
	{
	  for (int r = 0;  r < oh; r++) (*result)(r,c) = (*this)(r,c) - B(r,c);
	  for (int r = oh; r < h;  r++) (*result)(r,c) = (*this)(r,c);
	}
	for (int c = ow; c < w; c++)
	{
	  for (int r = 0; r < h; r++)   (*result)(r,c) = (*this)(r,c);
	}
	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixAbstract<T>::operator - (const T scalar) const
  {
	int h = rows ();
	int w = columns ();
	Matrix<T> * result = new Matrix<T> (h, w);
	for (int c = 0; c < w; c++)
	{
	  for (int r = 0; r < h; r++)
	  {
		(*result)(r,c) = (*this)(r,c) - scalar;
	  }
	}
	return result;
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixAbstract<T>::operator ^= (const MatrixAbstract<T> & B)
  {
	copyFrom ((*this) ^ B);
	return *this;
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixAbstract<T>::operator &= (const MatrixAbstract<T> & B)
  {
	copyFrom ((*this) & B);
	return *this;
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixAbstract<T>::operator *= (const MatrixAbstract<T> & B)
  {
	copyFrom ((*this) * B);
	return *this;
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixAbstract<T>::operator *= (const T scalar)
  {
	copyFrom ((*this) * scalar);
	return *this;
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixAbstract<T>::operator /= (const MatrixAbstract<T> & B)
  {
	copyFrom ((*this) / B);
	return *this;
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixAbstract<T>::operator /= (const T scalar)
  {
	copyFrom ((*this) / scalar);
	return *this;
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixAbstract<T>::operator += (const MatrixAbstract<T> & B)
  {
	copyFrom ((*this) + B);
	return *this;
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixAbstract<T>::operator += (const T scalar)
  {
	copyFrom ((*this) + scalar);
	return *this;
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixAbstract<T>::operator -= (const MatrixAbstract<T> & B)
  {
	copyFrom ((*this) - B);
	return *this;
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixAbstract<T>::operator -= (const T scalar)
  {
	copyFrom ((*this) - scalar);
	return *this;
  }

  template<class T>
  void
  MatrixAbstract<T>::serialize (Archive & archive, uint32_t version)
  {
  }

  /**
	 Relies on ostream to absorb the variability in the type T.
	 This function should be specialized for char (since ostreams treat
	 chars as characters, not numbers).
  **/
  template<class T>
  std::string elementToString (const T & value)
  {
	std::ostringstream formatted;
	formatted.precision (MatrixAbstract<T>::displayPrecision);
	formatted << value;
	return formatted.str ();
  }

  template<class T>
  T elementFromString (const std::string & value)
  {
	std::istringstream formatted (value);
	T result = (T) 0;
	formatted >> result;
	return result;
  }

  template<class T>
  std::ostream &
  operator << (std::ostream & stream, const MatrixAbstract<T> & A)
  {
	const int rows = A.rows ();
	const int columns = A.columns ();

	// The code below assumes a non-empty matrix, so early out if it is empty.
	if (rows == 0  ||  columns == 0)
	{
	  stream << "[]";
	  return stream;
	}

	std::string line = columns > 1 ? "[" : "~[";
	int r = 0;
	while (true)
	{
	  int c = 0;
	  while (true)
	  {
		line += elementToString (A(r,c));
		if (++c >= columns) break;
		line += ' ';
		while (line.size () < c * A.displayWidth + 1)  // +1 to allow for opening "[" all the way down
		{
		  line += ' ';
		}
	  }
	  stream << line;

	  if (++r >= rows) break;
	  if (columns > 1)
	  {
		stream << std::endl;
		line = " ";  // adjust for opening "["
	  }
	  else
	  {
		stream << " ";
		line.clear ();
	  }
	}
	stream << "]";

	return stream;
  }

  template<class T>
  std::istream &
  operator >> (std::istream & stream, MatrixAbstract<T> & A)
  {
	std::vector<std::vector<T> > temp;
	int columns = 0;
	bool transpose = false;

	// Scan for opening "["
	char token;
	do
	{
	  stream.get (token);
	  if (token == '~') transpose = true;
	}
	while (token != '['  &&  stream.good ());

	// Read rows until closing "]"
	std::string line;
	bool comment = false;
	bool done = false;
	while (stream.good ()  &&  ! done)
	{
	  stream.get (token);

	  bool processLine = false;
	  switch (token)
	  {
		case '\r':
		  break;  // ignore CR characters
		case '#':
		  comment = true;
		  break;
		case '\n':
		  comment = false;
		case ';':
		  if (! comment) processLine = true;
		  break;
		case ']':
		  if (! comment)
		  {
			done = true;
			processLine = true;
		  }
		  break;
		default:
		  if (! comment) line += token;
	  }

	  if (processLine)
	  {
		std::vector<T> row;
		std::string element;
		trim (line);
		while (line.size ())
		{
		  int position = line.find_first_of (" \t");
		  element = line.substr (0, position);
		  row.push_back (elementFromString<T> (element));
		  if (position == std::string::npos) break;
		  line = line.substr (position);
		  trim (line);
		}
		int c = row.size ();
		if (c)
		{
		  temp.push_back (row);
		  columns = std::max (columns, c);
		}
		line.clear ();
	  }
	}

	// Assign elements to A.
	const int rows = temp.size ();
	if (transpose)
	{
	  A.resize (columns, rows);
	  A.clear ();
	  for (int r = 0; r < rows; r++)
	  {
		std::vector<T> & row = temp[r];
		for (int c = 0; c < row.size (); c++)
		{
		  A(c,r) = row[c];
		}
	  }
	}
	else
	{
	  A.resize (rows, columns);
	  A.clear ();
	  for (int r = 0; r < rows; r++)
	  {
		std::vector<T> & row = temp[r];
		for (int c = 0; c < row.size (); c++)
		{
		  A(r,c) = row[c];
		}
	  }
	}

	return stream;
  }

  template<class T>
  MatrixAbstract<T> &
  operator << (MatrixAbstract<T> & A, const std::string & source)
  {
	std::istringstream stream (source);
	stream >> A;
	return A;
  }


  // class MatrixStrided<T> ---------------------------------------------------

  template<class T>
  MatrixStrided<T>::MatrixStrided ()
  {
	offset   = 0;
	rows_    = 0;
	columns_ = 0;
	strideR  = 1;
	strideC  = 0;
  }

  template<class T>
  MatrixStrided<T>::MatrixStrided (const MatrixAbstract<T> & that)
  {
	if (that.classID () & MatrixStridedID)
	{
	  this->operator = ((const MatrixStrided<T> &) that);
	}
	else
	{
	  int h = that.rows ();
	  int w = that.columns ();

	  // Equivalent to Matrix::resize(h,w).  We can't use our own resize(),
	  // because it does not actually allocate memory.
	  data.grow (w * h * sizeof (T));
	  offset   = 0;
	  strideR  = 1;
	  strideC  = h;

	  copyFrom (that);
	}
  }

  template<class T>
  MatrixStrided<T>::MatrixStrided (const Pointer & that, const int offset, const int rows, const int columns, const int strideR, const int strideC)
  : data (that),
	offset (offset),
	rows_ (rows),
	columns_ (columns),
	strideR (strideR),
	strideC (strideC)
  {
  }

  template<class T>
  void
  MatrixStrided<T>::detach ()
  {
	offset   = 0;
	rows_    = 0;
	columns_ = 0;
	strideR  = 1;
	strideC  = 0;
	data.detach ();
  }

  template<class T>
  uint32_t
  MatrixStrided<T>::classID () const
  {
	return MatrixAbstractID | MatrixStridedID;
  }

  template<class T>
  MatrixAbstract<T> *
  MatrixStrided<T>::clone (bool deep) const
  {
	if (! deep) return new MatrixStrided (*this);

	// For deep copy, we don't actually return a MatrixStrided, but rather a dense Matrix...
	Matrix<T> * result = new Matrix<T> (rows_, columns_);
	T * i = (T *) result->data;
	T * j = (T *) data + offset;
	T * end = i + rows_ * columns_;
	const int stepC = strideC - rows_ * strideR;
	while (i < end)
	{
	  T * columnEnd = i + rows_;
	  while (i < columnEnd)
	  {
		*i++ = *j;
		j += strideR;
	  }
	  j += stepC;
	}
	return result;
  }

  template<class T>
  void
  MatrixStrided<T>::copyFrom (const MatrixAbstract<T> & that, bool deep)
  {
	T * i = (T *) data + offset;
	if (that.classID () & MatrixStridedID)
	{
	  const MatrixStrided & M = (const MatrixStrided &) (that);
	  resize (M.rows_, M.columns_);
	  T * j = (T *) M.data + M.offset;
	  T * end = i + columns_ * strideC;
	  const int istepC =   strideC - rows_ *   strideR;
	  const int jstepC = M.strideC - rows_ * M.strideR;
	  while (i != end)
	  {
		T * columnEnd = i + rows_ * strideR;
		while (i != columnEnd)
		{
		  *i = *j;
		  i +=   strideR;
		  j += M.strideR;
		}
		i += istepC;
		j += jstepC;
	  }
	}
	else
	{
	  int h = that.rows ();
	  int w = that.columns ();
	  resize (h, w);
	  const int stepC = strideC - rows_ * strideR;
	  for (int c = 0; c < w; c++)
	  {
		for (int r = 0; r < h; r++)
		{
		  *i = that(r,c);
		  i += strideR;
		}
		i += stepC;
	  }
	}
  }

  template<class T>
  int
  MatrixStrided<T>::rows () const
  {
	return rows_;
  }

  template<class T>
  int
  MatrixStrided<T>::columns () const
  {
	return columns_;
  }

  template<class T>
  void
  MatrixStrided<T>::resize (const int rows, const int columns)
  {
	this->rows_    = rows;
	this->columns_ = columns;
  }

  template<class T>
  void
  MatrixStrided<T>::clear (const T scalar)
  {
	T * i = (T *) data + offset;
	T * end = i + columns_ * strideC;
	const int stepC = strideC - rows_ * strideR;
	while (i != end)
	{
	  T * columnEnd = i + rows_ * strideR;
	  while (i != columnEnd)
	  {
		*i = scalar;
		i += strideR;
	  }
	  i += stepC;
	}
  }

  template<class T>
  T
  MatrixStrided<T>::norm (float n) const
  {
	T * i = (T *) data + offset;
	T * end = i + columns_ * strideC;
	const int stepC = strideC - rows_ * strideR;
	if (n == INFINITY)
	{
	  T result = (T) 0;
	  while (i != end)
	  {
		T * columnEnd = i + rows_ * strideR;
		while (i != columnEnd)
		{
		  result = std::max (std::abs (*i), result);
		  i += strideR;
		}
		i += stepC;
	  }
	  return result;
	}
	else if (n == 0.0f)
	{
	  unsigned int result = 0;
	  while (i != end)
	  {
		T * columnEnd = i + rows_ * strideR;
		while (i != columnEnd)
		{
		  if (*i != (T) 0) result++;
		  i += strideR;
		}
		i += stepC;
	  }
	  return (T) result;
	}
	else if (n == 1.0f)
	{
	  T result = (T) 0;
	  while (i != end)
	  {
		T * columnEnd = i + rows_ * strideR;
		while (i != columnEnd)
		{
		  result += *i;
		  i += strideR;
		}
		i += stepC;
	  }
	  return result;
	}
	else if (n == 2.0f)
	{
#     ifdef HAVE_BLAS
	  if (columns_ == 1)                 return nrm2 (rows_,            i, strideR);
	  if (rows_    == 1)                 return nrm2 (columns_,         i, strideC);
	  if (strideC == rows_    * strideR) return nrm2 (rows_ * columns_, i, strideR);
	  if (strideR == columns_ * strideC) return nrm2 (rows_ * columns_, i, strideC);
#     endif

	  T result = (T) 0;
	  while (i != end)
	  {
		T * columnEnd = i + rows_ * strideR;
		while (i != columnEnd)
		{
		  result += (*i) * (*i);
		  i += strideR;
		}
		i += stepC;
	  }
	  return (T) std::sqrt (result);
	}
	else
	{
	  T result = (T) 0;
	  while (i != end)
	  {
		T * columnEnd = i + rows_ * strideR;
		while (i != columnEnd)
		{
		  result += (T) std::pow (*i, (T) n);
		  i += strideR;
		}
		i += stepC;
	  }
	  return (T) std::pow (result, (T) (1.0 / n));
	}
  }

  template<class T>
  T
  MatrixStrided<T>::sumSquares () const
  {
	T * i = (T *) data + offset;
	T * end = i + columns_ * strideC;
	const int stepC = strideC - rows_ * strideR;
	T result = 0;
	while (i != end)
	{
	  T * columnEnd = i + rows_ * strideR;
	  while (i != columnEnd)
	  {
		result += (*i) * (*i);
		i += strideR;
	  }
	  i += stepC;
	}
	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixStrided<T>::transposeSquare () const
  {
	Matrix<T> * result = new Matrix<T> (columns_, columns_);
	T * base = (T *) data + offset;
	for (int i = 0; i < columns_; i++)
	{
	  for (int j = i; j < columns_; j++)
	  {
		T * ki = base + i * strideC;
		T * kj = base + j * strideC;
		T * end = ki + rows_ * strideR;
		register T sum = (T) 0;
		while (ki != end)
		{
		  sum += (*ki) * (*kj);
		  ki += strideR;
		  kj += strideR;
		}
		(*result)(i,j) = sum;
	  }
	}
	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixStrided<T>::visit (T (*function) (const T &)) const
  {
	Matrix<T> * result = new Matrix<T> (rows_, columns_);
	const int step = strideC - rows_ * strideR;
	T * r   = (T *) result->data;
	T * a   = (T *) data + offset;
	T * end = a + strideC * columns_;
	while (a != end)
	{
	  T * columnEnd = a + rows_ * strideR;
	  while (a != columnEnd)
	  {
		*r++ = (*function) (*a);
		a += strideR;
	  }
	  a += step;
	}

	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixStrided<T>::visit (T (*function) (const T)) const
  {
	Matrix<T> * result = new Matrix<T> (rows_, columns_);
	const int step = strideC - rows_ * strideR;
	T * r   = (T *) result->data;
	T * a   = (T *) data + offset;
	T * end = a + strideC * columns_;
	while (a != end)
	{
	  T * columnEnd = a + rows_ * strideR;
	  while (a != columnEnd)
	  {
		*r++ = (*function) (*a);
		a += strideR;
	  }
	  a += step;
	}

	return result;
  }

  template<class T>
  T
  MatrixStrided<T>::dot (const MatrixAbstract<T> & B) const
  {
	register T result = (T) 0;
	const int n = std::min (rows_, B.rows ());
	T * i = (T *) data + offset;
	if (B.classID () & MatrixStridedID)
	{
	  const MatrixStrided & M = (const MatrixStrided &) B;
	  T * j = (T *) M.data + M.offset;
#     ifdef HAVE_BLAS
	  result = fl::dot (n, i, strideR, j, M.strideR);
#     else
	  T * end = i + n * strideR;
	  while (i != end)
	  {
		result += (*i) * (*j);
		i +=   strideR;
		j += M.strideR;
	  }
#     endif
	}
	else
	{
	  int j = 0;
	  T * end = i + n * strideR;
	  while (i != end)
	  {
		result += (*i) * B[j++];
		i += strideR;
	  }
	}
	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixStrided<T>::row (const int r) const
  {
	return new MatrixStrided (data, offset + r * strideR, 1, columns_, strideR, strideC);
  }

  template<class T>
  MatrixResult<T>
  MatrixStrided<T>::column (const int c) const
  {
	return new MatrixStrided (data, offset + c * strideC, rows_, 1, strideR, strideC);
  }

  template<class T>
  MatrixResult<T>
  MatrixStrided<T>::region (const int firstRow, const int firstColumn, int lastRow, int lastColumn) const
  {
	if (lastRow < 0)
	{
	  lastRow = rows_ - 1;
	}
	if (lastColumn < 0)
	{
	  lastColumn = columns_ - 1;
	}
	int offset  = this->offset + firstColumn * strideC + firstRow * strideR;
	int rows    = lastRow    - firstRow    + 1;
	int columns = lastColumn - firstColumn + 1;

	return new MatrixStrided (data, offset, rows, columns, strideR, strideC);
  }

  template<class T>
  MatrixResult<T>
  MatrixStrided<T>::operator ~ () const
  {
	return new MatrixStrided (data, offset, columns_, rows_, strideC, strideR);
  }

  template<class T>
  MatrixResult<T>
  MatrixStrided<T>::operator & (const MatrixAbstract<T> & B) const
  {
	if ((B.classID () & MatrixStridedID) == 0) return MatrixAbstract<T>::operator & (B);

	Matrix<T> * result = new Matrix<T> (rows_, columns_);
	const MatrixStrided & MB = (const MatrixStrided &) B;
	const int oh = std::min (rows_,    MB.rows_);
	const int ow = std::min (columns_, MB.columns_);
	const int stepA =    strideC - rows_ *    strideR;
	const int stepB = MB.strideC - oh    * MB.strideR;
	T * a = (T *)    data +    offset;
	T * b = (T *) MB.data + MB.offset;
	T * r = (T *) result->data;
	T * end = r + rows_ * ow;
	while (r < end)
	{
	  T * overlapEnd = r + oh;
	  T * columnEnd  = r + rows_;
	  while (r < overlapEnd)
	  {
		*r++ = *a * *b;
		a +=    strideR;
		b += MB.strideR;
	  }
	  while (r < columnEnd)
	  {
		*r++ = *a;
		a += strideR;
	  }
	  a += stepA;
	  b += stepB;
	}
	end += rows_ * (columns_ - ow);
	while (r < end)
	{
	  T * columnEnd = r + rows_;
	  while (r < columnEnd)
	  {
		*r++ = *a;
		a += strideR;
	  }
	  a += stepA;
	}

	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixStrided<T>::operator * (const MatrixAbstract<T> & B) const
  {
	if ((B.classID () & MatrixStridedID) == 0) return operator * (MatrixStrided (B));  // For efficiency's sake, realize the second matrix rather than repeatedly calling its accessors for each given element.

	const MatrixStrided & MB = (const MatrixStrided &) B;
	const int w  = std::min (columns_, MB.rows_);
	const int bw = MB.columns_;
	Matrix<T> * result = new Matrix<T> (rows_, bw);
	T * c = (T *) result->data;

#   ifdef HAVE_BLAS
	if (rows_ * bw * w > 1000)  // Only run BLAS for non-trivial sized problems.  Should really be a tuneable parameter.
	{
	  if (strideR == 1)
	  {
		if (MB.strideR == 1)
		{
		  gemm ('n', 'n', rows_, bw, w, (T) 1, (T *) data + offset, strideC, (T *) MB.data + MB.offset, MB.strideC, (T) 0, c, rows_);
		  return result;
		}
		if (MB.strideC == 1)
		{
		  gemm ('n', 'T', rows_, bw, w, (T) 1, (T *) data + offset, strideC, (T *) MB.data + MB.offset, MB.strideR, (T) 0, c, rows_);
		  return result;
		}
	  }
	  else if (strideC == 1)
	  {
		if (MB.strideR == 1)
		{
		  gemm ('T', 'n', rows_, bw, w, (T) 1, (T *) data + offset, strideR, (T *) MB.data + MB.offset, MB.strideC, (T) 0, c, rows_);
		  return result;
		}
		if (MB.strideC == 1)
		{
		  gemm ('T', 'T', rows_, bw, w, (T) 1, (T *) data + offset, strideR, (T *) MB.data + MB.offset, MB.strideR, (T) 0, c, rows_);
		  return result;
		}
	  }
	}
#   endif

	T * aa  = (T *)    data +    offset;
	T * b   = (T *) MB.data + MB.offset;
	T * end = c + rows_ * bw;
	while (c < end)
	{
	  T * a = aa;
	  T * columnEnd = c + rows_;
	  while (c < columnEnd)
	  {
		register T element = (T) 0;
		T * i = a;
		T * j = b;
		T * rowEnd = j + w * MB.strideR;
		while (j != rowEnd)
		{
		  element += (*i) * (*j);
		  i +=    strideC;
		  j += MB.strideR;
		}
		*c++ = element;
		a += strideR;
	  }
	  b += MB.strideC;
	}
	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixStrided<T>::operator * (const T scalar) const
  {
	Matrix<T> * result = new Matrix<T> (rows_, columns_);
	T * i   = (T *) data + offset;
	T * r   = (T *) result->data;
	T * end = r + rows_ * columns_;
	const int stepC = strideC - rows_ * strideR;
	while (r < end)
	{
	  T * columnEnd = r + rows_;
	  while (r < columnEnd)
	  {
		*r++ = *i * scalar;
		i += strideR;
	  }
	  i += stepC;
	}
	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixStrided<T>::operator / (const MatrixAbstract<T> & B) const
  {
	if ((B.classID () & MatrixStridedID) == 0) return MatrixAbstract<T>::operator / (B);

	Matrix<T> * result = new Matrix<T> (rows_, columns_);
	const MatrixStrided & MB = (const MatrixStrided &) B;
	const int oh = std::min (rows_,    MB.rows_);
	const int ow = std::min (columns_, MB.columns_);
	const int stepA =    strideC - rows_ *    strideR;
	const int stepB = MB.strideC - oh    * MB.strideR;
	T * a = (T *)    data +    offset;
	T * b = (T *) MB.data + MB.offset;
	T * r = (T *) result->data;
	T * end = r + rows_ * ow;
	while (r < end)
	{
	  T * overlapEnd = r + oh;
	  T * columnEnd  = r + rows_;
	  while (r < overlapEnd)
	  {
		*r++ = *a / *b;
		a +=    strideR;
		b += MB.strideR;
	  }
	  while (r < columnEnd)
	  {
		*r++ = *a;
		a += strideR;
	  }
	  a += stepA;
	  b += stepB;
	}
	end += rows_ * (columns_ - ow);
	while (r < end)
	{
	  T * columnEnd = r + rows_;
	  while (r < columnEnd)
	  {
		*r++ = *a;
		a += strideR;
	  }
	  a += stepA;
	}

	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixStrided<T>::operator / (const T scalar) const
  {
	Matrix<T> * result = new Matrix<T> (rows_, columns_);
	T * i   = (T *) data + offset;
	T * r   = (T *) result->data;
	T * end = r + rows_ * columns_;
	const int stepC = strideC - rows_ * strideR;
	while (r < end)
	{
	  T * columnEnd = r + rows_;
	  while (r < columnEnd)
	  {
		*r++ = *i / scalar;
		i += strideR;
	  }
	  i += stepC;
	}
	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixStrided<T>::operator + (const MatrixAbstract<T> & B) const
  {
	if ((B.classID () & MatrixStridedID) == 0) return MatrixAbstract<T>::operator + (B);

	Matrix<T> * result = new Matrix<T> (rows_, columns_);
	const MatrixStrided & MB = (const MatrixStrided &) B;
	const int oh = std::min (rows_,    MB.rows_);
	const int ow = std::min (columns_, MB.columns_);
	const int stepA =    strideC - rows_ *    strideR;
	const int stepB = MB.strideC - oh    * MB.strideR;
	T * a = (T *)    data +    offset;
	T * b = (T *) MB.data + MB.offset;
	T * r = (T *) result->data;
	T * end = r + rows_ * ow;
	while (r < end)
	{
	  T * overlapEnd = r + oh;
	  T * columnEnd  = r + rows_;
	  while (r < overlapEnd)
	  {
		*r++ = *a + *b;
		a +=    strideR;
		b += MB.strideR;
	  }
	  while (r < columnEnd)
	  {
		*r++ = *a;
		a += strideR;
	  }
	  a += stepA;
	  b += stepB;
	}
	end += rows_ * (columns_ - ow);
	while (r < end)
	{
	  T * columnEnd = r + rows_;
	  while (r < columnEnd)
	  {
		*r++ = *a;
		a += strideR;
	  }
	  a += stepA;
	}

	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixStrided<T>::operator + (const T scalar) const
  {
	Matrix<T> * result = new Matrix<T> (rows_, columns_);
	T * i   = (T *) data + offset;
	T * r   = (T *) result->data;
	T * end = r + rows_ * columns_;
	const int stepC = strideC - rows_ * strideR;
	while (r < end)
	{
	  T * columnEnd = r + rows_;
	  while (r < columnEnd)
	  {
		*r++ = *i + scalar;
		i += strideR;
	  }
	  i += stepC;
	}
	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixStrided<T>::operator - (const MatrixAbstract<T> & B) const
  {
	if ((B.classID () & MatrixStridedID) == 0) return MatrixAbstract<T>::operator - (B);

	Matrix<T> * result = new Matrix<T> (rows_, columns_);
	const MatrixStrided & MB = (const MatrixStrided &) B;
	const int oh = std::min (rows_,    MB.rows_);
	const int ow = std::min (columns_, MB.columns_);
	const int stepA =    strideC - rows_ *    strideR;
	const int stepB = MB.strideC - oh    * MB.strideR;
	T * a = (T *)    data +    offset;
	T * b = (T *) MB.data + MB.offset;
	T * r = (T *) result->data;
	T * end = r + rows_ * ow;
	while (r < end)
	{
	  T * overlapEnd = r + oh;
	  T * columnEnd  = r + rows_;
	  while (r < overlapEnd)
	  {
		*r++ = *a - *b;
		a +=    strideR;
		b += MB.strideR;
	  }
	  while (r < columnEnd)
	  {
		*r++ = *a;
		a += strideR;
	  }
	  a += stepA;
	  b += stepB;
	}
	end += rows_ * (columns_ - ow);
	while (r < end)
	{
	  T * columnEnd = r + rows_;
	  while (r < columnEnd)
	  {
		*r++ = *a;
		a += strideR;
	  }
	  a += stepA;
	}

	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixStrided<T>::operator - (const T scalar) const
  {
	Matrix<T> * result = new Matrix<T> (rows_, columns_);
	T * i   = (T *) data + offset;
	T * r   = (T *) result->data;
	T * end = r + rows_ * columns_;
	const int stepC = strideC - rows_ * strideR;
	while (r < end)
	{
	  T * columnEnd = r + rows_;
	  while (r < columnEnd)
	  {
		*r++ = *i - scalar;
		i += strideR;
	  }
	  i += stepC;
	}
	return result;
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixStrided<T>::operator &= (const MatrixAbstract<T> & B)
  {
	if ((B.classID () & MatrixStridedID) == 0) return MatrixAbstract<T>::operator &= (B);

	const MatrixStrided & MB = (const MatrixStrided &) B;
	const int oh = std::min (rows_,    MB.rows_);
	const int ow = std::min (columns_, MB.columns_);
	const int stepA =    strideC - oh *    strideR;
	const int stepB = MB.strideC - oh * MB.strideR;
	T * a   = (T *)    data +    offset;
	T * b   = (T *) MB.data + MB.offset;
	T * end = a + strideC * ow;
	while (a != end)
	{
	  T * columnEnd = a + oh * strideR;
	  while (a != columnEnd)
	  {
		*a *= *b;
		a +=    strideR;
		b += MB.strideR;
	  }
	  a += stepA;
	  b += stepB;
	}

	return *this;
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixStrided<T>::operator *= (const MatrixAbstract<T> & B)
  {
	if (B.classID () & MatrixStridedID) return this->operator = (operator * ((const MatrixStrided &) B));
	return MatrixAbstract<T>::operator *= (B);
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixStrided<T>::operator *= (const T scalar)
  {
	T * i = (T *) data + offset;

#   ifdef HAVE_BLAS
	if (columns_ == 1)
	{
	  scal (rows_, scalar, i, strideR);
	  return * this;
	}
	if (rows_ == 1)
	{
	  scal (columns_, scalar, i, strideC);
	  return *this;
	}
	if (strideC == rows_ * strideR)
	{
	  scal (rows_ * columns_, scalar, i, strideR);
	  return *this;
	}
	if (strideR == columns_ * strideC)
	{
	  scal (rows_ * columns_, scalar, i, strideC);
	  return *this;
	}
#   endif

	T * end = i + strideC * columns_;
	const int stepC = strideC - rows_ * strideR;
	while (i != end)
	{
	  T * columnEnd = i + rows_ * strideR;
	  while (i != columnEnd)
	  {
		*i *= scalar;
		i += strideR;
	  }
	  i += stepC;
	}
	return *this;
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixStrided<T>::operator /= (const MatrixAbstract<T> & B)
  {
	if ((B.classID () & MatrixStridedID) == 0) return MatrixAbstract<T>::operator /= (B);

	const MatrixStrided & MB = (const MatrixStrided &) B;
	const int oh = std::min (rows_,    MB.rows_);
	const int ow = std::min (columns_, MB.columns_);
	const int stepA =    strideC - oh *    strideR;
	const int stepB = MB.strideC - oh * MB.strideR;
	T * a   = (T *)    data +    offset;
	T * b   = (T *) MB.data + MB.offset;
	T * end = a + strideC * ow;
	while (a != end)
	{
	  T * columnEnd = a + oh * strideR;
	  while (a != columnEnd)
	  {
		*a /= *b;
		a +=    strideR;
		b += MB.strideR;
	  }
	  a += stepA;
	  b += stepB;
	}

	return *this;
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixStrided<T>::operator /= (const T scalar)
  {
	T * i = (T *) data + offset;
	T * end = i + strideC * columns_;
	const int stepC = strideC - rows_ * strideR;
	while (i != end)
	{
	  T * columnEnd = i + rows_ * strideR;
	  while (i != columnEnd)
	  {
		*i /= scalar;
		i += strideR;
	  }
	  i += stepC;
	}
	return *this;
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixStrided<T>::operator += (const MatrixAbstract<T> & B)
  {
	if ((B.classID () & MatrixStridedID) == 0) return MatrixAbstract<T>::operator += (B);

	const MatrixStrided & MB = (const MatrixStrided &) B;
	const int oh = std::min (rows_,    MB.rows_);
	const int ow = std::min (columns_, MB.columns_);
	T * a   = (T *)    data +    offset;
	T * b   = (T *) MB.data + MB.offset;

#   ifdef HAVE_BLAS
	if (ow == 1)
	{
	  axpy (oh, (T) 1, b, MB.strideR, a, strideR);
	  return * this;
	}
	if (oh == 1)
	{
	  axpy (ow, (T) 1, b, MB.strideC, a, strideC);
	  return *this;
	}
	if (strideC == oh * strideR  &&  MB.strideC == oh * MB.strideR)
	{
	  axpy (oh * ow, (T) 1, b, MB.strideR, a, strideR);
	  return *this;
	}
	if (strideR == ow * strideC  &&  MB.strideR == ow * MB.strideC)
	{
	  axpy (oh * ow, (T) 1, b, MB.strideC, a, strideC);
	  return *this;
	}
#   endif

	const int stepA =    strideC - oh *    strideR;
	const int stepB = MB.strideC - oh * MB.strideR;
	T * end = a + strideC * ow;
	while (a != end)
	{
	  T * columnEnd = a + oh * strideR;
	  while (a != columnEnd)
	  {
		*a += *b;
		a +=    strideR;
		b += MB.strideR;
	  }
	  a += stepA;
	  b += stepB;
	}

	return *this;
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixStrided<T>::operator += (const T scalar)
  {
	T * i = (T *) data + offset;
	T * end = i + strideC * columns_;
	const int stepC = strideC - rows_ * strideR;
	while (i != end)
	{
	  T * columnEnd = i + rows_ * strideR;
	  while (i != columnEnd)
	  {
		*i += scalar;
		i += strideR;
	  }
	  i += stepC;
	}
	return *this;
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixStrided<T>::operator -= (const MatrixAbstract<T> & B)
  {
	if ((B.classID () & MatrixStridedID) == 0) return MatrixAbstract<T>::operator -= (B);

	const MatrixStrided & MB = (const MatrixStrided &) B;
	const int oh = std::min (rows_,    MB.rows_);
	const int ow = std::min (columns_, MB.columns_);
	const int stepA =    strideC - oh *    strideR;
	const int stepB = MB.strideC - oh * MB.strideR;
	T * a   = (T *)    data +    offset;
	T * b   = (T *) MB.data + MB.offset;
	T * end = a + strideC * ow;
	while (a != end)
	{
	  T * columnEnd = a + oh * strideR;
	  while (a != columnEnd)
	  {
		*a -= *b;
		a +=    strideR;
		b += MB.strideR;
	  }
	  a += stepA;
	  b += stepB;
	}

	return *this;
  }

  template<class T>
  MatrixAbstract<T> &
  MatrixStrided<T>::operator -= (const T scalar)
  {
	T * i = (T *) data + offset;
	T * end = i + strideC * columns_;
	const int stepC = strideC - rows_ * strideR;
	while (i != end)
	{
	  T * columnEnd = i + rows_ * strideR;
	  while (i != columnEnd)
	  {
		*i -= scalar;
		i += strideR;
	  }
	  i += stepC;
	}
	return *this;
  }

  template<class T>
  void
  MatrixStrided<T>::serialize (Archive & archive, uint32_t version)
  {
	archive & rows_;
	archive & columns_;

	if (archive.in)
	{
	  offset  = 0;
	  strideR = 1;
	  strideC = rows_;

	  if (! archive.in->good ()) throw "Stream bad.  Unable to finish reading Matrix.";
	  int bytes = rows_ * columns_ * sizeof (T);
	  this->data.grow (bytes);
	  archive.in->read ((char *) data, bytes);
	}
	else
	{
	  T * i = (T *) data + offset;
	  // Chunk the output as much as possible.  This makes writing to the stream much faster.
	  if (strideR == 1)
	  {
		if (strideC == rows_)
		{
		  archive.out->write ((char *) i, rows_ * columns_ * sizeof (T));
		}
		else
		{
		  const int bytes = rows_ * sizeof (T);
		  int count = bytes * columns_;
		  while (count > 0)
		  {
			archive.out->write ((char *) i, bytes);
			i += strideC;
			count -= bytes;
		  }
		}
	  }
	  else
	  {
		T * end = i + strideC * columns_;
		const int stepC = strideC - rows_ * strideR;
		while (i != end)
		{
		  T * columnEnd = i + rows_ * strideR;
		  while (i != columnEnd)
		  {
			archive.out->write ((char *) i, sizeof (T));
			i += strideR;
		  }
		  i += stepC;
		}
	  }
	}
  }


  // class Matrix<T> ----------------------------------------------------------

  template<class T>
  Matrix<T>::Matrix ()
  {
  };

  template<class T>
  Matrix<T>::Matrix (const int rows, const int columns)
  {
	resize (rows, columns);
  }

  template<class T>
  Matrix<T>::Matrix (const MatrixAbstract<T> & that)
  {
	if (that.classID () & MatrixStridedID)
	{
	  const MatrixStrided<T> & MS = (const MatrixStrided<T> &) that;
	  if (MS.offset == 0  &&  MS.strideR == 1)
	  {
		MatrixStrided<T>::operator = (MS);
		return;
	  }
	}

	// same code as copyFrom()
	int h = that.rows ();
	int w = that.columns ();
	resize (h, w);
	T * i = (T *) this->data;
	for (int c = 0; c < w; c++)
	{
	  for (int r = 0; r < h; r++)
	  {
		*i++ = that(r,c);
	  }
	}
  }

  template<class T>
  Matrix<T>::Matrix (const std::string & source)
  {
	*this << source;
  }

  template<class T>
  Matrix<T>::Matrix (T * that, const int rows, const int columns)
  {
	this->data.attach (that, rows * columns * sizeof (T));
	this->rows_    = rows;
	this->columns_ = columns;
	this->strideC  = rows;
  }

  template<class T>
  Matrix<T>::Matrix (Pointer & that, const int rows, const int columns)
  {
	this->data = that;
	if (rows < 0  ||  columns < 0)  // infer number from size of memory block and size of our data type
	{
	  int size = this->data.size ();
	  if (size < 0)
	  {
		// Pointer does not know the size of memory block, so we pretend it is empty.  This is really an error condition.
		this->rows_    = 0;
		this->columns_ = 0;
	  }
	  else
	  {
		if (rows < 0)
		{
		  this->rows_    = size / (sizeof (T) * columns);
		  this->columns_ = columns;
		}
		else  // columns < 0
		{
		  this->rows_    = rows;
		  this->columns_ = size / (sizeof (T) * rows);
		}
	  }
	}
	else  // number of rows and columns is given
	{
	  this->rows_    = rows;
	  this->columns_ = columns;
	}
	this->strideC = this->rows_;
  }

  template<class T>
  uint32_t
  Matrix<T>::classID () const
  {
	return MatrixAbstractID | MatrixStridedID | MatrixID;
  }

  template<class T>
  MatrixAbstract<T> *
  Matrix<T>::clone (bool deep) const
  {
	if (deep)
	{
	  Matrix * result = new Matrix;
	  result->copyFrom (*this);
	  return result;
	}
	return new Matrix (*this);
  }

  template<class T>
  void
  Matrix<T>::copyFrom (const MatrixAbstract<T> & that, bool deep)
  {
	if (that.classID ()  &  MatrixID)
	{
	  const Matrix & M = (const Matrix &) (that);
	  if (! deep)
	  {
		this->operator = (M);
		return;
	  }

	  resize (M.rows_, M.columns_);
	  const int step = M.strideC - M.rows_;
	  if (step == 0)
	  {
		this->data.copyFrom (M.data);
	  }
	  else
	  {
		T * i = (T *) this->data;
		T * j = (T *) M.data;
		T * end = i + this->rows_ * this->columns_;
		while (i < end)
		{
		  T * columnEnd = i + this->rows_;
		  while (i < columnEnd) *i++ = *j++;
		  j += step;
		}
	  }
	}
	else
	{
	  int h = that.rows ();
	  int w = that.columns ();
	  resize (h, w);
	  T * i = (T *) this->data;
	  for (int c = 0; c < w; c++)
	  {
		for (int r = 0; r < h; r++)
		{
		  *i++ = that(r,c);
		}
	  }
	}
  }

  template<class T>
  void
  Matrix<T>::resize (const int rows, const int columns)
  {
	this->data.grow (rows * columns * sizeof (T));
	this->rows_    = rows;
	this->columns_ = columns;
	this->strideC  = rows;
  }

  /**
	 Unwind the elements columnwise and reflow them into a matrix of the
	 given size.  If the new matrix has a larger number of elements, then
	 repeat the element sequence until the new matrix is filled.  If the
	 number of rows is changed (the most likely case) and the source matrix
	 has a number of rows different than its stride, then this function
	 will move data in memory to provide contiguous access to elements.

	 @param inPlace If the new size is no larger in each dimension than
	 the old size, you can avoid copying the elements by setting this flag.
	 The consequence is that the values in the new matrix will not follow
	 the columnwise unwind behavior, but will simply be a block out of the
	 source matrix of the specified size.
  **/
  template<class T>
  Matrix<T>
  Matrix<T>::reshape (const int rows, const int columns, bool inPlace) const
  {
	if (inPlace)
	{
	  if (rows <= this->strideC  &&  columns <= this->columns_)
	  {
		Matrix result = *this;  // should be shallow copy; strideC will be the same
		result.rows_    = rows;
		result.columns_ = columns;
		return result;
	  }
	}
	else
	{
	  if (this->rows_ == this->strideC  &&  rows * columns <= this->rows_ * this->columns_)
	  {
		Matrix result = *this;
		result.rows_    = rows;
		result.columns_ = columns;
		result.strideC  = rows;
		return result;
	  }
	}

	// Create new matrix and copy data into a dense block.
	Matrix result (rows, columns);
	const int currentSize = this->rows_ * this->columns_;
	const int resultSize  =       rows  *       columns;
	const int step        = this->strideC - this->rows_;
	T * resultData = (T *) result.data;
	T * source     = (T *) this->data;
	T * dest       = resultData;
	//   Copy an integral number of columns over
	T * end = dest + (std::min (currentSize, resultSize) / this->rows_) * this->rows_;
	while (dest < end)
	{
	  T * columnEnd = source + this->rows_;
	  while (source < columnEnd) *dest++ = *source++;
	  source += step;
	}

	// Finish filling result:
	//   currentSize < resultSize -- duplicate data block in result
	//   currentSize > resultSize -- copy over part of a final column
	end = resultData + resultSize;
	if (currentSize < resultSize) source = resultData;
	while (dest < end) *dest++ = *source++;

	return result;
  }

  template<class T>
  void
  Matrix<T>::clear (const T scalar)
  {
	if (scalar == (T) 0)
	{
	  this->data.clear ();
	}
	else
	{
	  T * i = (T *) this->data;
	  T * end = i + this->strideC * this->columns_;
	  while (i < end)
	  {
		*i++ = scalar;
	  }
	}	  
  }


  // class MatrixTranspose<T> -------------------------------------------------

  template<class T>
  MatrixTranspose<T>::MatrixTranspose (MatrixAbstract<T> * that)
  {
	wrapped = that;
  }

  template<class T>
  MatrixTranspose<T>::~MatrixTranspose ()
  {
	delete wrapped;  // We can assume that wrapped != NULL.
  }

  template<class T>
  MatrixAbstract<T> *
  MatrixTranspose<T>::clone (bool deep) const
  {
	return new MatrixTranspose<T> (wrapped->clone (deep));
  }

  template<class T>
  int
  MatrixTranspose<T>::rows () const
  {
	return wrapped->columns ();
  }

  template<class T>
  int
  MatrixTranspose<T>::columns () const
  {
	return wrapped->rows ();
  }

  template<class T>
  void
  MatrixTranspose<T>::resize (const int rows, const int columns)
  {
	wrapped->resize (columns, rows);
  }

  template<class T>
  void
  MatrixTranspose<T>::clear (const T scalar)
  {
	wrapped->clear (scalar);
  }

  template<class T>
  MatrixResult<T>
  MatrixTranspose<T>::operator * (const MatrixAbstract<T> & B) const
  {
	int w = std::min (wrapped->rows (), B.rows ());
	int h = wrapped->columns ();
	int bw = B.columns ();
	Matrix<T> * result = new Matrix<T> (h, bw);
	for (int c = 0; c < bw; c++)
	{
	  for (int r = 0; r < h; r++)
	  {
		register T element = (T) 0;
		for (int i = 0; i < w; i++)
		{
		  element += (*wrapped)(i,r) * B(i,c);
		}
		(*result)(r,c) = element;
	  }
	}
	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixTranspose<T>::operator * (const T scalar) const
  {
    int h = wrapped->columns ();
    int w = wrapped->rows ();
	Matrix<T> * result = new Matrix<T> (h, w);
    for (int c = 0; c < w; c++)
    {
      for (int r = 0; r < h; r++)
      {
		(*result)(r,c) = (*wrapped)(c,r) * scalar;
      }
    }
    return result;
  }


  // class MatrixRegion -------------------------------------------------------

  template<class T>
  MatrixRegion<T>::MatrixRegion (const MatrixAbstract<T> & that, const int firstRow, const int firstColumn, int lastRow, int lastColumn)
  {
	wrapped = &that;
	this->firstRow = firstRow;
	this->firstColumn = firstColumn;
	if (lastRow < 0)
	{
	  lastRow = that.rows () - 1;
	}
	if (lastColumn < 0)
	{
	  lastColumn = that.columns () - 1;
	}
	rows_ = lastRow - firstRow + 1;
	columns_ = lastColumn - firstColumn + 1;
  }

  template<class T>
  MatrixAbstract<T> *
  MatrixRegion<T>::clone (bool deep) const
  {
	if (deep)
	{
	  // Deep copy implies we have permission to disengage from the orginal
	  // matrix, so just realize a dense Matrix.
	  Matrix<T> * result = new Matrix<T> (rows_, columns_);
	  T * i = (T *) result->data;
	  for (int c = firstColumn; c < firstColumn + columns_; c++)
	  {
		for (int r = firstRow; r < firstRow + rows_; r++)
		{
		  *i++ = (*wrapped)(r,c);
		}
	  }
	  return result;
	}
	return new MatrixRegion<T> (*wrapped, firstRow, firstColumn, firstRow + rows_ - 1, firstColumn + columns_ - 1);
  }

  template<class T>
  MatrixRegion<T> &
  MatrixRegion<T>::operator = (const MatrixRegion<T> & that)
  {
	this->copyFrom (that);
	return *this;
  }

  template<class T>
  int
  MatrixRegion<T>::rows () const
  {
	return rows_;
  }

  template<class T>
  int
  MatrixRegion<T>::columns () const
  {
	return columns_;
  }

  template<class T>
  void
  MatrixRegion<T>::resize (const int rows, const int columns)
  {
	// We can't resize a region of the wrapped object, but we can change
	// the number of rows or columns in the view.
	rows_ = rows;
	columns_ = columns;
  }

  template<class T>
  void
  MatrixRegion<T>::clear (const T scalar)
  {
	for (int r = firstRow + rows_ - 1; r >= firstRow; r--)
	{
	  for (int c = firstColumn + columns_ - 1; c >= firstColumn; c--)
	  {
		(*wrapped)(r,c) = scalar;
	  }
	}
  }

  template<class T>
  MatrixResult<T>
  MatrixRegion<T>::operator * (const MatrixAbstract<T> & B) const
  {
	int w = std::min (columns (), B.rows ());
	int h = rows ();
	int bw = B.columns ();
	Matrix<T> * result = new Matrix<T> (h, bw);
	for (int c = 0; c < bw; c++)
	{
	  for (int r = 0; r < h; r++)
	  {
		register T element = (T) 0;
		for (int i = 0; i < w; i++)
		{
		  element += (*this)(r,i) * B(i,c);
		}
		(*result)(r,c) = element;
	  }
	}
	return result;
  }

  template<class T>
  MatrixResult<T>
  MatrixRegion<T>::operator * (const T scalar) const
  {
    int h = rows ();
    int w = columns ();
	Matrix<T> * result = new Matrix<T> (h, w);
    for (int c = 0; c < w; c++)
    {
      for (int r = 0; r < h; r++)
      {
		(*result)(r,c) = (*this)(r,c) * scalar;
      }
    }
    return result;
  }
}


#endif

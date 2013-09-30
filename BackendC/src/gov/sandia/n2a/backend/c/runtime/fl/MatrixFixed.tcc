/*
Author: Fred Rothganger
Created 2/29/08 to replace Matrix2x2.tcc and Matrix3x3.tcc

Copyright 2009 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the GNU Lesser General Public License.  See the file LICENSE
for details.
*/


#ifndef fl_matrix_fixed_tcc
#define fl_matrix_fixed_tcc


#include "fl/matrix.h"


namespace fl
{
  // class MatrixFixed<T,R,C> -------------------------------------------------

  template<class T, int R, int C>
  MatrixFixed<T,R,C>::MatrixFixed ()
  {
  }

  template<class T, int R, int C>
  uint32_t
  MatrixFixed<T,R,C>::classID () const
  {
	return MatrixAbstractID | MatrixFixedID;
  }

  template<class T, int R, int C>
  MatrixAbstract<T> *
  MatrixFixed<T,R,C>::clone (bool deep) const
  {
	return new MatrixFixed<T,R,C> (*this);
  }

  template<class T, int R, int C>
  void
  MatrixFixed<T,R,C>::copyFrom (const MatrixAbstract<T> & that, bool deep)
  {
	int h = std::min (R, that.rows ());
	int w = std::min (C, that.columns ());
	for (int c = 0; c < w; c++)
	{
	  for (int r = 0; r < h; r++)
	  {
		data[c][r] = that(r,c);
	  }
	  for (int r = h; r < R; r++) data[c][r] = (T) 0;
	}
	for (int c = w; c < C; c++)
	{
	  for (int r = 0; r < R; r++)
	  {
		data[c][r] = (T) 0;
	  }
	}
  }

  template<class T, int R, int C>
  int
  MatrixFixed<T,R,C>::rows () const
  {
	return R;
  }

  template<class T, int R, int C>
  int
  MatrixFixed<T,R,C>::columns () const
  {
	return C;
  }

  template<class T, int R, int C>
  void
  MatrixFixed<T,R,C>::resize (const int rows, const int columns)
  {
	assert (rows == R  &&  columns == C);
  }

  template<class T, int R, int C>
  MatrixResult<T>
  MatrixFixed<T,R,C>::row (const int r) const
  {
	return new MatrixStrided<T> (Pointer ((void *) data, R * C * sizeof (T)), r, 1, C, 1, C);
  }

  template<class T, int R, int C>
  MatrixResult<T>
  MatrixFixed<T,R,C>::column (const int c) const
  {
	return new MatrixStrided<T> (Pointer ((void *) data, R * C * sizeof (T)), c * C, R, 1, 1, C);
  }

  template<class T, int R, int C>
  MatrixResult<T>
  MatrixFixed<T,R,C>::region (const int firstRow, const int firstColumn, int lastRow, int lastColumn) const
  {
	if (lastRow < 0)
	{
	  lastRow = R - 1;
	}
	if (lastColumn < 0)
	{
	  lastColumn = C - 1;
	}
	int offset  = firstColumn * C + firstRow;
	int rows    = lastRow    - firstRow    + 1;
	int columns = lastColumn - firstColumn + 1;

	return new MatrixStrided<T> (Pointer ((void *) data, R * C * sizeof (T)), offset, rows, columns, 1, C);
  }

  template<class T, int R, int C>
  MatrixResult<T>
  MatrixFixed<T,R,C>::operator ~ () const
  {
	MatrixFixed<T,C,R> * result = new MatrixFixed<T,C,R>;
	for (int c = 0; c < C; c++)
	{
	  for (int r = 0; r < R; r++)
	  {
		result->data[r][c] = data[c][r];
	  }
	}
	return result;
  }

  template<class T, int R, int C>
  MatrixResult<T>
  MatrixFixed<T,R,C>::operator * (const MatrixAbstract<T> & B) const
  {
	int bh = B.rows ();
	int bw = B.columns ();
	int w = std::min (C, bh);
	Matrix<T> * result = new Matrix<T> (R, bw);
	T * ri = (T *) result->data;

	if (B.classID () & (MatrixFixedID | MatrixID))
	{
	  const T * bd = &B(0,0);
	  for (int c = 0; c < bw; c++)
	  {
		for (int r = 0; r < R; r++)
		{
		  const T * i   = &data[0][r];
		  const T * bi  = bd;
		  const T * end = bi + w;
		  register T element = (T) 0;
		  while (bi < end)
		  {
			element += (*i) * (*bi++);
			i += R;
		  }
		  *ri++ = element;
		}
		bd += bh;
	  }
	  return result;
	}

	for (int c = 0; c < bw; c++)
	{
	  for (int r = 0; r < R; r++)
	  {
		const T * i = &data[0][r];
		register T element = (T) 0;
		for (int j = 0; j < w; j++)
		{
		  element += (*i) * B (j, c);
		  i += R;
		}
		*ri++ = element;
	  }
	}
	return result;
  }

  template<class T, int R, int C>
  MatrixResult<T>
  MatrixFixed<T,R,C>::operator * (const T scalar) const
  {
	MatrixFixed<T,R,C> * result = new MatrixFixed<T,R,C>;
	const T * i = (T *) data;
	T * o       = (T *) result->data;
	T * end     = o + R * C;
	while (o < end) *o++ = *i++ * scalar;
	return result;
  }

  template<class T, int R, int C>
  MatrixResult<T>
  MatrixFixed<T,R,C>::operator / (const T scalar) const
  {
	MatrixFixed<T,R,C> * result = new MatrixFixed<T,R,C>;
	const T * i = (T *) data;
	T * o       = (T *) result->data;
	T * end     = o + R * C;
	while (o < end) *o++ = *i++ / scalar;
	return result;
  }

  template<class T, int R, int C>
  MatrixAbstract<T> &
  MatrixFixed<T,R,C>::operator *= (const T scalar)
  {
	T * i = (T *) data;
	T * end = i + R * C;
	while (i < end) *i++ *= scalar;
	return *this;
  }

  template<class T, int R, int C>
  MatrixAbstract<T> &
  MatrixFixed<T,R,C>::operator /= (const T scalar)
  {
	T * i = (T *) data;
	T * end = i + R * C;
	while (i < end) *i++ /= scalar;
	return *this;
  }

  template<class T, int R, int C>
  void
  MatrixFixed<T,R,C>::serialize (Archive & archive, uint32_t version)
  {
	if (archive.in) archive.in ->read  ((char *) data, R * C * sizeof (T));
	else            archive.out->write ((char *) data, R * C * sizeof (T));
  }
}


#endif

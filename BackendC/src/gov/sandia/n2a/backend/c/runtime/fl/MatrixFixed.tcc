/*
Author: Fred Rothganger
Created 2/29/08 to replace Matrix2x2.tcc and Matrix3x3.tcc

Copyright 2009, 2010 Sandia Corporation.
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
  // Matrix inversion ---------------------------------------------------------

  template <class T, int R, int C>
  inline MatrixResult<T>
  invert (const MatrixFixed<T,R,C> & A)
  {
	return A.MatrixAbstract<T>::operator ! ();
  }


  // class MatrixFixed<T,2,2> -------------------------------------------------

  template <class T>
  T
  det (const MatrixFixed<T,2,2> & A)
  {
	return A.data[0][0] * A.data[1][1] - A.data[0][1] * A.data[1][0];
  }

  template <class T>
  inline MatrixResult<T>
  invert (const MatrixFixed<T,2,2> & A)
  {
	MatrixFixed<T,2,2> * result = new MatrixFixed<T,2,2>;

	T q = det (A);
	if (q == 0) throw "invert: Matrix is singular!";

	result->data[0][0] = A.data[1][1] / q;
	result->data[0][1] = A.data[0][1] / -q;
	result->data[1][0] = A.data[1][0] / -q;
	result->data[1][1] = A.data[0][0] / q;

	return result;
  }

  template<class T>
  void
  geev (const MatrixFixed<T,2,2> & A, Matrix<T> & eigenvalues, bool destroyA)
  {
	// a = 1  :)
	T b = A.data[0][0] + A.data[1][1];  // trace
	T c = A.data[0][0] * A.data[1][1] - A.data[0][1] * A.data[1][0];  // determinant
	T b4c = b * b - 4 * c;
	if (b4c < 0) throw "geev: 2x2 matrix has immaginary eigenvalues, which we are not equipped to handle";
	if (b4c > 0) b4c = sqrt (b4c);

	eigenvalues.resize (2, 1);
	eigenvalues (0, 0) = (b - b4c) / 2;
	eigenvalues (1, 0) = (b + b4c) / 2;
  }

  template<class T>
  void
  geev (const MatrixFixed<T,2,2> & A, Matrix<T> & eigenvalues, Matrix<T> & eigenvectors, bool destroyA)
  {
	T b = A.data[0][0] + A.data[1][1];  // trace
	T c = A.data[0][0] * A.data[1][1] - A.data[0][1] * A.data[1][0];  // determinant
	T b4c = b * b - 4 * c;
	if (b4c < 0) throw "geev: 2x2 matrix has immaginary eigenvalues, which we are not equipped to handle";
	if (b4c > 0) b4c = sqrt (b4c);

	eigenvalues.resize (2, 1);
	eigenvalues[0] = (b - b4c) / 2;
	eigenvalues[1] = (b + b4c) / 2;

	eigenvectors.resize (2, 2);
	if (A.data[0][1] != 0)
	{
	  T e00 = eigenvalues[0] - A.data[1][1];
	  T e10 =                  A.data[0][1];
	  T e01 = eigenvalues[1] - A.data[1][1];
	  // e11 = e10
	  T norm = sqrt (e00 * e00 + e10 * e10);
	  eigenvectors(0,0) = e00 / norm;
	  eigenvectors(1,0) = e10 / norm;
	  norm = sqrt (e01 * e01 + e10 * e10);
	  eigenvectors(0,1) = e01 / norm;
	  eigenvectors(1,1) = e10 / norm;
	}
	else if (A.data[1][0] != 0)
	{
	  T e00 =                  A.data[1][0];
	  T e10 = eigenvalues[0] - A.data[0][0];
	  // e01 = e00
	  T e11 = eigenvalues[1] - A.data[0][0];
	  T norm = sqrt (e00 * e00 + e10 * e10);
	  eigenvectors(0,0) = e00 / norm;
	  eigenvectors(1,0) = e10 / norm;
	  norm = sqrt (e00 * e00 + e11 * e11);
	  eigenvectors(0,1) = e00 / norm;
	  eigenvectors(1,1) = e11 / norm;
	}
	else
	{
	  eigenvectors.identity ();
	}
  }


  // class MatrixFixed<T,3,3> -------------------------------------------------

  template<class T>
  T
  det (const MatrixFixed<T,3,3> & A)
  {
	return   A.data[0][0] * A.data[1][1] * A.data[2][2]
	       - A.data[0][0] * A.data[2][1] * A.data[1][2]
	       - A.data[1][0] * A.data[0][1] * A.data[2][2]
	       + A.data[1][0] * A.data[2][1] * A.data[0][2]
	       + A.data[2][0] * A.data[0][1] * A.data[1][2]
	       - A.data[2][0] * A.data[1][1] * A.data[0][2];
  }

  template<class T>
  inline MatrixResult<T>
  invert (const MatrixFixed<T,3,3> & A)
  {
	MatrixFixed<T,3,3> * result = new MatrixFixed<T,3,3>;

	T q = det (A);
	if (q == 0)
	{
	  throw "invert: Matrix is singular!";
	}

	// Ugly, but ensures we actually inline this code.
#   define det22(data,r0,r1,c0,c1) (data[c0][r0] * data[c1][r1] - data[c1][r0] * data[c0][r1])

	result->data[0][0] = det22 (A.data, 1, 2, 1, 2) / q;
	result->data[0][1] = det22 (A.data, 1, 2, 2, 0) / q;
	result->data[0][2] = det22 (A.data, 1, 2, 0, 1) / q;
	result->data[1][0] = det22 (A.data, 0, 2, 2, 1) / q;
	result->data[1][1] = det22 (A.data, 0, 2, 0, 2) / q;
	result->data[1][2] = det22 (A.data, 0, 2, 1, 0) / q;
	result->data[2][0] = det22 (A.data, 0, 1, 1, 2) / q;
	result->data[2][1] = det22 (A.data, 0, 1, 2, 0) / q;
	result->data[2][2] = det22 (A.data, 0, 1, 0, 1) / q;

	return result;
  }


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
  MatrixFixed<T,R,C>::operator ! () const
  {
	return invert (*this);
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

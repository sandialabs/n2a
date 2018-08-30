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
  // class MatrixFixed<T,2,2> -------------------------------------------------

  template <class T>
  inline T
  det (const MatrixFixed<T,2,2> & A)
  {
	return A.data[0][0] * A.data[1][1] - A.data[0][1] * A.data[1][0];
  }

  template <class T>
  MatrixFixed<T,2,2>
  operator ! (const MatrixFixed<T,2,2> & A)
  {
	MatrixFixed<T,2,2> result;

	T q = det (A);
	if (q == 0) N2A_THROW ("invert: Matrix is singular!");

	result.data[0][0] = A.data[1][1] /  q;
	result.data[0][1] = A.data[0][1] / -q;
	result.data[1][0] = A.data[1][0] / -q;
	result.data[1][1] = A.data[0][0] /  q;

	return result;
  }


  // class MatrixFixed<T,3,3> -------------------------------------------------

  template<class T>
  inline T
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
  MatrixFixed<T,3,3>
  operator ! (const MatrixFixed<T,3,3> & A)
  {
	MatrixFixed<T,3,3> result;

	T q = det (A);
	if (q == 0) N2A_THROW ("invert: Matrix is singular!");

	// Ugly, but ensures we actually inline this code.
#   define det22(data,r0,r1,c0,c1) (data[c0][r0] * data[c1][r1] - data[c1][r0] * data[c0][r1])

	result.data[0][0] = det22 (A.data, 1, 2, 1, 2) / q;
	result.data[0][1] = det22 (A.data, 1, 2, 2, 0) / q;
	result.data[0][2] = det22 (A.data, 1, 2, 0, 1) / q;
	result.data[1][0] = det22 (A.data, 0, 2, 2, 1) / q;
	result.data[1][1] = det22 (A.data, 0, 2, 0, 2) / q;
	result.data[1][2] = det22 (A.data, 0, 2, 1, 0) / q;
	result.data[2][0] = det22 (A.data, 0, 1, 1, 2) / q;
	result.data[2][1] = det22 (A.data, 0, 1, 2, 0) / q;
	result.data[2][2] = det22 (A.data, 0, 1, 0, 1) / q;

	return result;
  }


  // class MatrixFixed<T,R,C> -------------------------------------------------

  template<class T, int R, int C>
  MatrixFixed<T,R,C>::MatrixFixed ()
  {
  }

  template<class T, int R, int C>
  MatrixFixed<T,R,C>::MatrixFixed (const MatrixAbstract<T> & that)
  {
    int h = std::min (R, that.rows ());
    int w = std::min (C, that.columns ());
    int c;
    for (c = 0; c < w; c++)
    {
      int r;
      for (r = 0; r < h; r++)
      {
        data[c][r] = that(r,c);
      }
      for (; r < R; r++) data[c][r] = (T) 0;
    }
    for (; c < C; c++)
    {
      for (int r = 0; r < R; r++)
      {
        data[c][r] = (T) 0;
      }
    }
  }

  template<class T, int R, int C>
  uint32_t
  MatrixFixed<T,R,C>::classID () const
  {
	return MatrixStridedID | MatrixFixedID;
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
  T *
  MatrixFixed<T,R,C>::base () const
  {
    return const_cast<T *> (data[0]);
  }

  template<class T, int R, int C>
  int
  MatrixFixed<T,R,C>::strideR () const
  {
    return 1;
  }

  template<class T, int R, int C>
  int
  MatrixFixed<T,R,C>::strideC () const
  {
    return R;
  }

  template<class T, int R, int C>
  MatrixFixed<T,C,R>
  operator ~ (const MatrixFixed<T,R,C> & A)
  {
	MatrixFixed<T,C,R> result;
	for (int c = 0; c < C; c++)
	{
	  for (int r = 0; r < R; r++)
	  {
		result.data[r][c] = A.data[c][r];
	  }
	}
	return result;
  }

  template<class T, int R, int C>
  MatrixFixed<T,R,C>
  operator * (const MatrixFixed<T,R,C> & A, const T scalar)
  {
    MatrixFixed<T,R,C> result;
    const T * i = A.data[0];
    T * o       = result.data[0];
    T * end     = o + R * C;
    while (o < end) *o++ = *i++ * scalar;
    return result;
  }

  template<class T, int R, int C>
  MatrixFixed<T,R,C>
  operator / (const MatrixFixed<T,R,C> & A, const T scalar)
  {
    MatrixFixed<T,R,C> result;
    const T * i = A.data[0];
    T * o       = result.data[0];
    T * end     = o + R * C;
    while (o < end) *o++ = *i++ / scalar;
    return result;
  }
}


#endif

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


#ifndef fl_vector_tcc
#define fl_vector_tcc


#include "fl/matrix.h"

#include <typeinfo>


namespace fl
{
  // class Vector<T> ----------------------------------------------------------

  template<class T>
  Vector<T>::Vector ()
  {
  }

  template<class T>
  Vector<T>::Vector (const int rows)
  : Matrix<T> (rows, 1)
  {
  }

  template<class T>
  Vector<T>::Vector (const MatrixAbstract<T> & that)
  {
	if (that.classID () & MatrixStridedID)
	{
	  const MatrixStrided<T> & MS = (const MatrixStrided<T> &) that;
	  if (MS.offset == 0  &&  MS.strideR == 1  &&  MS.strideC == MS.rows_)
	  {
		MatrixStrided<T>::operator = (MS);
		this->strideC = this->rows_ *= this->columns_;
		this->columns_ = 1;
		return;
	  }
	}

	int h = that.rows ();
	int w = that.columns ();
	resize (h * w, 1);
	T * i = (T *) this->data;
	for (int c = 0; c < w; c++)
	{
	  for (int r = 0; r < h; r++)
	  {
		*i++ = that(r,c);
	  }
	}
	// Implicitly, a MatrixPacked will convert into a Vector with rows * rows
	// elements.  If that is the wrong behaviour, add another case here.
  }

  template<class T>
  Vector<T>::Vector (const Matrix<T> & that)
  {
	this->data     = that.data;
	this->rows_    = that.rows_ * that.columns_;
	this->columns_ = 1;
	this->strideC  = this->rows_;
  }

  template<class T>
  Vector<T>::Vector (const std::string & source)
  {
	(*this) = Matrix<T> (source);
  }

  template<class T>
  Vector<T>::Vector (T * that, const int rows)
  : Matrix<T> (that, rows, 1)
  {
  }

  template<class T>
  Vector<T>::Vector (Pointer & that, const int rows)
  : Matrix<T> (that, rows, 1)
  {
  }

  template<class T>
  void
  Vector<T>::resize (const int rows, const int columns)
  {
	Matrix<T>::resize (rows * columns, 1);
  }
}


#endif

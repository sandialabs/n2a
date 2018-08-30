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


#ifndef fl_matrix_sparse_tcc
#define fl_matrix_sparse_tcc


#include "fl/matrix.h"


namespace fl
{
  template<class T>
  MatrixSparse<T>::MatrixSparse ()
  {
	rows_ = 0;
	data.initialize ();
  }

  template<class T>
  MatrixSparse<T>::MatrixSparse (const int rows, const int columns)
  {
    rows_ = rows;
	data.initialize ();
    data->resize (columns);
  }

  template<class T>
  MatrixSparse<T>::MatrixSparse (const MatrixAbstract<T> & that)
  {
	if (that.classID () & MatrixSparseID)
	{
	  const MatrixSparse<T> & S = (const MatrixSparse<T> &) that;
	  rows_ = S.rows_;
	  data  = S.data;
	}
	else
	{
	  int m = that.rows ();
	  int n = that.columns ();
	  rows_ = m;
      data.initialize ();
	  data->resize (n);
	  for (int c = 0; c < n; c++)
	  {
		for (int r = 0; r < m; r++)
		{
		  set (r, c, that(r,c));
		}
	  }
	}
  }

  template<class T>
  uint32_t
  MatrixSparse<T>::classID () const
  {
	return MatrixSparseID;
  }

  template<class T>
  void
  MatrixSparse<T>::set (const int row, const int column, const T value)
  {
	if (value == (T) 0)
	{
	  if (column < data->size ())
	  {
		(*data)[column].erase (row);
	  }
	}
	else
	{
	  if (row >= rows_)
	  {
		rows_ = row + 1;
	  }
	  if (column >= data->size ())
	  {
		data->resize (column + 1);
	  }
	  (*data)[column][row] = value;
	}
  }

  template<class T>
  T &
  MatrixSparse<T>::operator () (const int row, const int column) const
  {
	if (column < data->size ())
	{
	  std::map<int, T> & c = (*data)[column];
	  typename std::map<int, T>::iterator i = c.find (row);
	  if (i != c.end ())
	  {
		return i->second;
	  }
	}
	static T zero;
	zero = (T) 0;
	return zero;
  }

  template<class T>
  int
  MatrixSparse<T>::rows () const
  {
	return rows_;
  }

  template<class T>
  int
  MatrixSparse<T>::columns () const
  {
	return data->size ();
  }
}

#endif

/*
Author: Fred Rothganger
Copyright (c) 2001-2004 Dept. of Computer Science and Beckman Institute,
                        Univ. of Illinois.  All rights reserved.
Distributed under the UIUC/NCSA Open Source License.


Copyright 2005-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_matrix_sparse_tcc
#define n2a_matrix_sparse_tcc


#include "matrix.h"


template<class T>
MatrixSparse<T>::MatrixSparse ()
:   data (std::make_shared<std::vector<std::map<int,T>>> ())
{
    rows_ = 0;
}

template<class T>
MatrixSparse<T>::MatrixSparse (const int rows, const int columns)
:   data (std::make_shared<std::vector<std::map<int,T>>> ())
{
    rows_ = rows;
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
        data = std::make_shared<std::vector<std::map<int,T>>> ();
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
        if (i != c.end ()) return i->second;
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

#endif

/*
Author: Fred Rothganger
Copyright (c) 2001-2004 Dept. of Computer Science and Beckman Institute,
                        Univ. of Illinois.  All rights reserved.
Distributed under the UIUC/NCSA Open Source License.


Copyright 2005-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_matrix_sparse_tcc
#define n2a_matrix_sparse_tcc


#include "matrix.h"


// MatrixSparse --------------------------------------------------------------

template<class T>
MatrixSparse<T>::MatrixSparse ()
:   data (std::make_shared<std::vector<std::map<int,T>>> ())
{
    rows_      = 0;
    emptyValue = (T) 0;
}

template<class T>
MatrixSparse<T>::MatrixSparse (const int rows, const int columns)
:   data (std::make_shared<std::vector<std::map<int,T>>> ())
{
    rows_      = rows;
    data->resize (columns);
    emptyValue = (T) 0;
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
    emptyValue = (T) 0;
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
    return (T &) emptyValue;
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


// MatrixSparseRegion --------------------------------------------------------

template<class T>
MatrixSparseRegion<T>::MatrixSparseRegion (MatrixSparse<T> & that, int firstRow, int firstColumn, int lastRow, int lastColumn)
{
    if (firstRow    < 0) firstRow    = 0;
    if (firstColumn < 0) firstColumn = 0;
    if (lastRow     < 0) lastRow     = that.rows ()    - 1;
    if (lastColumn  < 0) lastColumn  = that.columns () - 1;

    this->data     = that.data;
    ar             = firstRow;
    ac             = firstColumn;
    this->rows_    = lastRow    - firstRow    + 1;  // Changes meaning. Now relative to ar rather than 0.
    this->columns_ = lastColumn - firstColumn + 1;
}

template<class T>
uint32_t
MatrixSparseRegion<T>::classID () const
{
    return MatrixSparseRegionID;
}

template<class T>
void
MatrixSparseRegion<T>::set (const int row, const int column, const T value)
{
    int r = ar + row;
    int c = ac + column;
    MatrixSparse<T>::set (r, c, value);                         // Change is also visible in the original sparse matrix, since data is shared.
    if (row    >= this->rows_)    this->rows_    = row    + 1;  // However, original matrix does not share our rows_, and they mean slightly different things.
    if (column >= this->columns_) this->columns_ = column + 1;
}

template<class T>
T &
MatrixSparseRegion<T>::operator () (const int row, const int column) const
{
    return MatrixSparse<T>::operator() (ar + row, ac + column);
}

template<class T>
int
MatrixSparseRegion<T>::columns () const
{
    return this->columns_;
}


#endif

/*
Author: Fred Rothganger
Copyright (c) 2001-2004 Dept. of Computer Science and Beckman Institute,
                        Univ. of Illinois.  All rights reserved.
Distributed under the UIUC/NCSA Open Source License.


Copyright 2005-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


#ifndef n2a_matrix_tcc
#define n2a_matrix_tcc


#include "mymath.h"
#include "matrix.h"
#include "mystring.h"


// class MatrixAbstract<T> --------------------------------------------------

template<class T>
MatrixAbstract<T>::~MatrixAbstract ()
{
}

template<class T>
void
clear (const MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            A(r,c) = scalar;
        }
    }
}

template<class T>
void
identity (const MatrixAbstract<T> & A)
{
    int h = A.rows ();
    int w = A.columns ();
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            A(r,c) = (r == c) ? (T) 1 : (T) 0;
        }
    }
}

template<class T>
void
copy (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int ah = A.rows ();
    int aw = A.columns ();
    int bh = B.rows ();
    int bw = B.columns ();
    int h = std::min (ah, bh);
    int w = std::min (aw, bw);
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            A(r,c) = B(r,c);
        }
    }
}

template<class T>
T
norm (const MatrixAbstract<T> & A, T n)
{
    int h = A.rows ();
    int w = A.columns ();
    if (n == (T) INFINITY)
    {
        T result = (T) 0;
        for (int c = 0; c < w; c++)
        {
            for (int r = 0; r < h; r++)
            {
                result = std::max (std::abs (A(r,c)), result);
            }
        }
        return result;
    }
    else if (n == (T) 0)
    {
        unsigned int result = 0;
        for (int c = 0; c < w; c++)
        {
            for (int r = 0; r < h; r++)
            {
                if (A(r,c) != (T) 0) result++;
            }
        }
        return (T) result;
    }
    else if (n == (T) 1)
    {
        T result = (T) 0;
        for (int c = 0; c < w; c++)
        {
            for (int r = 0; r < h; r++)
            {
                result += std::abs (A(r,c));
            }
        }
        return result;
    }
    else if (n == (T) 2)
    {
        T result = (T) 0;
        for (int c = 0; c < w; c++)
        {
            for (int r = 0; r < h; r++)
            {
                T t = A(r,c);
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
                result += (T) std::pow (std::abs (A(r,c)), (T) n);
            }
        }
        return (T) std::pow (result, (T) (1.0 / n));
    }
}

template<class T>
Matrix<T>
normalize (const MatrixAbstract<T> & A)
{
    return A / norm (A, (T) 2);
}

template<class T>
T
sumSquares (const MatrixAbstract<T> & A)
{
    int h = A.rows ();
    int w = A.columns ();
    T result = (T) 0;
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            T t = A(r,c);
            result += t * t;
        }
    }
    return result;
}

template<class T>
Matrix<T>
cross (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int ah = A.rows ();
    int bh = B.rows ();
    int h = std::min (ah, bh);
    Matrix<T> result (h, 1);
    for (int i = 0; i < h; i++)
    {
        int j = (i + 1) % h;
        int k = (i + 2) % h;
        result[i] = A(j,0) * B(k,0) - A(k,0) * B(j,0);
    }
    return result;
}

template<class T>
Matrix<T>
visit (const MatrixAbstract<T> & A, T (*function) (const T &))
{
    return visit (Matrix<T> (A), function);
}

template<class T>
Matrix<T>
visit (const MatrixAbstract<T> & A, T (*function) (const T))
{
    return visit (Matrix<T> (A), function);
}

template<class T>
bool
equal (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    if (B.rows () != h  ||  B.columns () != w) return false;

    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            if (A(r,c) != B(r,c)) return false;
        }
    }
    return true;
}

template<class T>
Matrix<T>
operator == (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    int oh = std::min (h, B.rows ());
    int ow = std::min (w, B.columns ());
    Matrix<T> result (h, w);
    for (int c = 0; c < ow; c++)
    {
        for (int r = 0;  r < oh; r++) result(r,c) =  A(r,c) == B(r,c);
        for (int r = oh; r < h;  r++) result(r,c) =  A(r,c) == 0;
    }
    for (int c = ow; c < w; c++)
    {
        for (int r = 0;  r < h;  r++) result(r,c) =  A(r,c) == 0;
    }
    return result;
}

template<class T>
Matrix<T>
operator == (const MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    Matrix<T> result (h, w);
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            result(r,c) =  A(r,c) == scalar;
        }
    }
    return result;
}

template<class T>
Matrix<T>
operator != (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    int oh = std::min (h, B.rows ());
    int ow = std::min (w, B.columns ());
    Matrix<T> result (h, w);
    for (int c = 0; c < ow; c++)
    {
        for (int r = 0;  r < oh; r++) result(r,c) =  A(r,c) != B(r,c);
        for (int r = oh; r < h;  r++) result(r,c) =  A(r,c) != 0;
    }
    for (int c = ow; c < w; c++)
    {
        for (int r = 0;  r < h;  r++) result(r,c) =  A(r,c) != 0;
    }
    return result;
}

template<class T>
Matrix<T>
operator != (const MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    Matrix<T> result (h, w);
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            result(r,c) =  A(r,c) != scalar;
        }
    }
    return result;
}

template<class T>
Matrix<T>
operator < (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    int oh = std::min (h, B.rows ());
    int ow = std::min (w, B.columns ());
    Matrix<T> result (h, w);
    for (int c = 0; c < ow; c++)
    {
        for (int r = 0;  r < oh; r++) result(r,c) =  A(r,c) < B(r,c);
        for (int r = oh; r < h;  r++) result(r,c) =  A(r,c) < 0;
    }
    for (int c = ow; c < w; c++)
    {
        for (int r = 0;  r < h;  r++) result(r,c) =  A(r,c) < 0;
    }
    return result;
}

template<class T>
Matrix<T>
operator < (const MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    Matrix<T> result (h, w);
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            result(r,c) =  A(r,c) < scalar;
        }
    }
    return result;
}

template<class T>
Matrix<T>
operator <= (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    int oh = std::min (h, B.rows ());
    int ow = std::min (w, B.columns ());
    Matrix<T> result (h, w);
    for (int c = 0; c < ow; c++)
    {
        for (int r = 0;  r < oh; r++) result(r,c) =  A(r,c) <= B(r,c);
        for (int r = oh; r < h;  r++) result(r,c) =  A(r,c) <= 0;
    }
    for (int c = ow; c < w; c++)
    {
        for (int r = 0;  r < h;  r++) result(r,c) =  A(r,c) <= 0;
    }
    return result;
}

template<class T>
Matrix<T>
operator <= (const MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    Matrix<T> result (h, w);
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            result(r,c) =  A(r,c) <= scalar;
        }
    }
    return result;
}

template<class T>
Matrix<T>
operator > (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    int oh = std::min (h, B.rows ());
    int ow = std::min (w, B.columns ());
    Matrix<T> result (h, w);
    for (int c = 0; c < ow; c++)
    {
        for (int r = 0;  r < oh; r++) result(r,c) =  A(r,c) > B(r,c);
        for (int r = oh; r < h;  r++) result(r,c) =  A(r,c) > 0;
    }
    for (int c = ow; c < w; c++)
    {
        for (int r = 0;  r < h;  r++) result(r,c) =  A(r,c) > 0;
    }
    return result;
}

template<class T>
Matrix<T>
operator > (const MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    Matrix<T> result (h, w);
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            result(r,c) =  A(r,c) > scalar;
        }
    }
    return result;
}

template<class T>
Matrix<T>
operator >= (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    int oh = std::min (h, B.rows ());
    int ow = std::min (w, B.columns ());
    Matrix<T> result (h, w);
    for (int c = 0; c < ow; c++)
    {
        for (int r = 0;  r < oh; r++) result(r,c) =  A(r,c) >= B(r,c);
        for (int r = oh; r < h;  r++) result(r,c) =  A(r,c) >= 0;
    }
    for (int c = ow; c < w; c++)
    {
        for (int r = 0;  r < h;  r++) result(r,c) =  A(r,c) >= 0;
    }
    return result;
}

template<class T>
Matrix<T>
operator >= (const MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    Matrix<T> result (h, w);
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            result(r,c) =  A(r,c) >= scalar;
        }
    }
    return result;
}

template<class T>
Matrix<T>
operator && (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    int oh = std::min (h, B.rows ());
    int ow = std::min (w, B.columns ());
    Matrix<T> result (h, w);
    for (int c = 0; c < ow; c++)
    {
        for (int r = 0;  r < oh; r++) result(r,c) =  A(r,c) && B(r,c);
        for (int r = oh; r < h;  r++) result(r,c) = 0;
    }
    for (int c = ow; c < w; c++)
    {
        for (int r = 0;  r < h;  r++) result(r,c) = 0;
    }
    return result;
}

template<class T>
Matrix<T>
operator && (const MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    Matrix<T> result (h, w);
    if (scalar == 0)  // Early-out, because all the elements will be false.
    {
        clear (result);
        return result;
    }
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            result(r,c) =  A(r,c) != 0;
        }
    }
    return result;
}

template<class T>
Matrix<T>
operator || (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    int oh = std::min (h, B.rows ());
    int ow = std::min (w, B.columns ());
    Matrix<T> result (h, w);
    for (int c = 0; c < ow; c++)
    {
        for (int r = 0;  r < oh; r++) result(r,c) =  A(r,c) || B(r,c);
        for (int r = oh; r < h;  r++) result(r,c) =  A(r,c) != 0;
    }
    for (int c = ow; c < w; c++)
    {
        for (int r = 0;  r < h;  r++) result(r,c) =  A(r,c) != 0;
    }
    return result;
}

template<class T>
Matrix<T>
operator || (const MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    Matrix<T> result (h, w);
    if (scalar != 0)  // Early-out, because all the elements will be true.
    {
        clear (result, (T) 1);
        return result;
    }
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            result(r,c) =  A(r,c) != 0;
        }
    }
    return result;
}

template<class T>
Matrix<T>
operator & (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    int oh = std::min (h, B.rows ());
    int ow = std::min (w, B.columns ());
    Matrix<T> result (h, w);
    for (int c = 0; c < ow; c++)
    {
        for (int r = 0;  r < oh; r++) result(r,c) = A(r,c) * B(r,c);
        for (int r = oh; r < h;  r++) result(r,c) = A(r,c);
    }
    for (int c = ow; c < w; c++)
    {
        for (int r = 0;  r < h;  r++) result(r,c) = A(r,c);
    }
    return result;
}

template<class T>
Matrix<T>
operator * (const MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    Matrix<T> result (h, w);
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            result(r,c) = A(r,c) * scalar;
        }
    }
    return result;
}

template<class T>
Matrix<T>
operator / (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    int oh = std::min (h, B.rows ());
    int ow = std::min (w, B.columns ());
    Matrix<T> result (h, w);
    for (int c = 0; c < ow; c++)
    {
        for (int r = 0;  r < oh; r++) result(r,c) = A(r,c) / B(r,c);
        for (int r = oh; r < h;  r++) result(r,c) = A(r,c);
    }
    for (int c = ow; c < w; c++)
    {
        for (int r = 0;  r < h;  r++) result(r,c) = A(r,c);
    }
    return result;
}

template<class T>
Matrix<T>
operator / (const MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    Matrix<T> result (h, w);
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            result(r,c) = A(r,c) / scalar;
        }
    }
    return result;
}

template<class T>
Matrix<T>
operator / (const T scalar, const MatrixAbstract<T> & A)
{
    int h = A.rows ();
    int w = A.columns ();
    Matrix<T> result (h, w);
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            result(r,c) = scalar / A(r,c);
        }
    }
    return result;
}

template<class T>
Matrix<T>
operator + (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    int oh = std::min (h, B.rows ());
    int ow = std::min (w, B.columns ());
    Matrix<T> result (h, w);
    for (int c = 0; c < ow; c++)
    {
        for (int r = 0;  r < oh; r++) result(r,c) = A(r,c) + B(r,c);
        for (int r = oh; r < h;  r++) result(r,c) = A(r,c);
    }
    for (int c = ow; c < w; c++)
    {
        for (int r = 0;  r < h;  r++) result(r,c) = A(r,c);
    }
    return result;
}

template<class T>
Matrix<T>
operator + (const MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    Matrix<T> result (h, w);
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            result(r,c) = A(r,c) + scalar;
        }
    }
    return result;
}

template<class T>
Matrix<T>
operator - (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    int oh = std::min (h, B.rows ());
    int ow = std::min (w, B.columns ());
    Matrix<T> result (h, w);
    for (int c = 0; c < ow; c++)
    {
        for (int r = 0;  r < oh; r++) result(r,c) = A(r,c) - B(r,c);
        for (int r = oh; r < h;  r++) result(r,c) = A(r,c);
    }
    for (int c = ow; c < w; c++)
    {
        for (int r = 0;  r < h;  r++) result(r,c) = A(r,c);
    }
    return result;
}

template<class T>
Matrix<T>
operator - (const MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    Matrix<T> result (h, w);
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            result(r,c) = A(r,c) - scalar;
        }
    }
    return result;
}

template<class T>
Matrix<T>
operator - (const T scalar, const MatrixAbstract<T> & A)
{
    int h = A.rows ();
    int w = A.columns ();
    Matrix<T> result (h, w);
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            result(r,c) = scalar - A(r,c);
        }
    }
    return result;
}

template<class T>
void
operator *= (MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    int oh = std::min (h, B.rows ());
    int ow = std::min (w, B.columns ());
    // Because the multiplicative identity is 1, elements of A outside the overlap remain unchanged.
    for (int c = 0; c < ow; c++)
    {
        for (int r = 0; r < oh; r++)
        {
            A(r,c) *= B(r,c);
        }
    }
}

template<class T>
void
operator *= (MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            A(r,c) *= scalar;
        }
    }
}

template<class T>
void
operator /= (MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    int oh = std::min (h, B.rows ());
    int ow = std::min (w, B.columns ());
    for (int c = 0; c < ow; c++)
    {
        for (int r = 0; r < oh; r++)
        {
            A(r,c) /= B(r,c);
        }
    }
}

template<class T>
void
operator /= (MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            A(r,c) /= scalar;
        }
    }
}

template<class T>
void
operator += (MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    int oh = std::min (h, B.rows ());
    int ow = std::min (w, B.columns ());
    for (int c = 0; c < ow; c++)
    {
        for (int r = 0; r < oh; r++)
        {
            A(r,c) += B(r,c);
        }
    }
}

template<class T>
void
operator += (MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            A(r,c) += scalar;
        }
    }
}

template<class T>
void
operator -= (MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    int oh = std::min (h, B.rows ());
    int ow = std::min (w, B.columns ());
    for (int c = 0; c < ow; c++)
    {
        for (int r = 0; r < oh; r++)
        {
            A(r,c) -= B(r,c);
        }
    }
}

template<class T>
void
operator -= (MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            A(r,c) -= scalar;
        }
    }
}

template<class T>
Matrix<T>
min (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    int oh = std::min (h, B.rows ());
    int ow = std::min (w, B.columns ());
    Matrix<T> result (h, w);
    for (int c = 0; c < ow; c++)
    {
        for (int r = 0;  r < oh; r++) result(r,c) = std::min (A(r,c), B(r,c));
        for (int r = oh; r < h;  r++) result(r,c) = std::min (A(r,c), (T) 0);
    }
    for (int c = ow; c < w; c++)
    {
        for (int r = 0;  r < h;  r++) result(r,c) = std::min (A(r,c), (T) 0);
    }
    return result;
}

template<class T>
Matrix<T>
min (const MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    Matrix<T> result (h, w);
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            result(r,c) = std::min (A(r,c), scalar);
        }
    }
    return result;
}

template<class T>
Matrix<T>
max (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B)
{
    int h = A.rows ();
    int w = A.columns ();
    int oh = std::min (h, B.rows ());
    int ow = std::min (w, B.columns ());
    Matrix<T> result (h, w);
    for (int c = 0; c < ow; c++)
    {
        for (int r = 0;  r < oh; r++) result(r,c) = std::max (A(r,c), B(r,c));
        for (int r = oh; r < h;  r++) result(r,c) = std::max (A(r,c), (T) 0);
    }
    for (int c = ow; c < w; c++)
    {
        for (int r = 0;  r < h;  r++) result(r,c) = std::max (A(r,c), (T) 0);
    }
    return result;
}

template<class T>
Matrix<T>
max (const MatrixAbstract<T> & A, const T scalar)
{
    int h = A.rows ();
    int w = A.columns ();
    Matrix<T> result (h, w);
    for (int c = 0; c < w; c++)
    {
        for (int r = 0; r < h; r++)
        {
            result(r,c) = std::max (A(r,c), scalar);
        }
    }
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

    String line = columns > 1 ? "[" : "~[";
    int r = 0;
    while (true)
    {
        int c = 0;
        while (true)
        {
            line += A(r,c);
            if (++c >= columns) break;
            line += ' ';
            while (line.size () < c * 10 + 1)  // display width is 10; add 1 to allow for opening "[" all the way down
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


// class MatrixStrided<T> ---------------------------------------------------

template<class T>
void
clear (MatrixStrided<T> & A, const T scalar)
{
    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    int stepC = sc - h * sr;
    T * i     = A.base ();
    T * end   = i + w * sc;
    while (i != end)
    {
        T * columnEnd = i + h * sr;
        while (i != columnEnd)
        {
            *i = scalar;
            i += sr;
        }
        i += stepC;
    }
}

template<class T>
T
norm (const MatrixStrided<T> & A, T n)
{
    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    int stepC = sc - h * sr;
    T * i     = A.base ();
    T * end   = i + w * sc;
    if (n == (T) INFINITY)
    {
        T result = (T) 0;
        while (i != end)
        {
            T * columnEnd = i + h * sr;
            while (i != columnEnd)
            {
                result = std::max (std::abs (*i), result);
                i += sr;
            }
            i += stepC;
        }
        return result;
    }
    else if (n == (T) 0)
    {
        unsigned int result = 0;
        while (i != end)
        {
            T * columnEnd = i + h * sr;
            while (i != columnEnd)
            {
                if (*i != (T) 0) result++;
                i += sr;
            }
            i += stepC;
        }
        return (T) result;
    }
    else if (n == (T) 1)
    {
        T result = (T) 0;
        while (i != end)
        {
            T * columnEnd = i + h * sr;
            while (i != columnEnd)
            {
                result += std::abs (*i);
                i += sr;
            }
            i += stepC;
        }
        return result;
    }
    else if (n == (T) 2)
    {
        T result = (T) 0;
        while (i != end)
        {
            T * columnEnd = i + h * sr;
            while (i != columnEnd)
            {
                result += (*i) * (*i);
                i += sr;
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
            T * columnEnd = i + h * sr;
            while (i != columnEnd)
            {
                result += (T) std::pow (std::abs (*i), (T) n);
                i += sr;
            }
            i += stepC;
        }
        return (T) std::pow (result, (T) (1.0 / n));
    }
}

template<class T>
Matrix<T>
visit (const MatrixStrided<T> & A, T (*function) (const T &))
{
    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    Matrix<T> result (h, w);
    int step = sc - h * sr;
    T * r    = result.base ();
    T * a    = A.base ();
    T * end  = a + sc * w;
    while (a != end)
    {
        T * columnEnd = a + h * sr;
        while (a != columnEnd)
        {
            *r++ = (*function) (*a);
            a += sr;
        }
        a += step;
    }

    return result;
}

template<class T>
Matrix<T>
visit (const MatrixStrided<T> & A, T (*function) (const T))
{
    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    Matrix<T> result (h, w);
    int step = sc - h * sr;
    T * r    = result.base ();
    T * a    = A.base ();
    T * end  = a + sc * w;
    while (a != end)
    {
        T * columnEnd = a + h * sr;
        while (a != columnEnd)
        {
            *r++ = (*function) (*a);
            a += sr;
        }
        a += step;
    }

    return result;
}

template<class T>
Matrix<T>
operator & (const MatrixStrided<T> & A, const MatrixAbstract<T> & B)
{
    if ((B.classID () & MatrixStridedID) == 0) return operator & ((const MatrixAbstract<T> &) A, B);

    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    const MatrixStrided<T> & MB = (const MatrixStrided<T> &) B;
    int bh  = MB.rows ();
    int bw  = MB.columns ();
    int bsc = MB.strideC ();
    int bsr = MB.strideR ();

    Matrix<T> result (h, w);
    int oh = std::min (h, bh);
    int ow = std::min (w, bw);
    int stepA = sc  - h  * sr;
    int stepB = bsc - oh * bsr;
    T * a   = A.base ();
    T * b   = MB.base ();
    T * r   = result.base ();
    T * end = r + h * ow;
    while (r < end)
    {
        T * overlapEnd = r + oh;
        T * columnEnd  = r + h;
        while (r < overlapEnd)
        {
            *r++ = *a * *b;
            a += sr;
            b += bsr;
        }
        while (r < columnEnd)
        {
            *r++ = *a;
            a += sr;
        }
        a += stepA;
        b += stepB;
    }
    end += h * (w - ow);
    while (r < end)
    {
        T * columnEnd = r + h;
        while (r < columnEnd)
        {
            *r++ = *a;
            a += sr;
        }
        a += stepA;
    }

    return result;
}

template<class T>
Matrix<T>
operator * (const MatrixStrided<T> & A, const MatrixAbstract<T> & B)
{
    if ((B.classID () & MatrixStridedID) == 0) return operator * (A, Matrix<T> (B));  // For efficiency's sake, realize the second matrix rather than repeatedly calling its accessors for each given element.

    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    const MatrixStrided<T> & MB = (const MatrixStrided<T> &) B;
    int bh  = MB.rows ();
    int bw  = MB.columns ();
    int bsc = MB.strideC ();
    int bsr = MB.strideR ();

    Matrix<T> result (h, bw);
    int ow = std::min (w, bh);
    T * aa  = A.base ();
    T * b   = MB.base ();
    T * c   = result.base ();
    T * end = c + h * bw;
    while (c < end)
    {
        T * a = aa;
        T * columnEnd = c + h;
        while (c < columnEnd)
        {
            T element = (T) 0;
            T * i = a;
            T * j = b;
            T * rowEnd = j + ow * bsr;
            while (j != rowEnd)
            {
                element += (*i) * (*j);
                i += sc;
                j += bsr;
            }
            *c++ = element;
            a += sr;
        }
        b += bsc;
    }
    return result;
}

template<class T>
Matrix<T>
operator * (const MatrixStrided<T> & A, const T scalar)
{
    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    Matrix<T> result (h, w);
    int stepC = sc - h * sr;
    T * i   = A.base ();
    T * r   = result.base ();
    T * end = r + h * w;
    while (r < end)
    {
        T * columnEnd = r + h;
        while (r < columnEnd)
        {
            *r++ = *i * scalar;
            i += sr;
        }
        i += stepC;
    }
    return result;
}

template<class T>
Matrix<T>
operator / (const MatrixStrided<T> & A, const MatrixAbstract<T> & B)
{
    if ((B.classID () & MatrixStridedID) == 0) return operator / ((const MatrixAbstract<T> &) A, B);

    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    const MatrixStrided<T> & MB = (const MatrixStrided<T> &) B;
    int bh  = MB.rows ();
    int bw  = MB.columns ();
    int bsc = MB.strideC ();
    int bsr = MB.strideR ();

    Matrix<T> result (h, w);
    int oh = std::min (h, bh);
    int ow = std::min (w, bw);
    int stepA = sc  - h  * sr;
    int stepB = bsc - oh * bsr;
    T * a   = A.base ();
    T * b   = MB.base ();
    T * r   = result.base ();
    T * end = r + h * ow;
    while (r < end)
    {
        T * overlapEnd = r + oh;
        T * columnEnd  = r + h;
        while (r < overlapEnd)
        {
            *r++ = *a / *b;
            a += sr;
            b += bsr;
        }
        while (r < columnEnd)
        {
            *r++ = *a;
            a += sr;
        }
        a += stepA;
        b += stepB;
    }
    end += h * (w - ow);
    while (r < end)
    {
        T * columnEnd = r + h;
        while (r < columnEnd)
        {
            *r++ = *a;
            a += sr;
        }
        a += stepA;
    }

    return result;
}

template<class T>
Matrix<T>
operator / (const MatrixStrided<T> & A, const T scalar)
{
    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    Matrix<T> result (h, w);
    T * i   = A.base ();
    T * r   = result.base ();
    T * end = r + h * w;
    int stepC = sc - h * sr;
    while (r < end)
    {
        T * columnEnd = r + h;
        while (r < columnEnd)
        {
            *r++ = *i / scalar;
            i += sr;
        }
        i += stepC;
    }
    return result;
}

template<class T>
Matrix<T>
operator / (const T scalar, const MatrixStrided<T> & A)
{
    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    Matrix<T> result (h, w);
    T * i   = A.base ();
    T * r   = result.base ();
    T * end = r + h * w;
    int stepC = sc - h * sr;
    while (r < end)
    {
        T * columnEnd = r + h;
        while (r < columnEnd)
        {
            *r++ = scalar / *i;
            i += sr;
        }
        i += stepC;
    }
    return result;
}

template<class T>
Matrix<T>
operator + (const MatrixStrided<T> & A, const MatrixAbstract<T> & B)
{
    if ((B.classID () & MatrixStridedID) == 0) return operator + ((const MatrixAbstract<T> &) A, B);

    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    const MatrixStrided<T> & MB = (const MatrixStrided<T> &) B;
    int bh  = MB.rows ();
    int bw  = MB.columns ();
    int bsc = MB.strideC ();
    int bsr = MB.strideR ();

    Matrix<T> result (h, w);
    int oh = std::min (h, bh);
    int ow = std::min (w, bw);
    int stepA = sc  - h  * sr;
    int stepB = bsc - oh * bsr;
    T * a = A.base ();
    T * b = MB.base ();
    T * r = result.base ();
    T * end = r + h * ow;
    while (r < end)
    {
        T * overlapEnd = r + oh;
        T * columnEnd  = r + h;
        while (r < overlapEnd)
        {
            *r++ = *a + *b;
            a +=    sr;
            b += bsr;
        }
        while (r < columnEnd)
        {
            *r++ = *a;
            a += sr;
        }
        a += stepA;
        b += stepB;
    }
    end += h * (w - ow);
    while (r < end)
    {
        T * columnEnd = r + h;
        while (r < columnEnd)
        {
            *r++ = *a;
            a += sr;
        }
        a += stepA;
    }

    return result;
}

template<class T>
Matrix<T>
operator + (const MatrixStrided<T> & A, const T scalar)
{
    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    Matrix<T> result (h, w);
    int stepC = sc - h * sr;
    T * i   = A.base ();
    T * r   = result.base ();
    T * end = r + h * w;
    while (r < end)
    {
        T * columnEnd = r + h;
        while (r < columnEnd)
        {
            *r++ = *i + scalar;
            i += sr;
        }
        i += stepC;
    }
    return result;
}

template<class T>
Matrix<T>
operator - (const MatrixStrided<T> & A, const MatrixAbstract<T> & B)
{
    if ((B.classID () & MatrixStridedID) == 0) return operator - ((const MatrixAbstract<T> &) A, B);

    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    const MatrixStrided<T> & MB = (const MatrixStrided<T> &) B;
    int bh  = MB.rows ();
    int bw  = MB.columns ();
    int bsc = MB.strideC ();
    int bsr = MB.strideR ();

    Matrix<T> result (h, w);
    int oh = std::min (h, bh);
    int ow = std::min (w, bw);
    int stepA = sc  - h  * sr;
    int stepB = bsc - oh * bsr;
    T * a   = A.base ();
    T * b   = MB.base ();
    T * r   = result.base ();
    T * end = r + h * ow;
    while (r < end)
    {
        T * overlapEnd = r + oh;
        T * columnEnd  = r + h;
        while (r < overlapEnd)
        {
            *r++ = *a - *b;
            a +=    sr;
            b += bsr;
        }
        while (r < columnEnd)
        {
            *r++ = *a;
            a += sr;
        }
        a += stepA;
        b += stepB;
    }
    end += h * (w - ow);
    while (r < end)
    {
        T * columnEnd = r + h;
        while (r < columnEnd)
        {
            *r++ = *a;
            a += sr;
        }
        a += stepA;
    }

    return result;
}

template<class T>
Matrix<T>
operator - (const MatrixStrided<T> & A, const T scalar)
{
    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    Matrix<T> result (h, w);
    int stepC = sc - h * sr;
    T * i   = A.base ();
    T * r   = result.base ();
    T * end = r + h * w;
    while (r < end)
    {
        T * columnEnd = r + h;
        while (r < columnEnd)
        {
            *r++ = *i - scalar;
            i += sr;
        }
        i += stepC;
    }
    return result;
}

template<class T>
Matrix<T>
operator - (const T scalar, const MatrixStrided<T> & A)
{
    int h  = A.rows ();
    int w  = A.columns ();
    int sc = A.strideC ();
    int sr = A.strideR ();

    Matrix<T> result (h, w);
    int stepC = sc - h * sr;
    T * i   = A.base ();
    T * r   = result.base ();
    T * end = r + h * w;
    while (r < end)
    {
        T * columnEnd = r + h;
        while (r < columnEnd)
        {
            *r++ = scalar - *i;
            i += sr;
        }
        i += stepC;
    }
    return result;
}


// class Matrix<T> ----------------------------------------------------------

template<class T>
Matrix<T>::Matrix ()
{
    offset   = 0;
    rows_    = 0;
    columns_ = 0;
    strideR_ = 1;
    strideC_ = 0;
};

template<class T>
Matrix<T>::Matrix (const int rows, const int columns)
{
    resize (rows, columns);
}

template<class T>
Matrix<T>::Matrix (const Matrix<T> & that)
{
    this->operator = (that);
}

template<class T>
Matrix<T>::Matrix (const n2a::Pointer & that, const int offset, const int rows, const int columns, const int strideR, const int strideC)
:   data     (that),
    offset   (offset),
    rows_    (rows),
    columns_ (columns),
    strideR_ (strideR),
    strideC_ (strideC)
{
}

template<class T>
uint32_t
Matrix<T>::classID () const
{
    return MatrixStridedID | MatrixID;
}

template<class T>
void
Matrix<T>::resize (const int rows, const int columns)
{
    this->data.grow (rows * columns * sizeof (T));
    this->offset   = 0;
    this->rows_    = rows;
    this->columns_ = columns;
    this->strideR_ = 1;
    this->strideC_ = rows;
}

template<class T>
Matrix<T>
operator ~ (const Matrix<T> & A)
{
    return Matrix<T> (A.data, A.offset, A.columns_, A.rows_, A.strideC_, A.strideR_);
}

template<class T>
Matrix<T>
row (const Matrix<T> & A, int row)
{
    return Matrix<T> (A.data, A.offset + row * A.strideR_, 1, A.columns_, A.strideR_, A.strideC_);
}

template<class T>
Matrix<T>
column (const Matrix<T> & A, int column)
{
    return Matrix<T> (A.data, A.offset + column * A.strideC_, A.rows_, 1, A.strideR_, A.strideC_);
}

#endif

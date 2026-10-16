/*
Author: Fred Rothganger
Copyright (c) 2001-2004 Dept. of Computer Science and Beckman Institute,
                        Univ. of Illinois.  All rights reserved.
Distributed under the UIUC/NCSA Open Source License.


Copyright 2005-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
void
clear (const MatrixAbstract<T> & A, T scalar)
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
visit (const MatrixAbstract<T> & A, T (*function) (T))
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
backSubstitute (const MatrixAbstract<T> & A, const MatrixAbstract<T> & B, bool lower)
{
    int nx = B.columns ();
    int mx = B.rows ();
    int d = std::min (mx, std::min (A.rows (), A.columns ()));  // length of diagonal
    Matrix<T> X (B);  // May contain excess elements, but hopefully that's a rare case.

    T * Xdata = X.data;
    if (lower)
    {
        // Iterate over the columns of X
        for (int j = 0; j < nx; j++)
        {
            // Iterate over diagonal
            for (int k = 0; k < d; k++)
            {
                int kj = k * X.strideR_ + j * X.strideC_;
                if (Xdata[kj] == 0) continue;
                T e = Xdata[kj] /= A(k,k);  // solve for element ij
                // Apply effect of solved element to rest of column.
                int ij = kj + X.strideR_;
                for (int i = k+1; i < d; i++)
                {
                    Xdata[ij] -= e * A(i,k);
                    ij += X.strideR_;
                }
            }
        }
    }
    else  // upper
    {
        for (int j = 0; j < nx; j++)
        {
            for (int k = d-1; k >= 0; k--)
            {
                int kj = k * X.strideR_ + j * X.strideC_;
                if (Xdata[kj] == 0) continue;
                T e = Xdata[kj] /= A(k,k);
                int ij = kj - X.strideR_;
                for (int i = k-1; i >= 0; i--)
                {
                    Xdata[ij] -= e * A(i,k);
                    ij -= X.strideR_;
                }
            }
        }
    }
    return region (X, 0, 0, d-1, nx-1);
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
operator == (const MatrixAbstract<T> & A, T scalar)
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
operator != (const MatrixAbstract<T> & A, T scalar)
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
operator < (const MatrixAbstract<T> & A, T scalar)
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
operator <= (const MatrixAbstract<T> & A, T scalar)
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
operator > (const MatrixAbstract<T> & A, T scalar)
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
operator >= (const MatrixAbstract<T> & A, T scalar)
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
operator && (const MatrixAbstract<T> & A, T scalar)
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
operator || (const MatrixAbstract<T> & A, T scalar)
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
operator ! (const MatrixAbstract<T> & A)
{
    int w = A.columns ();
    int h = A.rows ();

    if (h >= w)
    {
        FactorizationQR<T> qr (A);
        return qr.invert ();  // Exact inverse if square. Left inverse if h > w.
    }
    else  // h < w
    {
        // FactorizationQR can only be applied when h >= w. Instead, we apply it to the transpose to create the right inverse.
        Matrix<T> M (A);  // Need dense matrix, because transpose is not available on abstract matrix.
        FactorizationQR<T> qr (~M);
        return qr.invert ();
    }
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
operator * (const MatrixAbstract<T> & A, T scalar)
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
operator / (const MatrixAbstract<T> & A, T scalar)
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
operator / (T scalar, const MatrixAbstract<T> & A)
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
operator + (const MatrixAbstract<T> & A, T scalar)
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
operator - (const MatrixAbstract<T> & A, T scalar)
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
operator - (T scalar, const MatrixAbstract<T> & A)
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
operator *= (MatrixAbstract<T> & A, T scalar)
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
operator /= (MatrixAbstract<T> & A, T scalar)
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
operator += (MatrixAbstract<T> & A, T scalar)
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
operator -= (MatrixAbstract<T> & A, T scalar)
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
min (const MatrixAbstract<T> & A, T scalar)
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
max (const MatrixAbstract<T> & A, T scalar)
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

#ifndef N2A_SPINNAKER
template<class T>
std::ostream &
operator << (std::ostream & stream, const MatrixAbstract<T> & A)
{
    int rows = A.rows ();
    int columns = A.columns ();

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
#endif


// class MatrixStrided<T> ---------------------------------------------------

template<class T>
void
clear (MatrixStrided<T> & A, T scalar)
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
visit (const MatrixStrided<T> & A, T (*function) (T))
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
operator * (const MatrixStrided<T> & A, T scalar)
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
operator / (const MatrixStrided<T> & A, T scalar)
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
operator / (T scalar, const MatrixStrided<T> & A)
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
operator + (const MatrixStrided<T> & A, T scalar)
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
operator - (const MatrixStrided<T> & A, T scalar)
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
operator - (T scalar, const MatrixStrided<T> & A)
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
Matrix<T>::Matrix (int rows, int columns)
{
    resize (rows, columns);
}

template<class T>
Matrix<T>::Matrix (const Matrix<T> & that)
{
    this->operator = (that);
}

template<class T>
Matrix<T>::Matrix (const n2a::Pointer & that, int offset, int rows, int columns, int strideR, int strideC)
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
Matrix<T>::resize (int rows, int columns)
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

template<class T>
Matrix<T>
region  (const Matrix<T> & A, int firstRow, int firstColumn, int lastRow, int lastColumn)
{
    // This aggressive range checking is not really meant to hide user errors, just prevent memory violations.
    if (firstRow    <  0         ) firstRow    = 0;
    if (firstColumn <  0         ) firstColumn = 0;
    if (firstRow    >= A.rows_   ) firstRow    = A.rows_    - 1;
    if (firstColumn >= A.columns_) firstColumn = A.columns_ - 1;
    if (lastRow    < 0  ||  lastRow    >= A.rows_   ) lastRow    = A.rows_    - 1;
    if (lastColumn < 0  ||  lastColumn >= A.columns_) lastColumn = A.columns_ - 1;
    return Matrix<T> (A.data,
                      A.offset + firstRow * A.strideR_ + firstColumn * A.strideC_,
                      lastRow - firstRow + 1,
                      lastColumn - firstColumn + 1,
                      A.strideR_,
                      A.strideC_);
}


// class FactorizationQR -----------------------------------------------------

// TODO: The implementations here are only suitable for float or double. Need more careful work for int.

template<class T>
FactorizationQR<T>::FactorizationQR ()
{
}

template<class T>
FactorizationQR<T>::FactorizationQR (const MatrixAbstract<T> & A)
{
    factorize (A);
}

template<class T>
void
FactorizationQR<T>::factorize (const MatrixAbstract<T> & A)
{
    m = A.rows ();
    n = A.columns ();
    int d = std::min (m, n);

    QR = Matrix<T> (A);  // Deep copy.
    Qdiag.resize (d);
    P.resize (n);
    for (int c = 0; c < n; c++) P[c] = c;

    // Main loop.
    T * data = QR.data;
    for (int k = 0; k < d; k++)
    {
        // Pivot remaining column with largest norm into column k.
        //   Find column with largest norm.
        T   bestNorm2  = (T) 0;
        int bestColumn = k;
        for (int c = k; c < n; c++)
        {
            T norm2 = 0;
            int i   = c * m + k;  // (k,c). Only consider column in minor matrix.
            int end = c * m + m;  // (m,c). Bottom of column c in QR.
            while (i < end)
            {
                T e = data[i++];
                norm2 += e * e;
            }
            if (norm2 > bestNorm2)  // This also happens when c == n-1 == k, so we get norm of last column by itself.
            {
                bestNorm2  = norm2;
                bestColumn = c;
            }
        }
        //   Swap columns
        if (bestColumn != k)
        {
            int i   = k          * m;
            int j   = bestColumn * m;
            int end = i + m;
            while (i < end)
            {
                T temp = data[i];
                data[i] = data[j];
                data[j] = temp;
                i++;
                j++;
            }
            int temp      = P[k];
            P[k]          = P[bestColumn];
            P[bestColumn] = temp;
        }
        T norm = std::sqrt (bestNorm2);

        int kk = k * m + k;  // Index of diagonal element.
        if (norm != 0)
        {
            // Form k-th Householder vector.
            if (data[kk] < (T) 0) norm = -norm;
            int i   = kk;
            int end = k * m + m;
            while (i < end) data[i++] /= norm;
            data[kk] += 1;

            // Apply transformation to remaining columns.
            for (int j = k + 1; j < n; j++)
            {
                T s = (T) 0;
                i = kk;
                int kj = j * m + k;
                int l = kj;
                while (i < end) s += data[i++] * data[l++];

                s /= data[kk];
                i = kk;
                l = kj;
                while (i < end) data[l++] -= s * data[i++];
            }
        }
        Qdiag[k] = data[kk];
        data[kk] = -norm;
    }
}

template<class T>
int
FactorizationQR<T>::rank (T threshold)
{
    int d = Qdiag.size ();
    for (int j = 0; j < d; j++) if (std::abs (QR(j,j)) < threshold) return j;
    return d;
}

template<class T>
bool
FactorizationQR<T>::isFullRank ()
{
    int d = Qdiag.size ();
    for (int j = 0; j < d; j++) if (QR(j,j) == 0) return false;
    return true;
}

template<class T>
Matrix<T>
FactorizationQR<T>::getH ()
{
    int d = Qdiag.size ();
    Matrix<T> H (m, d);
    clear (H);  // So upper triangular area does not need to be explicitly set.
    T * Hdata  = H.data;
    T * QRdata = QR.data;
    for (int j = 0; j < d; j++)  // columns
    {
        int i   = j * m + j;
        int end = j * m + m;
        Hdata[i++] = Qdiag[j];  // Set diagonal element separately.
        while (i < end) Hdata[i] = QRdata[i++];  // rows
    }
    return H;
}

template<class T>
Matrix<T>
FactorizationQR<T>::getR ()
{
    int d = Qdiag.size ();
    Matrix<T> R (d, n);
    clear (R);  // So lower triangular area does not need to be explicitly set.
    int end = d * n;  // all of R data
    T * Rdata  = R.data;
    T * QRdata = QR.data;
    for (int i = 0; i < d; i++)  // rows
    {
        int r = i * d + i;  // Index into R, initially at (i,i)
        int j = i * m + i;  // Index into QR, initially at (i,i)
        while (r < end)  // columns
        {
            Rdata[r] = QRdata[j];
            r += d;
            j += m;
        }
    }
    return R;
}

template<class T>
Matrix<T>
FactorizationQR<T>::getQ ()
{
    int d = Qdiag.size ();
    Matrix<T> Q (m, d);
    clear (Q);  // The code below assumes that elements start with value zero.
    T * Qdata  = Q.data;
    T * QRdata = QR.data;
    for (int k = d - 1; k >= 0; k--)
    {
        int kk  = k * m + k;
        int end = k * m + m;
        Qdata[kk] = 1;
        if (Qdiag[k] == 0) continue;

        for (int j = k; j < d; j++)
        {
            int q = j * m + k;
            T s = Qdiag[k] * Qdata[q++];
            int i = kk + 1;
            while (i < end) s += QRdata[i++] * Qdata[q++];

            q = j * m + k;
            Qdata[q++] -= s;
            s /= Qdiag[k];
            i = kk + 1;
            while (i < end) Qdata[q++] -= s * QRdata[i++];
        }
    }
    return Q;
}

template<class T>
Matrix<T>
FactorizationQR<T>::getP ()
{
    Matrix<T> result (n, n);
    clear (result);
    for (int c = 0; c < n; c++) result(P[c],c) = 1;
    return result;
}

template<class T>
Matrix<T>
FactorizationQR<T>::solve (const MatrixAbstract<T> & B)
{
    // Approach:
    //   Note that A = QRP'
    //   QRP'X = B
    //   Let Y = Q'B
    //   Solve R(P'X) = Y through back substitution
    //   Un-permute P(P'X) --> X

    if (m < n) throw "solve() can't work when rows are less then columns. Factorize A' insteand, then call solveTranspose().";
    if (B.rows () != m) throw "B rows must agree with A rows.";

    // Copy right hand side
    int nx = B.columns ();
    Matrix<T> X (B);

    // Compute Y = Q'B
    // This combines the job of getQ() with transpose and multiply by B.
    // The transpose itself enables this simultaneous construction of Q during the multiply.
    int d = Qdiag.size ();
    T * QRdata = QR.data;
    T * Xdata  = X .data;
    for (int k = 0; k < d; k++)  // Iterate over diagonal of Q.
    {
        int kk  = k * m + k;
        int end = k * m + m;  // (m,k). Bottom of column k in Q.
        for (int j = 0; j < nx; j++)  // Iterate over columns of B.
        {
            // Partial dot product of column k in Q and column j in B, starting from row k.
            int x = j * m + k;  // B(k,m)
            T s = Qdiag[k] * Xdata[x++];
            int a = kk + 1;
            while (a < end) s += QRdata[a++] * Xdata[x++];

            // Subtract product from column j in B.
            x = j * m + k;
            Xdata[x++] -= s;
            s /= Qdiag[k];
            a = kk + 1;
            while (a < end) Xdata[x++] -= s * QRdata[a++];
        }
    }

    // Solve R(P'X) = Y through back substitution
    int Xend = m * nx;
    for (int k = d-1; k >= 0; k--)
    {
        int j = k;  // iterate over row k
        while (j < Xend)
        {
            Xdata[j] /= QRdata[k*m+k];
            j += m;
        }

        int a = k * m;  // iterate over column k
        for (int i = 0; i < k; i++)
        {
            j     = i;  // iterate over row i
            int l = k;  // iterate over row k
            T qr = QRdata[a++];
            while (j < Xend)
            {
                Xdata[j] -= Xdata[l] * qr;
                j += m;
                l += m;
            }
        }
    }

    // Un-permute P(P'X) --> X
    Matrix<T> result (n, nx);
    T * data = result.data;
    for (int f = 0; f < d; f++)  // from row
    {
        int x = f;    // index into X, at start of source row
        int r = P[f]; // index into result, at start of destination row
        while (x < Xend)
        {
            data[r] = Xdata[x];
            x += m;
            r += n;
        }
    }
    return result;
}

template<class T>
Matrix<T>
FactorizationQR<T>::solveTranspose (const MatrixAbstract<T> & B)
{
    // Approach:
    //   If QRP' = A', then PR'Q' = A.
    //   Solve PR'Q'X = B.
    //   Let Z = Q'X, and solve R'Z = P'B.
    //   Finally, compute result as X = QZ.

    if (B.rows () != n) throw "B rows must match A' columns, that is, original A rows.";
    int nx = B.columns ();
    int d = Qdiag.size ();  // Should be same as n.

    // Construct P'B
    Matrix<T> Z (n, nx);  // No need to clear, because we will write all elements.
    T * Zdata = Z.data;
    for (int r = 0; r < d; r++)  // from row
    {
        int b = P[r];  // source row in B (abstract matrix)
        int z = r;     // index into Z, at start of destination row
        for (int c = 0; c < nx; c++)
        {
            Zdata[z] = B(b,c);
            z += n;
        }
    }

    // Solve R'Z = P'B with forward substitution.
    // This avoids an extra copy of R by transposing in-situ.
    T * QRdata = QR.data;
    for (int j = 0; j < nx; j++)  // Iterate over the columns of Z.
    {
        for (int k = 0; k < d; k++)  // Iterate over diagonal of R
        {
            int kj = k * Z.strideR_ + j * Z.strideC_;  // Accessing elements of Z in usual order.
            if (Zdata[kj] == 0) continue;
            int a = k * (QR.strideC_ + QR.strideR_);  // On diagonal of QR, which belongs to R rather than Q.
            T e = Zdata[kj] /= QRdata[a];  // solve for element ij

            // Apply effect of solved element to rest of column.
            a += QR.strideC_;  // Step one position in R. Travel direction is across a single row, because we are transposing.
            int ij  = kj + Z.strideR_;  // Step one position in Z (down a single column).
            int end = d * Z.strideR_ + j * Z.strideC_;  // Stop iterating just before Z(d,j).
            while (ij != end)
            {
                Zdata[ij] -= e * QRdata[a];
                a  += QR.strideC_;
                ij += Z .strideR_;
            }
        }
    }

    // Compute X = QZ
    // Unlike in solve(), the lack of transpose on Q prevents its simultaneous construction.
    // It is simpler to construct Q separately.
    return getQ () * Z;
}

template<class T>
Matrix<T>
FactorizationQR<T>::invert ()
{
    // Approach:
    //   LA = I
    //   A = QRP'
    //   !(QRP')A = I
    //   L = !(QRP') = P!RQ'
    //   Solve R(P'L) = Q' via back substitution.
    //   Un-permute P(P'L) --> L

    // Solve R(P'L) = Q' via back substitution.
    Matrix<T> PL = backSubstitute (QR, ~ getQ ());

    // Un-permute P(P'L) --> L
    Matrix<T> result (n, m);
    int d = Qdiag.size ();
    int PLend = PL.rows () * PL.columns ();
    T * data   = result.data;
    T * PLdata = PL    .data;
    for (int i = 0; i < d; i++)  // from row
    {
        int l   = i;    // index into PL, at start of source row
        int r   = P[i]; // index into result, at start of destination row
        while (l < PLend)
        {
            data[r] = PLdata[l];
            l += n;
            r += n;
        }
    }
    return result;
}

template<class T>
void
FactorizationQR<T>::saveRdiag (std::vector<T> & Rdiag)
{
    int d = Qdiag.size ();
    Rdiag.resize (d);
    for (int j = 0; j < d; j++) Rdiag[j] = QR(j,j);
}

template<class T>
void
FactorizationQR<T>::restoreRdiag (const std::vector<T> & Rdiag)
{
    int d = Qdiag.size ();
    for (int j = 0; j < d; j++) QR(j,j) = Rdiag[j];
}


#endif

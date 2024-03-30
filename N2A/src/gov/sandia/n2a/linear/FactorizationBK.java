/*
Copyright 2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.linear;

import gov.sandia.n2a.language.type.Matrix;

/**
    Factor a symmetric indefinite matrix using Bunch-Kaufman.
    This is an adaptation of the LAPACK routine dsytf2.
    This version works on dense matrices.
    If the matrix is very large and sparse, a version can be written
    to take advantage of MatrixSparse to do some operations more efficiently.
    See FL LevenbergMarquardtSparseBK.tcc for working example.
**/
public class FactorizationBK implements Factorization
{
    public int         maxPivot = Integer.MAX_VALUE;
    public MatrixDense A;
    public int[]       pivots;

    public FactorizationBK ()
    {
    }

    public FactorizationBK (Matrix A)
    {
        factorize (A);
    }

    public void factorize (Matrix inputA)
    {
        double alpha = (1 + Math.sqrt (17)) / 8;
        int n = inputA.columns ();
        pivots = new int[n];

        // Only copy the upper triangular region
        A = new MatrixDense (n, n);
        for (int c = 0; c < n; c++)
        {
            for (int r = 0; r <= c; r++)
            {
                A.set (r, c, inputA.get (r, c));
            }
        }

        // K is the main loop index, decreasing from N-1 to 0 in steps of 1 or 2
        int k = n - 1;
        while (k >= 0)
        {
            // Determine rows and columns to be interchanged and whether
            // a 1-by-1 or 2-by-2 pivot block will be used.
            int kstep = 1;
            double absakk = Math.abs (A.get (k, k));

            // IMAX is the row-index of the largest off-diagonal element in column K,
            // and COLMAX is its absolute value
            Integer imax   = 0;
            Double  colmax = 0.0;
            colmax (k, imax, colmax);

            int kp;
            if (Math.max (absakk, colmax) == 0)
            {
                throw new RuntimeException ("Column of zeros at " + k);
                // kp = k;  // Alternately, we could ignore the error condition and keep going.
            }
            else
            {
                if ((k - imax) > maxPivot  ||  absakk >= alpha * colmax)
                {
                    // no interchange, use 1-by-1 pivot block
                    kp = k;
                }
                else
                {
                    // JMAX is the column-index of the largest off-diagonal element in row IMAX,
                    // and ROWMAX is its absolute value
                    // (JMAX isn't actually used in this adaptation)
                    Integer jmax   = 0;
                    Double  rowmax = 0.0;
                    colmax (imax, jmax, rowmax);
                    for (int j = imax + 1; j <= k; j++)
                    {
                        // no need to update jmax, because it is not used below
                        rowmax = Math.max (rowmax, Math.abs (A.get (imax, j)));
                    }

                    if (absakk >= alpha * colmax * colmax / rowmax)
                    {
                        // no interchange, use 1-by-1 pivot block
                        kp = k;
                    }
                    else if (Math.abs (A.get (imax, imax)) >= alpha * rowmax)
                    {
                        // interchange rows and columns K and IMAX, use 1-by-1 pivot block
                        kp = imax;
                    }
                    else
                    {
                        // interchange rows and columns K-1 and IMAX, use 2-by-2 pivot block
                        kp = imax;
                        kstep = 2;
                    }
                }

                int kk = k - kstep + 1;
                if (kp != kk)  // then kp < kk
                {
                    interchange (A, k, kk, kp);
                    /*
                    // Interchange rows and columns KK and KP in the leading submatrix A(1:k,1:k)
                    swap (A, kk, kp, kp - 1);  // exchange columns kk and kp up to row kp-1
                    for (int j = kp + 1; j < kk; j++)
                    {
                        swap (A, j, kk, kp, j);  // exchange elements (j,kk) and (kp,j)
                    }
                    swap (A, kk, kk, kp, kp);
                    if (kstep == 2) swap (A, k-1, k, kp, k);
                    */
                }

                // Update the leading submatrix
                if (kstep == 1)  // 1-by-1 pivot block D(k)
                {
                    // column k now holds W(k) = U(k)*D(k)
                    // where U(k) is the k-th column of U

                    // Perform a rank-1 update of A(1:k-1,1:k-1) as
                    // A := A - U(k)*D(k)*U(k)' = A - W(k)*1/D(k)*W(k)'
                    // and store U(k) in column k
                    double dk = A.get (k, k);
                    if (dk == 0) throw new RuntimeException ("diagonal element is zero: " + k);
                    for (int j = k - 1; j >= 0; j--)
                    {
                        double temp = - A.get (j, k) / dk;
                        if (temp != 0)
                        {
                            for (int i = j; i >= 0; i--)
                            {
                                A.set (i, j, A.get (i, j) + A.get (i, k) * temp);
                            }
                        }
                        A.set (j, k, -temp);
                    }
                }
                else  // 2-by-2 pivot block D(k)
                {
                    // columns k and k-1 now hold ( W(k-1) W(k) ) = ( U(k-1) U(k) )*D(k)
                    // where U(k) and U(k-1) are the k-th and (k-1)-th columns of U

                    // Perform a rank-2 update of A(1:k-2,1:k-2) as
                    // A := A - ( U(k-1) U(k) )*D(k)*( U(k-1) U(k) )'
                    //    = A - ( W(k-1) W(k) )*inv(D(k))*( W(k-1) W(k) )'
                    int k1 = k - 1;
                    double d11 = A.get (k, k);
                    double d22 = A.get (k1, k1);
                    if (d11 == 0  ||  d22 == 0)
                    {
                        throw new RuntimeException ("updateRank2: diagonal element is zero: " + k);
                    }
                    double d12 = A.get (k1, k);

                    double temp = d12;
                    d12 = d11 * d22 / d12 - d12;
                    d22 /= temp;
                    d11 /= temp;

                    for (int j = k - 2; j >= 0; j--)
                    {
                        double Ajk  = A.get (j, k);
                        double Ajk1 = A.get (j, k1);
                        double wk1 = (d11 * Ajk1 - Ajk)  / d12;
                        double wk  = (d22 * Ajk  - Ajk1) / d12;

                        // follow column j
                        for (int i = j; i >= 0; i--)
                        {
                            double Aik  = A.get (i, k);
                            double Aik1 = A.get (i, k1);
                            temp = Aik * wk + Aik1 * wk1;
                            A.set (i, j, A.get (i, j) - temp);
                        }

                        A.set (j, k,  wk);
                        A.set (j, k1, wk1);
                    }
                }
            }

            // Store details of the interchanges in IPIV
            // Pivot values must be one-based so that negation can work. The output
            // of this routine is therefore compatible with dsytf2, etc.
            kp++;
            if (kstep == 1)
            {
                pivots[k] = kp;
            }
            else
            {
                pivots[k] = -kp;
                pivots[k-1] = -kp;
            }

            // Decrease K
            k -= kstep;
        }
    }

    /**
        Solve AX=B, where A=UDU'
    **/
    public MatrixDense solve (Matrix B)
    {
        MatrixDense X = new MatrixDense (B);

        int n  = A.columns ();
        int bw = B.columns ();

        // Solve for each column of X separately.
        for (int c = 0; c < bw; c++)
        {
            MatrixDense x = X.getColumn (c);  // The column currently being solved.

            // First solve U*D*X = B
            // K is the main loop index, decreasing from N-1 to 0 in steps of
            // 1 or 2, depending on the size of the diagonal blocks.
            int k = n - 1;
            while (k >= 0)
            {
                if (pivots[k] > 0)  // 1 x 1 diagonal block
                {
                    // Interchange rows K and IPIV(K).
                    int kp = pivots[k] - 1;
                    if (kp != k)
                    {
                        double temp = x.get (k);
                        x.set (k, x.get (kp));
                        x.set (kp, temp);
                    }

                    // Multiply by inv(U(K)), where U(K) is the transformation
                    // stored in column K of A.
                    minus (k, k - 1, x);

                    // Multiply by the inverse of the diagonal block.
                    x.set (k, x.get (k) / A.get (k, k));
 
                    k--;
                }
                else  // 2 x 2 diagonal block
                {
                    // Interchange rows K-1 and -IPIV(K).
                    int kp = - pivots[k] - 1;
                    if (kp != k - 1)
                    {
                        double temp = x.get (k - 1);
                        x.set (k - 1, x.get (kp));
                        x.set (kp, temp);
                    }

                    // Multiply by inv(U(K)), where U(K) is the transformation
                    // stored in columns K-1 and K of A.
                    minus (k,     k - 2, x);
                    minus (k - 1, k - 2, x);

                    // Multiply by the inverse of the diagonal block.
                    double akm1k = A.get (k-1,k);
                    double akm1  = A.get (k-1,k-1) / akm1k;
                    double ak    = A.get (k,k)     / akm1k;
                    double denom = akm1 * ak - 1;
                    double bkm1  = x.get (k-1) / akm1k;
                    double bk    = x.get (k)   / akm1k;
                    x.set (k-1, (ak   * bkm1 - bk)   / denom);
                    x.set (k,   (akm1 * bk   - bkm1) / denom);

                    k -= 2;
                }
            }

            // Next solve U'*X = B
            // K is the main loop index, increasing from 0 to N-1 in steps of
            // 1 or 2, depending on the size of the diagonal blocks.
            k = 0;
            while (k < n)
            {
                if (pivots[k] > 0)  // 1 x 1 diagonal block
                {
                    // Multiply by inv(U'(K)), where U(K) is the transformation stored in column K of A.
                    x.set (k, x.get (k) - dot (k, k - 1, x));

                    // Interchange rows K and IPIV(K)
                    int kp = pivots[k] - 1;
                    if (kp != k)
                    {
                        double temp = x.get (k);
                        x.set (k, x.get (kp));
                        x.set (kp, temp);
                    }

                    k++;
                }
                else  // 2 x 2 diagonal block
                {
                    // Multiply by inv(U'(K+1)), where U(K+1) is the transformation
                    // stored in columns K and K+1 of A.
                    x.set (k,   x.get (k)   - dot (k,   k-1, x));
                    x.set (k+1, x.get (k+1) - dot (k+1, k-1, x));

                    // Interchange rows K and -IPIV(K).
                    int kp = - pivots[k] - 1;
                    if (kp != k)
                    {
                        double temp = x.get (k);
                        x.set (k, x.get (kp));
                        x.set (kp, temp);
                    }

                    k += 2;
                }
            }
        }

        return X;
    }

    public Matrix invert ()
    {
        return null;
    }

    /**
        Find element with largest absolute value in given column.
        Both row and value are used to return result to caller.
    **/
    protected void colmax (int column, Integer row, Double value)
    {
        for (int r = 0; r < column; r++)
        {
            double temp = Math.abs (A.get (r, column));
            if (temp > value)
            {
                row   = r;
                value = temp;
            }
        }
    }

    /**
        Interchange rows and columns KK and KP in the leading submatrix A(1:k,1:k)
    **/
    protected void interchange (MatrixDense A, int k, int kk, int kp)
    {
        swap (A, kk, kp, kp - 1);  // exchange columns kk and kp up to row kp-1
        for (int j = kp + 1; j < kk; j++)
        {
            swap (A, j, kk, kp, j);  // exchange elements (j,kk) and (kp,j)
        }
        swap (A, kk, kk, kp, kp);
        if (k != kk)  // step==2
        {
            swap (A, k-1, k, kp, k);  // k-1 == kk
        }
    }

    protected void swap (MatrixDense A, int row1, int column1, int row2, int column2)
    {
        double temp = A.get (row1, column1);
        A.set (row1, column1, A.get (row2, column2));
        A.set (row2, column2, temp);
    }

    protected void swap (MatrixDense A, int column1, int column2, int lastRow)
    {
        for (int r = 0; r <= lastRow; r++)
        {
            double temp = A.get (r, column1);
            A.set (r, column1, A.get (r, column2));
            A.set (r, column2, temp);
        }
    }

    protected void minus (int column, int lastRow, MatrixDense x)
    {
        double alpha = x.get (column);
        if (alpha == 0) return;
        for (int r = 0; r <= lastRow; r++)
        {
            x.set (r, x.get (r) - A.get (r, column) * alpha);
        }
    }

    protected double dot (int column, int lastRow, MatrixDense x)
    {
        double result = 0;
        for (int r = 0; r <= lastRow; r++)
        {
            result += x.get (r) * A.get (r, column);
        }
        return result;
    }

    /**
        Recreates the original matrix A from its factorized form.
        This can be used for debugging, to see how accurate the decomposition is.
        TODO: this function doesn't work correctly yet.
    **/
    public MatrixDense reconstruct ()
    {
        int n = A.rows ();
        MatrixDense U = new MatrixDense (n, n);
        U.identity ();
        MatrixDense D = new MatrixDense (n, n);
        D.clear ();

        int k = n - 1;
        while (k >= 0)
        {
            MatrixDense PUk = new MatrixDense (n, n);
            PUk.identity ();
            if (pivots[k] > 0)
            {
                PUk.getRegion (0, k, k-1, k).set (A.getRegion (0, k, k-1, k));
                int kp = pivots[k] - 1;
                if (kp != k) interchange (PUk, k, k, kp);
                U = U.multiply (PUk);
                D.set (k, k, A.get (k, k));
                k -= 1;
            }
            else  // pivots[k] < 0 and pivots[k-1] < 0
            {
                PUk.getRegion (0, k-1, k-2, k).set (A.getRegion (0, k-1, k-2, k));
                int kp = -pivots[k] - 1;
                int kk = k - 1;
                if (kp != kk) interchange (PUk, k, kk, kp);
                U = U.multiply (PUk);
                D.set (k,  k,   A.get (k,  k));
                D.set (k-1,k-1, A.get (k-1,k-1));
                D.set (k-1,k,   A.get (k-1,k));
                D.set (k,  k-1, A.get (k-1,k));
                k -= 2;
            }
        }
        return U.multiply (D.multiply (U.transpose ()));
    }
}

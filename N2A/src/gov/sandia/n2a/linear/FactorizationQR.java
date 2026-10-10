/*
Copyright 2021-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.linear;

import gov.sandia.n2a.language.type.Matrix;

/**
    QR Decomposition, computed by Householder reflections, with column pivoting.

    Code taken from the public-domain packages JAMA (https://math.nist.gov/javanumerics/jama/)
    and MINPACK (https://netlib.org/minpack).

    Original comments from JAMA package ...

    "For an m-by-n matrix A with m >= n, the QR decomposition is an m-by-n
    orthogonal matrix Q and an n-by-n upper triangular matrix R so that A = Q*R.

    "The QR decomposition always exists, even if the matrix does not have full
    rank, so the constructor will never fail. The primary use of the QR
    decomposition is in the least squares solution of non-square systems of
    simultaneous linear equations. This will fail if isFullRank() returns false."

    This class actually handles any shape of matrix: square (m=n), tall (m>n), wide (m<n).
    In the tall case, it returns the reduced QR decomposition.
    In the wide case, R is a trapezoid [R1 Q'A2].
**/
public class FactorizationQR implements Factorization
{
    // These members are public because it is expected that a caller may want to
    // access them directly. The functions that return component matrices are there
    // as a convenience to reconstruct the relevant pieces, but there is nothing
    // about this data that needs to be hidden. There are even circumstances in
    // which it makes sense to modify the data. For example, the Levenberg-Marquardt
    // algorithm has a use for modifying Rdiag.
    public MatrixDense QR;
    public int         m;
    public int         n;
    public double[]    Qdiag;
    public int[]       P;  // Permutation matrix, stored as a list. AP=QR. Matrix P(r,c)==1 iff list P[c]==r. That is, P[c] stores the original position of column c before it was permuted.

    public FactorizationQR ()
    {
    }

    public FactorizationQR (Matrix A)
    {
        factorize (A);
    }

    public void factorize (Matrix A)
    {
        m = A.rows ();
        n = A.columns ();
        int d = Math.min (m, n);

        QR    = new MatrixDense (A);
        Qdiag = new double[d];
        P     = new int[n];
        for (int c = 0; c < n; c++) P[c] = c;

        // Main loop.
        for (int k = 0; k < d; k++)
        {
            // Pivot remaining column with largest norm into column k.
            //   Find column with largest norm.
            double bestNorm2  = 0;
            int    bestColumn = k;
            for (int c = k; c < n; c++)
            {
                double norm2 = 0;
                int i   = c * m + k;  // (k,c). Only consider column in minor matrix.
                int end = c * m + m;  // (m,c). Bottom of column c in QR.
                while (i < end)
                {
                    double e = QR.data[i++];
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
                    double temp = QR.data[i];
                    QR.data[i] = QR.data[j];
                    QR.data[j] = temp;
                    i++;
                    j++;
                }
                int temp      = P[k];
                P[k]          = P[bestColumn];
                P[bestColumn] = temp;
            }
            double norm = Math.sqrt (bestNorm2);

            int kk = k * m + k;  // Index of diagonal element.
            if (norm != 0)
            {
                // Form k-th Householder vector.
                if (QR.data[kk] < 0) norm = -norm;
                int i   = kk;
                int end = k * m + m;
                while (i < end) QR.data[i++] /= norm;
                QR.data[kk] += 1;

                // Apply transformation to remaining columns.
                for (int j = k + 1; j < n; j++)
                {
                    double s = 0.0;
                    i = kk;
                    int kj = j * m + k;
                    int l = kj;
                    while (i < end) s += QR.data[i++] * QR.data[l++];

                    s /= QR.data[kk];
                    i = kk;
                    l = kj;
                    while (i < end) QR.data[l++] -= s * QR.data[i++];
                }
            }
            Qdiag[k] = QR.data[kk];
            QR.data[kk] = -norm;
        }
    }

    /**
        @return Zero-based index of first element in diagonal of R that falls below threshold.
        If no such element exists, result points one past the last element.
    **/
    public int rank (double threshold)
    {
        for (int j = 0; j < Qdiag.length; j++) if (Math.abs (QR.data[j*m+j]) < threshold) return j;
        return Qdiag.length;
    }

    /**
        @return true if all elements of R are nonzero. Unlike rank(double), there is no threshold,
        so this is a more relaxed criterion.
    **/
    public boolean isFullRank ()
    {
        for (int j = 0; j < Qdiag.length; j++) if (QR.data[j*m+j] == 0) return false;
        return true;
    }

    /**
        Return the Householder vectors
        @return Lower trapezoidal matrix whose columns define the reflections
    **/
    public MatrixDense getH ()
    {
        int d = Qdiag.length;
        MatrixDense H = new MatrixDense (m, d);  // Zero-filled, so upper triangular area does not need to be explicitly set.
        for (int j = 0; j < d; j++)  // columns
        {
            int i   = j * m + j;
            int end = j * m + m;
            H.data[i++] = Qdiag[j];  // Set diagonal element separately.
            while (i < end) H.data[i] = QR.data[i++];  // rows
        }
        return H;
    }

    /**
        Return the upper triangular factor R
    **/
    public MatrixDense getR ()
    {
        int d = Qdiag.length;
        MatrixDense R = new MatrixDense (d, n);  // Zero-filled, so lower triangular area does not need to be explicitly set.
        int end = R.data.length;
        for (int i = 0; i < d; i++)  // rows
        {
            int r = i * d + i;  // Index into R, initially at (i,i)
            int j = i * m + i;  // Index into QR, initially at (i,i)
            while (r < end)  // columns
            {
                R.data[r] = QR.data[j];
                r += d;
                j += m;
            }
        }
        return R;
    }

    /**
        Generate and return the (economy-sized) orthogonal factor Q
    **/
    public MatrixDense getQ ()
    {
        int d = Qdiag.length;
        MatrixDense Q = new MatrixDense (m, d);
        for (int k = d - 1; k >= 0; k--)
        {
            int kk  = k * m + k;
            int end = k * m + m;
            Q.data[kk] = 1;
            if (Qdiag[k] == 0) continue;

            for (int j = k; j < d; j++)
            {
                int q = j * m + k;
                double s = Qdiag[k] * Q.data[q++];
                int i = kk + 1;
                while (i < end) s += QR.data[i++] * Q.data[q++];

                q = j * m + k;
                Q.data[q++] -= s;
                s /= Qdiag[k];
                i = kk + 1;
                while (i < end) Q.data[q++] -= s * QR.data[i++];
            }
        }
        return Q;
    }

    public MatrixDense getP ()
    {
        MatrixDense result = new MatrixDense (n, n);
        for (int c = 0; c < n; c++) result.set (P[c], c, 1);
        return result;
    }

    /**
        Least squares solution of AX = B, where m >= n.
        Approach:
        Note that A = QRP'
        QRP'X = B
        Let Y = Q'B
        Solve R(P'X) = Y through back substitution
        Un-permute P(P'X) --> X
        @param B Matrix with exactly as many rows as A, along with any number of columns.
        @return X that minimizes the two norm of AX-B.
    **/
    public MatrixDense solve (Matrix B)
    {
        if (m < n) throw new IllegalArgumentException ("solve() can't work when rows are less then columns. Factorize A' insteand, then call solveTranspose().");
        if (B.rows () != m) throw new IllegalArgumentException ("B rows must agree with A rows.");

        // Copy right hand side
        int nx = B.columns ();
        MatrixDense X = new MatrixDense (B);

        // Compute Y = Q'B
        // This combines the job of getQ() with transpose and multiply by B.
        // The transpose itself enables this simultaneous construction of Q during the multiply.
        int d = Qdiag.length;
        for (int k = 0; k < d; k++)  // Iterate over diagonal of Q.
        {
            int kk  = k * m + k;
            int end = k * m + m;  // (m,k). Bottom of column k in Q.
            for (int j = 0; j < nx; j++)  // Iterate over columns of B.
            {
                // Partial dot product of column k in Q and column j in B, starting from row k.
                int x = j * m + k;  // B(k,m)
                double s = Qdiag[k] * X.data[x++];
                int a = kk + 1;
                while (a < end) s += QR.data[a++] * X.data[x++];

                // Subtract product from column j in B.
                x = j * m + k;
                X.data[x++] -= s;
                s /= Qdiag[k];
                a = kk + 1;
                while (a < end) X.data[x++] -= s * QR.data[a++];
            }
        }

        // Solve R(P'X) = Y through back substitution
        for (int k = d-1; k >= 0; k--)
        {
            int j = k;  // iterate over row k
            while (j < X.data.length)
            {
                X.data[j] /= QR.data[k*m+k];
                j += m;
            }

            int a = k * m;  // iterate over column k
            for (int i = 0; i < k; i++)
            {
                j     = i;  // iterate over row i
                int l = k;  // iterate over row k
                double qr = QR.data[a++];
                while (j < X.data.length)
                {
                    X.data[j] -= X.data[l] * qr;
                    j += m;
                    l += m;
                }
            }
        }

        // Un-permute P(P'X) --> X
        MatrixDense result = new MatrixDense (n, nx);
        for (int f = 0; f < d; f++)  // from row
        {
            int x   = f;    // index into X, at start of source row
            int r   = P[f]; // index into result, at start of destination row
            int end = X.data.length;
            while (x < end)
            {
                result.data[r] = X.data[x];
                x += m;
                r += n;
            }
        }
        return result;
    }

    /**
        Least squares solution of AX = B, where A is wide (m < n) and factorization has been run on A' instead of A.
        This class does not try to automate the choice between A and A'. The user must determine which is suitable.
        Approach:
        If QRP' = A', then PR'Q' = A.
        Solve PR'Q'X = B.
        Let Z = Q'X, and solve R'Z = P'B.
        Finally, compute result as X = QZ.
        @param B Matrix with exactly as many rows as original A (not A'), along with any number of columns.
        @return X with smallest-norm that produces AX-B = exactly zero.
    **/
    public MatrixDense solveTranspose (Matrix B)
    {
        if (B.rows () != n) throw new IllegalArgumentException ("B rows must match A' columns, that is, original A rows.");
        int nx = B.columns ();
        int d = Qdiag.length;

        // Construct P'B
        MatrixDense Z = new MatrixDense (n, nx);
        for (int r = 0; r < d; r++)  // from row
        {
            int b = P[r];  // source row in B (abstract matrix)
            int z = r;     // index into Z, at start of destination row
            for (int c = 0; c < nx; c++)
            {
                Z.data[z] = B.get (b, c);
                z += n;
            }
        }

        // Solve R'Z = P'B with forward substitution.
        // See MatrixDense.backSubstitute(). This version avoids an extra copy of R by transposing in-situ.
        for (int j = 0; j < nx; j++)  // Iterate over the columns of Z.
        {
            for (int k = 0; k < d; k++)  // Iterate over diagonal of R
            {
                int kj = k * Z.strideR + j * Z.strideC;  // Accessing elements of Z in usual order.
                if (Z.data[kj] == 0) continue;
                int a = k * (QR.strideC + QR.strideR);  // On diagonal of QR, which belongs to R rather than Q.
                double e = Z.data[kj] /= QR.data[a];  // solve for element ij

                // Apply effect of solved element to rest of column.
                a += QR.strideC;  // Step one position in R. Travel direction is across a single row, because we are transposing.
                int ij  = kj + Z.strideR;  // Step one position in Z (down a single column).
                int end = d * Z.strideR + j * Z.strideC;  // Stop iterating just before Z(d,j).
                while (ij != end)
                {
                    Z.data[ij] -= e * QR.data[a];
                    a  += QR.strideC;
                    ij += Z .strideR;
                }
            }
        }

        // Compute X = QZ
        // Unlike in solve(), the lack of transpose on Q prevents its simultaneous construction.
        // It is simpler to construct Q separately.
        return getQ ().multiply (Z);
    }

    /**
        Determine the left inverse of A, where m >= n.
        Approach:
        LA = I
        A = QRP'
        !(QRP')A = I
        L = !(QRP') = P!RQ'
        Solve R(P'L) = Q' via back substitution.
        Un-permute P(P'L) --> L
    **/
    public MatrixDense invert ()
    {
        // Solve R(P'L) = Q' via back substitution.
        MatrixDense PL = QR.backSubstitue (false, getQ ().transpose ());

        // Un-permute P(P'L) --> L
        MatrixDense result = new MatrixDense (n, m);
        int d = Qdiag.length;
        for (int i = 0; i < d; i++)  // from row
        {
            int l   = i;    // index into PL, at start of source row
            int r   = P[i]; // index into result, at start of destination row
            int end = PL.data.length;
            while (l < end)
            {
                result.data[r] = PL.data[l];
                l += n;
                r += n;
            }
        }
        return result;
    }

    public double[] saveRdiag ()
    {
        int d = Qdiag.length;
        double[] result = new double[d];
        for (int j = 0; j < d; j++) result[j] = QR.data[j*m+j];
        return result;
    }

    public void restoreRdiag (double[] Rdiag)
    {
        for (int j = 0; j < Qdiag.length; j++) QR.data[j*m+j] = Rdiag[j];
    }
}

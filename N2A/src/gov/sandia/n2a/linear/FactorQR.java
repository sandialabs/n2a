package gov.sandia.n2a.linear;

import gov.sandia.n2a.language.type.Matrix;

/**
    QR Decomposition, computed by Householder reflections.

    Code taken from the public-domain package JAMA. Original comments:

    For an m-by-n matrix A with m >= n, the QR decomposition is an m-by-n
    orthogonal matrix Q and an n-by-n upper triangular matrix R so that A = Q*R.

    The QR decomposition always exists, even if the matrix does not have full
    rank, so the constructor will never fail. The primary use of the QR
    decomposition is in the least squares solution of non-square systems of
    simultaneous linear equations. This will fail if isFullRank() returns false.
**/
public class FactorQR
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
    public double[]    Rdiag;
    public int[]       P;  // Permutation matrix, stored as a list. AP=QR. Matrix P(r,c)==1 iff list P[c]==r. That is, P[c] stores the original position of column c before it was permuted.

    public FactorQR (Matrix A)
    {
        m = A.rows ();
        n = A.columns ();
        if (m < n) throw new IllegalArgumentException ("There must be at least as many rows as columns.");

        QR    = new MatrixDense (A);
        Rdiag = new double[n];
        P     = new int[n];
        for (int c = 0; c < n; c++) P[c] = c;

        // Main loop.
        for (int k = 0; k < n; k++)
        {
            // Pivot remaining column with largest norm into column k.
            if (k < n - 1)  // Only do this if there are at least two columns left to compare.
            {
                //   Find column with largest norm.
                double bestNorm   = 0;
                int    bestColumn = k;
                for (int c = k; c < n; c++)
                {
                    double norm = 0;
                    int i   = c * m;
                    int end = i + m;
                    while (i < end)
                    {
                        double e = QR.data[i++];
                        norm += e * e;
                    }
                    if (norm > bestNorm)
                    {
                        bestNorm   = norm;
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
            }

            // Compute 2-norm of k-th column.
            double norm = 0;
            int kk  = k * m + k;
            int i   = kk;
            int end = k * m + m;
            while (i < end)
            {
                double e = QR.data[i++];
                norm += e * e;
            }
            norm = Math.sqrt (norm);

            if (norm != 0.0)
            {
                // Form k-th Householder vector.
                if (QR.data[kk] < 0) norm = -norm;
                i = kk;
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
                    s = -s / QR.data[kk];
                    i = kk;
                    l = kj;
                    while (i < end) QR.data[l++] += s * QR.data[i++];
                }
            }
            Rdiag[k] = -norm;
        }
    }

    /**
        @return true if R, and hence A, has full rank.
    **/
    public boolean isFullRank ()
    {
        for (int j = 0; j < n; j++) if (Rdiag[j] == 0) return false;
        return true;
    }

    public int rank ()
    {
        return rank (Math.ulp (1.0));  // machine epsilon
    }

    public int rank (double cutoff)
    {
        for (int j = 0; j < n; j++) if (Math.abs (Rdiag[j]) < cutoff) return j;
        return n;
    }

    /**
        Return the Householder vectors
        @return Lower trapezoidal matrix whose columns define the reflections
    **/
    public MatrixDense getH ()
    {
        MatrixDense H = new MatrixDense (m, n);
        for (int j = 0; j < n; j++)
        {
            int i   = j * m + j;
            int end = j * m + m;
            while (i < end) H.data[i] = QR.data[i++];
        }
        return H;
    }

    /**
        Return the upper triangular factor R
    **/
    public MatrixDense getR ()
    {
        MatrixDense R = new MatrixDense (n, n);
        int end = R.data.length;
        for (int i = 0; i < n; i++)
        {
            int r = i * n + i;  // Index into R, initially at (i,i)
            R.data[r] = Rdiag[i];
            r += n;  // advance by 1 column, to (i,i+1)
            int j = (i + 1) * m + i;  // Index into QR, initially at (i,i+1)
            while (r < end)
            {
                R.data[r] = QR.data[j];
                r += n;
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
        MatrixDense Q = new MatrixDense (m, n);
        for (int k = n - 1; k >= 0; k--)
        {
            int kk  = k * m + k;
            int end = k * m + m;
            Q.data[kk] = 1;
            if (QR.data[kk] == 0) continue;

            for (int j = k; j < n; j++)
            {
                double s = 0;
                int i = kk;
                int q = j * m + k;
                while (i < end) s += QR.data[i++] * Q.data[q++];
                s = -s / QR.data[kk];
                i = kk;
                q = j * m + k;
                while (i < end) Q.data[q++] += s * QR.data[i++];
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

    public MatrixDense solve (Matrix B)
    {
        return solve (B, true);
    }

    /**
        Least squares solution of AX = B
        @param B Matrix with exactly as many rows as A, along with any number of columns.
        @param unpermute Indicates to return X rather than ~PX. This is slightly cheaper than multiplying answer with P to get X.
        @return X that minimizes the two norm of QR~PX-B.
    **/
    public MatrixDense solve (Matrix B, boolean unpermute)
    {
        if (B.rows () != m) throw new IllegalArgumentException ("Matrix row dimensions must agree.");

        // Copy right hand side
        int nx = B.columns ();
        MatrixDense X = new MatrixDense (B);

        // Compute Y = ~QB
        for (int k = 0; k < n; k++)
        {
            int kk  = k * m + k;
            int end = k * m + m;
            for (int j = 0; j < nx; j++)
            {
                double s = 0;
                int a = kk;
                int x = j * m + k;
                while (a < end) s += QR.data[a++] * X.data[x++];
                s = -s / QR.data[kk];
                a = kk;
                x = j * m + k;
                while (a < end) X.data[x++] += s * QR.data[a++];
            }
        }

        // Solve RX = Y;
        for (int k = n - 1; k >= 0; k--)
        {
            int j = k;  // iterate over row k
            while (j < X.data.length)
            {
                X.data[j] /= Rdiag[k];
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

        if (! unpermute) return X.getRegion (0, 0, n - 1, nx - 1);

        MatrixDense result = new MatrixDense (n, nx);
        for (int f = 0; f < n; f++)  // from row
        {
            int x = f;    // index into X, at start of source row
            int r = P[f]; // index into result, at start of destination row
            int end = x + m * nx;
            while (x < end)
            {
                result.data[r] = X.data[x];
                x += m;
                r += n;
            }
        }
        return result;
    }
}

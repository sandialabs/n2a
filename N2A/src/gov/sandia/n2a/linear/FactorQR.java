package gov.sandia.n2a.linear;

import gov.sandia.n2a.language.type.Matrix;

/**
    QR Decomposition, computed by Householder reflections.

    Code taken from the public-domain package JAMA. Original comments:

    For an m-by-n matrix A with m >= n, the QR decomposition is an m-by-n
    orthogonal matrix Q and an n-by-n upper triangular matrix R so that A = Q*R.

    The QR decompostion always exists, even if the matrix does not have full
    rank, so the constructor will never fail. The primary use of the QR
    decomposition is in the least squares solution of nonsquare systems of
    simultaneous linear equations. This will fail if isFullRank() returns false.
**/
public class FactorQR
{
    protected MatrixDense QR;
    protected int         m;
    protected int         n;
    protected double[]    Rdiag;

    public FactorQR (Matrix A)
    {
        m = A.rows ();
        n = A.columns ();
        if (m < n) throw new IllegalArgumentException ("There must be at least as many rows as columns.");

        QR    = new MatrixDense (A);
        Rdiag = new double[n];

        // Main loop.
        for (int k = 0; k < n; k++)
        {
            // Compute 2-norm of k-th column without under/overflow.
            double nrm = 0;
            int kk  = k * m + k;
            int i   = kk;
            int end = k * m + m;
            while (i < end) nrm = hypot (nrm, QR.data[i++]);

            if (nrm != 0.0)
            {
                // Form k-th Householder vector.
                if (QR.data[kk] < 0) nrm = -nrm;
                i = kk;
                while (i < end) QR.data[i++] /= nrm;
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
            Rdiag[k] = -nrm;
        }
        System.out.print ("Rdiag ");
        for (int i = 0; i < Rdiag.length; i++) System.out.print (Rdiag[i] + " ");
        System.out.println ();
    }

    /**
        @return true if R, and hence A, has full rank.
    **/
    public boolean isFullRank ()
    {
        for (int j = 0; j < n; j++) if (Rdiag[j] == 0) return false;
        return true;
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

    /**
        Least squares solution of A*X = B
        @param B Matrix with exactly as many rows as A, along with any number of columns.
        @return X that minimizes the two norm of Q*R*X-B.
    **/
    public MatrixDense solve (Matrix B)
    {
        if (B.rows () != m) throw new IllegalArgumentException ("Matrix row dimensions must agree.");
        if (! isFullRank ()) throw new RuntimeException ("Matrix is rank deficient.");

        // Copy right hand side
        int nx = B.columns ();
        MatrixDense X = new MatrixDense (B);

        // Compute Y = transpose(Q)*B
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
        // Solve R*X = Y;
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
        return X.getRegion (0, 0, n - 1, nx - 1);
    }

    /**
       sqrt(a^2 + b^2) without under/overflow.
    **/
    public static double hypot (double a, double b)
    {
        double r;
        if (Math.abs (a) > Math.abs (b))
        {
            r = b / a;
            r = Math.abs (a) * Math.sqrt (1 + r * r);
        }
        else if (b != 0)
        {
            r = a / b;
            r = Math.abs (b) * Math.sqrt (1 + r * r);
        }
        else
        {
            r = 0.0;
        }
        return r;
    }
}

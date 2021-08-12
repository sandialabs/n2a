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
            for (int i = k; i < m; i++)
            {
                nrm = hypot (nrm, QR.get (i, k));
            }

            if (nrm != 0.0)
            {
                // Form k-th Householder vector.
                if (QR.get (k, k) < 0) nrm = -nrm;
                for (int i = k; i < m; i++)
                {
                    QR.set (i, k, QR.get (i, k) / nrm);
                }
                QR.set (k, k, QR.get (k, k) + 1);

                // Apply transformation to remaining columns.
                for (int j = k + 1; j < n; j++)
                {
                    double s = 0.0;
                    for (int i = k; i < m; i++)
                    {
                        s += QR.get (i, k) * QR.get (i, j);
                    }
                    s = -s / QR.get (k, k);
                    for (int i = k; i < m; i++)
                    {
                        QR.set (i, j, QR.get (i, j) + s * QR.get (i, k));
                    }
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
            for (int i = j; i < m; i++)
            {
                H.set (i, j, QR.get (i, j));
            }
        }
        return H;
    }

    /**
        Return the upper triangular factor R
    **/
    public MatrixDense getR ()
    {
        MatrixDense R = new MatrixDense (n, n);
        for (int i = 0; i < n; i++)
        {
            R.set (i, i, Rdiag[i]);
            for (int j = i + 1; j < n; j++) R.set (i, j, QR.get (i, j));
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
            Q.set (k, k, 1);
            for (int j = k; j < n; j++)
            {
                if (QR.get (k, k) != 0)
                {
                    double s = 0.0;
                    for (int i = k; i < m; i++)
                    {
                        s += QR.get (i, k) * Q.get (i, j);
                    }
                    s = -s / QR.get (k, k);
                    for (int i = k; i < m; i++)
                    {
                        Q.set (i, j, Q.get (i, j) + s * QR.get (i, k));
                    }
                }
            }
        }
        return Q;
    }

    /**
        Least squares solution of A*X = B
        @param B Matrix with as many rows as A and any number of columns.
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
            for (int j = 0; j < nx; j++)
            {
                double s = 0.0;
                for (int i = k; i < m; i++)
                {
                    s += QR.get (i, k) * X.get (i, j);
                }
                s = -s / QR.get (k, k);
                for (int i = k; i < m; i++)
                {
                    X.set (i, j, X.get (i, j) + s * QR.get (i, k));
                }
            }
        }
        // Solve R*X = Y;
        for (int k = n - 1; k >= 0; k--)
        {
            for (int j = 0; j < nx; j++)
            {
                X.set (k, j, X.get (k, j) / Rdiag[k]);
            }
            for (int i = 0; i < k; i++)
            {
                for (int j = 0; j < nx; j++)
                {
                    X.set (i, j, X.get (i, j) - X.get (k, j) * QR.get (i, k));
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

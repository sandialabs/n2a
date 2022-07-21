/*
Copyright 2021-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.studies;

import java.nio.file.Path;
import java.util.List;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.linear.FactorQR;
import gov.sandia.n2a.linear.MatrixDense;
import gov.sandia.n2a.ui.jobs.NodeJob;
import gov.sandia.n2a.ui.jobs.OutputParser;

public class OptimizerLM extends StudyIterator
{
    // Machine epsilon, the difference between 1 and next larger representable value.
    // We take square root because we want to allow for squaring a number and it still be representable.
    // Even though we use double for all internal work, we assume only float precision in the
    // model being optimized.
    public static final double epsilon = Math.sqrt (Math.ulp (1.0f));

    protected Study       study;
    protected int         iteration = -1;
    protected int         sample;       // This is always relative to one iteration of LM.
    protected int         baseIndex;    // Overall position in study as of the start of the current iteration. baseIndex+sample should give the current job number.
    protected int         maxSamples;   // This is only an estimate.
    protected int         maxIterations;
    protected List<MNode> variables;
    protected double      toleranceF;
    protected double      toleranceX;
    protected double      toleranceG = 0;
    protected double      perturbation;

    protected MatrixDense x;       // current state vector
    protected MatrixDense y;       // current value of time series output by model.  y=f(x) where f() is one entire job run.
    protected MatrixDense min;     // limits on domain
    protected MatrixDense max;
    protected MatrixDense J;       // the Jacobian, given current state x
    protected FactorQR    qr;
    protected MatrixDense Qy;
    protected MatrixDense p;
    protected MatrixDense xp;
    protected MatrixDense H;       // actual perturbation value for each variable in x
    protected MatrixDense scales;  // sensitivity of each variable in x
    protected MatrixDense Jnorms;  // norm of each column in J
    protected double      ratio;
    protected double      par;     // Levenberg-Marquardt parameter (mix between Gauss-Newton and gradient-descent)
    protected double      xnorm;
    protected double      ynorm;
    protected double      gnorm;
    protected double      pnorm;
    protected double      delta;   // step bound

    public OptimizerLM (Study study, String[] keys, List<MNode> variables)
    {
        super (keys);
        this.study     = study;
        this.variables = variables;

        maxIterations = study.source.getOrDefault (200,     "config", "maxIterations");
        toleranceF    = study.source.getOrDefault (epsilon, "config", "toleranceF");
        toleranceX    = study.source.getOrDefault (epsilon, "config", "toleranceX");
        perturbation  = study.source.getOrDefault (epsilon, "config", "perturbation");
        maxSamples    = maxIterations * (variables.size () + 1);

        // Determine range limits for each variable
        int n = variables.size ();
        x   = new MatrixDense (n, 1);
        H   = new MatrixDense (n, 1);
        min = new MatrixDense (n, 1);
        max = new MatrixDense (n, 1);
        for (int r = 0; r < n; r++)
        {
            MNode v = variables.get (r);

            double vMin = Double.NEGATIVE_INFINITY;
            double vMax = Double.POSITIVE_INFINITY;
            String value = v.get ();
            if (value.startsWith ("["))
            {
                value = value.substring (1);
                value = value.split ("]", 2)[0];
                String[] pieces = value.split (",");
                vMin = new UnitValue (pieces[0]).get ();
                vMax = new UnitValue (pieces[pieces.length - 1]).get ();
            }

            if (v.child ("min") != null) vMin = new UnitValue (v.get ("min")).get ();
            if (v.child ("max") != null) vMax = new UnitValue (v.get ("max")).get ();
            min.set (r, vMin);
            max.set (r, vMax);
        }
    }

    public int count ()
    {
        // TODO: Since LM has quadratic convergence, we dispose of roughly half the number of bits
        // of error in each iteration. To estimate remaining iterations, we need an estimate
        // of remaining bits of error, then take log2(number of error bits).
        // We also allow for there to always be at least one cycle left.
        int result = maxSamples;
        if (inner != null) result *= inner.count ();
        return result;
    }

    public void restart ()
    {
        iteration = -1;
    }

    public boolean step ()
    {
        // At this point, "sample" refers to the one most recently collected.

        if (iteration == -1)
        {
            iteration = 0;
            baseIndex = 1;
            sample    = -1;  // -1 is the base sample. 0 through n-1 are used to construct the Jacobian. sample >= n are for probing optimal step size.
            return true;  // Causes one sample (job) to be collected with parameters at the start point. This is the base sample for the first iteration.
            // In subsequent iterations, the base sample will come from the last step of the inner loop.
        }

        if (iteration == 0  &&  sample == -1)
        {
            // Results of first sample are now available. This establishes number of rows in Jacobian.
            OutputParser.Column series = getSeries (baseIndex + sample);
            int m = series.startRow + series.values.size () - 1;  // TODO: -1 is just for testing
            y = new MatrixDense (m, 1);
            for (int r = 0; r < m; r++) y.set (r, series.get (r+1));  // TODO: +1 is just for testing
            ynorm = y.norm (2);
            if (ynorm == 0) return false;  // Exact convergence (unlikely).
        }

        sample++;
        // At this point, "sample" refers to the one we will collect next.
        int n = variables.size ();
        if (sample < n) return true;  // Collect remaining samples needed to build Jacobian.

        // Build Jacobian right after the last needed sample has been collected. 
        int m = y.rows ();
        if (sample == n)  // We now have exactly enough samples to compute Jacobian. Next sample we collect (n) will be the first check for step size.
        {
            J = new MatrixDense (m, n);
            Jnorms = new MatrixDense (n, 1);
            for (int c = 0; c < n; c++)
            {
                OutputParser.Column series = getSeries (baseIndex + c);
                double h = H.get (c);
                double norm = 0;
                for (int r = 0; r < m; r++)
                {
                    double value = (series.get (r+1) - y.get (r)) / h;  // TODO: +1 is just for testing
                    J.set (r, c, value);
                    norm += value * value;
                }
                Jnorms.set (c, Math.sqrt (norm));
            }

            if (iteration == 0)
            {
                // Scale according to the norms of the columns of the initial Jacobian.
                scales = new MatrixDense (n, 1);
                for (int j = 0; j < n; j++)
                {
                    double scale = Jnorms.get (j);
                    if (scale == 0) scale = 1;
                    scales.set (j, scale);
                }

                xnorm = ((MatrixDense) x.multiplyElementwise (scales)).norm (2);
                if (xnorm == 0) delta = 1;
                else            delta = xnorm;
            }

            // Factorize J
            qr = new FactorQR (J);
            Qy = qr.getQ ().transpose ().multiply (y);  // Qy is permuted

            // compute the norm of the scaled gradient
            if (ynorm == 0) return false;  // exact convergence
            double gnorm = 0;
            for (int j = 0; j < n; j++)
            {
                double jnorm = Jnorms.get (qr.P[j]);
                if (jnorm == 0) continue;
                // Compute jth element of J'y, taking advantage of decomposition to reduce work.
                // We actually compute R'Q'y. This requires half or fewer multiplies than naive J'y.
                double temp = qr.QR.getRegion (0, j, j, j).dot (Qy);
                gnorm = Math.max (gnorm, Math.abs (temp / (ynorm * jnorm)));  // infinity norm of g=J'y/|y| with some additional scaling
            }
            if (gnorm <= toleranceG) return false;  // Gradient has gotten too small to follow.
            if (gnorm <= epsilon)    return false;  // failure 8

            // rescale if necessary
            for (int j = 0; j < n; j++) scales.set (j, Math.max (scales.get (j), Jnorms.get (j)));

            ratio = 0;
        }

        // Inner loop of algorithm -- Search over possible step sizes until we find one that gives acceptable improvement.

        //   Work done after sample
        if (sample > n)  // Skip this the first time (when sample==n).
        {
            // Retrieve sample
            OutputParser.Column series = getSeries (baseIndex + sample - 1);
            MatrixDense tempY = new MatrixDense (m, 1);
            for (int r = 0; r < m; r++) tempY.set (r, series.get (r+1));  // TODO: +1 is only for testing
            double ynorm1 = tempY.norm (2);

            // compute the scaled actual reduction
            double reductionActual = -1;
            if (ynorm1 / 10 < ynorm)
            {
                double temp = ynorm1 / ynorm;
                reductionActual = 1 - temp * temp;
            }

            // compute the scaled predicted reduction and the scaled directional derivative
            MatrixDense Jp = new MatrixDense (n, 1);
            for (int j = 0; j < n; j++)
            {
                // equivalent to J*p using the original J, since all scale information is in the R part of the QR factorization
                Jp = Jp.add (qr.QR.getRegion (0, j, j, j).multiply (p.get (qr.P[j])));
            }
            double temp1 = Jp.norm (2) / ynorm;
            double temp2 = Math.sqrt (par) * pnorm / ynorm;
            double reductionPredicted = temp1 * temp1 + 2 * temp2 * temp2;
            double dirder = -(temp1 * temp1 + temp2 * temp2);

            // compute the ratio of the actual to the predicted reduction
            ratio = 0;
            if (reductionPredicted != 0) ratio = reductionActual / reductionPredicted;

            // update the step bound
            if (ratio <= 0.25)
            {
                double update;
                if (reductionActual >= 0) update = 0.5;
                else                      update = dirder / (2 * dirder + reductionActual);
                if (ynorm1 / 10 >= ynorm  ||  update < 0.1) update = 0.1;
                delta = update * Math.min (delta, pnorm * 10);
                par /= update;
            }
            else if (par == 0  ||  ratio >= 0.75)
            {
                delta = pnorm * 2;
                par /= 2;
            }

            if (ratio >= 1e-4)  // successful iteration.
            {
                // update x, y, and their norms
                x     = xp;
                y     = tempY;
                xnorm = x.multiplyElementwise (scales).norm (2);
                ynorm = ynorm1;
            }

            // tests for convergence
            if (   Math.abs (reductionActual) <= toleranceF
                && reductionPredicted <= toleranceF
                && ratio <= 2)
            {
                // info = 1;
                return false;
            }
            if (delta <= toleranceX * xnorm)
            {
                // info = 2;
                return false;
            }

            // tests for termination and stringent tolerances
            if (   Math.abs (reductionActual) <= epsilon
                && reductionPredicted <= epsilon
                && ratio <= 2)
            {
                // failure 6
                return false;
            }
            if (delta <= epsilon * xnorm)
            {
                // failure 7
                return false;
            }
        }

        //   Work done before sample
        if (ratio < 1e-4)
        {
            // Determine the Levenberg-Marquardt parameter.
            p = lmpar ();  // par is updated as a side-effect

            // Store the direction p and x+p. Calculate the norm of p.
            xp = x.subtract (p);  // p is actually negative
            pnorm = p.multiplyElementwise (scales).norm (2);

            // On the first iteration, adjust the initial step bound.
            if (iteration == 0)
            {
                delta = Math.min (delta, pnorm);
            }

            return true;  // Collect next sample.
        }

        // Bottom of loop
        iteration++;
        baseIndex += sample;
        sample = 0;  // because y already contains result of last sample
        return iteration < maxIterations;
    }

    /**
        lmpar algorithm:

        A constrained lls problem:
            solve (J'J + parDD)x = J'f
            such that |Dx| is pretty close to delta

        Start with par = 0 and determine x
            Solve for x in J'Jx = J'f
            Early out if |Dx| is close to delta
        Determine min and max values for par
            J = QR  (so J'J = R'R)
            solve for b in R'b = DDx / |Dx|
            parl = (|Dx| - delta) / (delta * |b|^2)  // par lower (min)
            paru = |!DJ'f| / delta                   // par upper (max)
        Initialize par
            make sure it is in bounds
            if par is zero, par = |!DJ'f| / |Dx|
        Iterate
            solve for x in (J'J + parDD)x = J'f
                let (J'J + parDD) = QR (=SS' from qrsolv)
            end if |Dx| is close to delta
                or too many iterations
                or |Dx| is becoming smaller than delta when parl == 0
            solve for b in R'b = DDx / |Dx|  (that is, Sb = DDx / |Dx|)
            par += (|Dx| - delta) / (delta * |b|^2)
    **/
    public MatrixDense lmpar ()
    {
        double minimum = Double.MIN_NORMAL;
        int n = J.columns ();

        // Compute and store in x the Gauss-Newton direction. If the Jacobian is
        // rank-deficient, obtain a least squares solution.
        int nsing = qr.rank (0);  // index of the first zero diagonal of J.  If no such element exists, then nsing points to one past then end of the diagonal.
        MatrixDense d = new MatrixDense (n, 1);
        for (int j = 0; j < nsing; j++) d.set (j, Qy.get (j));  // d is permuted, just like Qy. Elements beyond nsing are zero.
        //   Solve for x by back-substitution in Rx=Q'y (which comes from QRx=y, where J=QR).
        d = qr.QR.backSubstitue (false, d);
        MatrixDense x = new MatrixDense (n, 1);
        for (int j = 0; j < n; j++) x.set (j, d.get (qr.P[j]));

        // Evaluate the function at the origin, and test
        // for acceptance of the Gauss-Newton direction.
        MatrixDense dx = x.multiplyElementwise (scales);
        double dxnorm = dx.norm (2);
        double fp = dxnorm - delta;
        if (fp <= 0.1 * delta)
        {
            par = 0;
            return x;
        }

        // If the Jacobian is not rank deficient, the Newton
        // step provides a lower bound, parl, for the zero of
        // the function. Otherwise set this bound to zero.
        double parl = 0;
        if (nsing == n)
        {
            for (int j = 0; j < n; j++)
            {
                int l = qr.P[j];
                d.set (j, scales.get (l) * (dx.get (l) / dxnorm));
            }

            // Solve by back-substitution for b in R'b=x (where "x" = d*d*x and x is normalized).
            d = qr.QR.transpose ().backSubstitue (true, d);
            double temp = d.norm (2);
            parl = fp / delta / temp / temp;
        }

        // Calculate an upper bound, paru, for the zero of the function.
        //   d = R'Q'y, equivalent to J'y before factorization
        d = qr.getR ().transpose ().multiply (Qy);
        for (int j = 0; j < n; j++) d.set (j, d.get (j) / scales.get (qr.P[j]));
        double gnorm = d.norm (2);
        double paru = gnorm / delta;
        if (paru == 0) paru = minimum / Math.min (delta, 0.1);

        // If the input par lies outside of the interval (parl,paru),
        // set par to the closer endpoint.
        par = Math.max (par, parl);
        par = Math.min (par, paru);
        if (par == 0) par = gnorm / dxnorm;

        int iteration = 0;
        while (true)
        {
            iteration++;

            // Evaluate the function at the current value of par.
            if (par == 0) par = Math.max (minimum, 0.001 * paru);
            d = scales.multiply (Math.sqrt (par));  // d is now non-permuted (same ordering as scales).

            double[] jdiag = qr.saveRdiag ();
            qrsolv (d, x);

            dx = x.multiplyElementwise (scales);
            dxnorm = dx.norm (2);
            double temp = fp;
            fp = dxnorm - delta;

            // If the function is small enough, accept the current value
            // of par.  Also test for the exceptional cases where parl
            // is zero or the number of iterations has reached 10.
            if (   Math.abs (fp) <= 0.1 * delta
                || (parl == 0  &&  fp <= temp  &&  temp < 0)
                || iteration >= 10)
            {
                qr.restoreRdiag (jdiag);
                return x;
            }

            // Compute the Newton correction.
            for (int j = 0; j < n; j++)
            {
                int l = qr.P[j];
                d.set (j, scales.get (l) * (dx.get (l) / dxnorm));  // d is now permuted (again)
            }
            d = qr.QR.backSubstitue (true, d);  // Using the modified Q from qrsolv(). This also includes the overwritten diagonal.
            qr.restoreRdiag (jdiag);

            temp = d.norm (2);
            double parc = fp / delta / temp / temp;

            // Depending on the sign of the function, update parl or paru.
            if (fp > 0) parl = Math.max (parl, par);
            if (fp < 0) paru = Math.min (paru, par);

            // Compute an improved estimate for par.
            par = Math.max (parl, par + parc);
        }
    }

    /**
        Updates the QR factorization of J to include an additional amount on the
        diagonal, and solves for x using the updated factorization. On entry,
        we start with the original QR factors: JP=QR
        This routine computes a new factorization SS' = P'(J'J+dd)P, and S is
        stored in the lower triangular part of J.  (Since P'(J'J+dd)P is
        symmetric positive semi-definite, SS' is both its Cholesky decomposition
        and its QR decomposition.)  The original diagonal of J is set aside in
        jdiag, so that it can be restored after the calling routine is finished
        with S.  Finally, this routine solves for x in
            P'(J'J+dd)Px=J'y
        which simplifies as follows
            SS'x=R'Q'y
            S'x=(!S'R')Q'y
        The code seems to apply the effect of matrix !S'R' on Q'y as it
        computes S, so at the end it simply solves S'x=z, where z contains the
        modified Q'y.
        @param J The QR-factored Jacobian.  Since Q has already been applied to
        y in "Qy", we don't need it any more, so the lower-triangular part is free.
    **/
    public void qrsolv (MatrixDense d, MatrixDense x)
    {
        int n = qr.n;

        // Copy J and Qy to preserve input and initialize s.
        // In particular, save the diagonal elements of J in x.
        MatrixDense z = new MatrixDense (Qy);
        for (int j = 0; j < n; j++)
        {
            for (int i = j + 1; i < n; i++)
            {
                qr.QR.set (i, j, qr.QR.get (j, i));
            }
        }
        MatrixDense stemp = x;  // Alias stemp to x to use its (currently free) storage space for computation. x will be filled in with a meaningful value at the end of this function.

        // Eliminate the diagonal matrix d using a givens rotation.
        for (int j = 0; j < n; j++)
        {
            // Prepare the row of d to be eliminated, locating the
            // diagonal element using P from the qr factorization.
            int l = qr.P[j];
            if (d.get (l) == 0) continue;
            stemp.set (j, d.get (l));
            for (int i = j+1; i < n; i++) stemp.set (i, 0);

            // The transformations to eliminate the row of d modify only a single
            // element of Qy beyond the first n.  This element is initially zero.
            double extraElement = 0;
            for (int k = j; k < n; k++)
            {
                // Determine a givens rotation which eliminates the appropriate
                // element in the current row of d.
                if (stemp.get (k) == 0) continue;
                double sin;
                double cos;
                double Jkk = qr.QR.get (k, k);
                double stk = stemp.get (k);
                if (Math.abs (Jkk) < Math.abs (stk))
                {
                    double cotan = Jkk / stk;
                    sin = 0.5 / Math.sqrt (0.25 + 0.25 * cotan * cotan);
                    cos = sin * cotan;
                }
                else
                {
                    double tan = stk / Jkk;
                    cos = 0.5 / Math.sqrt (0.25 + 0.25 * tan * tan);
                    sin = cos * tan;
                }

                // Compute the modified diagonal element of S and the modified
                // extra element of Qy.
                double zk = z.get (k);
                qr.QR.set (k, k, cos * Jkk + sin * stk);
                double temp  =   cos * zk  + sin * extraElement;
                extraElement =  -sin * zk  + cos * extraElement;
                z.set (k, temp);

                // Accumulate the transformation in the row of S.
                for (int i = k + 1; i < n; i++)
                {
                    double Jik = qr.QR.get (i, k);
                    double sti = stemp.get (i);
                    temp       =   cos * Jik + sin * sti;
                    stemp.set (i, -sin * Jik + cos * sti);
                    qr.QR.set (i, k, temp);
                }
            }
        }

        // Solve the triangular system S'x=z.  If the system is singular,
        // then obtain a least squares solution.
        int nsing = qr.rank (0);
        for (int i = nsing; i < n; i++) z.set (i, 0);
        z = qr.QR.transpose ().backSubstitue (false, z);
        for (int j = 0; j < n; j++) x.set (j, z.get (qr.P[j]));  // x is non-permuted
    }

    public boolean barrier ()
    {
        if (iteration < 0) return false;
        return sample < 0  ||  sample >= variables.size () - 1;  // At this point, "sample" refers the one most recently started. We want to start blocking right after the last column of the Jacobian is issued.
    }

    public void save (MNode studySource)
    {
        if (inner != null) inner.save (studySource);

        studySource.set (iteration, "iteration");
        studySource.set (sample,    "sample");
        studySource.set (baseIndex, "baseIndex");
        // TODO: when and where to update baseIndex?

        int rows = variables.size ();
        for (int r = 0; r < rows; r++)
        {
            MNode v = variables.get (r);
            v.set (x.get (r), "x");
        }
    }

    public void load (MNode study)
    {
        if (inner != null) inner.load (study);

        iteration = study.getOrDefault (0,  "iteration");
        sample    = study.getOrDefault (-1, "sample");
        baseIndex = study.getOrDefault (1,  "baseIndex");

        int rows = variables.size ();
        for (int r = 0; r < rows; r++)
        {
            MNode v = variables.get (r);
            x.set (r, v.getOrDefault (0, "x"));
        }
    }

    public void assign (MNode model)
    {
        int n = variables.size ();
        MNode root = study.source.child ("variables");

        // Output "loss"
        // Compare with EquationSet ctor code that builds output() for watch variables.
        // Output is always indirect, via a dummy expression. If "loss" is a state variable
        // (as opposed to temporary), then there will be a 1-cycle delay, and the first
        // row will be useless. TODO: detect when first row is inert and ignore it in LM
        // calculations.
        MNode g = model.child (keyPath);
        //   Determine dummy variable name
        MNode p = g.parent ();
        String dummy = "x0";
        int suffix = 1;
        while (p.child (dummy) != null) dummy = "x" + suffix++;
        //   Create output expression
        p.set ("output(\"study\"," + g.key () + ",\"loss\")", dummy);
        // TODO: sampling intervals (see EquationSet ctor)

        if (sample < 0)  // Set starting point
        {
            for (int r = 0; r < n; r++)
            {
                double value = 0;
                MNode v = variables.get (r);
                String[] keyPath = v.keyPath (root);
                String text = model.get (keyPath);
                if (text.isBlank ())
                {
                    double a = min.get (r);
                    double b = max.get (r);
                    if (Double.isFinite (a)  &&  Double.isFinite (b)) value = (a + b) / 2;
                }
                else
                {
                    value = new UnitValue (text).get ();
                }
                x.set (r, value);

                // Because this comes after the initial save, we cheat by inserting an extra save here.
                root.child (keyPath).set (value, "x");
            }
            return;
        }

        if (sample < n)  // Finite-difference samples to create Jacobian
        {
            // TODO: use structurally-orthogonal cover of Jacobian.
            // Requires either user-specified structure, or detect structure by observing zeroes in elements of J
            // for the entire run up to now. If the squared error is over something like a time series, it is
            // unlikely that any variables are separable. Only when subsets of the output are uniquely connected
            // to subsets of the input do we get separability. In that case, it is possible to do fewer samples
            // than the number of variables.
            for (int c = 0; c < n; c++)
            {
                MNode v = variables.get (c);
                String[] keyPath = v.keyPath (root);
                double value = x.get (c);
                if (c == sample)
                {
                    double h = perturbation * Math.abs (value);
                    if (h == 0) h = perturbation;
                    value += h;
                    H.set (c, h);
                }
                model.set (value, keyPath);
            }
            return;
        }

        // sample >= n -- Apply xp, the sample for testing step
        for (int c = 0; c < n; c++)
        {
            MNode v = variables.get (c);
            String[] keyPath = v.keyPath (root);
            double value = xp.get (c);
            model.set (value, keyPath);
        }
    }

    public OutputParser.Column getSeries (int index)
    {
        NodeJob node = study.getJob (index);
        Path jobDir = node.getJobPath ().getParent ();
        OutputParser parser = new OutputParser ();
        parser.parse (jobDir.resolve ("study"));
        return parser.getColumn ("loss");
    }
}

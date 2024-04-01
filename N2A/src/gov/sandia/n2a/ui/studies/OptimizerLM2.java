/*
Copyright 2021-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.studies;

import java.nio.file.Path;
import java.util.List;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.linear.Factorization;
import gov.sandia.n2a.linear.FactorizationBK;
import gov.sandia.n2a.linear.MatrixDense;
import gov.sandia.n2a.ui.jobs.NodeJob;
import gov.sandia.n2a.ui.jobs.OutputParser;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.language.type.Matrix;

/**
    This version of Levenberg-Marquardt uses Bunch-Kaufman factorization of the
    squared Jacobian (~J*J), rather than factoring J directly.
    See OptimizerLM for history on where this code came from.

    The matrix ~J*J tends to be more compact than J for large sparse systems, since its
    size depends only on the number of variables rather than the number of data points.
    This also tends to be more tolerant of zeroes in J.
**/
public class OptimizerLM2 extends StudyIterator
{
    // Machine epsilon, the difference between 1 and next larger representable value.
    // We take square root because we want to allow for squaring a number and it still be representable.
    // Even though we use double for all internal work, we assume only float precision in the
    // model being optimized.
    public static final double epsilon = Math.sqrt (Math.ulp (1.0f));

    protected Study       study;
    protected int         iteration  = -1;
    protected int         sample;             // This is always relative to one iteration of LM.
    protected int         baseIndex;          // Overall position in study as of the start of the current iteration. baseIndex+sample should give the current job number.
    protected int         yindex     = -1;    // Index of the job that provided the current value of y. Used to restore iterator state.
    protected int         maxIterations;
    protected int         expectedIterations; // estimate updated after first sample
    protected List<MNode> loss;
    protected List<MNode> variables;
    protected double      toleranceF;
    protected double      toleranceX;
    protected double      toleranceG;
    protected double      perturbation;
    protected double      perturbationMin;  // Lower limit on absolute size of perturbation.
    protected String[]    dummy;

    protected MatrixDense   x;       // current state vector
    protected MatrixDense   y;       // current value of time series output by model.  y=f(x) where f() is one entire job run.
    protected MatrixDense   min;     // limits on domain
    protected MatrixDense   max;
    protected MatrixDense   J;       // the Jacobian, given current state x
    protected Factorization factorization;
    protected MatrixDense   p;
    protected MatrixDense   xp;
    protected MatrixDense   scales;  // sensitivity of each variable in x
    protected double        ratio;
    protected double        par;     // Levenberg-Marquardt parameter (mix between Gauss-Newton and gradient-descent)
    protected double        xnorm;
    protected double        ynorm;
    protected double        pnorm;
    protected double        delta;   // step bound

    public OptimizerLM2 (Study study, List<MNode> loss, List<MNode> variables)
    {
        super (loss.get (0).keyPath (study.source.child ("variables")));  // by convention, we store optimizer state under the first loss variable
        this.study     = study;
        this.loss      = loss;
        this.variables = variables;

        maxIterations   = study.source.getOrDefault (200,     "config", "maxIterations");
        toleranceF      = study.source.getOrDefault (epsilon, "config", "toleranceF");
        toleranceX      = study.source.getOrDefault (epsilon, "config", "toleranceX");
        toleranceG      = study.source.getOrDefault (epsilon, "config", "toleranceG");
        perturbation    = study.source.getOrDefault (epsilon, "config", "perturbation");
        perturbationMin = study.source.getOrDefault (0.0,     "config", "perturbationMin");

        int n = variables.size ();
        x      = new MatrixDense (n, 1);
        min    = new MatrixDense (n, 1);
        max    = new MatrixDense (n, 1);
        p      = new MatrixDense (n, 1);
        scales = new MatrixDense (n, 1);

        // Determine range limit and initial value for each variable
        MNode root = study.source.child ("variables");
        String inherit = study.source.get ("$inherit");
        MNode model = AppData.docs.childOrEmpty ("models", inherit);
        for (int c = 0; c < n; c++)
        {
            MNode v = variables.get (c);

            double vMin = Double.NEGATIVE_INFINITY;
            double vMax = Double.POSITIVE_INFINITY;
            String range = v.get ();
            if (range.startsWith ("["))
            {
                range = range.substring (1);
                range = range.split ("]", 2)[0];
                String[] pieces = range.split (",");
                vMin = new UnitValue (pieces[0]).get ();
                vMax = new UnitValue (pieces[pieces.length - 1]).get ();
            }

            if (v.child ("min") != null) vMin = new UnitValue (v.get ("min")).get ();
            if (v.child ("max") != null) vMax = new UnitValue (v.get ("max")).get ();
            min.set (c, vMin);
            max.set (c, vMax);

            double value = 0;
            String initial;
            if (v.child ("start") == null) initial = model.get (v.keyPath (root));
            else                           initial = v.get ("start");
            if (initial.isBlank ())
            {
                if (Double.isFinite (vMin)  &&  Double.isFinite (vMax)) value = (vMin + vMax) / 2;
            }
            else
            {
                value = new UnitValue (initial).get ();
            }
            x.set (c, value);
        }

        // Determine dummy variable for loss output() statement
        // Compare with EquationSet ctor code that handles watch variables.
        // If "loss" is a state variable (as opposed to temporary), then there will be
        // a 1-cycle delay, and the first row will be useless.
        int count = loss.size ();
        dummy = new String[count];
        for (int i = 0; i < count; i++)
        {
            MNode g = model.child (loss.get (i).keyPath (root));
            MNode p = g.parent ();
            String d = "x0";
            int suffix = 1;
            while (p.child (d) != null) d = "x" + suffix++;
            dummy[i] = d;
        }
    }

    public int count ()
    {
        int result = (variables.size () + 1) * (expectedIterations == 0 ? maxIterations : expectedIterations);
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
            baseIndex++;     // Always relative to last position of base, especially when coming from a previous optimization run.
            sample    = -1;  // -1 is the base sample. 0 through n-1 are used to construct the Jacobian. sample >= n are for probing optimal step size.
            yindex    = -1;
            if (factorization == null)
            {
                switch (study.source.getOrDefault ("bk", "config", "factorizer"))
                {
                    case "bk":
                    default:
                        factorization = new FactorizationBK ();
                }
            }
            return true;  // Causes one sample (job) to be collected with parameters at the start point. This is the base sample for the first iteration.
            // In subsequent iterations, the base sample will come from the last step of the inner loop.
        }

        if (iteration == 0  &&  sample == -1)
        {
            // Results of first sample are now available. This establishes number of rows in Jacobian.
            yindex = 0;
            y = new MatrixDense (getSeries (yindex));
            ynorm = y.norm (2);

            // Update estimated number of iterations, based on size of initial error.
            // Since LM has quadratic convergence, we dispose of roughly half the number of bits
            // of error in each iteration. Unfortunately, this theory applies to x, not y.
            // Since we don't know the correct value of x, nor the slope of the error surface,
            // we make the assumption that we dispose of 1 bit of error per iteration
            // (error cut in half). We also assume the error will never get smaller than epsilon.
            if (expectedIterations == 0)  // Only do this once for the entire study. TODO: keep a running average
            {
                long bitsY       = (Double.doubleToLongBits (ynorm  ) & 0x7FF0000000000000L) >> 52;
                long bitsEpsilon = (Double.doubleToLongBits (epsilon) & 0x7FF0000000000000L) >> 52;
                long bitsError   = bitsY - bitsEpsilon;
                expectedIterations = (int) Math.max (1, bitsError);
            }

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
            MatrixDense Jnorms = new MatrixDense (n, 1);  // norm of each column in J
            for (int c = 0; c < n; c++)
            {
                double[] series = getSeries (baseIndex + c);
                double h = perturbation * Math.abs (x.get (c));
                if      (h < perturbationMin) h = perturbationMin;
                else if (h == 0)              h = perturbation;
                double norm = 0;
                for (int r = 0; r < m; r++)
                {
                    double value = (series[r] - y.get (r)) / h;
                    J.set (r, c, value);
                    norm += value * value;
                }
                Jnorms.set (c, Math.sqrt (norm));
            }

            if (iteration == 0)
            {
                // Scale according to the norms of the columns of the initial Jacobian.
                for (int j = 0; j < n; j++)
                {
                    double scale = Jnorms.get (j);
                    if (scale == 0) scale = 1;
                    scales.set (j, scale);
                }

                xnorm = x.multiplyElementwise (scales).norm (2);
                if (xnorm == 0) delta = 1;
                else            delta = xnorm;
            }

            // compute the norm of the scaled gradient
            if (ynorm == 0) return false;  // exact convergence
            double gnorm = 0;
            MatrixDense Jy = J.transpose ().multiply (y);  // Since J is dense, then transpose is zero cost.
            for (int j = 0; j < n; j++)
            {
                double jnorm = Jnorms.get (j);
                if (jnorm == 0) continue;
                gnorm = Math.max (gnorm, Math.abs (Jy.get (j) / (ynorm * jnorm)));  // infinity norm of g=J'y/|y| with some additional scaling
            }
            if (gnorm <= toleranceG) return false;  // Gradient has gotten too small to follow.

            // rescale if necessary
            for (int j = 0; j < n; j++) scales.set (j, Math.max (scales.get (j), Jnorms.get (j)));

            ratio = 0;
        }

        // Inner loop of algorithm -- Search over possible step sizes until we find one that gives acceptable improvement.

        //   Work done after sample
        if (sample > n)  // Skip this the first time (when sample==n). Notice that the last section of code below is the first to be executed on sample==n.
        {
            // Retrieve sample
            int yindexNext = baseIndex + sample - 1;
            MatrixDense tempY = new MatrixDense (getSeries (yindexNext));
            double ynormNext = tempY.norm (2);

            // compute the scaled actual reduction
            double reductionActual = -1;
            if (ynormNext / 10 < ynorm)
            {
                double temp = ynormNext / ynorm;
                reductionActual = 1 - temp * temp;
            }
            //System.err.println ("reductionActual=" + reductionActual + " " + ynormNext + " " + ynorm);

            // compute the scaled predicted reduction and the scaled directional derivative
            double temp1 = J.multiply (p).norm (2) / ynorm;
            double temp2 = Math.sqrt (par) * pnorm / ynorm;
            double reductionPredicted = temp1 * temp1 + 2 * temp2 * temp2;
            double dirder = -(temp1 * temp1 + temp2 * temp2);

            // compute the ratio of the actual to the predicted reduction
            if (reductionPredicted == 0) ratio = 0;
            else                         ratio = reductionActual / reductionPredicted;

            // update the step bound
            if (ratio <= 0.25)
            {
                double update;
                if (reductionActual >= 0) update = 0.5;
                else                      update = dirder / (2 * dirder + reductionActual);
                if (ynormNext / 10 >= ynorm  ||  update < 0.1) update = 0.1;
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
                x      = xp;
                y      = tempY;
                yindex = yindexNext;
                xnorm  = x.multiplyElementwise (scales).norm (2);
                ynorm  = ynormNext;
            }
            //System.err.println ("ratio=" + ratio);

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
            lmpar ();  // par is updated as a side-effect

            // Limit p so x+p stays within bounds.
            // This only consider dimensions individually. A more accurate approach would re-scale all elements of p together.
            for (int c = 0; c < n; c++)
            {
                double tx = x.get (c);
                double tp = p.get (c);
                double txp = tx - tp;  // p is actually stored as negative, so compute x+p by subtracting p.
                double limit = max.get (c);
                if (txp > limit)
                {
                    p.set (c, tx - limit);  // solve for tp in limit=tx-tp
                }
                else
                {
                    limit = min.get (c);
                    if (txp < limit) p.set (c, tx - limit);
                }
            }

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
        sample = 0;  // Start collection of next Jacobian. y already contains result of last sample.
        return iteration < maxIterations;
    }

    /**
        A constrained LLS problem. Using Newton's method, solve
            (J'J + parDD)x = J'f
            such that |Dx| is pretty close to delta
    **/
    public void lmpar ()
    {
        double minimum = Double.MIN_NORMAL;
        int n = J.columns ();

        // Compute and store in x the Gauss-Newton direction.
        // ~J * J * x = ~J * y
        MatrixDense JT = J.transpose ();
        MatrixDense Jy = JT.multiply (y);
        MatrixDense JJ = JT.multiply (J);
        factorization.factorize (JJ);
        p = (MatrixDense) factorization.solve (Jy);  // TODO: this cast may not be valid for future factorization methods.
        //System.err.println ("x=" + p);

        // Evaluate the function at the origin, and test
        // for acceptance of the Gauss-Newton direction.
        MatrixDense dx = p.multiplyElementwise (scales);
        double dxnorm = dx.norm (2);
        double fp = dxnorm - delta;
        //System.err.println ("fp=" + fp + " " + dxnorm + " " + delta);
        if (fp <= 0.1 * delta)
        {
            par = 0;
            return;
        }

        // J is required to have full rank, so the Newton step provides
        // a lower bound, parl, for the zero of the function.
        Matrix wa1 = dx.multiplyElementwise (scales).divide (dxnorm);
        Matrix wa2 = factorization.solve (wa1);
        double parl = Math.max (0, fp / (delta * wa1.dot (wa2)));

        // Calculate an upper bound, paru, for the zero of the function.
        double gnorm = Jy.divide (scales).norm (2);
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
            wa1 = new MatrixDense (JJ);
            for (int i = 0; i < n; i++)  // add parDD to diagonal of JJ
            {
                double w = wa1.get (i, i);
                double s = scales.get (i);
                wa1.set (i, i, w + s * s * par);
            }
            factorization.factorize (wa1);
            p = (MatrixDense) factorization.solve (Jy);

            dx = p.multiplyElementwise (scales);
            dxnorm = dx.norm (2);
            double oldFp = fp;
            fp = dxnorm - delta;

            // If the function is small enough, accept the current value
            // of par.  Also test for the exceptional cases where parl
            // is zero or the number of iterations has reached 10.
            //System.err.println ("par=" + par + " " + parl + " " + paru + " " + fp + " " + delta);
            if (   Math.abs (fp) <= 0.1 * delta
                || (parl == 0  &&  fp <= oldFp  &&  oldFp < 0)
                || iteration >= 10)
            {
                return;
            }

            // Compute the Newton correction.
            wa1 = dx.multiplyElementwise (scales).divide (dxnorm);
            wa2 = factorization.solve (wa1);
            double parc = fp / (delta * wa1.dot (wa2));

            // Depending on the sign of the function, update parl or paru.
            if (fp > 0) parl = Math.max (parl, par);
            if (fp < 0) paru = Math.min (paru, par);

            // Compute an improved estimate for par.
            par = Math.max (parl, par + parc);
        }
    }

    public boolean barrier ()
    {
        if (iteration < 0) return false;
        return sample < 0  ||  sample >= variables.size () - 1;  // At this point, "sample" refers the one most recently started. We want to start blocking right after the last column of the Jacobian is issued.
    }

    public void save (MNode study)
    {
        if (inner != null) inner.save (study);
        MNode state = node (study);

        state.set (iteration, "iteration");
        state.set (sample,    "sample");
        state.set (baseIndex, "baseIndex");
        state.set (yindex,    "yindex");
        state.set (ratio,     "ratio");
        state.set (par,       "par");
        state.set (delta,     "delta");

        int n = variables.size ();
        for (int c = 0; c < n; c++)
        {
            MNode v = variables.get (c);
            v.set (x     .get (c), "x");
            v.set (scales.get (c), "scale");
            v.set (p     .get (c), "p");
        }

        // Remaining variables will be recreated from existing data.
    }

    public void load (MNode study)
    {
        if (inner != null) inner.load (study);
        MNode state = node (study);

        iteration = state.getOrDefault (0,  "iteration");
        sample    = state.getOrDefault (-1, "sample");
        baseIndex = state.getOrDefault (1,  "baseIndex");
        yindex    = state.getOrDefault (-1, "yindex");
        ratio     = state.getOrDefault (0,  "ratio");
        par       = state.getOrDefault (0,  "par");
        delta     = state.getOrDefault (1,  "delta");

        int n = variables.size ();
        for (int c = 0; c < n; c++)
        {
            MNode v = variables.get (c);
            x     .set (c, v.getOrDefault (0.0, "x"));
            scales.set (c, v.getOrDefault (1.0, "scale"));
            p     .set (c, v.getOrDefault (0.0, "p"));
        }

        // Recreate other variables
        xnorm = x.multiplyElementwise (scales).norm (2);
        pnorm = p.multiplyElementwise (scales).norm (2);
        xp    = x.subtract (p);

        //   y -- Based on saved index
        if (yindex < 0) return;
        y = new MatrixDense (getSeries (yindex));
        int m = y.rows ();
        ynorm = y.norm (2);

        //   J
        //   Note that the first barrier after collecting J is when sample==n-1.
        //   That is just before the cycle where J is computed, so no need to do it in that case.
        //   The case here is for barriers that come later, probing to find step size.
        if (sample < n) return;
        J = new MatrixDense (m, n);
        for (int c = 0; c < n; c++)
        {
            double[] series = getSeries (baseIndex + c);
            double h = perturbation * Math.abs (x.get (c));
            if (h == 0) h = perturbation;
            for (int r = 0; r < m; r++)
            {
                J.set (r, c, (series[r] - y.get (r)) / h);
            }
        }
    }

    public void assign (MNode model)
    {
        int n = variables.size ();
        MNode root = study.source.child ("variables");

        // Output "loss"
        int count = loss.size ();
        for (int i = 0; i < count; i++)
        {
            MNode l = loss.get (i);
            MNode g = model.child (l.keyPath (root));
            MNode p = g.parent ();
            p.set ("output(\"study\"," + g.key () + ",\"loss" + i + "\")", dummy[i]);
            // TODO: sampling intervals (see EquationSet ctor)
        }

        // Set starting point
        if (sample < 0)
        {
            for (int c = 0; c < n; c++)
            {
                MNode v = variables.get (c);
                String[] keyPath = v.keyPath (root);
                double value = x.get (c);
                model.set (value, keyPath);
            }
            return;
        }

        // Finite-difference samples to create Jacobian
        if (sample < n)
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
                    if      (h < perturbationMin) h = perturbationMin;
                    else if (h == 0)              h = perturbation;
                    value += h;
                    // We don't bound the perturbation. Presumably it is small enough that
                    // if it transgresses min or max, it does not go so far that the model fails.
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

    public double[] getSeries (int index)
    {
        NodeJob node = study.getJob (index);
        Path jobDir = node.getJobPath ().getParent ();
        OutputParser parser = new OutputParser ();
        parser.parse (jobDir.resolve ("study"));
        int columns = loss.size ();
        double[] result = new double[parser.rows * columns];
        for (int i = 0; i < columns; i++)
        {
            OutputParser.Column c = parser.getColumn ("loss" + i);
            int count = c.values.size ();
            int offset = i * parser.rows + c.startRow;
            for (int j = 0; j < count; j++) result[offset+j] = c.values.get (j);
        }
        return result;
    }
}

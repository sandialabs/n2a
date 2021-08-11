/*
Copyright 2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.studies;

import java.nio.file.Path;
import java.util.List;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.language.type.MatrixDense;
import gov.sandia.n2a.ui.jobs.NodeJob;
import gov.sandia.n2a.ui.jobs.OutputParser;

public class OptimizerLM extends StudyIterator
{
    public static final double epsilon = Math.sqrt (Math.ulp (1.0));  // square root of machine epsilon, the difference between 1 and next larger representable value. We take square root because we want to allow for squaring a number and it still be representable.

    protected Study       study;
    protected int         iteration = -2;
    protected int         sample;       // This is always relative to one iteration of LM.
    protected int         baseIndex;    // Overall position in study as of the start of the current iteration. baseIndex+sample should give the current job number.
    protected int         maxSamples;   // This is only an estimate.
    protected int         maxIterations;
    protected List<MNode> variables;
    protected double      toleranceF;
    protected double      toleranceX;
    protected double      perturbation;

    protected MatrixDense x;       // current state vector
    protected MatrixDense y;       // current value of time series output by model.  y=f(x) where f() is one entire job run.
    protected MatrixDense min;     // limits on domain
    protected MatrixDense max;
    protected MatrixDense J;       // the Jacobian, given current state x
    protected MatrixDense H;       // actual perturbation value for each variable in x
    protected MatrixDense scales;  // sensitivity of each variable in x
    protected MatrixDense Jnorms;  // norm of each column in J
    protected double      par;     // Levenberg-Marquardt parameter (mix between Gauss-Newton and gradient-descent)
    protected double      xnorm;
    protected double      ynorm;
    protected double      delta;

    public OptimizerLM (String[] keys, List<MNode> variables, Study study)
    {
        super (keys);
        this.study     = study;
        this.variables = variables;

        // Determine maxSamples from iteration limit and number of variables
        maxSamples    = maxIterations * (variables.size () + 1);
        maxIterations = study.source.getOrDefault (200,     "maxIterations");
        toleranceF    = study.source.getOrDefault (epsilon, "toleranceF");
        toleranceX    = study.source.getOrDefault (epsilon, "toleranceX");
        perturbation  = study.source.getOrDefault (epsilon, "perturbation");

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
            if (! value.isEmpty ())
            {
                if (value.startsWith ("["))
                {
                    value = value.substring (1);
                    value = value.split ("]", 2)[0];
                }
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

    /**
        Returns the worst-case estimate of number of samples, including any combinatorial parameter sweeps.
    **/
    public int count ()
    {
        int result = maxSamples;
        if (inner != null) result *= inner.count ();
        return result;
    }

    public void restart ()
    {
        iteration = -2;
        sample    = 0;
    }

    public boolean step ()
    {
        if (iteration == -2)
        {
            iteration = -1;
            return true;
        }

        sample++;
        // TODO: Determine if current iteration of LM is complete
        boolean iterationDone = true;
        if (iterationDone) baseIndex = study.index;
        // TODO: Determine if entire optimization is done.
        boolean optimizationDone = true;
        return optimizationDone;
    }

    public boolean barrier ()
    {
        return iteration == -1  ||  sample >= variables.size ();
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

        iteration = study.getOrDefault (0, "iteration");
        sample    = study.getOrDefault (0, "sample");
        baseIndex = study.getOrDefault (0, "baseIndex");

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

        // First-time initialization. We need the model in hand to do this, mainly to get the initial value of x.
        MNode root = study.source.child ("variables");
        if (iteration == -1)
        {
            for (int r = 0; r < n; r++)
            {
                MNode v = variables.get (r);
                String[] keyPath = v.keyPath (root);
                double min = v.getOrDefault (Double.NEGATIVE_INFINITY, "min");
                double max = v.getOrDefault (Double.POSITIVE_INFINITY, "max");
                double defaultValue = 0;
                if (Double.isFinite (min)  &&  Double.isFinite (max)) defaultValue = (min + max) / 2;
                double value = model.getOrDefault (defaultValue, keyPath);
                x.set (n, value);

                // Because this comes after the initial save, we cheat by inserting an extra save here.
                root.child (keyPath).set (value, "x");
            }
            return;  // Run first job on unperturbed initial value of x.
        }
        if (iteration == 0  &&  sample == 0)  // Collect results of first job. This establishes number of rows in Jacobian.
        {
            OutputParser.Column series = getSeries (baseIndex - 1);
            int m = series.startRow + series.values.size ();
            y = new MatrixDense (m, 1);
            for (int r = 0; r < m; r++) y.set (r, series.get (r));
            ynorm = y.getColumn (0).norm (2);
        }

        if (sample < n)  // Finite-difference samples to create Jacobian
        {
            // TODO: use structurally-orthogonal cover of Jacobian.
            // Requires either user-specified structure, or detect structure by observing zeroes in elements of J
            // for the entire run up to now. If the squared error is over something like a time series, it is
            // unlikely that any variables are separable. Only when subsets of the output are uniquely connected
            // to subsets of the inputs do we get separability. In that case, it is possible to do fewer samples
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
        }
        else  // Main LM algorithm
        {
            int m = y.rows ();

            // On first sample after perturbations are done, load data and compute Jacobian.
            if (J == null)
            {
                J = new MatrixDense (m, n);
                for (int c = 0; c < n; c++)
                {
                    OutputParser.Column series = getSeries (baseIndex + c);
                    double h = H.get (c);
                    double norm = 0;
                    for (int r = 0; r < m; r++)
                    {
                        double value = (series.get (r) - y.get (r)) / h;
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

                    xnorm = ((MatrixDense) x.multiplyElementwise (scales)).norm (2);
                    if (xnorm == 0) delta = 1;
                    else            delta = xnorm;
                }
            }

            // Factorize J
        }
    }

    public OutputParser.Column getSeries (int index)
    {
        NodeJob node = study.getJob (index);
        Path jobDir = node.getJobPath ().getParent ();
        OutputParser parser = new OutputParser ();
        parser.parse (jobDir.resolve ("study"));
        return parser.getColumn ("goal");
    }
}

/*
Copyright 2022-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.util.Random;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.linear.MatrixDense;

public class Sphere extends Function
{
    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "sphere";
            }

            public Operator createInstance ()
            {
                return new Sphere ();
            }
        };
    }

    public boolean canBeConstant ()
    {
        return false;
    }

    public Operator simplify (Variable from, boolean evalOnly)
    {
        if (operands.length == 0)
        {
            // No need to set from.changed
            operands = new Operator[1];
            Operator sigma = new Constant (new MatrixDense (3, 1, 1));
            sigma.parent = this;
            sigma.center   = MSB / 2;  // Constant matrix should always carry fixed-point information.
            sigma.exponent = -sigma.center;
            operands[0] = sigma;
            return this;
        }

        // Any nonzero quantity of operands.
        // We only pay attention to first operand.
        operands[0] = operands[0].simplify (from, evalOnly);
        Operator sigma = operands[0];
        if (sigma.isScalar ())
        {
            double s = sigma.getDouble ();
            if (s == 0)  // There's no good reason for the user to set this value, but could come up while simplifying an expression.
            {
                from.changed = true;  // Needed because we are replacing ourself with a simpler constant.
                sigma.parent = parent;
                return sigma;
            }

            // Determine exponent for sigma. This will be passed on to the matrix that replaces it.
            // This code is a hack to avoid repeating Constant.determineExponent() here.
            // That code does sophisticated analysis of the source to allocate bits,
            // so we should avoid duplicating it.
            ExponentContext context = new ExponentContext (new EquationSet (null, "bogus"));
            context.from = new Variable ("bogus");
            sigma.determineExponent (context);

            // No need to set from.changed
            Constant newSigma = new Constant (new MatrixDense (3, 1, s));
            newSigma.parent = this;
            newSigma.center   = sigma.center;
            newSigma.exponent = sigma.exponent;
            operands[0] = newSigma;
            return this;
        }
        else if (sigma instanceof Constant)
        {
            Constant cs = (Constant) sigma;
            if (cs.value instanceof Matrix)
            {
                // No need to set from.changed for any of the transforms below ...

                Matrix A = (Matrix) cs.value;
                int rows = A.rows ();
                int cols = A.columns ();
                if (rows == 1  &&  cols > 1)
                {
                    cs.value = A = A.transpose ();
                    rows = cols;
                    cols = 1;
                }

                if (cols == 1)  // Check for zeroes in single column.
                {
                    int rank = (int) A.norm (0);
                    if (rank < rows)  // Generate a matrix to project a lower-dimensional disc onto the subspace.
                    {
                        from.changed = true;
                        MatrixDense B = new MatrixDense (rows, rank);
                        cs.value = B;
                        int c = 0;
                        for (int r = 0; r < rows; r++)
                        {
                            double value = A.get (r);
                            if (value != 0) B.set (r, c++, value);
                        }
                    }
                }
                // Don't bother checking for columns that are all zeroes.
                // Since sigma is a constant, this should be obvious to the user.
            }
        }

        return this;
    }

    public void determineExponent (ExponentContext context)
    {
        if (operands.length == 0)
        {
            updateExponent (context, -1 - MSB, MSB);  // Current implementation never quite reaches 1, only [0,1).
        }
        else
        {
            Operator op = operands[0];
            op.determineExponent (context);
            if (op.exponent != UNKNOWN) updateExponent (context, op.exponent, op.center);
        }
    }

    public void determineExponentNext ()
    {
        if (operands.length > 0) super.determineExponentNext ();
    }

    // This code assumes that simplify() has been run, so operand 0 is a matrix of correct form.
    public Type eval (Instance context) throws EvaluationException
    {
        Random random;
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) random = new Random ();
        else                   random = simulator.random;

        Matrix sigma = (Matrix) operands[0].eval (context);

        int rows = sigma.rows ();
        int cols = sigma.columns ();
        int dimension =  cols == 1 ? rows : cols;

        MatrixDense temp = new MatrixDense (dimension, 1);
        for (int r = 0; r < dimension; r++) temp.set (r, random.nextGaussian ());
        temp = temp.multiply (new Scalar (Math.pow (random.nextDouble (), 1.0 / dimension) / temp.norm (2)));

        if (cols == 1) return sigma.multiplyElementwise (temp);
        return sigma.multiply (temp);
    }

    public String toString ()
    {
        return "sphere";
    }
}

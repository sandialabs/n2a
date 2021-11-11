/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.n2a.linear.MatrixDense;
import gov.sandia.n2a.plugins.extpoints.Backend;
import tech.units.indriya.AbstractUnit;

public class ReadMatrix extends Function
{
    public String name;     // For C backend, the name of the MatrixInput object.
    public String fileName; // For C backend, the name of the string variable holding the file name, if any.

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "matrix";
            }

            public Operator createInstance ()
            {
                return new ReadMatrix ();
            }
        };
    }

    public boolean canBeConstant ()
    {
        return false;
    }

    public boolean canBeInitOnly ()
    {
        return true;
    }

    public void determineExponent (ExponentContext context)
    {
        String mode = "";
        int lastParm = operands.length - 1;
        if (lastParm > 0) mode = operands[lastParm].getString ();

        int centerNew   = MSB / 2;
        int exponentNew = getExponentHint (mode, 0) + MSB - centerNew;
        updateExponent (context, exponentNew, centerNew);
    }

    public void determineExponentNext ()
    {
        exponent = exponentNext;
        // All our operands are strings, so no point in passing the exponent down.
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        unit = AbstractUnit.ONE;
    }

    public Matrix open (Instance context)
    {
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return null;  // absence of simulator indicates analysis phase, so opening files is unnecessary

        String path = ((Text) operands[0].eval (context)).value;
        Object A = simulator.holders.get (path);
        if (A == null)
        {
            A = Matrix.factory (simulator.jobDir.resolve (path));
            simulator.holders.put (path, A);
        }
        else if (! (A instanceof Matrix))
        {
            Backend.err.get ().println ("ERROR: Reopening file as a different resource type.");
            throw new Backend.AbortRun ();
        }
        return (Matrix) A;
    }

    public Type getType ()
    {
        return new MatrixDense ();
    }

    public Type eval (Instance context)
    {
        Matrix A = open (context);
        if (A == null) return new MatrixDense ();  // C backend depends on this being a zero-dimensional matrix.
        return A;
    }

    public String toString ()
    {
        return "matrix";
    }
}

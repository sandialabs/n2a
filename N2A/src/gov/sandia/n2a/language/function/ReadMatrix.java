/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.backend.internal.Holder;
import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
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

    public void determineExponent (Variable from)
    {
        String mode = "";
        int lastParm = operands.length - 1;
        if (lastParm > 0) mode = operands[lastParm].getString ();
        boolean RC = mode.contains ("rows")  ||  mode.contains ("columns");

        lastParm = Math.min (lastParm, 2);
        for (int i = 1; i <= lastParm; i++) operands[i].determineExponent (from);

        if (RC)
        {
            updateExponent (from, MSB, 0);  // Return an integer
        }
        else
        {
            int centerNew   = MSB / 2;
            int exponentNew = getExponentHint (mode, 0) + MSB - centerNew;
            updateExponent (from, exponentNew, centerNew);
        }
    }

    public void determineExponentNext (Variable from)
    {
        String mode = "";
        int lastParm = operands.length - 1;
        if (lastParm > 0) mode = operands[lastParm].getString ();
        boolean raw = mode.contains ("raw");

        lastParm = Math.min (lastParm, 2);
        for (int i = 1; i <= lastParm; i++)
        {
            Operator op = operands[i];
            if (raw) op.exponentNext = MSB;  // We expect an integer.
            else     op.exponentNext = 0;    // We expect a number in [0,1], with some provision for going slightly out of bounds.
            op.determineExponentNext (from);
        }
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        for (int i = 0; i < operands.length; i++) operands[i].determineUnit (fatal);
        unit = AbstractUnit.ONE;
    }

    public Matrix open (Instance context)
    {
        Simulator simulator = Simulator.getSimulator (context);
        if (simulator == null) return null;  // absence of simulator indicates analysis phase, so opening files is unnecessary

        String path = ((Text) operands[0].eval (context)).value;
        Holder A = simulator.holders.get (path);
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
        return new Scalar ();
    }

    public Type eval (Instance context)
    {
        Matrix A = open (context);
        if (A == null) return new Scalar (0);

        String mode = "";
        int lastParm = operands.length - 1;
        if (lastParm > 0) mode = operands[lastParm].getString ();  // Must be a constant string, not computed.
        if (mode.equals ("columns")) return new Scalar (A.columns ());
        if (mode.equals ("rows"   )) return new Scalar (A.rows    ());

        double row    = ((Scalar) operands[1].eval (context)).value;
        double column = ((Scalar) operands[2].eval (context)).value;
        return new Scalar (A.get (row, column, mode.equals ("raw")));
    }

    public String toString ()
    {
        return "matrix";
    }
}

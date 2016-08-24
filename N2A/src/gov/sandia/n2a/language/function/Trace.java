/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.function;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class Trace extends Function
{
    String variableName;  // Trace needs to know its target variable in order to auto-generate a column name. This value is set by an analysis process.

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "trace";
            }

            public Operator createInstance ()
            {
                return new Trace ();
            }
        };
    }

    public boolean isOutput ()
    {
        return true;
    }

    public Type eval (Instance context)
    {
        Scalar result = (Scalar) operands[0].eval (context);

        Simulator simulator = Simulator.getSimulator (context);
        if (simulator != null)
        {
            String column;
            if (operands.length >= 2)  // column name is specified
            {
                column = operands[1].eval (context).toString ();  // evaluate every time, because it could change
            }
            else  // auto-generate column name
            {
                column = context.path () + "." + variableName;
            }
            simulator.trace (column, (float) result.value);
        }

        return result;
    }

    // This method should be called by analysis, with v set to the variable that holds this equation.
    public void determineVariableName (Variable v)
    {
        if (operands[0] instanceof AccessVariable)
        {
            variableName = ((AccessVariable) operands[0]).name;  // the raw name, including prime marks for derivatives
        }
        else
        {
            variableName = v.name;
        }
    }

    public String toString ()
    {
        return "trace";
    }
}

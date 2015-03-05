/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.functions;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.SpecialVariables;
import gov.sandia.n2a.language.parse.ASTNodeBase;

import java.util.HashMap;
import java.util.Map;

public class EvaluationContext
{
    public Map<Variable, Object> values = new HashMap<Variable, Object> ();

    public EvaluationContext ()
    {
    }

    public Object get (Variable v) throws EvaluationException
    {
        if (! values.containsKey (v))
        {
            // Not stored, so try to compute the value.
            values.put (v, null);  // If successful, we will replace this with the correct result. Otherwise, it remains null. This also functions as a guard against infinite recursion.

            // Select the default equation
            boolean init = v.container.getInit ();
            EquationEntry defaultEquation = null;
            for (EquationEntry e : v.equations)
            {
                if (init  &&  e.ifString.equals ("$init"))  // TODO: also handle $init==1, or any other equivalent expression
                {
                    defaultEquation = e;
                    break;
                }
                if (e.ifString.length () == 0)
                {
                    defaultEquation = e;
                }
            }

            boolean evaluated = false;
            for (EquationEntry e : v.equations)  // Scan for first equation whose condition is nonzero
            {
                if (e == defaultEquation) continue;
                Object result = e.conditional.eval (this);
                if (result instanceof Number  &&  ((Number) result).floatValue () != 0)
                {
                    values.put (v, e.expression.eval (this));
                    evaluated = true;
                }
            }
            if (! evaluated  &&  defaultEquation != null)
            {
                values.put (v, defaultEquation.expression.eval (this));
            }
        }

        return values.get (v);
    }

    public void set (Variable v, Object value)
    {
        values.put (v, value);
    }
}

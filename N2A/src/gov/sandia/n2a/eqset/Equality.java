/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.eqset;

import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Transformer;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class Equality
{
    public AccessVariable target; // Variable we want to isolate on the lhs
    public Constant       rc;     // Row or column value which we control on the rhs
    public Operator lhs;
    public Operator rhs;

    public Equality (Operator op, AccessVariable target)
    {
        this.target = target;
        rc = new Constant (0);
        lhs = op;
        rhs = rc;
    }

    public void solve ()
    {
        try
        {
            while (lhs != target)
            {
                lhs.solve (this);
            }
        }
        catch (EvaluationException e) {}
    }

    public int getIndex (Instance context, int i)
    {
        ((Scalar) rc.value).value = i;
        return (int) Math.round (((Scalar) rhs.eval (context)).value);
    }

    /**
        Directly substitute replacement for rc in rhs.
    **/
    public void replaceRC (Operator replacement)
    {
        rhs = rhs.transform (new Transformer ()
        {
            public Operator transform (Operator op)
            {
                if (op == rc) return replacement;
                return null;
            }
        });
    }
}

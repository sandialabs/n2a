/*
Copyright 2018-2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.eqset;

import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Transformer;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.operator.Add;
import gov.sandia.n2a.language.operator.Divide;
import gov.sandia.n2a.language.operator.EQ;
import gov.sandia.n2a.language.operator.Multiply;
import gov.sandia.n2a.language.operator.Subtract;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class Equality
{
    public AccessVariable target; // Variable we want to isolate on the lhs
    public Constant       rc;     // Row or column value which we control on the rhs
    public Operator       lhs;
    public Operator       rhs;

    public Equality (Operator op, AccessVariable target)
    {
        this.target = target;
        rc = new Constant (0);
        lhs = op;
        rhs = rc;
    }

    /**
        Set up to solve equation for a given variable.
        If this.target comes back null, then don't try to solve! Caller is responsible to check.
    **/
    public Equality (EQ eq, Variable v)
    {
        // Find the specified variable
        eq = (EQ) eq.deepCopy ();
        class VariableVisitor implements Visitor
        {
            public AccessVariable found;
            public boolean visit (Operator op)
            {
                if (op instanceof AccessVariable)
                {
                    AccessVariable av = (AccessVariable) op;
                    if (av.reference.variable == v)
                    {
                        found = av;
                        return false;
                    }
                }
                return true;
            }
        }
        VariableVisitor vv = new VariableVisitor ();
        eq.operand0.visit (vv);
        if (vv.found != null)
        {
            lhs = eq.operand0;
            rhs = eq.operand1;
            target = vv.found;
            return;
        }
        eq.operand1.visit (vv);
        lhs = eq.operand1;
        rhs = eq.operand0;
        target = vv.found;  // If this is null, then variable was not found on either side of equality. Don't try to solve!
    }

    public void solve ()
    {
        try
        {
            while (lhs != target) lhs.solve (this);
        }
        catch (EvaluationException e) {}
    }

    public int getIndex (Instance context, int i)
    {
        ((Scalar) rc.value).value = i;
        return (int) Math.round (((Scalar) rhs.eval (context)).value);
    }

    /**
        Given y=ax+b or y=b-ax, where a and b are constants, determine values of a and b.
        Verifies that y and x are in the correct positions. If not, throws an EvaluationException.
        No need to call anything beyond the constructor. This is a driver function that handles the entire process.
        @return A double array with two elements, a and b, in that order.
    **/
    public double[] extractLinear (Variable x) throws EvaluationException
    {
        if (target == null) throw new EvaluationException ("Target variable not found");
        solve ();
        if (! (lhs instanceof AccessVariable)) throw new EvaluationException ("Failed to solve for target variable");

        double[] result = new double[2];
        result[0] = 1;
        result[1] = 0;
        if (rhs instanceof AccessVariable  &&  ((AccessVariable) rhs).reference.variable == x) return result;  // simple equality

        Operator temp = rhs;
        if (temp instanceof Add)
        {
            Add add = (Add) temp;
            if (add.operand0 instanceof Constant)
            {
                result[1] = add.operand0.getDouble ();
                temp = add.operand1;
            }
            else if (add.operand1 instanceof Constant)
            {
                result[1] = add.operand1.getDouble ();
                temp = add.operand0;
            }
            else throw new EvaluationException ("Not a simple linear expression");
        }
        else if (temp instanceof Subtract)
        {
            Subtract s = (Subtract) temp;
            if (s.operand0 instanceof Constant)
            {
                result[0] = -1;
                result[1] = s.operand0.getDouble ();
                temp = s.operand1;
            }
            else if (s.operand1 instanceof Constant)
            {
                result[1] = -s.operand1.getDouble ();
                temp = s.operand0;
            }
            else throw new EvaluationException ("Not a simple linear expression");
        }

        if (temp instanceof Multiply)
        {
            Multiply m = (Multiply) temp;
            if (m.operand0 instanceof Constant)
            {
                result[0] *= m.operand0.getDouble ();
                temp = m.operand1;
            }
            else if (m.operand1 instanceof Constant)
            {
                result[0] *= m.operand1.getDouble ();
                temp = m.operand0;
            }
            else throw new EvaluationException ("Not a simple linear expression");
        }
        else if (temp instanceof Divide)
        {
            Divide d = (Divide) temp;
            if (d.operand0 instanceof Constant) throw new EvaluationException ("Not a simple linear expression");
            if (d.operand1 instanceof Constant)
            {
                result[0] /= d.operand1.getDouble ();
                temp = d.operand0;
            }
            else throw new EvaluationException ("Not a simple linear expression");
        }

        // At this point, temp must be x.
        if (temp instanceof AccessVariable  &&  ((AccessVariable) temp).reference.variable == x) return result;
        throw new EvaluationException ("x is missing or in wrong form");
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

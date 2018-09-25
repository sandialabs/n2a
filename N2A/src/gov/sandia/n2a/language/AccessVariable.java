/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet.ConnectionBinding;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.parse.SimpleNode;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class AccessVariable extends Operator
{
    public String name; // only needed to resolve the variable (since we will abandon the AST node)
    public VariableReference reference;  // non-null when this node has been resolved in the context of an EquationSet

    public AccessVariable ()
    {
    }

    public AccessVariable (String name)
    {
        this.name = name;
    }

    public AccessVariable (VariableReference reference)
    {
        this.reference = reference;
        name = reference.variable.nameString ();
    }

    public int getOrder ()
    {
        String temp = name;
        int order = 0;
        while (temp.endsWith ("'"))
        {
            order++;
            temp = temp.substring (0, temp.length () - 1);
        }
        return order;
    }

    public String getName ()
    {
        String[] pieces = name.split ("'", 2);
        return pieces[0];
    }

    public void getOperandsFrom (SimpleNode node)
    {
        name = node.jjtGetValue ().toString ();
    }

    public Operator simplify (Variable from)
    {
        if (reference == null  ||  reference.variable == null) return this;  // unresolved!
        Variable v = reference.variable;
        if (v.name.equals ("$connect")  ||  v.name.equals ("$init")  ||  v.name.equals ("$live")) return this;  // specifically prevent phase indicators from being replaced by a Constant
        if (v.hasAttribute ("externalWrite")) return this;  // A variable may locally evaluate to a constant, yet be subject to change from outside equations.
        if (v.equations.size () != 1) return this;
        EquationEntry e = v.equations.first ();
        if (e.expression == null) return this;
        if (e.condition != null)
        {
            if (! (e.condition instanceof Constant)) return this;
            // Check for nonzero constant
            Type value = ((Constant) e.condition).value;
            if (! (value instanceof Scalar)  ||  ((Scalar) value).value == 0) return this;
        }

        if (! (e.expression instanceof Constant))
        {
            // Attempt to simplify expression, and maybe get a Constant
            Variable p = from;
            while (p != null)
            {
                if (p == v) return this;  // can't simplify, because we've already visited this variable
                p = p.visited;
            }
            v.visited = from;
            e.expression = e.expression.simplify (v);
        }

        if (e.expression instanceof Constant)
        {
            from.removeDependencyOn (v);
            from.changed = true;
            Operator result = e.expression.deepCopy ();
            result.parent = parent;
            return result;
        }
        if (e.expression instanceof AccessVariable)  // Our variable is simply an alias for another variable, so grab the other variable instead.
        {
            AccessVariable av = (AccessVariable) e.expression;
            Variable v2 = av.reference.variable;
            if (v2 == v) return this;
            if (v2.hasAttribute ("temporary")  &&  v2.container != from.container) return this;  // Can't reference a temporary outside the current equation set.

            // Fold aliased variable
            from.removeDependencyOn (v);
            from.addDependencyOn (v2);
            from.changed = true;
            name = av.reference.variable.nameString ();
            reference.variable = v2;
            //   Merge resolution paths
            //   Our current resolution path should end with the equation set that contains v.
            //   Thus, any resolution from v to v2 could simply be tacked onto the end.
            //   However, we want to avoid doubling back. This occurs if our penultimate
            //   resolution step matches the first step of v2's path. In that case,
            //   delete both our last step and the first step from v2's path.
            int last = reference.resolution.size () - 1;
            for (Object o2 : av.reference.resolution)
            {
                if (last > 0)
                {
                    Object o = reference.resolution.get (last - 1);
                    if (o instanceof ConnectionBinding) o = ((ConnectionBinding) o).endpoint;
                    if (o == o2)
                    {
                        reference.resolution.remove (last--);
                        continue;  // Keeps from adding the next step from v2's path.
                    }
                    last = 0;  // Stop checking
                }
                reference.resolution.add (o2);
            }
        }
        return this;
    }

    public void determineExponent (Variable from)
    {
        Variable v = reference.variable;
        // Don't flag a change, because we are merely reflecting the current state of v.
        exponent = v.exponent;
        center   = v.center;
    }

    public Type getType ()
    {
        return reference.variable.type;
    }

    public Type eval (Instance instance)
    {
        return instance.get (reference);
    }

    public String toString ()
    {
        return name;
    }

    public boolean equals (Object that)
    {
        if (! (that instanceof AccessVariable)) return false;
        AccessVariable a = (AccessVariable) that;

        if (reference != null  &&  a.reference != null) return reference.variable == a.reference.variable;
        return name.equals (a.name);
    }
}

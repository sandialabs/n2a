/*
Copyright 2013-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.parse.SimpleNode;
import gov.sandia.n2a.language.type.Instance;

public class AccessVariable extends Operator
{
    public String            name;      // of target variable as it appears in the AST node (which we throw away). May be modified by EquationSet.flatten(), but not by our own simplify().
    public VariableReference reference; // non-null when this node has been resolved in the context of an EquationSet

    // For in-place renaming of variables by the UI.
    public int columnBegin; // Position of first character in source line.
    public int columnEnd;   // Position of last character in source line. Not necessarily same as columnBegin+name.lenght()-1, since original text might not be canonical.

    public AccessVariable ()
    {
    }

    public AccessVariable (String name, int columnBegin, int columnEnd)
    {
        this.name        = name;
        this.columnBegin = columnBegin;
        this.columnEnd   = columnEnd;
    }

    public AccessVariable (VariableReference reference)
    {
        this.reference = reference;
        name = reference.variable.nameString ();
    }

    public void getOperandsFrom (SimpleNode node)
    {
        Identifier ID = (Identifier) node.jjtGetValue ();
        name        = ID.name;
        columnBegin = ID.columnBegin;
        columnEnd   = ID.columnEnd;
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

    public Operator simplify (Variable from, boolean evalOnly)
    {
        if (reference == null  ||  reference.variable == null) return this;  // unresolved!
        Variable v = reference.variable;
        if (v.name.equals ("$connect")  ||  v.name.equals ("$init")  ||  v.name.equals ("$live")) return this;  // Specifically prevent phase indicators from being replaced by a constant. They are tagged "initOnly", but that designation can change, particularly for $live.
        if (v.hasAny ("externalWrite", "initOnly")) return this;  // A variable may locally evaluate to a constant, yet be subject to change from outside.
        if (v.equations.size () != 1) return this;
        EquationEntry e = v.equations.first ();
        if (e.expression == null) return this;
        if (e.condition != null)
        {
            if (! (e.condition instanceof Constant)) return this;
            if (e.condition.getDouble () == 0) return this;  // must be nonzero
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
            e.expression = e.expression.simplify (v, evalOnly);
        }

        if (e.expression instanceof Constant)
        {
            from.changed = true;
            if (! evalOnly) releaseDependencies (from);
            Operator result = e.expression.deepCopy ();
            result.parent = parent;
            return result;
        }
        if (evalOnly) return this;
        if (e.expression instanceof AccessVariable)  // Our variable is simply an alias for another variable, so grab the other variable instead.
        {
            AccessVariable av = (AccessVariable) e.expression;
            Variable v2 = av.reference.variable;
            if (v2 == v) return this;
            if (v2.container != from.container)  // Referencing external variable, so exercise some caution.
            {
                // If v2 is explicitly marked as "temporary", we must not reference it.
                if (v2.hasAttribute ("temporary")) return this;

                // If v2 will be changed into a more expensive form (buffered), nothing is gained by this "optimization".
                // Not forming a dependency on v2 also supports the case where v2 is acting as an (unmarked) local
                // temporary that is made externally visible via a second variable.
                if (! v2.hasAny ("externalRead", "externalWrite")) return this;
            }

            // Fold aliased variable
            from.changed = true;
            releaseDependencies (from);
            from.addDependencyOn (v2);
            reference.variable = v2;
            reference.mergeResolutionPath (av.reference);
            reference.addDependencies (from);
        }
        return this;
    }

    public void determineExponent (ExponentContext context)
    {
        Variable v = reference.variable;
        // Don't flag a change, because we are merely reflecting the current state of v.
        exponent = v.exponent;
        center   = v.center;
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        if (reference != null) unit = reference.variable.unit;
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

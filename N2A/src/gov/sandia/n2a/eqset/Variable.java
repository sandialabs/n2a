/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.eqset;

import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.Transformer;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.function.Max;
import gov.sandia.n2a.language.function.Min;
import gov.sandia.n2a.language.operator.Add;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

public class Variable implements Comparable<Variable>
{
    public String                       name;
    public int                          order;      // of differential
    public Type                         type;       // Stores an actual instance of the type. Necessary to get the size of Matrix. Otherwise, only class matters.
    public Set<String>                  attributes;
    public NavigableSet<EquationEntry>  equations;
    // resolution
    public EquationSet                  container;  // non-null iff this variable is contained in an EquationSet.variables collection
    public VariableReference            reference;  // points to variable that actually contains the data, which is usually us unless we are a proxy for a variable in another equation set. null if not resolved yet.
    // graph analysis
    public List<Variable>               uses;       // Variables we depend on. Forms a digraph (which may have cycles) on Variable nodes.
    public boolean                      hasUsers;   // Indicates that some variable depends on us. That is, we exist in some Variable.uses collection.
    public List<Variable>               before;     // Variables that must be evaluated after us. Generally the same as uses, unless we are a temporary, in which case the ordering is reversed. Note EquationSet.ordered
    public Variable                     visited;    // Points to the previous variable visited on the current path. Used to prevent infinite recursion. Only work on a single thread.
    public int                          priority;   // For evaluation order.

    public Variable (String name)
    {
        this (name, -1);
    }

    public Variable (String name, int order)
    {
        this.name  = name;
        this.order = order;
    }

    public void add (EquationEntry e)
    {
        if (equations == null) equations = new TreeSet<EquationEntry> ();
        equations.add (e);
        e.variable = this;
    }

    /**
        The given equation takes precedence over any existing entry.
    **/
    public void replace (EquationEntry e)
    {
        if (equations == null)
        {
            equations = new TreeSet<EquationEntry> ();
        }
        else
        {
            equations.remove (e);
        }
        equations.add (e);
        e.variable = this;
    }

    /**
        Combine equations from given variable with this, where this takes precedence.
        All equations will be detached from given variable, whether or not they are added to this.
    **/
    public void merge (Variable v)
    {
        if (equations == null)
        {
            equations = new TreeSet<EquationEntry> ();
        }
        for (EquationEntry e : v.equations)
        {
            e.variable = this;
        }
        equations.addAll (v.equations);
        v.equations.clear ();
    }

    public void mergeExpressions (Variable v)
    {
        if (equations == null) equations = new TreeSet<EquationEntry> ();
        for (EquationEntry e : v.equations)
        {
            if (e.assignment.matches ("[+<>]="))
            {
                EquationEntry e2 = equations.floor (e);
                if (e.compareTo (e2) == 0)  // conditionals are exactly the same
                {
                    // merge expressions
                    if (e.assignment.startsWith ("+"))
                    {
                        OperatorBinary op = new Add ();
                        op.operand0 = e2.expression;
                        op.operand1 = e .expression;
                        e2.expression = op;
                        continue;
                    }

                    Function f = null;
                    if      (e.assignment.startsWith ("<")) f = new Min ();
                    else if (e.assignment.startsWith (">")) f = new Max ();
                    if (f != null)
                    {
                        f.operands[0] = e2.expression;
                        f.operands[1] = e .expression;
                        e2.expression = f;
                        continue;
                    }
                }
            }
            e.variable = this;
            equations.add (e);  // any pre-existing equation takes precedence over this one
        }
        v.equations.clear ();
    }

    public void transform (Transformer transformer)
    {
        if (equations == null) return;
        for (EquationEntry e : equations)
        {
            if (e.expression != null)  // could be null if this is a special variable or integrated value that is added automatically
            {
                e.expression = e.expression.transform (transformer);
            }
            if (e.conditional != null)
            {
                e.conditional = e.conditional.transform (transformer);
                e.ifString = e.conditional.render ();
            }
        }
    }

    public void simplify ()
    {
        if (equations == null) return;
        for (EquationEntry e : equations)
        {
            if (e.expression != null)
            {
                e.expression = e.expression.simplify (this);
            }
            if (e.conditional != null)
            {
                e.conditional = e.conditional.simplify (this);
                e.ifString = e.conditional.render ();
            }
        }
    }

    public void addDependency (Variable whatWeNeed)
    {
        if (uses == null)
        {
            uses = new ArrayList<Variable> ();
        }
        else
        {
            // This is crude and costs O(n^2), but using something better like a TreeSet would
            // force us to use the Comparable interface, which won't handle Variable identity
            // quite right.
            // FWIW, when the number of dependencies is low, this is probably more efficient.
            for (Variable v : uses)
            {
                if (v == whatWeNeed) return;  // already recorded this dependency, so don't do it again
            }
        }
        uses.add (whatWeNeed);
        whatWeNeed.hasUsers = true;
    }

    /**
        Determines if query exists anywhere in our dependency graph.
        @return The actual Variable we depend on (as opposed to the query object).
        If we don't depend on the item described by the query, then null.
    **/
    public Variable dependsOn (Variable query)
    {
        if (query.equals (this)) return null;  // Don't depend on ourself.
        visited = null;
        return dependsOnRecursive (query);
    }

    protected Variable dependsOnRecursive (Variable query)
    {
        if (query.equals (this)) return this;

        // Prevent infinite recursion
        Variable p = visited;
        while (p != null)
        {
            if (p == this) return null;
            p = p.visited;
        }

        if (uses != null)
        {
            for (Variable u : uses)
            {
                if (u.container != container) continue;  // Don't exit the current equation set.
                u.visited = this;
                Variable result = u.dependsOnRecursive (query);
                if (result != null) return result;
            }
        }
        
        return null;
    }

    public void setBefore (Variable after)
    {
        if (before == null)
        {
            before = new ArrayList<Variable> ();
        }
        before.add (after);  // I am before the given variable, and it is after me.
    }

    public void setBefore ()
    {
        if (uses == null)
        {
            return;
        }
        for (Variable v : uses)
        {
            if (v.reference == null  ||  v.reference.variable.container != container)  // not resolved to the same equation set
            {
                continue;
            }
            if (v.name.equals (name)  &&  v.order == order + 1)  // my derivative
            {
                // We don't consider derivatives when sorting variables, because integration is done
                // in a separate phase.
                continue;
            }
            if (v.hasAttribute ("temporary"))
            {
                v.setBefore (this);
            }
            else
            {
                setBefore (v);
            }
        }
    }

    public void setPriority (int value)
    {
        // Prevent infinite recursion
        Variable p = visited;
        while (p != null)
        {
            if (p == this) return;
            p = p.visited;
        }

        // Ripple-increment priority
        if (value <= priority) return;
        priority = value++;  // note the post-increment here
        for (Variable v : before)
        {
            if (v == this) continue; // This could be detected by the recursion test above, but our trail would still be damaged.
            if (v.container != container) continue;  // Don't exit the current equation set when setting priority.
            v.visited = this;
            v.setPriority (value);
        }
    }

    public void visitTemporaries ()
    {
        if (hasAttribute ("derivativeOrDependency")) return;
        addAttribute ("derivativeOrDependency");
        if (uses == null) return;
        for (Variable u : uses)
        {
            if (u.hasAttribute ("temporary")) u.visitTemporaries ();
        }
    }

    /**
        Add given string to a collection of tags that describe this variable.
        Defined attributes include:
        <dl>
            <dt>global</dt>
                <dd>shared by all instances of a part</dd>
            <dt>constant</dt>
                <dd>value is known at generation time, so can be hard-coded</dd>
            <dt>initOnly</dt>
                <dd>value is set at init time, and never changed after that.</dd>
            <dt>reference</dt>
                <dd>the actual value of the variable is stored in a different
                equation set</dd>
            <dt>integrated</dt>
                <dd>this lower-ordered version of a variable is defined by a
                higher-order derivative rather than a direct equation</dd>
            <dt>accessor</dt>
                <dd>value is given by a function rather than stored</dd>
            <dt>preexistent</dt>
                <dd>storage does not need to be created for the variable (in C)
                because it is either inherited or passed into a function</dd>
            <dt>simulator</dt>
                <dd>accessible via the simulator object; a subcategory of preexistent</dd>
            <dt>temporary</dt>
                <dd>this variable is used immediately in the equation set and
                never stored between time-steps; a temporary must not be
                accessed by other equation sets</dd>
            <dt>externalRead</dt>
                <dd>an equation in some other equation-set uses this variable</dd>
            <dt>externalWrite</dt>
                <dd>an equation in some other equation-set changes this variable</dd>
            <dt>cycle</dt>
                <dd>needs a second storage location to break a cyclic dependency</dd>
            <dt>dummy</dt>
                <dd>an equation has some important side-effect, but the result itself
                is not stored because it is never referenced.</dd>
        </dl>
    **/
    public void addAttribute (String attribute)
    {
        if (attributes == null)
        {
            attributes = new TreeSet<String> ();
        }
        attributes.add (attribute);
    }

    public void removeAttribute (String attribute)
    {
        if (attributes == null)
        {
            return;
        }
        attributes.remove (attribute);
    }

    public boolean hasAttribute (String attribute)
    {
        if (attributes == null)
        {
            return false;
        }
        return attributes.contains (attribute);
    }

    public boolean hasAll (String[] attributeArray)
    {
        if (attributes == null)
        {
            return false;
        }
        for (String s : attributeArray)
        {
            if (! attributes.contains (s))
            {
                return false;
            }
        }
        return true;
    }

    public boolean hasAny (String[] attributeArray)
    {
        if (attributes == null)
        {
            return false;
        }
        for (String s : attributeArray)
        {
            if (attributes.contains (s))
            {
                return true;
            }
        }
        return false;
    }

    public String nameString ()
    {
        String result = name;
        for (int i = 0; i < order; i++)
        {
            result = result + "'";
        }
        return result;
    }

    public String attributeString ()
    {
        if (attributes == null) return "[]";
        else                    return attributes.toString ();
    }

    public int compareTo (Variable that)
    {
        int result = name.compareTo (that.name);
        if (result != 0)
        {
            return result;
        }
        if (order >= 0  &&  that.order >= 0)
        {
            return order - that.order;  // ascending order; necessary for generating integrate() function in C-backend
        }
        return 0;
    }

    @Override
    public boolean equals (Object that)
    {
        if (this == that)
        {
            return true;
        }
        Variable e = (Variable) that;
        if (e == null)
        {
            return false;
        }
        return compareTo (e) == 0;
    }
}

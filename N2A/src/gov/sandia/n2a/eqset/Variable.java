/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.eqset;

import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.Transformer;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.function.Max;
import gov.sandia.n2a.language.function.Min;
import gov.sandia.n2a.language.operator.Add;
import gov.sandia.n2a.language.operator.Multiply;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

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
    public int                          assignment; // TODO: this should probably replace EquationEntry.assignment
    // resolution
    public EquationSet                  container;  // non-null iff this variable is contained in an EquationSet.variables collection
    public VariableReference            reference;  // points to variable that actually contains the data, which is usually us unless we are a proxy for a variable in another equation set. null if not resolved yet.
    public Variable                     derivative; // The variable from which we are integrated, if any.
    // graph analysis
    public List<Variable>               uses;       // Variables we depend on. Forms a digraph (which may have cycles) on Variable nodes.
    public List<Object>                 usedBy;     // Variables and EquationSets that depends on us.
    public List<Variable>               before;     // Variables that must be evaluated after us. Generally the same as uses, unless we are a temporary, in which case the ordering is reversed. Note EquationSet.ordered
    public Variable                     visited;    // Points to the previous variable visited on the current path. Used to prevent infinite recursion. Only work on a single thread.
    public int                          priority;   // For evaluation order.

    // Internal backend
    // TODO: put this in a beckendData field, similar to EquationSet.backendData. The problem with this is the extra overhead to unpack the object.
    public int      readIndex  = -1; // Position in Instance.values to read
    public boolean  readTemp;        // Read the temp Instance rather than the main one
    public int      writeIndex = -1; // Position Instance.values to write
    public boolean  writeTemp;       // Write the temp Instance rather than the main one
    public boolean  global;          // redundant with "global" attribute; for faster execution, since it is a frequently checked

    // Assignment modes
    public static final int UNKNOWN  = 0;  // the default state of assignment when this object is constructed
    public static final int REPLACE  = 1;  // =
    public static final int ADD      = 2;  // +=
    public static final int MULTIPLY = 3;  // *=
    public static final int MAX      = 4;  // >=
    public static final int MIN      = 5;  // <=

    public Variable (String name)
    {
        this (name, -1);
    }

    public Variable (String name, int order)
    {
        this.name  = name;
        this.order = order;
    }

    public void determineAssignment ()
    {
        // Only change the assignment mode if we actually encounter an equation
        for (EquationEntry e : equations)
        {
            if (e.assignment == null  ||  e.assignment.isEmpty ()) continue;
            if      (e.assignment.equals ("=" )) assignment = REPLACE;
            else if (e.assignment.equals ("=+")) assignment = ADD;
            else if (e.assignment.equals ("=*")) assignment = MULTIPLY;
            else if (e.assignment.equals ("=>")) assignment = MAX;
            else if (e.assignment.equals ("=<")) assignment = MIN;
            else if (e.assignment.equals ("=:"))
            {
                assignment = REPLACE;
                addAttribute ("temporary");
            }
            else continue;
            break;  // stop on the first valid equation
        }
    }

    public void add (EquationEntry e)
    {
        if (equations == null) equations = new TreeSet<EquationEntry> ();
        equations.add (e);
        e.variable = this;
        determineAssignment ();
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
        determineAssignment ();
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
        determineAssignment ();
    }

    public void mergeExpressions (Variable v)
    {
        if (equations == null) equations = new TreeSet<EquationEntry> ();
        for (EquationEntry e : v.equations)
        {
            char mode = 'N';
            if (e.assignment.length () == 2) mode = e.assignment.charAt (1);

            if (mode == '+'  ||  mode == '*'  ||  mode == '<'  ||  mode == '>')
            {
                EquationEntry e2 = equations.floor (e);
                if (e.compareTo (e2) == 0)  // conditionals are exactly the same
                {
                    // merge expressions
                    OperatorBinary op = null;
                    if      (mode == '+') op = new Add ();
                    else if (mode == '*') op = new Multiply ();
                    if (op != null)
                    {
                        op.operand0 = e2.expression;
                        op.operand1 = e .expression;
                        e2.expression = op;
                        continue;
                    }

                    Function f = null;
                    if      (mode == '<') f = new Min ();
                    else if (mode == '>') f = new Max ();
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
        determineAssignment ();
    }

    public void visit (Visitor visitor)
    {
        if (equations == null) return;
        for (EquationEntry e : equations) e.visit (visitor);
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
        // TODO: Re-sort the equations? Delete any that have constant 0 for their condition?
    }

    public Type eval (Instance instance) throws EvaluationException
    {
        // Assume that EquationEntry orders itself to put the default equations last, and particularly an unconditional equation after one with $init
        for (EquationEntry e : equations)  // Scan for first equation whose condition is nonzero
        {
            if (e.conditional == null) return e.expression.eval (instance);
            Object doit = e.conditional.eval (instance);
            if (doit instanceof Scalar  &&  ((Scalar) doit).value != 0) return e.expression.eval (instance);
        }
        if (name.equals ("$type")) return new Scalar (0);  // $type should not have a default equation. Instead, always reset to 0.
        return null;
    }

    /**
        Similar to eval(), but simply returns the selected equation.
    **/
    public EquationEntry select (Instance instance) throws EvaluationException
    {
        for (EquationEntry e : equations)
        {
            if (e.conditional == null) return e;
            Object doit = e.conditional.eval (instance);
            if (doit instanceof Scalar  &&  ((Scalar) doit).value != 0) return e;
        }
        return null;
    }

    /**
        Record what other variables this variable depends on.
    **/
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
            if (uses.contains (whatWeNeed)) return;  // already recorded this dependency, so don't do it again
        }
        uses.add (whatWeNeed);
        whatWeNeed.addUser (this);
    }

    /**
        Record variables or equation sets that depend on this variable.
        Note that addDependency(Variable) handles both sides of the add. You only call this function
        directly to add equation sets.
    **/
    public void addUser (Object user)
    {
        if (usedBy == null)
        {
            usedBy = new ArrayList<Object> ();
        }
        else
        {
            if (usedBy.contains (user)) return;
        }
        usedBy.add (user);
    }

    public boolean hasUsers ()
    {
        if (usedBy == null) return false;
        return ! usedBy.isEmpty ();
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

    public void tagDerivativeOrDependency ()
    {
        if (hasAttribute ("derivativeOrDependency")) return;
        addAttribute ("derivativeOrDependency");
        if (uses == null) return;
        for (Variable u : uses)
        {
            if (u.hasAttribute ("temporary")) u.tagDerivativeOrDependency ();
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
            <dt>readOnly</dt>
                <dd>the value can change, but it should never be written to directly.</dd>
            <dt>reference</dt>
                <dd>the actual value of the variable is stored in a different
                equation set</dd>
            <dt>accessor</dt>
                <dd>value is given by a function rather than stored</dd>
            <dt>preexistent</dt>
                <dd>storage does not need to be created for the variable
                because it is either inherited or passed into a function.</dd>
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
            <dt>updates</dt>
                <dd>this variable has both a derivative and regular update equations.</dd>
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
        if (result != 0) return result;
        if (order >= 0  &&  that.order >= 0) return order - that.order;  // ascending order; necessary for generating integrate() function in C-backend
        return 0;
    }

    @Override
    public boolean equals (Object that)
    {
        if (this == that) return true;
        Variable e = (Variable) that;
        if (e == null) return false;
        return compareTo (e) == 0;
    }
}

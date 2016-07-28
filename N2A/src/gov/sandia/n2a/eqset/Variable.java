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
import gov.sandia.n2a.language.operator.Divide;
import gov.sandia.n2a.language.operator.Multiply;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.umf.platform.db.MNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class Variable implements Comparable<Variable>
{
    public MNode                        source;
    public String                       name;
    public int                          order;      // of differential
    public Type                         type;       // Stores an actual instance of the type. Necessary to get the size of Matrix. Otherwise, only class matters.
    public Set<String>                  attributes;
    public NavigableSet<EquationEntry>  equations;
    public int                          assignment;
    public Map<String, String>          metadata;

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
    public static final int REPLACE  = 0;  // =      Note: because this is 0, it is the default state of assignment when this object is constructed
    public static final int ADD      = 1;  // =+
    public static final int MULTIPLY = 2;  // =*
    public static final int DIVIDE   = 3;  // =/
    public static final int MIN      = 4;  // =<
    public static final int MAX      = 5;  // =>
    // Note that there is no =-, because the minus could be ambiguous with content of expression.
    // DIVIDE is redundant with MULTIPLY, but may make some expressions easier to write. Since they are compatible, no error will be flagged if they are used together.

    public Variable ()
    {
    }

    public Variable (String name)
    {
        this (name, -1);
    }

    public Variable (String name, int order)
    {
        this.name  = name;
        this.order = order;
    }

    public Variable (String index, MNode source) throws Exception
    {
        this.source = source;
        equations = new TreeSet<EquationEntry> ();  // It is possible for Variable to be parse from MNode without any equations, but code that relies this ctor expects a non-null equations member.

        parseLHS (index);
        String rhs = source.get ();
        if (! rhs.isEmpty ())
        {
            rhs = parseAssignment (rhs);
            if (! rhs.isEmpty ()) add (new EquationEntry (rhs));
        }
        for (Entry<String,MNode> i : source)
        {
            String key = i.getKey ();
            if (key.startsWith ("@")) add (new EquationEntry (key.substring (1), i.getValue ()));
            if (key.equals ("$metadata"))
            {
                if (metadata == null) metadata = new TreeMap<String,String> ();
                for (Entry<String,MNode> m : i.getValue ())
                {
                    metadata.put (m.getKey (), m.getValue ().get ());
                }
            }
            if (key.equals ("$reference"))
            {
                // TODO: handle references
            }
        }
    }

    /**
        Convert a full line (variable=expression@condition) into a Variable/EquationEntry structure.
        Technically, this is a constructor, but Variable(String) is already used for making queries,
        and that is probably best.
    **/
    public static Variable parse (String line) throws Exception
    {
        Variable result = new Variable ();
        String[] parts = line.split ("=", 2);
        result.parseLHS (parts[0]);
        if (parts.length > 1)
        {
            String rhs = result.parseAssignment (parts[1]);
            result.add (new EquationEntry (rhs));
        }
        return result;
    }

    public void parseLHS (String lhs)
    {
        name = lhs.trim ();
        order = 0;
        while (name.endsWith ("'"))
        {
            order++;
            name = name.substring (0, name.length () - 1);
        }
    }

    /**
        Checks for a combining operator at the beginning of the right-hand side, and removes it
        for further processing.
    **/
    public String parseAssignment (String rhs)
    {
        assignment = REPLACE;  // Assuming that an = was found before calling this function.

        rhs = rhs.trim ();
        char first;
        if (rhs.isEmpty ()) first = 0;
        else                first = rhs.charAt (0);
        switch (first)
        {
            case '+': assignment = ADD;           return rhs.substring (1);
            case '*': assignment = MULTIPLY;      return rhs.substring (1);
            case '/': assignment = DIVIDE;        return rhs.substring (1);
            case '>': assignment = MAX;           return rhs.substring (1);
            case '<': assignment = MIN;           return rhs.substring (1);
            case ':': addAttribute ("temporary"); return rhs.substring (1);  // We are already set to REPLACE
        }

        return rhs;
    }

    public EquationEntry find (EquationEntry query)
    {
        EquationEntry result = equations.floor (query);
        if (result != null  &&  result.compareTo (query) == 0) return result;
        return null;
    }

    /**
        Any existing entry takes precedence over the given equation.
    **/
    public void add (EquationEntry e)
    {
        if (equations == null) equations = new TreeSet<EquationEntry> ();
        equations.add (e);
        e.variable = this;
    }

    /**
        Combine equations from given variable with this, where that takes precedence.
        All equations will be detached from given variable, whether or not they are added to this.
        TODO: Revoke conditional forms.
            variable=@condition  -- revokes the given condition on the given variable
            variable=@           -- revokes all conditions
            variabel=expression@ -- revples all conditions, and replaces them with a single new unconditional expression
    **/
    public void merge (Variable that)
    {
        if (equations == null) equations = new TreeSet<EquationEntry> ();
        for (EquationEntry e : that.equations) e.variable = this;
        that.equations.addAll (equations);  // Set.addAll() keeps pre-existing entries
        equations = that.equations;
        that.equations = new TreeSet<EquationEntry> ();

        // Merge $metadata
        if (that.metadata != null)
        {
            if (metadata == null) metadata = new TreeMap<String,String> ();
            metadata.putAll (that.metadata);
        }

        // TODO: merge references
    }

    /**
        Store changes to metadata into the DB, in a way that will play back correctly when our
        containing EquationSet tree is re-built from the top-level model document.
    **/
    public void updateDB (String tag, String value)
    {
        String path = nameString ();
        EquationSet s = container;
        while (s.container != null)
        {
            path = container.name + "." + path;
            s = s.container;
        }
        s.source.set (value, path, "$metadata", tag);
    }

    public void flattenExpressions (Variable v)
    {
        if (equations == null) equations = new TreeSet<EquationEntry> ();
        for (EquationEntry e : v.equations)
        {
            if (v.assignment > REPLACE)  // assuming UNKNOWN=0 and REPLACE=1, and all other constants are greater 
            {
                EquationEntry e2 = equations.floor (e);
                if (e.compareTo (e2) == 0)  // conditionals are exactly the same
                {
                    // merge expressions
                    OperatorBinary op = null;
                    if      (v.assignment == ADD)      op = new Add ();
                    else if (v.assignment == MULTIPLY) op = new Multiply ();
                    else if (v.assignment == DIVIDE)   op = new Divide ();
                    if (op != null)
                    {
                        op.operand0 = e2.expression;
                        op.operand1 = e .expression;
                        e2.expression = op;
                        continue;
                    }

                    Function f = null;
                    if      (v.assignment == MIN) f = new Min ();
                    else if (v.assignment == MAX) f = new Max ();
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
            equations.add (e);  // Any pre-existing equation takes precedence over this one. Note also that the merged e2 above does not need to be added for the changes to become effective.
        }
        v.equations.clear ();
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

    public String combinerString ()
    {
        switch (assignment)
        {
            case ADD:      return "+";
            case MULTIPLY: return "*";
            case DIVIDE:   return "/";
            case MAX:      return ">";
            case MIN:      return "<";
        }
        return "";
    }

    public String attributeString ()
    {
        if (attributes == null) return "[]";
        else                    return attributes.toString ();
    }

    public String getNamedValue (String name)
    {
        return getNamedValue (name, "");
    }

    public String getNamedValue (String name, String defaultValue)
    {
        if (metadata == null) return defaultValue;
        if (metadata.containsKey (name)) return metadata.get (name);
        return defaultValue;
    }

    public void setNamedValue (String name, String value)
    {
        if (metadata == null) metadata = new TreeMap<String, String> ();
        metadata.put (name, value);
    }

    /**
        Safe method to access metadata for iteration
    **/
    public Set<Entry<String,String>> getMetadata ()
    {
        if (metadata == null) metadata = new TreeMap<String, String> ();
        return metadata.entrySet ();
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

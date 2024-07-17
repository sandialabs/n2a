/*
Copyright 2013-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.eqset;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.ParseException;
import gov.sandia.n2a.language.Transformer;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.UnsupportedFunctionException;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.function.Event;
import gov.sandia.n2a.language.function.Max;
import gov.sandia.n2a.language.function.Min;
import gov.sandia.n2a.language.operator.AND;
import gov.sandia.n2a.language.operator.Add;
import gov.sandia.n2a.language.operator.Divide;
import gov.sandia.n2a.language.operator.Multiply;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.plugins.extpoints.Backend;
import tech.units.indriya.AbstractUnit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

import javax.measure.Dimension;
import javax.measure.Unit;

public class Variable implements Comparable<Variable>, Cloneable
{
    public String                       name;
    public int                          order;      // of differential
    public Type                         type;       // Stores an actual instance of the type. Necessary to get the size of Matrix. Otherwise, only class matters.
    public Unit<?>                      unit;       // Stands in for the physical dimensions associated with this variable.
    public Set<String>                  attributes;
    public NavigableSet<EquationEntry>  equations;
    public int                          assignment;
    public MNode                        metadata;

    // resolution
    public EquationSet                  container;  // non-null iff this variable is contained in an EquationSet.variables collection
    public VariableReference            reference;  // points to variable that actually contains the data, which is usually us unless we are a proxy for a variable in another equation set. null if not resolved yet.
    public Variable                     derivative; // The variable from which we are integrated, if any.

    // graph analysis
    public Map<Variable,Integer>        uses;       // Variables we depend on, along with count of the number of references we make. Forms a digraph (which may have cycles) on Variable nodes.
    public List<Object>                 usedBy;     // Variables and EquationSets that depend on us.
    public Set<Variable>                before;     // Variables that must be evaluated after us. Generally the same as uses, unless we are a temporary, in which case the ordering is reversed. Note EquationSet.ordered
    public Variable                     visited;    // Points to the previous variable visited on the current path. Used to prevent infinite recursion. Only works on a single thread.
    public int                          priority;   // For evaluation order.
    public boolean                      changed;    // Indicates that analysis touched one or more equations in a way that merits another pass.
    public boolean                      blocked;    // For Johnson circuit algorithm.

    // fixed-point analysis
    //public boolean                      signed   = true;             // For now we assume that variables are always signed. Handling a mix of signed and unsigned values is significantly more complex.
    public int                          MSB      = 30;               // Zero-based position of most significant bit, not including sign bit.
    public int                          exponent = Operator.UNKNOWN; // power of bit 0 (LSB) for values stored in this variable
    public int                          center   = MSB / 2;
    public Operator                     bound;                       // The expression that imposes the largest magnitude on this variable. May be null.

    // Internal backend
    // TODO: put this in a beckendData field, similar to EquationSet.backendData. The problem with this is the extra overhead to unpack the object.
    public int      readIndex  = -1; // Position in Instance.values to read
    public boolean  readTemp;        // Read the temp Instance rather than the main one
    public int      writeIndex = -1; // Position Instance.values to write
    public boolean  writeTemp;       // Write the temp Instance rather than the main one
    public boolean  global;          // redundant with "global" attribute; for faster execution
    public boolean  externalWrite;   // redundant with "externalWrite" attribute; for faster execution

    // Assignment modes
    public static final int REPLACE  = 0;  // =      Note: because this is 0, it is the default state of assignment when this object is constructed
    public static final int ADD      = 1;  // =+
    public static final int MULTIPLY = 2;  // =*
    public static final int DIVIDE   = 3;  // =/
    public static final int MIN      = 4;  // =<
    public static final int MAX      = 5;  // =>
    // Note that there is no =-, because the minus could be ambiguous with content of expression.
    // DIVIDE is redundant with MULTIPLY, but may make some expressions easier to write.
    // Since they are compatible, no error will be flagged if they are used together.

    /**
        Create variable with given name and default order 0.
    **/
    public Variable (String name)
    {
        this (name, 0);
    }

    /**
        Create variable with given name and order (level of derivative).
        When this variable is used as a query object, an order of -1 means match any order. 
    **/
    public Variable (String name, int order)
    {
        this.name  = name;
        this.order = order;
    }

    public Variable (EquationSet container, MNode source) throws Exception
    {
        this.container = container;
        equations = new TreeSet<EquationEntry> ();  // It is possible for Variable to be parsed from MNode without any equations, but code that relies on this ctor expects a non-null equations member.
        metadata = new MVolatile ();

        parseLHS (source.key ());
        try
        {
            String rhs = source.get ();
            if (! rhs.isEmpty ())
            {
                rhs = parseAssignment (rhs);
                EquationEntry e = new EquationEntry (rhs);
                if (e.expression != null) add (e);
            }
            for (MNode i : source)
            {
                if (i.getFlag ("$kill")) continue;
                String key = i.key ();
                if (key.startsWith ("@"))
                {
                    EquationEntry e = new EquationEntry (i);
                    if (e.expression != null) add (e);
                }
                else if (key.equals ("$meta")) metadata.merge (i);
                // Ignore references, as they have no function in simulation.
            }

            if (equations.isEmpty ())
            {
                EquationEntry e = null;
                switch (assignment)
                {
                    case ADD:
                        e = new EquationEntry ("0");
                        break;
                    case MULTIPLY:
                    case DIVIDE:
                        e = new EquationEntry ("1");
                        break;
                    case MIN:
                        e = new EquationEntry ("∞");
                        break;
                    case MAX:
                        e = new EquationEntry ("-∞");
                        break;
                }
                if (e != null) add (e);
            }
        }
        catch (ParseException e)
        {
            String prefix = fullName () + "=" + combinerString ();
            e.line = prefix + e.line;
            e.column += prefix.length ();
            throw e;
        }
        catch (UnsupportedFunctionException e)
        {
            e.message = "Unsupported function " + e.message + " in " + fullName ();
            throw e;
        }
    }

    public void parseLHS (String lhs)
    {
        name = lhs.trim ();
        if (name.startsWith ("$all."))
        {
            name = name.substring (5);
            addAttribute ("global");
        }
        else if (name.startsWith ("$each."))
        {
            name = name.substring (6);
            addAttribute ("local");
        }

        order = 0;
        while (name.endsWith ("'"))
        {
            order++;
            name = name.substring (0, name.length () - 1);
        }
    }

    /**
        A quick-and-dirty way to construct a variable with a single expression.
    **/
    public static Variable from (String equation) throws Exception
    {
        String[] pieces = equation.split ("=", 2);
        String key = pieces[0];
        String value = "";
        if (pieces.length > 1) value = pieces[1];
        return new Variable (null, new MVolatile (value, key));
    }

    /**
        Convenience method for making queries.
        A properly-formed variable requires different processing.
    **/
    public static Variable fromLHS (String lhs)
    {
        lhs = lhs.trim ();
        lhs = stripContextPrefix (lhs);
        int order = 0;
        while (lhs.endsWith ("'"))
        {
            order++;
            lhs = lhs.substring (0, lhs.length () - 1);
        }
        return new Variable (lhs, order);
    }

    public static String stripContextPrefix (String name)
    {
        if (name.startsWith ("$all." )) return name.substring (5);
        if (name.startsWith ("$each.")) return name.substring (6);
        return name;
    }

    /**
        Checks for a combining operator at the beginning of the right-hand side, and removes it
        for further processing.
    **/
    public String parseAssignment (String rhs)
    {
        assignment = REPLACE;  // Assuming that an equals sign was found before calling this function.

        rhs = rhs.trim ();
        char first;
        if (rhs.isEmpty ()) first = 0;
        else                first = rhs.charAt (0);
        switch (first)
        {
            case '+': assignment = ADD;           return rhs.substring (1);
            case '*': assignment = MULTIPLY;      return rhs.substring (1);
            case '/': assignment = DIVIDE;        return rhs.substring (1);
            case '<': assignment = MIN;           return rhs.substring (1);
            case '>': assignment = MAX;           return rhs.substring (1);
            case ':': addAttribute ("state");     return rhs.substring (1);  // We are already set to REPLACE
            case ';': addAttribute ("temporary"); return rhs.substring (1);
        }

        return rhs;
    }

    public static boolean isCombiner (String value)
    {
        if (value.length () != 1) return false;
        return "+*/<>:;".contains (value);
    }

    /**
        Utility class for separating the RHS of an equation, as it might appear in a document line,
        into its respective components.
        This appears as an inner class of Variable because the most maintenance-sensitive part
        of this is the combiner characters, and those are defined in Variable.
    **/
    public static class ParsedValue
    {
        public String combiner;
        public String expression;
        public String condition;

        public ParsedValue (String value)
        {
            if (value.isEmpty ())
            {
                combiner = "";
                expression = "";
                condition = "";
                return;
            }

            // Extract the combiner string, if any
            value = value.trim ();
            combiner = value.substring (0, 1);
            if (isCombiner (combiner)) value = value.substring (1).trim ();
            else combiner = "";

            // Extract the conditional expression, if any
            String[] pieces = value.split ("@", 2);
            expression = pieces[0].trim ();
            if (pieces.length < 2) condition = "";
            else                   condition = pieces[1].trim ().replaceAll ("[\\n\\t]", "");
        }

        public boolean equals (Object that)
        {
            if (! (that instanceof ParsedValue)) return false;
            ParsedValue p = (ParsedValue) that;
            return  p.combiner.equals (combiner)  &&  p.expression.equals (expression)  &&  p.condition.equals (condition);
        }

        public String toString ()
        {
            if (condition.isEmpty ()) return combiner + expression;
            return combiner + expression + "@" + condition;
        }
    }

    /**
        Replicates an individual variable without consideration of its neighbors.
    **/
    public Variable deepCopy ()
    {
        Variable result = null;
        try
        {
            result = (Variable) clone ();
        }
        catch (CloneNotSupportedException e) {}  // Since we do support clone(), this exception will never be thrown, and result will always be defined.

        TreeSet<EquationEntry> newEquations = new TreeSet<EquationEntry> ();
        for (EquationEntry e : result.equations) newEquations.add (e.deepCopy (result));
        result.equations = newEquations;

        if (attributes != null) result.attributes = new HashSet<String> (attributes);

        // Don't worry about uses and usedBy, since we don't consider our neighbors.

        return result;
    }

    /**
        Replaces each variable in a given subset, such that the result can be
        modified by optimization procedures without damaging the original equation set.
    **/
    public static void deepCopy (List<Variable> list)
    {
        // First step is to clone all the variables.
        // Clone is a shallow copy (one-level deep), so the resulting variables share exactly
        // the same members as the original variables.
        try
        {
            for (int i = 0; i < list.size (); i++) list.set (i, (Variable) list.get (i).clone ());
        }
        catch (CloneNotSupportedException e) {}

        // Second step is to drill down and duplicate any members that need more isolation.
        // We don't duplicate everything, only enough to ensure no damage to the original by
        // optimization procedures.
        for (Variable v : list)
        {
            TreeSet<EquationEntry> newEquations = new TreeSet<EquationEntry> ();
            for (EquationEntry e : v.equations) newEquations.add (e.deepCopy (v));
            v.equations = newEquations;

            if (v.attributes != null) v.attributes = new HashSet<String> (v.attributes);

            v.usedBy = null;
            v.uses = null;
        }

        // Rebuild dependency structure within the list.
        // We only care about dependencies that determine ordering within the list.
        // External dependencies won't be counted, but also won't be changed.
        class DependencyTransformer implements Transformer
        {
            public Variable v;
            public Operator transform (Operator op)
            {
                if (op instanceof AccessVariable)
                {
                    AccessVariable av = (AccessVariable) op;
                    Variable listVariable = EquationSet.find (av.reference.variable, list);
                    if (listVariable != null)
                    {
                        av.reference = new VariableReference ();
                        av.reference.variable = listVariable;
                        v.addDependencyOn (listVariable);
                        return av;
                    }
                }
                return null;
            }
        }
        DependencyTransformer xform = new DependencyTransformer ();
        for (Variable v : list)
        {
            xform.v = v;
            v.transform (xform);
        }
        EquationSet.addDrawDependencies (list);
    }

    public static int equationCount (MNode v)
    {
        int result = 0;
        for (MNode e : v) if (e.key ().startsWith ("@")) result++;
        return result;
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

    public void flattenExpressions (Variable v)
    {
        removeDependenciesOfReferences ();
        v.removeDependenciesOfReferences ();

        if (   assignment != v.assignment
            && ! (   (assignment == MULTIPLY  &&  v.assignment == DIVIDE)  // This line and the next say that * and / are compatible with each other, so ignore that case.
                  || (assignment == DIVIDE    &&  v.assignment == MULTIPLY)))
        {
            Backend.err.get ().println ("WARNING: Flattened variable " + v.container.prefix () + "." + v.nameString () + " has different combining operator than target (" + container.prefix () + "." + nameString () + "). Resolving in favor of higher-precedence operator.");
            v.assignment = assignment = Math.max (v.assignment, assignment);
        }

        if (v.assignment == REPLACE) 
        {
            // We want equations from the lower level to override any pre-existing equations in the upper level.
            // Why? Because the lower-level equations represent an elaboration of the model, and are thus more specific.
            NavigableSet<EquationEntry> equations0 = equations;
            equations = new TreeSet<EquationEntry> ();
            for (EquationEntry e2 : v.equations) add (e2);  // First add the lower-level equations, so they take precedence.
            for (EquationEntry e0 : equations0) add (e0);
        }
        else
        {
            // For combiners, it is necessary to create new equations for every combination of conditions.
            // Of course, unconditional equations and equations with matching conditions can be combined more directly.

            // First step is to augment both equation sets with an unconditional form, if they lack it.
            EquationEntry query = new EquationEntry (this, "");
            EquationEntry e = find (query);
            if (e == null) add (query);  // expression and condition are null; we deal with this below
            query = new EquationEntry (v, "");
            e = v.find (query);
            if (e == null) v.add (query);

            NavigableSet<EquationEntry> equations0 = equations;
            equations = new TreeSet<EquationEntry> ();
            for (EquationEntry e0 : equations0)
            {
                for (EquationEntry e1 : v.equations)
                {
                    if (e0.compareTo (e1) == 0)  // Condition strings are exactly the same. TODO: compare ASTs for actual logic equivalence.
                    {
                        e = new EquationEntry (this, "");
                        if (e0.condition != null) e.condition = e0.condition.deepCopy ();
                    }
                    else if (e0.condition == null)
                    {
                        // Check if e1.condition exists in this.equations
                        // If so, then only those equations should be merged, so skip this one.
                        if (find (e1) != null) continue;
                        e = new EquationEntry (this, "");
                        if (e1.condition != null) e.condition = e1.condition.deepCopy ();
                    }
                    else if (e1.condition == null)
                    {
                        // Check if e0.condition exists in v.equations
                        if (v.find (e0) != null) continue;
                        e = new EquationEntry (this, "");
                        if (e0.condition != null) e.condition = e0.condition.deepCopy ();
                    }
                    else
                    {
                        e = new EquationEntry (this, "");
                        AND and = new AND ();
                        e.condition = and;
                        and.operand0 = e0.condition.deepCopy ();
                        and.operand1 = e1.condition.deepCopy ();
                        and.operand0.parent = and;
                        and.operand1.parent = and;
                    }
                    if (e.condition != null) e.ifString = e.condition.render ();

                    // merge expressions
                    if (e0.expression == null)
                    {
                        if (e1.expression != null) e.expression = e1.expression.deepCopy ();
                    }
                    else if (e1.expression == null)
                    {
                        if (e0.expression != null) e.expression = e0.expression.deepCopy ();
                    }
                    else  // Both expressions exist
                    {
                        OperatorBinary op = null;
                        Function       f  = null;
                        switch (v.assignment)
                        {
                            case ADD:      op = new Add ();      break;
                            case MULTIPLY: op = new Multiply (); break;
                            case DIVIDE:   op = new Divide ();   break;
                            case MIN:      f  = new Min ();      break;
                            case MAX:      f  = new Max ();      break;
                        }
                        if (op != null)
                        {
                            op.operand0 = e0.expression.deepCopy ();
                            op.operand1 = e1.expression.deepCopy ();
                            op.operand0.parent = op;
                            op.operand1.parent = op;
                            e.expression = op;
                        }
                        else if (f != null)
                        {
                            f.operands = new Operator[2];  // Because neither Min nor Max allocate their operand array directly.
                            f.operands[0] = e0.expression.deepCopy ();
                            f.operands[1] = e1.expression.deepCopy ();
                            f.operands[0].parent = f;
                            f.operands[1].parent = f;
                            e.expression = f;
                        }
                    }
                    if (e.expression != null) equations.add (e);
                }
            }
        }

        addDependenciesOfReferences ();
        v.equations.clear ();
    }

    public boolean isEmptyCombiner ()
    {
        if (equations.size () != 1  ||  assignment == Variable.REPLACE) return false;

        Operator e = equations.first ().expression;
        if (! e.isScalar ()) return false;
        double value = e.getDouble ();
        switch (assignment)
        {
            case Variable.ADD:
                return value == 0;
            case Variable.MULTIPLY:
            case Variable.DIVIDE:
                return value == 1;
            case Variable.MIN:
                return value == Double.POSITIVE_INFINITY;
            case Variable.MAX:
                return value == Double.NEGATIVE_INFINITY;
        }
        return false;  // This statement should never be reached.
    }

    public boolean isConstant ()
    {
        if (equations == null  ||  equations.size () != 1) return false;
        EquationEntry e = equations.first ();
        if (e.condition != null) return false;
        return e.expression instanceof Constant;
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
            if (e.condition != null)
            {
                e.condition = e.condition.transform (transformer);
                e.ifString  = e.condition.render ();
            }
        }
    }

    public boolean simplify ()
    {
        changed = false;
        TreeSet<EquationEntry> nextEquations = new TreeSet<EquationEntry> ();
        EquationEntry defaultEquation = null;
        EquationEntry alwaysTrue = null;
        visited = null;
        for (EquationEntry e : equations)
        {
            if (e.expression != null)
            {
                e.expression = e.expression.simplify (this, false);
            }
            if (e.condition == null)
            {
                defaultEquation = e;
            }
            else
            {
                e.condition = e.condition.simplify (this, false);
                e.ifString  = e.condition.render ();
            }

            if (e.expression instanceof AccessVariable)
            {
                Variable v = ((AccessVariable) e.expression).reference.variable;
                if (v == this  &&  assignment == REPLACE)  // Vacuous assignment
                {
                    // For REPLACE, simulator always copies value to next cycle when no equation fires,
                    // so there is never need for an explicit equation to do this.
                    // OTOH, explicit self-assignment with a combiner is a special case the user may deploy on purpose.
                    changed = true;
                    e.expression.releaseDependencies (this);
                    if (e.condition == null) defaultEquation = null;
                    else                     e.condition.releaseDependencies (this);
                    continue;
                }
            }
            if (e.condition instanceof Constant)
            {
                if (e.condition.getDouble () == 0)  // Will never fire
                {
                    // Drop equation
                    changed = true;
                    if (e.expression != null) e.expression.releaseDependencies (this);
                    continue;
                }
                else  // Will always fire
                {
                    if (alwaysTrue == null) alwaysTrue = e;
                }
            }

            nextEquations.add (e);
        }
        if (! nextEquations.isEmpty ()  &&  nextEquations.first () == alwaysTrue)  // alwaysTrue requires an explicit (non-null) condition. The default equation is never selected.
        {
            changed = true;
            equations.clear ();
            equations.add (alwaysTrue);

            for (EquationEntry e : nextEquations)
            {
                if (e == alwaysTrue) continue;
                if (e.expression != null) e.expression.releaseDependencies (this);
                if (e.condition  != null) e.condition .releaseDependencies (this);
            }

            // Make the equation unconditional, since it always fires anyway.
            EquationEntry e = equations.first ();
            e.condition = null;
            e.ifString = "";
        }
        else
        {
            equations = nextEquations;

            if (equations.isEmpty ())
            {
                if (hasAttribute ("temporary"))
                {
                    changed = true;
                    addAttribute ("constant");
                    EquationEntry e = new EquationEntry (this, "");
                    equations.add (e);
                    e.expression = new Constant (0);  // This is the default value for a temporary when no equation fires.
                }
            }
            else if (equations.size () > 1)
            {
                // Eliminate equations if they output the same constant value as the default.
                // Basically, if all the conditions are mutually exclusive, then default will take
                // the place of the removed equation. If they are not mutually exclusive, then the
                // contract with the user allows us to choose another equation instead.
                if (defaultEquation != null  &&  defaultEquation.expression instanceof Constant)
                {
                    nextEquations = new TreeSet<EquationEntry> ();
                    nextEquations.add (defaultEquation);
                    // Add any equations that don't match the default equation.
                    for (EquationEntry e : equations)
                    {
                        if (   ! (e.expression instanceof Constant)
                            || ! ((Constant) defaultEquation.expression).value.equals (((Constant) e.expression).value))
                        {
                            nextEquations.add (e);
                        }
                    }
                    equations = nextEquations;
                }
            }
        }

        // Check if we have become eligible to execute in the global context.
        // Only applies to external write references.
        Variable rv = reference.variable;  // for convenience
        if (rv.container != container  &&  rv.hasAttribute ("global")  &&  ! hasAny ("global", "local"))
        {
            class CheckGlobal implements Visitor
            {
                boolean allGlobal = true;
                public boolean visit (Operator op)
                {
                    if (op instanceof AccessVariable)
                    {
                        AccessVariable av = (AccessVariable) op;
                        Variable target = av.reference.variable;
                        if (! target.hasAttribute ("global")) allGlobal = false;
                    }
                    return allGlobal;
                }
            }
            CheckGlobal check = new CheckGlobal ();
            visit (check);
            if (check.allGlobal)
            {
                changed = true;
                addAttribute ("global");
                convertToGlobal ();
            }
        }

        // Check for constant combiner.
        // This is a special case needed by some models that use pins.
        if (uses != null  &&  hasAttribute ("externalWrite"))  // Note: uses can be null when "externalWrite" is set. This can happen during EquationSet.simplify().
        {
            // Determine if self is constant.
            boolean constant = equations.isEmpty ();
            boolean replaced = false;
            double value = 0;  // Default value suitable for ADD and REPLACE
            switch (assignment)
            {
                case MULTIPLY: value = 1;                        break;
                case DIVIDE:   value = 1;                        break;
                case MIN:      value = Double.POSITIVE_INFINITY; break;
                case MAX:      value = Double.NEGATIVE_INFINITY; break;
            }
            if (! constant  &&  equations.size () == 1)
            {
                EquationEntry e = equations.first ();
                if (e.condition == null  &&  e.expression.isScalar ())
                {
                    constant = true;
                    replaced = true;
                    value = e.expression.getDouble ();
                }
            }
            if (constant)
            {
                // Scan all our writers to see if they are constant.
                for (Variable u : uses.keySet ())
                {
                    if (   (assignment == ADD  ||  assignment == MULTIPLY  ||  assignment == DIVIDE)
                        && ! u.isSingletonRelativeTo (container))
                    {
                        // Can't predict how many of these operations there will be at run time,
                        // so can't predict result.
                        constant = false;
                        break;
                    }
                    if (! u.hasAttribute ("constant"))
                    {
                        constant = false;
                        break;
                    }
                    double uvalue = u.equations.first ().expression.getDouble ();
                    switch (assignment)
                    {
                        case ADD:      value += uvalue;                   break;
                        case MULTIPLY: value *= uvalue;                   break;
                        case DIVIDE:   value /= uvalue;                   break;
                        case MIN:      value  = Math.min (value, uvalue); break;
                        case MAX:      value  = Math.max (value, uvalue); break;
                        default:  // REPLACE
                            if (value != uvalue)
                            {
                                if (replaced)
                                {
                                    constant = false;  // Can only set the value once.
                                }
                                else
                                {
                                    replaced = true;
                                    value = uvalue;
                                }
                            }
                    }
                    if (! constant) break;
                }
            }
            if (constant)
            {
                changed = true;
                EquationEntry e;
                if (equations.isEmpty ())
                {
                    e = new EquationEntry (this, "");
                    equations.add (e);
                }
                else
                {
                    e = equations.first ();
                }
                e.expression = new Constant (value);
                addAttribute ("constant");
                removeAttribute ("externalWrite");
                removeDependencies ();
            }
        }

        return changed;
    }

    public boolean isSingletonRelativeTo (EquationSet that)
    {
        // EquationSet.isSingletonRelativeTo() is slightly too restrictive for connections.
        // If it can be proven that all sides of a connection are singletons, then the
        // connection is a singleton too.
        if (hasAttribute ("global"))
        {
            if (container.container == null) return true;
            return container.container.isSingletonRelativeTo (that);
        }
        return container.isSingletonRelativeTo (that);
    }

    /**
        If resolution path starts with a connection, then convert it to a walk through containers instead.
    **/
    public void convertToGlobal ()
    {
        reference.convertToGlobal (this);

        visit (new Visitor ()
        {
            public boolean visit (Operator op)
            {
                if (op instanceof AccessVariable)
                {
                    AccessVariable av = (AccessVariable) op;
                    av.reference.convertToGlobal (Variable.this);
                    return false;
                }
                return true;
            }
        });
    }

    public Type eval (Instance instance) throws EvaluationException
    {
        // Assume that EquationEntry orders itself to put the default equations last, and particularly an unconditional equation after one with $init
        for (EquationEntry e : equations)  // Scan for first equation whose condition is nonzero
        {
            if (e.condition == null) return e.expression.eval (instance);
            Object doit = e.condition.eval (instance);
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
            if (e.condition == null) return e;
            Object doit = e.condition.eval (instance);
            if (doit instanceof Scalar  &&  ((Scalar) doit).value != 0) return e;
        }
        return null;
    }

    public void determineExponent (ExponentContext context)
    {
        context.from = this;
        changed = false;
        int centerNew   = center;
        int exponentNew = exponent;

        if (name.equals ("$t")  &&  order == 0)
        {
            centerNew   = Operator.MSB - 1;  // Half the time, the msb won't be set. The other half, it will.
            exponentNew = context.exponentTime;
        }
        else if (name.equals ("$index")  ||  name.equals ("$type"))  // Variables stored as a pure integer.
        {
            centerNew   = 0;  // Unless we have an estimate of population size, this is the best we can do.
            exponentNew = 0;
        }
        else if (name.equals ("$connect")  ||  name.equals ("$init")  ||  name.equals ("$live"))
        {
            // Booleans are stored as simple integers where possible.
            centerNew   = 0;
            exponentNew = 0;
        }
        else
        {
            // There are three sources of information on size of variable:
            // 1) Direct equations -- Give specific values, particularly initial values.
            // 2) Derivative -- Assuming 1 second of simulation, the derivative is about the same size as max integrated value.
            // 3) Comparisons -- indicate a bound on how large the variable can become
            // How to combine these?

            // For center, we need to know the median of possible values.
            // Very likely, one equation will fire most of the time, and we should simply use its center.
            // However, there's no good way to estimate that, so assume all equations fire with equal frequency
            // and take an average of their centers.
            int cent  = 0;
            int pow   = 0;
            int count = 0;
            for (EquationEntry e : equations)
            {
                if (e.expression != null)
                {
                    e.expression.determineExponent (context);
                    if (e.expression.exponent != Operator.UNKNOWN)  // Only count equations that have known values of exponent and center.
                    {
                        cent += e.expression.center;
                        pow  += e.expression.exponent;
                        count++;
                    }
                }
                if (e.condition != null)
                {
                    e.condition.determineExponent (context);
                    // Condition does not directly affect value stored in variable.
                }
            }
            // Scan external references and see if any of them write to us. If so, then consider
            // their exponents as well. For all combiner types, we simply contribute to the average center power.
            // For multiplicative combiners (=* and =/), we only increment count once, regardless of how
            // many external references there are. This has the effect of summing their powers, which
            // accounts for the multiplication. However, this is only correct in the case of external singletons.
            // It does not account for entire populations, and certainly not dynamic populations.
            // Accounting for large populations could produce absurd results. Instead, we hope the user set
            // the power to 0, which would allow an unbounded number of multiplications to stay in range.
            if (uses != null)
            {
                boolean multiplicative = false;
                for (Variable v : uses.keySet ())
                {
                    if (v.reference == null  ||  v.reference.variable != this) continue;  // Must be an external reference to us.
                    if (v.exponent == Operator.UNKNOWN) continue;  // Must have a known exponent. Otherwise, there is no point in processing it.
                    if (v.assignment == MULTIPLY)
                    {
                        cent += v.center;
                        pow  += v.exponent;
                        multiplicative = true;
                    }
                    else if (v.assignment == DIVIDE)
                    {
                        int centerPower = v.exponent + v.center;
                        centerPower *= -1;  // Negate to account for division 1/v
                        cent += Operator.MSB / 2;  // Fix quotient center at MSB/2
                        pow  += centerPower - Operator.MSB / 2;
                        multiplicative = true;
                    }
                    else
                    {
                        cent += v.center;
                        pow  += v.exponent;
                        count++;
                    }
                }
                if (multiplicative) count++;
            }

            if (count > 0)
            {
                centerNew   = cent / count;
                exponentNew = pow  / count;
            }
            else if (derivative != null  &&  derivative.exponent != Operator.UNKNOWN)
            {
                // A derivative is difficult to interpret, even for a human.
                // Results are often better when we don't use this code.
                // Here we assume 1 second of integration time, all moving in a constant direction.
                // This is a bad assumption, as derivatives are usually intended to move the
                // variable toward an attractor. If there is an automatic way to determine that
                // attractor, we should use it instead. For example, if the expression contains
                // something of the form (C-V), where C is a constant and V is us, then we can
                // assume that C is the attractor.
                centerNew   = derivative.center;
                exponentNew = derivative.exponent;
            }

            // Apply bound, if it exists.
            if (bound != null  &&  bound.exponent != Operator.UNKNOWN)
            {
                if (exponentNew == Operator.UNKNOWN)
                {
                    centerNew   = bound.center;
                    exponentNew = bound.exponent;
                }
                else
                {
                    int b = bound.exponent;  // For expressions, which have variable magnitude.
                    if (bound instanceof Constant) b = bound.centerPower () - Operator.MSB;  // Constants have exactly one magnitude (and center is always shifted to hold it), so use directly.
                    if (b > exponentNew)  // Ensure we can accommodate max magnitude.
                    {
                        centerNew  -= b - exponentNew;
                        exponentNew = b;
                    }
                }
            }

            // Special cases
            if (name.equals ("$t")  &&  order == 1)  // $t'
            {
                // Align with $t
                if (exponentNew == Operator.UNKNOWN)
                {
                    centerNew = Operator.MSB - 20;
                }
                else
                {
                    centerNew -= context.exponentTime - exponentNew;
                    if (centerNew < 0  ||  centerNew > Operator.MSB)
                    {
                        Backend.err.get ().println ("ERROR: not enough fixed-point resolution for given $t'");
                        throw new Backend.AbortRun ();
                    }
                }
                exponentNew = context.exponentTime;
            }
            if (name.equals ("$p")  &&  order == 0)
            {
                // The exponent of $p is hard-coded in the C runtime: getP() and connect().
                // If we know that both functions are overridden for this part, then we can let exponent be
                // determined by equations writing $p. However, it is not worth the effort to check that.
                // The hard-coded value (assuming 32-bit words) provides both sufficient precision and head-room.
                centerNew   = Operator.MSB / 2;
                exponentNew = -Operator.MSB;
            }
        }

        // User-specified exponent overrides any calculated value.
        if (metadata != null)
        {
            String medianHint = metadata.get ("median");
            String centerHint = metadata.get ("center");
            boolean haveMedian = ! medianHint.isBlank ();
            boolean haveCenter = ! centerHint.isBlank ();
            if (haveMedian  ||  haveCenter)
            {
                if (exponent != Operator.UNKNOWN) return;  // Already processed the hint.

                if (haveCenter)
                {
                    try
                    {
                        centerNew = Integer.valueOf (centerHint);
                        if (! haveMedian) exponentNew = -centerNew;  // assume center power 0
                    }
                    catch (NumberFormatException e) {}
                }

                if (haveMedian)
                {
                    try
                    {
                        double value = Operator.parse (medianHint).getDouble ();
                        int centerPower = 0;
                        if (value > 0) centerPower = (int) Math.floor (Math.log (value) / Math.log (2));  // log2 (value)
                        if (! haveCenter) centerNew = Operator.MSB / 2;
                        exponentNew = centerPower - centerNew;
                    }
                    catch (Exception e) {}
                }
            }
        }

        if (exponentNew != exponent  ||  centerNew != center) changed = true;
        exponent = exponentNew;
        center   = centerNew;
    }

    public void determineExponentNext ()
    {
        int exponentTarget = exponent;
        boolean multiplicative = false;
        if (reference != null  &&  reference.variable != this)
        {
            exponentTarget = reference.variable.exponent;
            multiplicative =  assignment == MULTIPLY  ||  assignment == DIVIDE;
        }
        for (EquationEntry e : equations)
        {
            if (e.expression != null)
            {
                if (multiplicative) e.expression.exponentNext = e.expression.exponent;
                else                e.expression.exponentNext = exponentTarget;
                e.expression.determineExponentNext ();
            }
            if (e.condition != null)
            {
                e.condition.exponentNext = e.condition.exponent;
                e.condition.determineExponentNext ();
            }
        }
    }

    public void dumpExponents ()
    {
        System.out.print (container.prefix () + "." + nameString () + " (");
        if (exponent == Operator.UNKNOWN)
        {
            System.out.print ("unknown");
        }
        else
        {
            System.out.print (exponent + "," + center);
        }
        System.out.println (")");

        for (EquationEntry e : equations)
        {
            System.out.println ("  equation");
            if (e.expression != null) e.expression.dumpExponents ("    ");
            if (e.condition != null)
            {
                System.out.println ("    @:");
                e.condition.dumpExponents ("    ");
            }
        }
    }

    public boolean determineUnit (boolean fatal) throws Exception
    {
        boolean changed = false;
        Unit<?> nextUnit = null;

        // Ensure that all equations share the same unit.
        for (EquationEntry e : equations)
        {
            if (e.condition != null) e.condition.determineUnit (fatal);
            if (e.expression != null)
            {
                e.expression.determineUnit (fatal);
                if (e.expression.unit != null)
                {
                    if (nextUnit == null  ||  nextUnit.isCompatible (AbstractUnit.ONE))
                    {
                        nextUnit = e.expression.unit;
                    }
                    else if (fatal  &&  ! e.expression.unit.isCompatible (AbstractUnit.ONE)  &&  ! e.expression.unit.isCompatible (nextUnit))
                    {
                        throw new Exception (nextUnit + " versus " + e.expression.unit);
                    }
                }
            }
        }

        // If we have a derivative, then use it as a clue, or offer a clue to it.
        if (derivative != null)
        {
            boolean haveNext       =  nextUnit        != null  &&  ! nextUnit       .isCompatible (AbstractUnit.ONE);
            boolean haveDerivative =  derivative.unit != null  &&  ! derivative.unit.isCompatible (AbstractUnit.ONE);
            if (haveDerivative)
            {
                Unit<?> integrated = UnitValue.simplify (derivative.unit.multiply (UnitValue.seconds));
                if (haveNext)  // Have both, so ensure units are compatible
                {
                    if (fatal  &&  ! integrated.isCompatible (AbstractUnit.ONE)  &&  ! integrated.isCompatible (nextUnit))
                    {
                        throw new Exception (nextUnit + " versus integrated " + integrated);
                    }
                }
                else  // derivative unit is better defined than nextUnit
                {
                    nextUnit = integrated;
                }
            }
            else if (haveNext  ||  nextUnit != null  &&  derivative.unit == null)  // nextUnit is better defined than derivative unit
            {
                derivative.unit = UnitValue.simplify (nextUnit.divide (UnitValue.seconds));
                changed = true;
            }
        }

        // If we are a reference to another variable, then synchronize unit with it,
        // but only if the combining operator is such that it must have the same unit as us.
        // Generally, this is any combiner that is not multiplicative in nature.
        if (reference != null  &&  reference.variable != this  &&  assignment != MULTIPLY  &&  assignment != DIVIDE)
        {
            boolean haveNext      =  nextUnit                != null  &&  ! nextUnit               .isCompatible (AbstractUnit.ONE);
            boolean haveReference =  reference.variable.unit != null  &&  ! reference.variable.unit.isCompatible (AbstractUnit.ONE);
            if (haveReference)
            {
                if (haveNext)
                {
                    if (fatal  &&  ! reference.variable.unit.isCompatible (nextUnit))
                    {
                        throw new Exception (nextUnit + " versus reference " + reference.variable.unit);
                    }
                }
                else  // reference unit is better defined than nextUnit
                {
                    nextUnit = reference.variable.unit;
                }
            }
            else if (haveNext  ||  nextUnit != null  &&  reference.variable.unit == null)  // nextUnit is better defined than reference unit
            {
                reference.variable.unit = nextUnit;
                changed = true;
            }
        }

        if (nextUnit != null  &&  (unit == null  ||  ! nextUnit.isCompatible (unit)))  // nextUnit is better defined than current unit
        {
            // Sanity check. Because dimensional analysis can form a feedback loop through the system of equations,
            // it is possible for a bizarre unit to grow without bound. This would be fine, except that the Units of Measurement
            // library uses a recursive function call to evaluate dimension powers, and this can result in a stack overflow for
            // excessively large numbers.
            final int maxPower = 10;  // The limit here is arbitrary, but should be enough for any sane unit (except maybe volume of 11-dimensional space). If not, maybe make this user-configurable on the same tab as dimension checking mode.
            Map<? extends Dimension, Integer> d = nextUnit.getDimension ().getBaseDimensions ();
            if (d != null)
            {
                for (Entry<? extends Dimension, Integer> e : d.entrySet ())
                {
                    if (Math.abs (e.getValue ()) > maxPower) return false;  // Stop propagating units when they go insane.
                }
            }

            changed = true;
            unit = nextUnit;
        }

        return changed;
    }

    /**
        Record other variables that this variable depends on.
    **/
    public void addDependencyOn (Variable whatWeNeed)
    {
        if (uses == null)
        {
            uses = new IdentityHashMap<Variable,Integer> ();
        }
        else if (uses.containsKey (whatWeNeed))
        {
            uses.put (whatWeNeed, uses.get (whatWeNeed) + 1);
            return;
        }
        uses.put (whatWeNeed, 1);
        whatWeNeed.addUser (this);
    }

    public void removeDependencyOn (Variable whatWeDontNeedAnymore)
    {
        if (uses == null  ||  ! uses.containsKey (whatWeDontNeedAnymore)) return;
        int newCount = uses.get (whatWeDontNeedAnymore) - 1;
        if (newCount > 0)
        {
            uses.put (whatWeDontNeedAnymore, newCount);
        }
        else
        {
            uses.remove (whatWeDontNeedAnymore);
            whatWeDontNeedAnymore.removeUser (this);
        }
    }

    /**
        Assuming none of our equations currently have their dependencies set, add dependencies on
        our direct references or the connection bindings they pass through. Used when rewriting
        equations in a way that is too complex to conveniently track as incremental changes.
        1) removeDepenciesFromReferences() to clear dependencies
        2) Do complex changes to equation structure (such as merging another variable) without
           updating dependencies.
        3) this function to apply new dependencies
    **/
    public void addDependenciesOfReferences ()
    {
        if (equations == null) return;
        for (EquationEntry e : equations)
        {
            if (e.expression != null) e.expression.addDependencies (this);
            if (e.condition  != null) e.condition .addDependencies (this);
        }
    }

    /**
        Removes only those dependencies created by our direct references or the connection
        bindings they pass through.
    **/
    public void removeDependenciesOfReferences ()
    {
        if (equations == null) return;
        for (EquationEntry e : equations)
        {
            if (e.expression != null) e.expression.releaseDependencies (this);
            if (e.condition  != null) e.condition .releaseDependencies (this);
        }
    }

    /**
        Releases all our dependencies, usually as a prelude to removing this variable from an equation set.
    **/
    public void removeDependencies ()
    {
        if (uses == null) return;
        for (Variable whatWeDontNeedAnymore : uses.keySet ())
        {
            whatWeDontNeedAnymore.removeUser (this);
        }
        uses = null;
    }

    /**
        Record variables or equation sets that depend on this variable.
        Note that addDependencyOn(Variable) handles both sides of the add. You only call this function
        directly to add equation sets.
        This function guards against duplicates, so it can be called multiple times with the same user.
    **/
    public void addUser (Object user)
    {
        if (usedBy == null)
        {
            usedBy = new ArrayList<Object> ();
        }
        else
        {
            for (Object b : usedBy) if (b == user) return;  // Don't add more than once.
        }
        usedBy.add (user);
    }

    public void removeUser (Object user)
    {
        if (usedBy == null) return;
        for (int i = 0; i < usedBy.size (); i++)
        {
            if (usedBy.get (i) == user)
            {
                usedBy.remove (i);
                break;
            }
        }
    }

    /**
        Indicates that other objects (mainly variables) depend on this one.
        A dependency from this variable on itself does not count as a user.
    **/
    public boolean hasUsers ()
    {
        if (usedBy == null  ||  usedBy.isEmpty ()) return false;
        if (usedBy.size () == 1  &&  usedBy.contains (this)) return false;
        return true;
    }

    public boolean neededBySpecial ()
    {
        return neededBySpecial (null);
    }

    protected boolean neededBySpecial (Variable from)
    {
        if (usedBy == null) return false;

        Variable p = from;
        while (p != null)
        {
            if (p == this) return false;
            p = p.visited;
        }
        visited = from;

        for (Object u : usedBy)
        {
            if (! (u instanceof Variable)) continue;
            Variable v = (Variable) u;

            if (v.container != container) continue;  // Don't exit the current equation set.
            if (v.name.startsWith ("$")) return true;
            if (v == this) continue;
            if (v.neededBySpecial (this)) return true;
        }

        return false;
    }

    /**
        Determines if query exists anywhere in our dependency graph.
        @return The actual Variable we depend on (as opposed to the query object).
        If we don't depend on the item described by the query, then null.
    **/
    public Variable dependsOn (Variable query)
    {
        if (query.equals (this)) return null;  // Don't report direct dependency on self. This simplifies code when collecting a list of dependencies, because generally this variable will be evaluated explicitly, after the dependencies.
        return dependsOn (query, null);
    }

    protected Variable dependsOn (Variable query, Variable from)
    {
        if (query.equals (this)) return this;
        if (uses == null) return null;

        // Prevent infinite recursion
        Variable p = from;
        while (p != null)
        {
            if (p == this) return null;
            p = p.visited;
        }
        visited = from;

        for (Variable u : uses.keySet ())
        {
            if (u.container != container) continue;  // Don't exit the current equation set.
            Variable result = u.dependsOn (query, this);
            if (result != null) return result;
        }

        return null;
    }

    /**
        Scans the expressions in this variable, and the expression in any temporary variables they reference,
        for any instance of event(). Only analyzes within the current equation set.
    **/
    public boolean dependsOnEvent ()
    {
        return dependsOnEvent (null);
    }

    protected boolean dependsOnEvent (Variable from)
    {
        // Prevent infinite recursion
        Variable p = from;
        while (p != null)
        {
            if (p == this) return false;
            p = p.visited;
        }
        visited = from;

        // Scan temporary variables we depend on
        if (uses != null)
        {
            for (Variable u : uses.keySet ())
            {
                if (! u.hasAttribute ("temporary")) continue;
                if (u.dependsOnEvent (this)) return true;
            }
        }

        // Scan equations
        class EventVisitor implements Visitor
        {
            boolean found;
            public boolean visit (Operator op)
            {
                if (op instanceof Event) found = true;
                return ! found;
            }
        }
        EventVisitor visitor = new EventVisitor ();
        visit (visitor);
        return visitor.found;
    }

    public boolean circuit (Variable from, int increment)
    {
        boolean result = false;
        visited = from;
        blocked = true;
        if (uses != null)
        {
            for (Variable w : uses.keySet ())
            {
                if (w.container != container) continue;
                if (w.before == null) continue;  // Presence of "before" indicates member of working set.
                if (w == this)  // Self edge
                {
                    if (from == this) priority += increment;  // If we are the root of the stack, then count 1 cycle.
                    continue;  // Johnson algorithm assumes these do not exist.
                }
                if (w.visited == w)  // Found root of stack, so completed a circuit.
                {
                    result = true;

                    // Increment count of everything on stack.
                    Variable i = this;
                    while (i.visited != i)
                    {
                        i.priority += increment;
                        i = i.visited;
                    }
                    i.priority += increment;
                }
                else if (! w.blocked)
                {
                    if (w.circuit (this, increment)) result = true;
                }
            }
        }
        if (result)
        {
            unblock ();
        }
        else if (uses != null)
        {
            for (Variable w : uses.keySet ())
            {
                if (w.container != container) continue;
                if (w.before == null) continue;
                w.before.add (this);
            }
        }
        return result;
    }

    public void unblock ()
    {
        blocked = false;
        for (Variable w : before) if (w.blocked) w.unblock ();
        before.clear ();
    }

    public void setBefore (Variable after)
    {
        if (before == null) before = new HashSet<Variable> ();
        before.add (after);  // I am before the given variable, and it is after me.
    }

    public void setBefore (boolean allTemp)
    {
        if (uses == null) return;

        for (Variable v : uses.keySet ())
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
            if (allTemp  ||  v.hasAttribute ("temporary"))
            {
                v.setBefore (this);
            }
            else
            {
                setBefore (v);
            }
        }
    }

    public void setPriority (int value, Variable from)
    {
        // Prevent infinite recursion
        Variable p = from;
        while (p != null)
        {
            if (p == this) return;
            p = p.visited;
        }
        visited = from;

        // Ripple-increment priority
        if (value <= priority) return;
        priority = value++;  // note the post-increment here
        for (Variable v : before)
        {
            if (v == this) continue; // This could be detected by the recursion test above, but our trail would still be damaged.
            if (v.container != container) continue;  // Don't exit the current equation set when setting priority.
            v.setPriority (value, this);
        }
    }

    public void tagDerivativeOrDependency ()
    {
        if (hasAttribute ("derivativeOrDependency")) return;
        addAttribute ("derivativeOrDependency");
        if (uses == null) return;
        for (Variable u : uses.keySet ())
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
            <dt>local</dt>
                <dd>prevents this variable from being tagged "global".
                Since not-global is the default state, this tag is only used in special circumstances.</dd>
            <dt>constant</dt>
                <dd>value is known at generation time, so can be hard-coded</dd>
            <dt>initOnly</dt>
                <dd>value is set at init time, and never changed after that.</dd>
            <dt>readOnly</dt>
                <dd>the value can change, but it should never be written to directly.</dd>
            <dt>instance</dt>
                <dd>pointer to an instance, rather than a regular value</dd>
            <dt>reference</dt>
                <dd>the actual value of the variable is stored in a different
                equation set</dd>
            <dt>accessor</dt>
                <dd>value is given by a function rather than stored</dd>
            <dt>preexistent</dt>
                <dd>storage does not need to be created for the variable
                because it is either inherited or passed into a function.</dd>
            <dt>temporary</dt>
                <dd>this variable is used immediately in the equation set and
                never stored between time-steps; a temporary must not be
                accessed by other equation sets</dd>
            <dt>state</dt>
                <dd>Explicitly marked as not temporary.</dd>
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
        if (attributes == null) attributes = new HashSet<String> ();
        attributes.add (attribute);
    }

    public void removeAttribute (String attribute)
    {
        if (attributes == null) return;
        attributes.remove (attribute);
    }

    public boolean hasAttribute (String attribute)
    {
        if (attributes == null) return false;
        return attributes.contains (attribute);
    }

    public boolean hasAll (String... attributeArray)
    {
        if (attributes == null) return false;
        for (String s : attributeArray) if (! attributes.contains (s)) return false;
        return true;
    }

    public boolean hasAny (String... attributeArray)
    {
        if (attributes == null) return false;
        for (String s : attributeArray) if (attributes.contains (s)) return true;
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

    public String fullName ()
    {
        String result = "";
        if (container != null) result = container.prefix ();
        if (! result.isEmpty ()) result += ".";
        result += nameString ();
        return result;
    }

    /**
        For variables in an equation set, returns a path that can be used to retrieve the source node
        from the database.
    **/
    public List<String> getKeyPath ()
    {
        List<String> result = container.getKeyPath ();
        result.add (nameString ());
        return result;
    }

    public String combinerString ()
    {
        switch (assignment)
        {
            case ADD:      return "+";
            case MULTIPLY: return "*";
            case DIVIDE:   return "/";
            case MIN:      return "<";
            case MAX:      return ">";
        }
        return "";
    }

    public String attributeString ()
    {
        if (attributes == null) return "[]";
        else                    return attributes.toString ();
    }

    /**
        Safe method to access metadata.
    **/
    public MNode getMetadata ()
    {
        if (metadata == null) metadata = new MVolatile ();
        return metadata;
    }

    public boolean codeEquals (Variable that)
    {
        if (equations.size () != that.equations.size ()) return false;
        // Equations should be sorted exactly the same, since they should have exactly the same ifString keys.
        Iterator<EquationEntry> bit = that.equations.iterator ();
        for (EquationEntry a : equations)
        {
            EquationEntry b = bit.next ();
            if (! a.codeEquals (b)) return false;
        }
        return true;
    }

    /**
        Warning: This function is does not distinguish between equation sets. It merely matches name and order.
        This function is not consistent with equals, so use containers with caution.
    **/
    public int compareTo (Variable that)
    {
        int result = name.compareTo (that.name);
        if (result != 0) return result;
        if (order >= 0  &&  that.order >= 0) return order - that.order;  // ascending order; necessary for generating integrate() function in C-backend
        return 0;
    }

    /**
        Two variables are equal if they have the same container, name and order.
        Order can be set to no-care with a value of -1.
        Container to be set to no-care with a value of null.
        Name and order are handled by compareTo().
    **/
    @Override
    public boolean equals (Object o)
    {
        if (this == o) return true;
        if (o instanceof Variable)
        {
            Variable that = (Variable) o;
            if (container != null  &&  that.container != null  &&  container != that.container) return false;
            return compareTo (that) == 0;
        }
        return false;
    }
}

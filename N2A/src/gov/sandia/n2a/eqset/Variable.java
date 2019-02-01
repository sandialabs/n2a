/*
Copyright 2013-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.eqset;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.OperatorBinary;
import gov.sandia.n2a.language.ParseException;
import gov.sandia.n2a.language.Transformer;
import gov.sandia.n2a.language.Type;
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
import tec.uom.se.AbstractUnit;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

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
    public List<Variable>               before;     // Variables that must be evaluated after us. Generally the same as uses, unless we are a temporary, in which case the ordering is reversed. Note EquationSet.ordered
    public Variable                     visited;    // Points to the previous variable visited on the current path. Used to prevent infinite recursion. Only works on a single thread.
    public int                          priority;   // For evaluation order.
    public boolean                      changed;    // Indicates that analysis touched one or more equations in a way that merits another pass.

    // fixed-point analysis
    public int                          exponent = Operator.UNKNOWN; // power of most significant bit expected to be stored by this variable. The initial value of MIN_VALUE indicates unknown.
    public int                          center   = Operator.MSB / 2;
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

    public Variable (String name)
    {
        this (name, -1);
    }

    public Variable (String name, int order)
    {
        this.name  = name;
        this.order = order;
    }

    public Variable (EquationSet container, MNode source) throws ParseException
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
                String key = i.key ();
                if (key.startsWith ("@"))
                {
                    EquationEntry e = new EquationEntry (i);
                    if (e.expression != null) add (e);
                }
                if (key.equals ("$metadata")) metadata.merge (i);
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
                        e = new EquationEntry ("inf");
                        break;
                    case MAX:
                        e = new EquationEntry ("-inf");
                        break;
                }
                if (e != null) add (e);
            }
        }
        catch (ParseException e)
        {
            String prefix = container.prefix () + "." + source.key () + "=" + combinerString ();
            e.line = prefix + e.line;
            e.column += prefix.length ();
            throw e;
        }
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

    public static Variable fromLHS (String lhs)
    {
        lhs = lhs.trim ();
        int order = 0;
        while (lhs.endsWith ("'"))
        {
            order++;
            lhs = lhs.substring (0, lhs.length () - 1);
        }
        return new Variable (lhs, order);
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
            case '<': assignment = MIN;           return rhs.substring (1);
            case '>': assignment = MAX;           return rhs.substring (1);
            case ':': addAttribute ("temporary"); return rhs.substring (1);  // We are already set to REPLACE
        }

        return rhs;
    }

    public static boolean isCombiner (String value)
    {
        if (value.length () != 1) return false;
        return "+*/<>:".contains (value);
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
        Replaces each variable in a given subset, such that the result can be
        modified by optimization procedures without damaging the original equation set.
    **/
    public static void deepCopy (List<Variable> list)
    {
        try
        {
            for (int i = 0; i < list.size (); i++) list.set (i, (Variable) list.get (i).clone ());
        }
        catch (CloneNotSupportedException e) {}

        for (Variable v : list)
        {
            TreeSet<EquationEntry> newEquations = new TreeSet<EquationEntry> ();
            for (EquationEntry e : v.equations) newEquations.add (e.deepCopy (v));
            v.equations = newEquations;

            v.usedBy = null;
            v.uses = null;
        }

        // Rebuild dependency structure within the list.
        // We only care about dependencies that determine ordering within the list.
        // External dependencies won't be counted, but also won't be changed.
        class DependencyTransformer extends Transformer
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
            // Note that Internal executes contained populations after the upper-level equations, so the contained
            // populations take precedence even without flattening.
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
                            e.expression = op;
                        }
                        else if (f != null)
                        {
                            f.operands[0] = e0.expression.deepCopy ();
                            f.operands[1] = e1.expression.deepCopy ();
                            e.expression = f;
                        }
                    }
                    if (e.expression != null) equations.add (e);
                }
            }
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
            if (e.condition != null)
            {
                e.condition = e.condition.transform (transformer);
                e.ifString  = e.condition.render ();
            }
        }
    }

    public boolean simplify ()
    {
        if (equations == null) return false;
        changed = false;
        TreeSet<EquationEntry> nextEquations = new TreeSet<EquationEntry> ();
        TreeSet<EquationEntry> alwaysTrue    = new TreeSet<EquationEntry> ();
        visited = null;
        for (EquationEntry e : equations)
        {
            if (e.expression != null)
            {
                e.expression = e.expression.simplify (this);
            }
            if (e.condition != null)
            {
                e.condition = e.condition.simplify (this);
                e.ifString  = e.condition.render ();
            }

            if (e.condition instanceof Constant)
            {
                if (e.condition.getDouble () == 0)  // Will never fire
                {
                    // Drop equation
                    changed = true;
                    continue;
                }
                else  // Will always fire
                {
                    alwaysTrue.add (e);
                }
            }
            else if (e.ifString.isEmpty ()  &&  e.expression instanceof AccessVariable  &&  ((AccessVariable) e.expression).reference.variable == this)
            {
                // Drop this default equation because it is redundant. Simulator always copies value to next cycle when no equation fires.
                changed = true;
                continue;
            }

            nextEquations.add (e);
        }
        if (alwaysTrue.size () == 1)  // Default equation will never be included in alwaysTrue.
        {
            changed = true;
            equations = alwaysTrue;

            // Make the equation unconditional, since it always fires anyway.
            EquationEntry e = equations.first ();
            e.condition = null;
            e.ifString = "";
        }
        else
        {
            equations = nextEquations;
        }
        return changed;
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

    public boolean determineExponent (int exponentTime)
    {
        int centerNew   = center;
        int exponentNew = exponent;
        changed = false;

        if (name.equals ("$t")  &&  order == 0)
        {
            centerNew   = Operator.MSB - 1;  // Half the time, the msb won't be set. The other half, it will.
            exponentNew = exponentTime;
        }
        else if (name.equals ("$index"))  // The only variable that is stored as a pure integer.
        {
            centerNew   = 0;
            exponentNew = Operator.MSB;
        }
        else if (name.equals ("$connect")  ||  name.equals ("$init")  ||  name.equals ("$live"))
        {
            // Booleans are stored as regular floats.
            centerNew   = Operator.MSB / 2;
            exponentNew = Operator.MSB - centerNew;
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
                    e.expression.determineExponent (this);
                    if (e.expression.exponent != Operator.UNKNOWN)  // Only count equations that have known values of exponent and center.
                    {
                        cent += e.expression.center;
                        pow  += e.expression.exponent;
                        count++;
                    }
                }
                if (e.condition != null)
                {
                    e.condition.determineExponent (this);
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
            if (usedBy != null)
            {
                boolean multiplicative = false;
                for (Object o : usedBy)
                {
                    if (! (o instanceof Variable)) continue;
                    Variable v = (Variable) o;
                    if (v.reference == null  ||  v.reference.variable != this) continue;
                    if (v.exponent == Operator.UNKNOWN) continue;
                    if (v.assignment == MULTIPLY)
                    {
                        cent += v.center;
                        pow  += v.exponent;
                        multiplicative = true;
                    }
                    else if (v.assignment == DIVIDE)
                    {
                        int centerPower = v.exponent - Operator.MSB + v.center;
                        centerPower *= -1;  // Negate to account for division 1/v
                        cent += Operator.MSB / 2;  // Fix quotient center at MSB/2
                        pow  += centerPower + Operator.MSB / 2;
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
                    if (bound instanceof Constant) b = bound.centerPower ();  // Constants have exactly one magnitude (and center is always shifted to hold it), so use directly.
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
                    centerNew -= exponentTime - exponentNew;
                    if (centerNew < 0  ||  centerNew > Operator.MSB)
                    {
                        Backend.err.get ().println ("ERROR: not enough fixed-point resolution for given $t'");
                        throw new Backend.AbortRun ();
                    }
                }
                exponentNew = exponentTime;
            }
        }

        // User-specified exponent overrides any calculated value.
        String magnitude = "";
        if (metadata != null) magnitude = metadata.get ("median");
        if (! magnitude.isEmpty ())
        {
            if (exponent != Operator.UNKNOWN) return changed;  // Already processed the hint.
            try
            {
                double value = Operator.parse (magnitude).getDouble ();
                int centerPower = 0;
                if (value > 0) centerPower = (int) Math.floor (Math.log (value) / Math.log (2));  // log2 (value)
                centerNew   = Operator.MSB / 2;
                exponentNew = centerPower + Operator.MSB - centerNew;
            }
            catch (ParseException e) {}
        }

        if (exponentNew != exponent  ||  centerNew != center) changed = true;
        exponent = exponentNew;
        center   = centerNew;
        return changed;
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
                e.expression.determineExponentNext (this);
            }
            if (e.condition != null)
            {
                e.condition.exponentNext = e.condition.exponent;
                e.condition.determineExponentNext (this);
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
                        throw new Exception ("derivative " + derivative.unit + " versus " + nextUnit);
                    }
                }
                else
                {
                    nextUnit = integrated;
                }
            }
            else if (haveNext  ||  nextUnit != null  &&  derivative.unit == null)
            {
                derivative.unit = UnitValue.simplify (nextUnit.divide (UnitValue.seconds));
                changed = true;
            }
        }

        boolean referenceOut = reference != null  &&  reference.variable != this;
        if (referenceOut)
        {
            boolean haveNext      =  nextUnit                != null  &&  ! nextUnit               .isCompatible (AbstractUnit.ONE);
            boolean haveReference =  reference.variable.unit != null  &&  ! reference.variable.unit.isCompatible (AbstractUnit.ONE);
            if (haveReference)
            {
                if (haveNext)
                {
                    if (fatal  &&  ! reference.variable.unit.isCompatible (nextUnit))
                    {
                        throw new Exception ("reference " + reference.variable.unit + " versus " + unit);
                    }
                }
                else
                {
                    nextUnit = reference.variable.unit;
                }
            }
            else if (haveNext  ||  nextUnit != null  &&  reference.variable.unit == null)
            {
                reference.variable.unit = nextUnit;
                changed = true;
            }
        }

        if (nextUnit != null  &&  (unit == null  ||  ! nextUnit.isCompatible (unit)))
        {
            changed = true;
            unit = nextUnit;
        }

        return changed;
    }

    /**
        Record what other variables this variable depends on.
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
        if (uses == null) return;
        if (uses.containsKey (whatWeDontNeedAnymore))
        {
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

    public boolean hasUsers ()
    {
        if (usedBy == null) return false;
        return ! usedBy.isEmpty ();
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
        class EventVisitor extends Visitor
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

    public void setBefore (Variable after)
    {
        if (before == null)
        {
            before = new ArrayList<Variable> ();
        }
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
        if (attributes == null) attributes = new TreeSet<String> ();
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

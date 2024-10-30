/*
Copyright 2020-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language.function;

import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.TreeSet;

import gov.sandia.n2a.backend.internal.InstanceTemporaries;
import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.eqset.EquationSet.ExponentContext;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.operator.Multiply;
import gov.sandia.n2a.language.operator.NOT;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class Delay extends Function
{
    public int index;  // For internal backend, the position in valuesObject of the buffer object. For C backend, the suffix of the buffer object name in the current class.
    public int depth;  // If greater than 0, indicates that this delay has a known fixed number of steps. Set by backend analysis routine.

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String name ()
            {
                return "delay";
            }

            public Operator createInstance ()
            {
                return new Delay ();
            }
        };
    }

    public boolean canBeConstant ()
    {
        return false;
    }

    public Operator simplify (Variable from, boolean evalOnly)
    {
        for (int i = 0; i < operands.length; i++) operands[i] = operands[i].simplify (from, evalOnly);
        if (evalOnly) return this;  // Everything below involves structural changes.

        // Ensure that every Delay call is by itself as the sole expression for some variable.

        if (from.equations.size () == 1  &&  from.equations.first ().expression == this)  // Already on private variable.
        {
            if (operands.length == 1)  // But delay() itself can be removed in favor of state buffering.
            {
                from.addAttribute ("state");
                from.removeAttribute ("temporary");

                // Construct the expression: !$init * operand[0]
                // This should return 0 in the init cycle, and a delayed value of operand[0] in all subsequent cycles.

                Multiply mult = new Multiply ();
                mult.parent = parent;

                Variable init = from.container.find (new Variable ("$init"));
                VariableReference avref = new VariableReference ();
                avref.variable = init;
                AccessVariable avinit = new AccessVariable (avref);

                NOT not = new NOT ();
                not.operand = avinit;
                mult.operand0 = not;
                mult.operand0.parent = mult;

                mult.operand1 = operands[0];
                mult.operand1.parent = mult;

                return mult;
            }
            return this;  // Already moved to our own variable.
        }

        //   Add a new variable for this Delay.
        //   TODO: check if there is already a delay variable that matches Delay.
        Variable d = new Variable ("delay1");
        int index = 2;
        while (from.container.find (d) != null) d.name = "delay" + index++;
        from.container.add (d);
        d.reference = new VariableReference ();
        d.reference.variable = d;
        from.addDependencyOn (d);

        d.equations = new TreeSet<EquationEntry> ();
        EquationEntry e = new EquationEntry (d, "");
        d.equations.add (e);
        e.expression = this;
        e.expression.parent = null;  // This will later be changed to "from".

        VariableReference r = new VariableReference ();
        r.variable = d;
        return new AccessVariable (r);
    }

    public void determineExponent (ExponentContext context)
    {
        for (int i = 0; i < operands.length; i++) operands[i].determineExponent (context);

        Operator value = operands[0];  // value to delay
        updateExponent (context, value.exponent, value.center);

        // Need to record the exponent for time, since we avoid passing context to determineExponentNext().
        if (operands.length > 1) operands[1].exponentNext = context.exponentTime;

        // Assume that defaultValue is representable in the same range as value,
        // so don't bother incorporating it into the estimate of our output exponent.
    }

    public void determineExponentNext ()
    {
        exponent = exponentNext;  // Assumes that we simply output the same exponent as our inputs.

        Operator value = operands[0];
        value.exponentNext = exponentNext;  // Passes the required exponent down to operands.
        value.determineExponentNext ();

        if (operands.length > 1)
        {
            Operator delta = operands[1];
            // delta.exponentNext already set in determineExponent()
            delta.determineExponentNext ();
        }

        if (operands.length > 2)
        {
            Operator defaultValue = operands[2];
            defaultValue.exponentNext = exponentNext;
            defaultValue.determineExponentNext ();
        }
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        operands[0].determineUnit (fatal);
        unit = operands[0].unit;
    }

    public static class DelayBuffer
    {
        double value;  // Return value is not strictly immutable, but generally treated that way, so we will use this repeatedly.
        NavigableMap<Double,Double> buffer = new TreeMap<Double,Double> ();

        public void step (double now, double delay, double value)
        {
            buffer.put (now + delay, value);
            while (buffer.firstKey () <= now)
            {
                Entry<Double,Double> e = buffer.pollFirstEntry ();
                this.value = e.getValue ();
            }
        }
    }

    public Type eval (Instance context)
    {
        Type tempValue = operands[0].eval (context);
        Simulator simulator = Simulator.instance.get ();
        if (simulator == null) return tempValue;

        double value = ((Scalar) tempValue).value;
        double delay = ((Scalar) operands[1].eval (context)).value;

        Instance wrapped = ((InstanceTemporaries) context).wrapped;  // Unpack the main instance data, to access buffer.
        DelayBuffer buffer = (DelayBuffer) wrapped.valuesObject[index];
        if (buffer == null)
        {
            wrapped.valuesObject[index] = buffer = new DelayBuffer ();
            if (operands.length > 2) buffer.value = ((Scalar) operands[2].eval (context)).value;
            else                     buffer.value = 0;
        }
        buffer.step (simulator.currentEvent.t, delay, value);
        return new Scalar (buffer.value);
    }

    public String toString ()
    {
        return "delay";
    }
}

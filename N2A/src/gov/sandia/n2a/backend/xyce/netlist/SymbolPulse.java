/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.xyce.netlist;

import java.util.ArrayList;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Transformer;
import gov.sandia.n2a.language.function.Pulse;
import gov.sandia.n2a.language.type.Scalar;

public class SymbolPulse extends Symbol
{
    // a more generic pulse than XycePulseInputSymbolDef
    // shows up in equations as pulse(var, width, period, rise, fall)
    // Might be scaled or shifted:  y = scale * pulse(x + xoffset, ...) + yoffset
    // IF var represents time, this can be translated into a Xyce pulse
    // otherwise, it needs to be treated as a Xyce .func

    public Operator lo;
    public Operator hi;
    public Operator delay;
    public Operator rise;
    public Operator fall;
    public Operator width;
    public Operator period;

    public SymbolPulse (EquationEntry eq)
    {
        super (eq);

        // Determine lo and hi
        // The basic idea is to replace the pulse() function with its two states (0, 1),
        // and compute the output of the expression.
        class ReplacePulse implements Transformer
        {
            public Constant pulseState = new Constant ();
            public Pulse pulse;
            public Operator transform (Operator op)
            {
                if (op instanceof Pulse)
                {
                    pulse = (Pulse) op;
                    return pulseState;
                }
                return null;
            }
        }
        ReplacePulse replacePulse = new ReplacePulse ();
        Operator o = eq.expression.deepCopy ().transform (replacePulse);
        try
        {
            replacePulse.pulseState.value = new Scalar (0);
            lo = new Constant (o.eval (null));
            replacePulse.pulseState.value = new Scalar (1);
            hi = new Constant (o.eval (null));
        }
        catch (NullPointerException exception)
        {
            lo = new Constant (new Scalar (0));
            hi = new Constant (new Scalar (1));
        }

        // Determine time offset
        // The basic idea (similar to above) is to substitute 0 for $t and compute output of expression
        // If $t is not present or the result is not constant, then we can't use a Xyce pulse function
        class ReplaceTime implements Transformer
        {
            public Constant time = new Constant (new Scalar (0));
            public Operator transform (Operator op)
            {
                if (op instanceof AccessVariable)
                {
                    AccessVariable av = (AccessVariable) op;
                    if (av.reference.variable.name.equals ("$t"))
                    {
                        return time;
                    }
                }
                return null;
            }
        }
        ReplaceTime replaceTime = new ReplaceTime ();
        o = replacePulse.pulse.operands[0].deepCopy ().transform (replaceTime);
        delay = o.simplify (eq.variable, true);

        // fill in operands that are equivalent
        width  = replacePulse.pulse.operands[1];
        period = replacePulse.pulse.operands[2];
        rise   = replacePulse.pulse.operands[3];
        fall   = replacePulse.pulse.operands[4];
    }

    @Override
    public String getDefinition (XyceRenderer renderer) 
    {
        ArrayList<String> params = new ArrayList<String> (7);
        params.add (renderer.change (lo));
        params.add (renderer.change (hi));
        params.add (renderer.change (delay));
        params.add (renderer.change (rise));
        params.add (renderer.change (fall));
        params.add (renderer.change (width));
        params.add (renderer.change (period));

        return Xyceisms.voltagePulse (eq.variable.name, renderer.pi.hashCode (), params);
    }
}

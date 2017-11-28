/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.xyce;

import java.util.HashMap;
import java.util.Map;

import gov.sandia.n2a.backend.internal.InternalBackendData;
import gov.sandia.n2a.backend.xyce.function.Pulse;
import gov.sandia.n2a.backend.xyce.function.Sinewave;
import gov.sandia.n2a.backend.xyce.netlist.SymbolConstantIC;
import gov.sandia.n2a.backend.xyce.netlist.SymbolStateVar0;
import gov.sandia.n2a.backend.xyce.netlist.SymbolStateVar1;
import gov.sandia.n2a.backend.xyce.netlist.Symbol;
import gov.sandia.n2a.backend.xyce.netlist.SymbolFunc;
import gov.sandia.n2a.backend.xyce.netlist.SymbolPulse;
import gov.sandia.n2a.backend.xyce.netlist.SymbolSinewave;
import gov.sandia.n2a.backend.xyce.netlist.Device;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.plugins.extpoints.Backend;

public class XyceBackendData 
{
    public InternalBackendData       internal;  // chain to preprocessor data
    public Device                    deviceSymbol;
    public Map<EquationEntry,Symbol> equationSymbols = new HashMap<EquationEntry,Symbol> ();
    public Map<String,Symbol>        variableSymbols = new HashMap<String,Symbol> ();

    public class ContainsVariable extends Visitor
    {
        public Variable target;
        boolean found;
        public ContainsVariable (Variable target)
        {
            this.target = target;
        }
        public boolean visit (Operator op)
        {
            if (found) return false;
            if (op instanceof AccessVariable  &&  ((AccessVariable) op).reference.variable.equals (target))
            {
                found = true;
                return false;
            }
            return true;
        }
        public boolean check (Operator o)
        {
            found = false;
            o.visit (this);
            return found;
        }
    }

    public void analyze (EquationSet s)
    {
        if (Device.isXyceDevice (s))
        {
            deviceSymbol = new Device (s);
        }

        class ContainsOperator extends Visitor
        {
            @SuppressWarnings("rawtypes")
            public Class targetClass;
            boolean found;
            public boolean visit (Operator op)
            {
                if (found) return false;
                if (op.getClass ().equals (targetClass))
                {
                    found = true;
                    return false;
                }
                return true;
            }
            public boolean check (EquationEntry e)
            {
                found = false;
                e.expression.visit (this);
                return found;
            }
        }
        ContainsOperator containsPulse = new ContainsOperator ();
        containsPulse.targetClass = Pulse.class;
        ContainsOperator containsSinewave = new ContainsOperator ();
        containsSinewave.targetClass = Sinewave.class;

        ContainsVariable containsT = new ContainsVariable (new Variable ("$t", 0));

        for (Variable v : s.variables)
        {
            if (v.name.startsWith ("$")) continue;  // in a static (no structural dynamics) simulation, no $variable needs to be computed at runtime
            if (v.hasAttribute ("constant")  ||  v.hasAttribute ("initOnly")) continue;  // Constants are already subbed in. "initOnly" values are defined during init cycle, and can now be subbed during code generation.

            for (EquationEntry eq : v.equations)
            {
                if (Device.isXyceDevice (s) && Device.ignoreEquation (eq)) continue;  // don't need to write out equations defining dynamics already defined by a device

                Symbol handler = null;
                if (eq.variable.order > 1)
                {
                    Backend.err.get ().println ("Support for higher order differential equations not implemented yet (" + eq + ")");
                    throw new Backend.AbortRun ();
                }
                else if (eq.variable.order == 1)
                {
                    handler = new SymbolStateVar1 (eq);
                }
                // The following are all order 0
                else if (containsPulse.check (eq))
                {
                    handler = new SymbolPulse (eq);
                }
                else if (containsSinewave.check (eq))
                {
                    handler = new SymbolSinewave (eq);
                }
                // TODO: should we also check for $dt?
                // TODO: this doesn't seem like an adequate test. Why would having a $t be the only reason to generate a zero-order symbol?
                else if (containsT.check (eq.expression))
                {
                    handler = new SymbolStateVar0 (eq);
                }
                else if (isExplicitInit (eq))
                {
                    handler = new SymbolConstantIC (eq);
                }
                else
                {
                    // The RHS expression depends on state variables, so we create a netlist .func for it.
                    handler = new SymbolFunc (eq);
                }
                equationSymbols.put (eq,     handler);
                variableSymbols.put (v.name, handler);  // May set the handler for v several times, but only the last one is kept. Multiple handlers should agree on symbol for reference. Better yet is to handle multiple equations together.
            }
        }
    }

    public boolean isExplicitInit (EquationEntry e)
    {
        if (e.expression == null  ||  e.condition == null) return false;  // We must have an expression and it must be conditional.
        Type condition = e.condition.eval (new Instance ()
        {
            public Type get (VariableReference r) throws EvaluationException
            {
                if (r.variable.name.equals ("$init")) return new Scalar (1);
                return new Scalar (0);
            }
        });
        if (! (condition instanceof Scalar)  ||  ((Scalar) condition).value == 0) return false;  // must actually fire during init phase
        return new ContainsVariable (new Variable ("$init")).check (e.condition);  // must actually contain $init
    }
}

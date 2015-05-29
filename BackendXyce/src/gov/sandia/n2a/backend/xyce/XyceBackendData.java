/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce;

import java.util.HashMap;
import java.util.Map;

import gov.sandia.n2a.backend.xyce.functions.XyceSineWaveFunction;
import gov.sandia.n2a.backend.xyce.symbol.FunctionSymbolDef;
import gov.sandia.n2a.backend.xyce.symbol.ParamSymbolDef;
import gov.sandia.n2a.backend.xyce.symbol.PulseSymbolDef;
import gov.sandia.n2a.backend.xyce.symbol.SineWaveInputSymbolDef;
import gov.sandia.n2a.backend.xyce.symbol.StateVar0SymbolDef;
import gov.sandia.n2a.backend.xyce.symbol.StateVar1SymbolDef;
import gov.sandia.n2a.backend.xyce.symbol.SymbolDef;
import gov.sandia.n2a.backend.xyce.symbol.XyceDeviceSymbolDef;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.function.Pulse;

public class XyceBackendData 
{
    public XyceDeviceSymbolDef          deviceSymbol;
    public Map<EquationEntry,SymbolDef> equationSymbols = new HashMap<EquationEntry,SymbolDef> ();
    public Map<Variable,SymbolDef>      variableSymbols = new HashMap<Variable,SymbolDef> ();

    public void analyze (EquationSet s)
    {
        if (XyceDeviceSymbolDef.isXyceDevice (s))
        {
            deviceSymbol = new XyceDeviceSymbolDef (s);
        }
        for (Variable v : s.variables)
        {
            if (v.name.startsWith ("$"))  // some special vars should not be written out
            {
                if (   v.name.equals ("$t")
                    || v.name.equals ("$dt")) continue;
            }

            for (EquationEntry eq : v.equations)
            {
                // TODO: xyce metadata should really be in the variable, not the equation. This is an issue with N2A storage structure in general.
                if (XyceDeviceSymbolDef.ignoreEquation (eq)) continue;  // don't need to write out equations defining dynamics already defined by a device

                SymbolDef handler = null;
                if (eq.variable.order > 1)
                {
                    throw new EvaluationException ("Support for higher order differential equations not implemented yet (" + eq + ")");
                }
                else if (eq.variable.order == 1)
                {
                    handler = new StateVar1SymbolDef (eq);
                }
                else if (eq.expression instanceof Pulse)
                {
                    handler = new PulseSymbolDef (eq);
                }
                else if (eq.expression instanceof XyceSineWaveFunction)
                {
                    handler = new SineWaveInputSymbolDef (eq);
                }
                else if (eq.toString ().contains ("$t"))
                {
                    handler = new StateVar0SymbolDef (eq);
                }
                else if (eq.variable.hasAttribute ("initOnly"))  // In theory, we should do the same thing for "constant", except they have already been substituted everywhere.
                {
                    handler = new ParamSymbolDef (eq);
                }
                else
                {
                    // The RHS expression depends on state variables, so we create a netlist .func for it.
                    handler = new FunctionSymbolDef (eq);
                }
                equationSymbols.put (eq, handler);
                variableSymbols.put (v,  handler);  // May set the handler for v several times, but only the last one is kept. Multiple handlers should agree on symbol for reference. Better yet is to handle multiple equations together.
            }
        }
    }
}

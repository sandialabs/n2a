/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce;

import java.util.HashMap;
import java.util.Map;

import gov.sandia.n2a.backend.xyce.netlist.SymbolStateVar0;
import gov.sandia.n2a.backend.xyce.netlist.SymbolStateVar1;
import gov.sandia.n2a.backend.xyce.netlist.Symbol;
import gov.sandia.n2a.backend.xyce.netlist.SymbolFunc;
import gov.sandia.n2a.backend.xyce.netlist.SymbolParam;
import gov.sandia.n2a.backend.xyce.netlist.SymbolPulse;
import gov.sandia.n2a.backend.xyce.netlist.SymbolSinewave;
import gov.sandia.n2a.backend.xyce.netlist.SymbolDevice;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.EvaluationException;

public class XyceBackendData 
{
    public SymbolDevice          deviceSymbol;
    public Map<EquationEntry,Symbol> equationSymbols = new HashMap<EquationEntry,Symbol> ();
    public Map<Variable,Symbol>      variableSymbols = new HashMap<Variable,Symbol> ();

    public void analyze (EquationSet s)
    {
        if (SymbolDevice.isXyceDevice (s))
        {
            deviceSymbol = new SymbolDevice (s);
        }
        for (Variable v : s.variables)
        {
            if (v.name.startsWith ("$"))  // some special vars should not be written out
            {
                if (   v.name.equals ("$t")
                    || v.name.equals ("$dt")) continue;
            }
            if (v.hasAttribute ("constant")) continue;  // Don't define constants. They are substituted in directly by earlier stages of processing.

            for (EquationEntry eq : v.equations)
            {
                if (SymbolDevice.ignoreEquation (eq)) continue;  // don't need to write out equations defining dynamics already defined by a device

                Symbol handler = null;
                if (eq.variable.order > 1)
                {
                    throw new EvaluationException ("Support for higher order differential equations not implemented yet (" + eq + ")");
                }
                else if (eq.variable.order == 1)
                {
                    handler = new SymbolStateVar1 (eq);
                }
                else if (eq.toString ().contains ("pulse"))  // TODO: this is a bad test. Use a visitor to detect the Pulse class
                {
                    handler = new SymbolPulse (eq);
                }
                else if (eq.toString ().contains ("sinewave"))  // TODO: this is a bad test. Use a visitor to detect the Sinewave class
                {
                    handler = new SymbolSinewave (eq);
                }
                else if (eq.toString ().contains ("$t"))
                {
                    handler = new SymbolStateVar0 (eq);
                }
                else if (eq.variable.hasAttribute ("initOnly"))  // In theory, we should do the same thing for "constant", except they have already been substituted everywhere.
                {
                    handler = new SymbolParam (eq);
                }
                else
                {
                    // The RHS expression depends on state variables, so we create a netlist .func for it.
                    handler = new SymbolFunc (eq);
                }
                equationSymbols.put (eq, handler);
                variableSymbols.put (v,  handler);  // May set the handler for v several times, but only the last one is kept. Multiple handlers should agree on symbol for reference. Better yet is to handle multiple equations together.
            }
        }
    }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.symbol;

import gov.sandia.n2a.backend.xyce.XyceTranslationException;
import gov.sandia.n2a.backend.xyce.network.PartInstance;
import gov.sandia.n2a.backend.xyce.network.PartSetInterface;
import gov.sandia.n2a.eqset.EquationEntry;

import java.util.HashMap;
import java.util.Map;

public class SymbolManager 
{
    private Map<EquationEntry, SymbolDef> eqSymbols;

    public SymbolManager()
    {
        eqSymbols = new HashMap<EquationEntry, SymbolDef>();
    }

    public SymbolDef getSymbolDef(PartSetInterface pSet, EquationEntry eq) 
            throws XyceTranslationException
    {
        if (!eqSymbols.containsKey(eq))
        {
            eqSymbols.put(eq, SymbolDefFactory.getSymbolDef(eq, pSet));
        }
        return eqSymbols.get(eq);
    }

    public SymbolDef getSymbolDef(String varname, PartInstance pi, boolean init) 
    {
        SymbolDef result = null;
        PartSetInterface pSet = pi.getPartSet();
        EquationEntry eq = null;
        try {
            eq = pSet.getEquation(varname, pi, init);
        }
        catch (Exception ex) {
            throw new RuntimeException("SymbolManager: cannot find correct equation for " + pSet.getName() + " variable " + varname +
                    " Original exception " + ex);
        }
        try {
            result = getSymbolDef(pSet, eq);
        }
        catch (Exception ex) {
            throw new RuntimeException("SymbolManager: cannot find correct symbol definition for " + pSet.getName() + " variable " + varname +
                    " Original exception " + ex);
        }
        return result;
    }

    public void add(EquationEntry eq, SymbolDef sd) {
        eqSymbols.put(eq,  sd);
    }
}

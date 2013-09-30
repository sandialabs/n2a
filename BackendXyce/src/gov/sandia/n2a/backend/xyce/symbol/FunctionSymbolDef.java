/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.symbol;

import gov.sandia.n2a.backend.xyce.Xyceisms;
import gov.sandia.n2a.backend.xyce.network.PartInstance;
import gov.sandia.n2a.backend.xyce.parsing.LanguageUtil;
import gov.sandia.n2a.backend.xyce.parsing.XyceASTUtil;
import gov.sandia.n2a.backend.xyce.parsing.XyceRHSTranslator;
import gov.sandia.n2a.eqset.EquationEntry;

import java.util.HashSet;
import java.util.Set;

public class FunctionSymbolDef extends DefaultSymbolDef {

    private Set<String> functionArgs = new HashSet<String>();

    public FunctionSymbolDef(EquationEntry eq, Set<String> args, PartInstance pi)
    {
        super(eq, pi);
        functionArgs = args;
        if (functionArgs.contains(LanguageUtil.$TIME)) {
            functionArgs.remove(LanguageUtil.$TIME);
        }
    }

    @Override
    public String getDefinition(SymbolManager symMgr, PartInstance pi) 
    {
        if (defWritten ) {
            return "";
        }
        if (instanceSpecific) {
            String translatedEq = XyceASTUtil.getRightHandSideReadableShort(eq,
                new XyceRHSTranslator(symMgr, pi, functionArgs, false));
            return Xyceisms.defineFunction(eq.variable.name, pi.serialNumber, functionArgs, translatedEq);
        } else {
            String translatedEq = XyceASTUtil.getRightHandSideReadableShort(eq,
                    new XyceRHSTranslator(symMgr, firstInstance, functionArgs, false));
            defWritten = true;
            return Xyceisms.defineFunction(eq.variable.name, firstInstance.serialNumber, functionArgs, translatedEq);
        }
    }

    @Override
    public String getReference(int SN) 
    {
        // This method should not be used; translator code handles references
        // to functions itself, because the function arguments have to be translated also
        // and this method doesn't have access to the necessary symbols
        // But try to keep going anyway
        System.out.println("Warning:  call to getReference for function defined by pe " + eq);
        return "";
    }

    public Set<String> getFunctionArgs()
    {
        return functionArgs;
    }
    
    public int getSN(int SN)
    {
        if (instanceSpecific) {
            return SN;
        }
        return firstInstance.serialNumber;
    }
}

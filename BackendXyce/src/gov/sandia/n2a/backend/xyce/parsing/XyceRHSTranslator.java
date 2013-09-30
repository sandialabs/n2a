/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.parsing;

import gov.sandia.n2a.backend.xyce.Xyceisms;
import gov.sandia.n2a.backend.xyce.network.CompartmentInstance;
import gov.sandia.n2a.backend.xyce.network.ConnectionInstance;
import gov.sandia.n2a.backend.xyce.network.NetworkGenerationException;
import gov.sandia.n2a.backend.xyce.network.PartInstance;
import gov.sandia.n2a.backend.xyce.symbol.FunctionSymbolDef;
import gov.sandia.n2a.backend.xyce.symbol.SymbolDef;
import gov.sandia.n2a.backend.xyce.symbol.SymbolManager;
import gov.sandia.n2a.eq.EquationLanguageConstants;
import gov.sandia.n2a.parsing.gen.ASTNodeBase;
import gov.sandia.n2a.parsing.gen.ASTNodeRenderer;
import gov.sandia.n2a.parsing.gen.ASTRenderingContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class XyceRHSTranslator implements ASTNodeRenderer
{
    public static final String REFPRE = "A.";
    public static final String REFPOST = "B.";

    private SymbolManager symMgr;
    private PartInstance pi;
    private Collection<String> exceptions;
    private boolean init;    // are we translating initial conditions, or dynamic equations?

    private XyceRHSTranslator preXlator, postXlator;

    public XyceRHSTranslator(
            SymbolManager symMgr, 
            PartInstance pi,
            Collection<String> exceptions,
            boolean init)
        {
            this.symMgr = symMgr;
            this.pi = pi;
            this.exceptions = exceptions;
            this.init = init;
            if (pi instanceof ConnectionInstance)
            {
                ConnectionInstance ci = (ConnectionInstance) this.pi;
                preXlator = new XyceRHSTranslator(symMgr, ci.A, exceptions, init);
                postXlator = new XyceRHSTranslator(symMgr, ci.B, exceptions, init);
            }
        }

    public String render(ASTNodeBase node, ASTRenderingContext context)
    {
        // start with variable name as it appears on RHS of equation
        String ret = change(node.getValue().toString());
        for(int a = 0; a < node.getCount(); a++) {
            ret += context.render (node.getChild (a));
            if(a != node.getCount() - 1) {
                ret += ", ";
            }
        }
        return ret;
    }

    public String change(String name)
    {
        // exceptions are strings we don't want to translate
        // e.g. parameters to a function - need to use name function knows it by,
        // not the name of a specific state variable
        if (!(exceptions==null) && exceptions.contains(name)) {
            return name;
        }

        if (name.startsWith(REFPRE)) {
            return preXlator.change(name.substring(REFPRE.length()));
        }
        if (name.startsWith(REFPOST)) {
            return postXlator.change(name.substring(REFPOST.length()));
        }
        // special variables don't get SymbolDefs; just translate directly
        if (name.equals(EquationLanguageConstants.$TIME)) {
            return "TIME";
        }
        if (name.equals(EquationLanguageConstants.$INDEX)) {
            return String.valueOf(pi.getPartSet().getIndex(pi));
        }
        if (name.equals(EquationLanguageConstants.$N)) {
            try {
                return String.valueOf(pi.getPartSet().getN());
            } catch (NetworkGenerationException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        if (name.equals(EquationLanguageConstants.$COORDS)) {
            CompartmentInstance ci = (CompartmentInstance) pi;
            double x = ci.xPosition;
            double y = ci.yPosition;
            return "[" + String.valueOf(x) + ", " + String.valueOf(y) + "]";
        }
        
        // finally, actual translation of some user-defined symbol!
        SymbolDef def = symMgr.getSymbolDef(name, pi, init);
        return change(def, name, pi.serialNumber);
    }

    public String change(SymbolDef def, String name, int SN)
    {
        if (def instanceof FunctionSymbolDef)
        {
            // need to know what argument(s) this function
            // takes, and translate those as well -
            // unless they're in the exception list, as for defining the function
            Set<String> args = ((FunctionSymbolDef) def).getFunctionArgs();
            List<String> newArgs = new ArrayList<String>();
            for (String arg : args) {
                newArgs.add(change(arg));
            }
            return Xyceisms.referenceFunction(name, newArgs, ((FunctionSymbolDef) def).getSN(SN));
        }
        else
        {
            return def.getReference(SN);
        }
    }
}

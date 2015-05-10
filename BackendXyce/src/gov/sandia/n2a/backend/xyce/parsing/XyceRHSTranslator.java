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
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.language.function.Uniform;
import gov.sandia.n2a.language.operator.Power;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class XyceRHSTranslator extends Renderer
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

    public boolean render (Operator op)
    {
        if (op instanceof AccessVariable)
        {
            AccessVariable av = (AccessVariable) op;
            result.append (change (av.name));
            return true;
        }
        if (op instanceof Uniform)
        {
            // TODO: check for unsupported parameters and issue warning
            result.append ("rand()");
            return true;
        }
        if (op instanceof Power)
        {
            Power p = (Power) op;
            result.append ("(");
            p.operand0.render (this);
            result.append (") ** (");
            p.operand1.render (this);
            result.append (")");
            return true;
        }
        return false;
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
        if (name.equals("$t")) {
            return "TIME";
        }
        if (name.equals("$index")) {
            return String.valueOf(pi.getPartSet().getIndex(pi));
        }
        if (name.equals("$n")) {
            try {
                return String.valueOf(pi.getPartSet().getN());
            } catch (NetworkGenerationException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        if (name.equals("$xyz")) {
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

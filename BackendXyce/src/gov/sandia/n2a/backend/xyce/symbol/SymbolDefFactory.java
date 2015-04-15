/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.symbol;

import gov.sandia.n2a.backend.xyce.XyceTranslationException;
import gov.sandia.n2a.backend.xyce.functions.XycePulseFunction;
import gov.sandia.n2a.backend.xyce.functions.XyceSineWaveFunction;
import gov.sandia.n2a.backend.xyce.network.NetworkGenerationException;
import gov.sandia.n2a.backend.xyce.network.PartInstance;
import gov.sandia.n2a.backend.xyce.network.PartSetInterface;
import gov.sandia.n2a.backend.xyce.parsing.LanguageUtil;
import gov.sandia.n2a.backend.xyce.parsing.XyceASTUtil;
import gov.sandia.n2a.backend.xyce.parsing.XyceRHSTranslator;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.language.EvaluationContext;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Scalar;

import java.util.Set;

public class SymbolDefFactory {

    public static SymbolDef getSymbolDef(EquationEntry eq, PartSetInterface partSet)
        throws XyceTranslationException
    {
        String varname = eq.variable.name;

        if (eq.variable.order > 1)
        {
            throw new XyceTranslationException("Support for higher order differential equations not implemented yet (" + eq + ")");
        }
        if (eq.variable.order == 1)
        {
            return new StateVar1SymbolDef(eq, partSet);
        }
        if (eq.toString().contains(new XycePulseFunction().name)) {
            return new XycePulseInputSymbolDef(eq, partSet);
        }
        if (eq.toString().contains(new XyceSineWaveFunction().name)) {
            return new SineWaveInputSymbolDef(eq, partSet);
        }
        // N2A pulse function not actually set up to use yet; not available as plugin extension point
//        if (eq.toString().contains(new Pulse().getName())) {
//            return new PulseSymbolDef(eq, partSet);
//        }
        if (eq.toString().contains(LanguageUtil.$TIME)) 
        {
            if (varname.startsWith(XyceRHSTranslator.REFPRE) ||
                varname.startsWith(XyceRHSTranslator.REFPOST)) {
                    throw new XyceTranslationException(
                        "Connections are not allowed to define state variables for their compartments");
            }
            return new StateVar0SymbolDef(eq, partSet);
        }
        if (LanguageUtil.isInitEq(eq))
        {
            return getICSymbolDef(eq, partSet);
        }
        // If it's not a diff eq or IC, try to evaluate equation to determine what it is:
        // constant value (param), input specification, or other which we'll treat as a Xyce function
        // Try to evaluate; save returned Object
        PartInstance pi = null;
        try {
            pi = partSet.getFirstApplicableInstance(eq, true);
        } catch (NetworkGenerationException e) {
            throw new XyceTranslationException("unable to determine first instance for " + eq);
        }
        EvaluationContext context = XyceASTUtil.getInstanceContext(eq, pi, false);
        Object evalResult = XyceASTUtil.tryEval(eq, context);
        if (evalResult == null)
        {
            // This equation can't be evaluated at this time, which may indicate problems,
            // or may just mean that the RHS expression depends on state variables,
            // in which case we can create a netlist .func for it.
            return tryFunctionDef(eq, context, pi);
        }
        else
        {
            if (evalResult instanceof Number)
            {
                return new ParamSymbolDef(eq, pi);
            }
            else
            {
                throw new XyceTranslationException("unrecognized evaluation result " + evalResult.getClass() +
                        " for " + eq.toString());
            }
        }
    }

    private static SymbolDef getICSymbolDef(EquationEntry eq, PartSetInterface partSet) 
            throws XyceTranslationException 
    {
        // try to evaluate; save returned Object
        PartInstance pi = null;
        try {
            pi = partSet.getFirstApplicableInstance(eq, true);
        } catch (NetworkGenerationException e) {
            throw new XyceTranslationException("unable to determine first instance for " + eq);
        }
        EvaluationContext context = XyceASTUtil.getInstanceContext(eq, pi, true);
        Type evalResult = eq.expression.eval (context);
        if (evalResult instanceof Scalar) return new ConstantICSymbolDef(eq, (Scalar) evalResult, pi);
        throw new XyceTranslationException ("unknown initial condition");
    }

	private static SymbolDef tryFunctionDef(EquationEntry eq, EvaluationContext context, PartInstance pi) 
            throws XyceTranslationException 
    {
        // TODO - try to determine whether everything in AST is defined already?
        // Determine what variables this function depends on,
        // and populate functionArgs as well
        // TODO - args could also be in the conditional expression
        Set<String> functionArgs = XyceASTUtil.getVariables(eq, context);
        if (functionArgs.size() == 0) {
            // if there aren't any arguments, something's wrong - this could have been a constant
            throw new XyceTranslationException("Trying to create .func for " + eq.variable.name +
                    " but there are no arguments");
        }
        return new FunctionSymbolDef(eq, functionArgs, pi);
    }
}

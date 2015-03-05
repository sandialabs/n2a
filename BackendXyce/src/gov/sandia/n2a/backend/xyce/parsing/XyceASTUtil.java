/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.parsing;

import gov.sandia.n2a.backend.xyce.XyceTranslationException;
import gov.sandia.n2a.backend.xyce.network.PartInstance;
import gov.sandia.n2a.backend.xyce.network.PartSetInterface;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.functions.EvaluationContext;
import gov.sandia.n2a.language.functions.EvaluationException;
import gov.sandia.n2a.language.functions.ExponentiationFunction;
import gov.sandia.n2a.language.functions.Function;
import gov.sandia.n2a.language.functions.UnknownFunction;
import gov.sandia.n2a.language.gen.ASTFunNode;
import gov.sandia.n2a.language.gen.ASTNodeBase;
import gov.sandia.n2a.language.gen.ASTNodeRenderer;
import gov.sandia.n2a.language.gen.ASTOpNode;
import gov.sandia.n2a.language.gen.ASTRenderingContext;
import gov.sandia.n2a.language.gen.ASTVarNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class XyceASTUtil {

	// TODO - much of this is not Xyce-specific; should be moved or named appropriately

    public static String getRightHandSideReadableShort(EquationEntry eq, ASTNodeRenderer xlator)
    {
        ASTRenderingContext context = getRenderingContext(xlator);
        return context.render (eq.expression);
    }

    public static String getReadableShort(ASTNodeBase subtree, ASTNodeRenderer xlator)
    {
        ASTRenderingContext context = getRenderingContext(xlator);
        return context.render (subtree);
    }

    public static ASTRenderingContext getRenderingContext(ASTNodeRenderer xlator)
    {
        ASTRenderingContext result = new ASTRenderingContext (true);
        result.add (ASTVarNode.class, xlator);

        // Add value overrider for changing ^ to **.
        result.add (ASTOpNode.class, new ASTNodeRenderer() {
            @Override
            public String render (ASTNodeBase node, ASTRenderingContext context) {
                if(node.getValue() instanceof ExponentiationFunction) {
                    return context.render (node.getChild (0)) + " ** " + context.render (node.getChild (1));
                }
                return node.render (context);
            }
        });

        // Add function translator - so far, just for uniform() -> rand()
        result.add (ASTFunNode.class, new XyceFunctionTranslator());

        return result;
    }

    public static EvaluationContext getEvalContext(EquationEntry eq, EquationSet eqSet)
            throws XyceTranslationException
    {
        return new EvaluationContext ();  // TODO: it may be necessary to attach EquationSet to the context. Check how callers use the result.
    }

    public static Set<String> getDependencies(EquationEntry eq)
    {
        Set<String> result = eq.expression.getVariables();
        result.remove(eq.variable.name);
        return result;
    }

    public static Set<String> getVariables(EquationEntry eq, EvaluationContext context)
    {
        Set<String> result = new HashSet<String>();
        Set<String> symbols = getDependencies(eq);
        for (String var : symbols)
        {
            // TODO: Need to clean up entire Xyce backend to use Variables explicitly, rather than Strings
            Variable v = eq.variable.container.find (new Variable (var));
            if (v != null  &&  ! v.hasAttribute ("constant"))
            {
                result.add(var);
            }
        }
        return result;
    }

    public static Object tryEval(EquationEntry eq, EvaluationContext context, int partIndex)
    {
        setContextIndex (eq.variable.container, context, partIndex);
        return tryEval(eq, context);
    }

    public static Object tryEval(EquationEntry eq, EvaluationContext context)
    {
        Object evalResult = null;
        try {
            evalResult = eq.expression.eval(context);
        }
        catch (EvaluationException Ex) {
            // I know some equations that I try to evaluate will not be evaluatable,
            // so an exception here isn't really surprising.  The point of this
            // not-handling print out is to help spot things that should be handled
            // better elsewhere.
            System.out.println("tryEval: caught exception " + Ex.toString() +
                    " on equation " + eq.toString());
        }
        return evalResult;
    }

    public static void setContextIndex(EquationSet s, EvaluationContext context, int index)
    {
        Variable v = s.find (new Variable (LanguageUtil.$INDEX));
        context.set (v, index);
    }

    public static Object evaluateEq(EquationEntry eq, EvaluationContext context, int partIndex)
    {
        setContextIndex (eq.variable.container, context, partIndex);
        return evaluateEq(eq, context);
    }

    // This method assumes that instance-specific values in context have already been set.
    public static Object evaluateEq(EquationEntry eq, EvaluationContext context)
    {
        Object evalResult = null;
        try {
            evalResult = eq.expression.eval(context);
        }
        catch (EvaluationException Ex) {
            throw new RuntimeException(Ex.toString());
        }
       return evalResult;
    }

    public static EvaluationContext getInstanceContext(EquationEntry eq, PartInstance pi, boolean init)
    {
        PartSetInterface pSet = pi.getPartSet();
        EvaluationContext context = new EvaluationContext ();
        pSet.setInstanceContext(context, pi, init);
        return context;
    }

    public static Object evalInstanceEq(EquationEntry eq, PartInstance pi, boolean init)
    {
        EvaluationContext context = XyceASTUtil.getInstanceContext(eq, pi, init);
        return evaluateEq(eq, context);
    }

    public static Object evalConditional(ASTNodeBase cond, PartInstance pi, boolean init)
    {
        // get context...
        PartSetInterface pSet = pi.getPartSet();
        EvaluationContext context = new EvaluationContext();
        pSet.setInstanceContext(context, pi, init);
        return cond.eval(context);
    }

    public static boolean hasUnknownFunction(EquationEntry eq)
    {
        return hasUnknownFunction(eq.expression);
    }

    public static boolean hasUnknownFunction(ASTNodeBase node)
    {
        boolean result = false;
        // check this node
        if (node instanceof ASTFunNode) {
            Function func = (Function) node.getValue();
            if (func instanceof UnknownFunction) {
                return true;
            }
        }
        // check children
        for (int i=0; i<node.getCount(); i++) {
            result = result && hasUnknownFunction(node.getChild(i));
        }
        return result;
    }
}

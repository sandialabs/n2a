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
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

import java.util.HashSet;
import java.util.Set;

public class XyceASTUtil {

	// TODO - much of this is not Xyce-specific; should be moved or named appropriately

    public static String getRightHandSideReadableShort(EquationEntry eq, Renderer xlator)
    {
        eq.expression.render (xlator);
        return xlator.result.toString ();
    }

    public static String getReadableShort(Operator subtree, Renderer xlator)
    {
        subtree.render (xlator);
        return xlator.result.toString ();
    }

    public static Instance getEvalContext(EquationEntry eq, EquationSet eqSet)
            throws XyceTranslationException
    {
        return new Instance ();
    }

    public static Set<String> getVariables (final EquationEntry eq, Instance context)
    {
        final Set<String> result = new HashSet<String>();
        Visitor visitor = new Visitor ()
        {
            public boolean visit (Operator op)
            {
                if (op instanceof AccessVariable)
                {
                    // TODO: Need to clean up entire Xyce backend to use Variables explicitly, rather than Strings
                    AccessVariable av = (AccessVariable) op;
                    if (! av.name.equals (eq.variable.name)  &&  av.reference != null  &&  av.reference.variable != null  &&  ! av.reference.variable.hasAttribute ("constant"))
                    {
                        result.add (av.name);
                    }
                }
                return true;
            }
        };
        eq.expression.visit (visitor);
        return result;
    }

    public static Object tryEval(EquationEntry eq, Instance context, int partIndex)
    {
        setContextIndex (eq.variable.container, context, partIndex);
        return tryEval(eq, context);
    }

    public static Object tryEval(EquationEntry eq, Instance context)
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

    public static void setContextIndex(EquationSet s, Instance context, int index)
    {
        Variable v = s.find (new Variable (LanguageUtil.$INDEX));
        context.set (v, new Scalar (index));
    }

    public static Object evaluateEq(EquationEntry eq, Instance context, int partIndex)
    {
        setContextIndex (eq.variable.container, context, partIndex);
        return eq.expression.eval (context);
    }

    public static Instance getInstanceContext(EquationEntry eq, PartInstance pi, boolean init)
    {
        PartSetInterface pSet = pi.getPartSet();
        Instance context = new Instance ();
        pSet.setInstanceContext(context, pi, init);
        return context;
    }

    public static Object evalInstanceEq(EquationEntry eq, PartInstance pi, boolean init)
    {
        Instance context = XyceASTUtil.getInstanceContext(eq, pi, init);
        return eq.expression.eval (context);
    }

    public static Object evalConditional(Operator tree, PartInstance pi, boolean init)
    {
        // get context...
        PartSetInterface pSet = pi.getPartSet();
        Instance context = new Instance();
        pSet.setInstanceContext(context, pi, init);
        return tree.eval(context);
    }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.parsing.functions;

import gov.sandia.n2a.parsing.SpecialVariables;
import gov.sandia.n2a.parsing.gen.ASTNodeBase;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

public class EvaluationContext {


    ////////////
    // FIELDS //
    ////////////

    private Map<String, ASTNodeBase> equationRoots = new HashMap<String, ASTNodeBase>();
    private Map<String, Object> evaluatedValues = new HashMap<String, Object>();
    private Stack<String> evalFrames = new Stack<String>();
    // TODO: Add an ASTNodeValueOverriderMap for evaluation similar to
    // how the ASTRenderingContext has one for rendering.

    private Map<String, Object> specialVariables = new HashMap<String, Object>();


    //////////////////
    // CONSTRUCTORS //
    //////////////////

    public EvaluationContext() {
        this(new ASTNodeBase[0]);
    }
    public EvaluationContext(ASTNodeBase[] initialEqs) {
        this(Arrays.asList(initialEqs));
    }
    public EvaluationContext(Iterable<ASTNodeBase> initialEqs) {
        Iterator<ASTNodeBase> it = initialEqs.iterator();
        while(it.hasNext()) {
            addEquation(it.next());
        }
        populateSpecialVariables();
    }
    private void populateSpecialVariables() {
        specialVariables.put(SpecialVariables.PI, Math.PI);
        specialVariables.put(SpecialVariables.E, Math.E);
    }

    // Ability to add more equations to context.
    public void addEquation(ASTNodeBase eq) {
        // Yes compound assignment, no single symbol, yes non-zero order, no include order.
        String varName = eq.getVariableName(true, false, true, false);
        if(varName != null) {
            equationRoots.put(varName, eq);
        }
    }


    //////////////////////
    // GET / SET VALUES //
    //////////////////////

    public Object getValueForVariable(String varName) throws EvaluationException {
        if(evaluatedValues.containsKey(varName)) {
            return evaluatedValues.get(varName);
        }
        // TODO: Think about whether to allow the redefinition of special variables.  Where to prevent?
        if(specialVariables.containsKey(varName)) {
            return specialVariables.get(varName);
        }
        ASTNodeBase eq = equationRoots.get(varName);
        if(eq != null) {
            if(evalFrames.contains(varName)) {
                throw new EvaluationException("Infinite recursion detected while evaluating variable '" + varName + "'.");
            }
            evalFrames.push(varName);
            try {
                return eq.eval(this);
            } finally {
                evalFrames.pop();
            }
        }
        // TODO: investigate does order matter?
        // Might need equations themselves in map as well.
        throw new EvaluationException("Could not locate the variable '" + varName + "'.");
    }

    public void setValueForVariable(String varName, Object value) throws EvaluationException {
        evaluatedValues.put(varName, value);
    }
}

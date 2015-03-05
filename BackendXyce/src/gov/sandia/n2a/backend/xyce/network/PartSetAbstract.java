/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.network;

import gov.sandia.n2a.backend.xyce.XyceTranslationException;
import gov.sandia.n2a.backend.xyce.parsing.LanguageUtil;
import gov.sandia.n2a.backend.xyce.parsing.XyceASTUtil;
import gov.sandia.n2a.backend.xyce.symbol.XyceDeviceSymbolDef;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Annotation;
import gov.sandia.n2a.language.functions.AdditionFunction;
import gov.sandia.n2a.language.functions.EvaluationContext;
import gov.sandia.n2a.language.parse.ASTNodeBase;
import gov.sandia.n2a.language.parse.ASTOpNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Random;
import java.util.Set;

public abstract class PartSetAbstract implements PartSetInterface
{
    String name;
    Random rng;

    protected EquationSet eqns;
    protected List<EquationEntry> initEqs;
    private long numInstances = -1;  // TODO - not set correctly for connections
    protected ArrayList<PartInstance> instances;
    public ArrayList<Integer> allSNs;
    public int firstSN;
    protected Map<EquationEntry, Integer> eqSNs;

    public EquationSet getEqSet()
    {
        return eqns;
    }

    // If this PartSet is a connection, or otherwise refers to other parts,
    // the list below will store the references to the other PartSets
    protected List<PartSetAbstract> relatedParts;

    protected void removeIgnoredEquations(EquationSet eqSet)
    {
        List<Variable> toRemove = new ArrayList<Variable>();
        for (Variable var : eqSet.variables) {
            NavigableSet<EquationEntry> eqns = var.equations;
            for (EquationEntry eq : eqns) {
                if (LanguageUtil.hasAnnotation(eq, XyceDeviceSymbolDef.IGNORE_TAG)) {
                    eqns.remove(eq);
                }
            }
            if (eqns.size()==0) {
                toRemove.add(var);
            }
        }
        for (Variable var : toRemove) {
            eqSet.variables.remove(var);
        }
    }

    protected void findInitEqs() throws NetworkGenerationException
    {
        // go through all of the PEs, checking annotations
        for (Variable var : eqns.variables)
        {
            // ignore layout/connection equations
            if (var.name.startsWith("$")) {
                continue;
            }
            for (EquationEntry eq : var.equations)
            {
                if (LanguageUtil.isInitEq(eq)) {
                    initEqs.add(eq);
                }
            }
        }
    }

    private boolean defaultAnnotations(EquationEntry eq)
    {
        // No conditions indicates a 'default' equation
        // Most of the time, that is...  Do I have to worry about the following comment from the old code?
        // If this is a xyce device, there could be a non-device equation that adds to
        // a device state variable.  In that case, we want to treat that equation as
        // the 'default' equation, and therefore exclude the device equation
        if (eq.conditional == null) {
            return true;
        }
        // A single $init annotation is also a 'default' equation
        if (eq.ifString.equals(LanguageUtil.ANN_INIT)) {
            return true;
        }
        return false;
    }

    /* (non-Javadoc)
	 * @see gov.sandia.n2a.backend.xyce.network.PartSetInterface#getName()
	 */
    @Override
	public String getName()
    {
        return name;
    }

    /* (non-Javadoc)
	 * @see gov.sandia.n2a.backend.xyce.network.PartSetInterface#getIndex(int)
	 */
    @Override
	public int getIndex(int SN) {
        return SN-firstSN;
    }

    /* (non-Javadoc)
	 * @see gov.sandia.n2a.backend.xyce.network.PartSetInterface#getInitEqs()
	 */
    @Override
	public List<EquationEntry> getInitEqs()
    {
        return initEqs;
    }

    /* (non-Javadoc)
	 * @see gov.sandia.n2a.backend.xyce.network.PartSetInterface#getRelatedPartSets()
	 */
    @Override
	public List<PartSetAbstract> getRelatedPartSets()
    {
        return relatedParts;
    }

    /* (non-Javadoc)
	 * @see gov.sandia.n2a.backend.xyce.network.PartSetInterface#addRelatedPart(gov.sandia.n2a.backend.xyce.network.PartSet)
	 */
    @Override
	public void addRelatedPart(PartSetAbstract part)
    {
        relatedParts.add(part);
    }

    /* (non-Javadoc)
	 * @see gov.sandia.n2a.backend.xyce.network.PartSetInterface#getN()
	 */
    @Override
	public long getN() throws NetworkGenerationException
    {
        if (numInstances == -1)
        {
            try {
                EquationEntry nEq = LanguageUtil.getNEq(eqns);
                if (nEq == null) {
                    numInstances = 1;
                }
                else {
                    EvaluationContext context = XyceASTUtil.getEvalContext(nEq, eqns);
                    Object evalResult = XyceASTUtil.evaluateEq(nEq, context);
                    if (evalResult==null) {
                        throw new NetworkGenerationException("cannot evaluate #instances equation " + nEq + " for " + getName());
                    }
                    if (evalResult instanceof Number) {
                        numInstances = ((Number)evalResult).longValue();
                    }
                    else {
                        throw new NetworkGenerationException("#instances equation does not evaluate to a number");
                    }
                }
            }
            catch (Exception ex) {
                throw new NetworkGenerationException("cannot evaluate #instances equation for " + getName(),
                        ex.getCause());
            }
        }
        return numInstances;
    }

    // get the map of equations specific to this instance - only one equation
    // per variable name
    // This is only for non-device equations!!
    /* (non-Javadoc)
	 * @see gov.sandia.n2a.backend.xyce.network.PartSetInterface#getEqns(gov.sandia.n2a.backend.xyce.network.PartInstance, boolean)
	 */
    @Override
	public Map<String,EquationEntry> getEqns(PartInstance pi, boolean initValues)
            throws NetworkGenerationException, XyceTranslationException
    {
        // TODO - can I cache whole equation sets?  or only individual equations?
        HashMap<String,EquationEntry> result = new HashMap<String,EquationEntry>();
        for (Variable var : eqns.variables)
        {
            result.put(var.name, getEquation(var, pi, initValues));
        }
        return result;
    }

    @Override
    public EquationEntry getEquation(String name, PartInstance pi, boolean init) 
            throws NetworkGenerationException, XyceTranslationException
    {
        // This assumes that it's appropriate to find the V' equation for name="V"
        Variable variable = eqns.find(new Variable (name));
        return getEquation(variable, pi, init);
    }

    @Override
	public EquationEntry getEquation(Variable var, PartInstance pi, boolean init)
            throws NetworkGenerationException, XyceTranslationException
    {
        // 'init' boolean interpreted to mean return the appropriate @init eq
        // if there is one, but if there's not, go ahead and return the best
        // equation for this index - may be needed for EvaluationContext.

        // possibilities:
        //   1 'default' equation - return that
        //   1 equation of the desired type (init or not init)
        //   multiple equations of the desired type - have to resolve @where
        // some error conditions:
        //  1 equation (of the desired type) with an @where that doesn't cover everything
        //  multiple equations for which @where is satisfied by the indexed instance
        // TODO - look for cached equations?  Am I caching these by index/init??
        NavigableSet<EquationEntry> varEqs = var.equations;
        if (varEqs == null || varEqs.isEmpty()) {
            throw new NetworkGenerationException("No equation for varname " + var.name);
        }
        if (varEqs.size()==1) {
            return varEqs.first();
        }

        List<EquationEntry> candidates = new ArrayList<EquationEntry>();
        for (EquationEntry eq : varEqs)
        {
            if (init == LanguageUtil.isInitEq(eq)) {
                candidates.add(eq);
            }
        }
        if (candidates.isEmpty()) {
            throw new NetworkGenerationException("no applicable equation for varname " + var.name +
                   " and instance SN " + pi.serialNumber);
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        return getMatch(candidates, pi, init);
     }

    @Override
	public Collection<EquationEntry> getEqsForEvaluation(EquationEntry target, PartInstance pi, boolean init)
            throws XyceTranslationException, NetworkGenerationException
    {
        Collection<EquationEntry> result = new ArrayList<EquationEntry>();
        if (target.variable.order!=0) {
            return result;   // target is diff eq; can't evaluate, so return empty list
        }
        result.add(target);
        Set<String> variables = XyceASTUtil.getDependencies(target);
        if (variables.size()>0)
        {
            for (String var : variables)
            {
                // skip $ variables; those values have to be set separately
                if (LanguageUtil.isSpecialVar(var)) {
                    continue;
                }
                EquationEntry eq = getEquation(eqns.find(new Variable(var)), pi, init);
                result.addAll(getEqsForEvaluation(eq, pi, init));
            }
        }
        return result;
    }

    @Override
	public EquationEntry getMatch(List<EquationEntry> candidates, PartInstance pi, boolean init)
            throws NetworkGenerationException, XyceTranslationException
    {
        EquationEntry defaultEq = null;
        for (EquationEntry eq : candidates)
        {
            // possibilities for each candidate:
            // could be the default equation
            // could have an @where, which we try to evaluate here - note that the idea is to change this
            // at some point to use more general conditional expressions
            // could have some other annotation, which this isn't set up to handle right now
            if (defaultAnnotations(eq))
            {
                if (defaultEq != null)
                {
                    throw new NetworkGenerationException("conflicting equations for " + eq.variable.name);
                }
                defaultEq = eq;
            }
            else if (!LanguageUtil.isInstanceSpecific(eq))
            {
                // Not an error, but print out an info message
                // code not currently set up to evaluate arbitrary annotations
                // TODO:  need to evaluate 'bare' conditional expressions eventually
//                System.out.println("PartSet::getMatch:  condition xyce backend can't handle " + eq);
            }
            else
            {
                if (conditionApplies(eq, pi, init)) {
                    // could still have conflicting equations if two or more apply to indexed instance
                    // not trying to catch that, just return a 'good' one
                    return eq;
                }
            }
        }
        if (defaultEq == null) {
            throw new NetworkGenerationException("no valid equation for part instance " + pi +
                    " out of candidates " + candidates);
        }
        return defaultEq;
    }

    private boolean conditionApplies(EquationEntry eq, PartInstance pi, boolean init)
            throws XyceTranslationException, NetworkGenerationException
    {
        ASTNodeBase tree = eq.conditional;
        Object evalResult = XyceASTUtil.evalConditional(tree, pi, init);
        if (evalResult == null) {
            throw new NetworkGenerationException("can't evaluate conditional " + tree.toString());
        }
        if (evalResult instanceof Boolean) {
            return (Boolean)evalResult;
        }
        if (evalResult instanceof Number) {
            return ((Number)evalResult).intValue() != 0;
        }
        throw new NetworkGenerationException("unexpected evaluation result for conditional " + tree.toString());
    }

    public void setValueForVariable (EvaluationContext context, String name, Object value)
    {
        Variable v = eqns.find (new Variable (name));
        // TODO: v may be null, in which case we should throw an exception. To minimize code changes at this time, we simply tolerate the NPE as our exception.
        context.set (v, value);
    }

    @Override
    public void setInstanceContext(EvaluationContext context, PartInstance pi, boolean init)
    {
        // TODO - this should set ALL special variables
        setValueForVariable (context, LanguageUtil.$N, numInstances);
        setValueForVariable (context, LanguageUtil.$INDEX, getIndex (pi));
        if (pi instanceof ConnectionInstance) {
            ConnectionInstance ci = (ConnectionInstance) pi;
            setConnectedContext(context, ci.A, ci.B);
        }
        if (pi instanceof CompartmentInstance) {
            CompartmentInstance ci = (CompartmentInstance) pi;
            setValueForVariable (context, LanguageUtil.$COORDS, ci.getPosition ());
        }
        // TODO:  handle init flag...
        // eventually, we want $init to be a boolean variable, which would have to be set
        // right now, we have @init as an annotation, handled above
    }

    protected void setConnectedContext(EvaluationContext context, CompartmentInstance piA,
            CompartmentInstance piB)
    {
        try
        {
            setValueForVariable (context, "A." + LanguageUtil.$N,      piA.getPartSet ().getN ());
            setValueForVariable (context, "B." + LanguageUtil.$N,      piB.getPartSet ().getN ());
            setValueForVariable (context, "A." + LanguageUtil.$INDEX,  piA.getPartSet ().getIndex (piA));
            setValueForVariable (context, "B." + LanguageUtil.$INDEX,  piB.getPartSet ().getIndex (piB));
            setValueForVariable (context, "A." + LanguageUtil.$COORDS, piA.getPosition ());
            setValueForVariable (context, "B." + LanguageUtil.$COORDS, piB.getPosition ());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /* (non-Javadoc)
	 * @see gov.sandia.n2a.backend.xyce.network.PartSetInterface#getInstances()
	 */
    @Override
	public List<PartInstance> getInstances() {
        return instances;
    }

    /* (non-Javadoc)
	 * @see gov.sandia.n2a.backend.xyce.network.PartSetInterface#getIndex(gov.sandia.n2a.backend.xyce.network.PartInstance)
	 */
    @Override
	public int getIndex(PartInstance pi)
    {
        return pi.serialNumber-firstSN;
    }

    public PartInstance getFirstInstance()
    {
        return instances.get(0);
    }

    @Override
    public PartInstance getFirstApplicableInstance(EquationEntry eq, boolean init)
            throws XyceTranslationException, NetworkGenerationException
    {
        if (!eqSNs.containsKey(eq)) {
            if (defaultAnnotations(eq)) {
                int index = 0;
                if (eq.variable.equations.size()>1) {
                    // We actually need to find out if there are other PEs for the same variable and order
                    // that are instance-specific, and if so,
                    // find the first instance that none of them apply to
                    List<EquationEntry> otherPEs = new ArrayList<EquationEntry>();
                    otherPEs.addAll(eq.variable.equations);
                    otherPEs.remove(eq);
                    if (otherPEs.isEmpty()) {
                        eqSNs.put(eq, firstSN);
                        return instances.get(0);
                    }
                    while (index<instances.size() && instanceCovered(instances.get(index), otherPEs, init)) {
                        index++;
                    }
                    if (index>=instances.size()) {
                        throw new XyceTranslationException("no applicable instance for " + eq);
                    }
                }
                eqSNs.put(eq,  instances.get(index).serialNumber);
            } else {
                for (PartInstance instance : instances) {
                    if (isApplicableInstance(eq, instance, init)) {
                        eqSNs.put(eq, instance.serialNumber);
                        break;
                    }
                }
            }
        }
        return instances.get(eqSNs.get(eq)-firstSN);
    }

    private boolean instanceCovered(PartInstance instance, List<EquationEntry> eqs, boolean init)
            throws XyceTranslationException, NetworkGenerationException
    {
        for (EquationEntry eq : eqs) {
            if (isApplicableInstance(eq, instance, init)) {
                return true;
            }
        }
        return false;
    }

    private boolean isApplicableInstance(EquationEntry eq, PartInstance instance, boolean init)
            throws XyceTranslationException, NetworkGenerationException
    {
        if (eq.conditional==null) {
            return true; 
        }
        if (conditionApplies(eq, instance, init)) {
            return true;
        }
        return false;
    }
}

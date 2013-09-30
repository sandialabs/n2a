/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.network;

import gov.sandia.n2a.backend.xyce.XyceTranslationException;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.PartMetadataMap;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.parsing.functions.EvaluationContext;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface PartSetInterface {

    public abstract String getName();

    public abstract int getIndex(int SN);

    public abstract List<EquationEntry> getInitEqs();

    public abstract List<PartSetAbstract> getRelatedPartSets();

    public abstract void addRelatedPart(PartSetAbstract part);

    public abstract long getN() throws NetworkGenerationException;

    // get the map of equations specific to this instance - only one equation
    // per variable name
    // This is only for non-device equations!!
    public abstract Map<String, EquationEntry> getEqns(PartInstance pi, boolean initValues) 
            throws NetworkGenerationException, XyceTranslationException;

    public abstract EquationEntry getEquation(String name, PartInstance pi, boolean init) 
            throws NetworkGenerationException, XyceTranslationException;

    public abstract EquationEntry getEquation(Variable var, PartInstance pi, boolean init) 
            throws NetworkGenerationException, XyceTranslationException;

    public abstract Collection<EquationEntry> getEqsForEvaluation(EquationEntry target, PartInstance pi, boolean init)
            throws XyceTranslationException, NetworkGenerationException;

    public abstract EquationEntry getMatch(List<EquationEntry> candidates, PartInstance pi, boolean init) 
            throws NetworkGenerationException, XyceTranslationException;

    public abstract void setInstanceContext(EvaluationContext context, PartInstance pi, boolean init);

    public abstract List<PartInstance> getInstances();

    public abstract int getIndex(PartInstance pi);

    public abstract PartInstance getFirstInstance();

    public abstract PartInstance getFirstApplicableInstance(EquationEntry eq, boolean init) 
            throws XyceTranslationException, NetworkGenerationException;

    public abstract EquationSet getEqSet();
}
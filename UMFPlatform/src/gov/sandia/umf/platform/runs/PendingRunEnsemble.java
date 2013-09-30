/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.runs;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ensemble.params.ParameterSet;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;

import java.util.List;

public class PendingRunEnsemble {

    private NDoc templateModel;  // Model type?
    private String label;
    private ExecutionEnv env;
    private Simulator sim;
    private ParameterSpecGroupSet groups;
    private List<ParameterSet> paramSets;
    private List<String> outputExpressions;

    public PendingRunEnsemble(NDoc templateModel, String label,
            ExecutionEnv env, Simulator sim, ParameterSpecGroupSet groups,
            List<String> outputExpressions) {
        this.templateModel = templateModel;
        this.label = label;
        this.env = env;
        this.sim = sim;
        this.groups = groups;
        this.outputExpressions = outputExpressions;
        paramSets = groups.generateAllSetsFromSpecs(false);
    }

    public List<ParameterSet> getParameterSets() {
        return paramSets;
    }

    public NDoc getTemplateModel() {
        return templateModel;
    }

    public String getLabel() {
        return label;
    }

    public ExecutionEnv getEnv() {
        return env;
    }

    public Simulator getSim() {
        return sim;
    }

    public ParameterSpecGroupSet getGroups() {
        return groups;
    }

    public List<String> getOutputExpressions() {
        return outputExpressions;
    }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ensemble.params.ParameterSet;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;

import java.util.ArrayList;
import java.util.List;

import replete.plugins.PluginManager;
import replete.xstream.XStreamWrapper;

public class RunEnsembleOrient implements RunEnsemble {
    private NDoc source;
    private List<NDoc> runDocs;
    private List<Run> runs;

    public RunEnsembleOrient(NDoc src) {
        source = src;
        runDocs = source.getAndSetValid("runs", new ArrayList<Run>(), List.class);
        runs = new ArrayList<Run>();
        for(NDoc runDoc : runDocs) {
            runs.add(new RunOrient(runDoc));
        }
    }

    /* 
     * 'groups' is for model and simulation parameters that the framework will manage
     * 'simGroups' is for model parameters that the Simulator will manage 
     */
    public RunEnsembleOrient(PlatformRecord modelCopy, String label,
            String environment, String simulator, ParameterSpecGroupSet groups,
            ParameterSpecGroupSet simGroups,
            List<String> outputExpressions) {
        // Create database record for run ensemble, and give it a reference to the model record.
        source = new NDoc("gov.sandia.umf.platform$RunEnsemble");
        source.set("templateModel", modelCopy.getSource());
        source.set("label", label);
        source.set("environment", environment);
        source.set("simulator", simulator);
        source.set("paramSpecs", XStreamWrapper.writeToString(groups));
        source.set("simParamSpecs", XStreamWrapper.writeToString(simGroups));
        source.set("outputExpressions", outputExpressions);
        // runCount is the total # of runs; frameworkRunCount will be less for
        // xyce simulations that include parameter sweeps 
        source.set("runCount", simGroups.size()==0? groups.getRunCount() :
            groups.getRunCount()*simGroups.getRunCount());
        source.set("frameworkRunCount", groups.getRunCount());
        source.save();
        source.dumpDebug("RunEnsembleOrient constructor");
        runDocs = new ArrayList<NDoc>();
        runs = new ArrayList<Run>();
    }

    @Override
    public String getLabel() {
        return source.get("label");
    }

    @Override
    public NDoc getSource() {
        return source;
    }

    @Override
    public NDoc getTemplateModelDoc() {
        return source.get("templateModel");
    }

    @Override
    public ExecutionEnv getEnvironment() {
        String envName =  source.get("environment");
        ExecutionEnv chosenEnv = null;
        for(ExecutionEnv env : ExecutionEnv.envs) {
            if(envName.equals(env.getNamedValue("name"))) {
                chosenEnv = env;
                break;
            }
        }
        if(chosenEnv == null) {
            // error - default env?
        }
        return chosenEnv;
    }

    @Override
    public Simulator getSimulator() {
        String simType = source.get("simulator");
        return (Simulator) PluginManager.getExtensionById(simType);
    }

    @Override
    public ParameterSpecGroupSet getGroups() {
        return (ParameterSpecGroupSet) XStreamWrapper.loadTargetFromString(
                ((String) source.get("paramSpecs")));
    }

    @Override
    public ParameterSpecGroupSet getSimHandledGroups() {
        return (ParameterSpecGroupSet) XStreamWrapper.loadTargetFromString(
                ((String) source.get("simParamSpecs")));
    }

    @Override
    public List<ParameterSet> getParameterSets() {
        return getGroups().generateAllSetsFromSpecs(false);
    }

    @Override
    public List<String> getOutputExprs() {
        return source.getAndSetValid("outputExpressions", new ArrayList<String>(), List.class);
    }

    @Override
    public long getTotalRunCount() {
        return ((Number) source.getAndSetValid("runCount", 0, Number.class)).longValue();
    }

    @Override
    public long getFrameworkRunCount() {
        return ((Number) source.getAndSetValid("frameworkRunCount", 0, Number.class)).longValue();
    }

    @Override
    public List<Run> getRuns() {
        return runs;
    }
    
    @Override
    public void addRun(Run run) {
        runs.add(run);
        runDocs.add(run.getSource());
        source.set("runs", runDocs);
        source.save();
    }
}

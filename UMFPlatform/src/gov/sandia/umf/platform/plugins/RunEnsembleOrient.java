/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.db.AppData;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.db.MVolatile;
import gov.sandia.umf.platform.ensemble.params.ParameterSet;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import replete.plugins.PluginManager;
import replete.xstream.XStreamWrapper;

public class RunEnsembleOrient implements RunEnsemble
{
    private MNode source;
    private List<MNode> runDocs;
    private List<Run> runs;

    public RunEnsembleOrient (MNode src)
    {
        source = src;
        for (MNode runDoc : source.childOrCreate ("runs"))
        {
            runDocs.add (runDoc);
            runs.add (new RunOrient (runDoc));
        }
    }

    /* 
     * 'groups' is for model and simulation parameters that the framework will manage
     * 'simGroups' is for model parameters that the Simulator will manage 
     */
    public RunEnsembleOrient (PlatformRecord modelCopy, String label,
            String environment, String simulator, ParameterSpecGroupSet groups,
            ParameterSpecGroupSet simGroups,
            List<String> outputExpressions)
    {
        // Create database record for run ensemble, and give it a reference to the model record.
        source = new MVolatile ();
        source.set (modelCopy.getSource().get (), "model");
        source.set (label,                        "label");
        source.set (environment,                  "environment");
        source.set (simulator,                    "simulator");
        source.set (XStreamWrapper.writeToString(groups),    "paramSpecs");
        source.set (XStreamWrapper.writeToString(simGroups), "simParamSpecs");
        source.set (outputExpressions,            "outputExpressions");
        // runCount is the total # of runs; frameworkRunCount will be less for
        // xyce simulations that include parameter sweeps 
        source.set (simGroups.size () == 0 ? groups.getRunCount () : groups.getRunCount ()*simGroups.getRunCount (), "runCount");
        source.set (groups.getRunCount(),         "frameworkRunCount");

        runDocs = new ArrayList<MNode> ();
        runs = new ArrayList<Run> ();
    }

    @Override
    public String getLabel() {
        return source.get("label");
    }

    @Override
    public MNode getSource() {
        return source;
    }

    @Override
    public MNode getTemplateModelDoc ()
    {
        return AppData.getInstance ().models.child (source.get ("model"));
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
    public List<String> getOutputExprs ()
    {
        List<String> result = new ArrayList<String> ();
        for (MNode e : source.childOrCreate ("outputExpressions"))
        {
            result.add (e.key ());  // assuming all "output expression" are unique
        }
        return result;
    }

    @Override
    public long getTotalRunCount ()
    {
        return source.getOrDefault (0, "runCount");
    }

    @Override
    public long getFrameworkRunCount ()
    {
        return source.getOrDefault (0, "frameworkRunCount");
    }

    @Override
    public List<Run> getRuns() {
        return runs;
    }
    
    @Override
    public void addRun (Run run)
    {
        runs.add (run);
        runDocs.add (run.getSource ());
        MNode runsNode = source.childOrCreate ("runs");
        runsNode.childOrCreate (String.valueOf (runsNode.length ())).merge (run.getSource ());
    }
}

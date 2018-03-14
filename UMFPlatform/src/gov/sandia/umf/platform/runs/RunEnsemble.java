/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.runs;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.execenvs.HostSystem;
import gov.sandia.n2a.parms.ParameterSet;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.plugins.PlatformRecord;

import java.util.ArrayList;
import java.util.List;

import replete.xstream.XStreamWrapper;

public class RunEnsemble
{
    private MNode source;
    private List<MNode> runDocs;
    private List<Run> runs;

    public RunEnsemble (MNode src)
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
    public RunEnsemble (PlatformRecord modelCopy, String label,
            String environment, String simulator, ParameterSpecGroupSet groups,
            ParameterSpecGroupSet simGroups,
            List<String> outputExpressions)
    {
        // Create database record for run ensemble, and give it a reference to the model record.
        source = new MVolatile ();
        source.set ("model",             modelCopy.getSource ().get ());
        source.set ("label",             label);
        source.set ("environment",       environment);
        source.set ("simulator",         simulator);
        source.set ("paramSpecs",        XStreamWrapper.writeToString (groups));
        source.set ("simParamSpecs",     XStreamWrapper.writeToString (simGroups));
        source.set ("outputExpressions", outputExpressions);
        // runCount is the total # of runs; frameworkRunCount will be less for
        // xyce simulations that include parameter sweeps 
        source.set ("runCount",          simGroups.size () == 0 ? groups.getRunCount () : groups.getRunCount ()*simGroups.getRunCount ());
        source.set ("frameworkRunCount", groups.getRunCount());

        runDocs = new ArrayList<MNode> ();
        runs = new ArrayList<Run> ();
    }

    public String getLabel() {
        return source.get("label");
    }

    public MNode getSource() {
        return source;
    }

    public MNode getTemplateModelDoc ()
    {
        return AppData.models.child (source.get ("model"));
    }

    public HostSystem getEnvironment() {
        String envName =  source.get("environment");
        HostSystem chosenEnv = null;
        for(HostSystem env : HostSystem.envs) {
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

    public Backend getSimulator() {
        String simType = source.get("simulator");
        return (Backend) PluginManager.getExtensionById(simType);
    }

    public ParameterSpecGroupSet getGroups() {
        return (ParameterSpecGroupSet) XStreamWrapper.loadTargetFromString(
                ((String) source.get("paramSpecs")));
    }

    public ParameterSpecGroupSet getSimHandledGroups() {
        return (ParameterSpecGroupSet) XStreamWrapper.loadTargetFromString(
                ((String) source.get("simParamSpecs")));
    }

    public List<ParameterSet> getParameterSets() {
        return getGroups().generateAllSetsFromSpecs(false);
    }

    public List<String> getOutputExprs ()
    {
        List<String> result = new ArrayList<String> ();
        for (MNode e : source.childOrCreate ("outputExpressions"))
        {
            result.add (e.key ());  // assuming all "output expression" are unique
        }
        return result;
    }

    public long getTotalRunCount ()
    {
        return source.getOrDefaultLong ("runCount", "0");
    }

    public long getFrameworkRunCount ()
    {
        return source.getOrDefaultLong ("frameworkRunCount", "0");
    }

    public List<Run> getRuns() {
        return runs;
    }
    
    public void addRun (Run run)
    {
        runs.add (run);
        runDocs.add (run.getSource ());
        MNode runsNode = source.childOrCreate ("runs");
        runsNode.childOrCreate (String.valueOf (runsNode.size ())).merge (run.getSource ());
    }
}

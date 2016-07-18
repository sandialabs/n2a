/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.umf.platform.UMF;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.RunOrient;
import gov.sandia.umf.platform.plugins.RunState;
import gov.sandia.umf.platform.plugins.Simulation;
import gov.sandia.umf.platform.ui.ensemble.domains.Parameter;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;

import java.io.File;
import java.util.Map.Entry;
import java.util.TreeMap;

public class InternalSimulation implements Simulation
{
    public TreeMap<String,String> metadata = new TreeMap<String, String> ();
    public InternalRunState runState;

    @Override
    public ParameterDomain getAllParameters ()
    {
        return null;
    }

    @Override
    public void setSelectedParameters (ParameterDomain domain)
    {
        for (Entry<Object, Parameter> p : domain.getParameterMap ().entrySet ())
        {
            String name  = p.getKey ().toString ();
            String value = p.getValue ().getDefaultValue ().toString ();
            metadata.put (name, value);
        }
    }

    @Override
    public RunState execute (Object run, ParameterSpecGroupSet groups, ExecutionEnv env) throws Exception
    {
        RunState result = prepare (run, groups, env);
        submit ();
        return result;
    }

    @Override
    public void submit () throws Exception
    {
        Runnable run = new Runnable ()
        {
            public void run ()
            {
                Euler simulator = new Euler (new Wrapper (runState.digestedModel), runState.jobDir);
                try
                {
                    simulator.run ();
                }
                catch (Exception e)
                {
                    simulator.err.println (e);
                    e.printStackTrace (simulator.err);
                }
            }
        };
        new Thread (run).start ();
    }

    @Override
    public boolean resourcesAvailable()
    {
        return true;
    }

    @Override
    public RunState prepare (Object run, ParameterSpecGroupSet groups, ExecutionEnv env) throws Exception
    {
        // from prepare method
        runState = new InternalRunState ();
        runState.model = ((RunOrient) run).getModel ();

        // Create file for final model
        runState.jobDir = env.createJobDir ();
        String sourceFileName = env.file (runState.jobDir, "model");

        EquationSet e = new EquationSet (runState.model);
        if (e.name.length () < 1) e.name = "Model";  // because the default is for top-level equation set to be anonymous

        // TODO: fix run ensembles to put metadata directly in a special derived part
        e.metadata.putAll (metadata);  // parameters pushed by run system override any we already have

        digestModel (e, runState.jobDir);
        runState.digestedModel = e;
        env.setFileContents (sourceFileName, e.flatList (false));

        return runState;
    }

    /**
        Utility function to enable other backends to use Internal to prepare static network structures.
        @return An Euler (simulator) object which contains the constructed network.
    **/
    public static Euler constructStaticNetwork (EquationSet e, String jobDir) throws Exception
    {
        digestModel (e, jobDir);
        return new Euler (new Wrapper (e), jobDir);
    }

    public static void digestModel (EquationSet e, String jobDir) throws Exception
    {
        // We need to set this first because certain analyses try to open files.
        if (jobDir.isEmpty ())
        {
            // Fall back: make paths relative to n2a data directory
            System.setProperty ("user.dir", UMF.getAppResourceDir ().getAbsolutePath ());
        }
        else
        {
            // Make paths relative to job directory
            System.setProperty ("user.dir", new File (jobDir).getAbsolutePath ());
        }

        e.flatten ();
        e.addSpecials ();  // $index, $init, $live, $n, $t, $t', $type
        e.fillIntegratedVariables ();
        e.findIntegrated ();
        e.resolveLHS ();
        e.resolveRHS ();
        e.determineTraceVariableName ();
        e.findConstants ();
        e.removeUnused ();  // especially get rid of unneeded $variables created by addSpecials()
        e.collectSplits ();
        e.findAccountableConnections ();
        e.findTemporary ();
        e.determineOrder ();
        e.findDerivative ();
        e.addAttribute ("global",      0, false, new String[] {"$max", "$min", "$k", "$n", "$radius"});
        e.addAttribute ("preexistent", 0, true,  new String[] {"$t'", "$t"});  // variables that are not stored because Instance.get/set intercepts them
        e.addAttribute ("readOnly",    0, true,  new String[] {"$t"});
        // We don't really need the "simulator" attribute, because it has no impact on the behavior of Internal
        e.makeConstantDtInitOnly ();
        e.findInitOnly ();
        e.findDeath ();
        e.setAttributesLive ();
        e.forceTemporaryStorageForSpecials ();
        e.determineTypes ();

        createBackendData (e);
        analyzeEvents (e);
        analyze (e);
        clearVariables (e);
    }

    public static void createBackendData (EquationSet s)
    {
        if (! (s.backendData instanceof InternalBackendData)) s.backendData = new InternalBackendData ();
        for (EquationSet p : s.parts) createBackendData (p);
    }

    public static void analyzeEvents (EquationSet s)
    {
        InternalBackendData bed = (InternalBackendData) s.backendData;
        bed.analyzeEvents (s);
        for (EquationSet p : s.parts) analyzeEvents (p);
    }

    public static void analyze (EquationSet s)
    {
        InternalBackendData bed = (InternalBackendData) s.backendData;
        bed.analyze (s);
        for (EquationSet p : s.parts) analyze (p);
        bed.analyzeLastT (s);
    }

    public static void clearVariables (EquationSet s)
    {
        for (EquationSet p : s.parts) clearVariables (p);
        for (Variable v : s.variables)
        {
            if (! v.hasAttribute ("constant")) v.type = v.type.clear ();  // So we can use these as backup when stored value is null.
        }
    }
}

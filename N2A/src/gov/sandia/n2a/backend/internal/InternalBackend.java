/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.umf.platform.UMF;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ensemble.params.specs.ParameterSpecification;
import gov.sandia.umf.platform.plugins.extpoints.Backend;
import gov.sandia.umf.platform.ui.ensemble.domains.Parameter;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class InternalBackend implements Backend
{
    @Override
    public String getName ()
    {
        return "Internal";
    }

    @Override
    public ParameterDomain getSimulatorParameters ()
    {
        ParameterDomain result = new ParameterDomain ();
        result.addParameter (new Parameter ("duration",            "1.0"  ));  // default is 1 second
        result.addParameter (new Parameter ("internal.integrator", "Euler"));  // alt is "RungeKutta"
        return result;
    }

    @Override
    public ParameterDomain getOutputVariables (MNode model)
    {
        return null;
    }

    @Override
    public boolean canHandleRunEnsembleParameter (MNode model, Object key, ParameterSpecification spec)
    {
        return false;
    }

    @Override
    public boolean canRunNow (MNode job)
    {
        return true;
    }

    @Override
    public void execute (MNode job) throws Exception
    {
        // Prepare
        final String jobDir = new File (job.get ()).getParent ();  // assumes the MNode "job" is really and MDoc. In any case, the value of the node should point to a file on disk where it is stored in a directory just for it.
        Files.createFile (Paths.get (jobDir, "started"));
        final EquationSet digestedModel = new EquationSet (job);
        digestModel (digestedModel, jobDir);

        // Submit
        Runnable run = new Runnable ()
        {
            public void run ()
            {
                Euler simulator = new Euler (new Wrapper (digestedModel), jobDir);
                try
                {
                    simulator.run ();  // Does not return until simulation is finished.
                    Files.copy (new ByteArrayInputStream ("success".getBytes ("UTF-8")), Paths.get (jobDir, "finished"));
                }
                catch (Exception e)
                {
                    simulator.err.println (e);
                    e.printStackTrace (simulator.err);

                    try
                    {
                        Files.copy (new ByteArrayInputStream ("failure".getBytes ("UTF-8")), Paths.get (jobDir, "finished"));
                    }
                    catch (Exception f)
                    {
                    }
                }
            }
        };
        new Thread (run).start ();
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

        e.resolveConnectionBindings ();
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

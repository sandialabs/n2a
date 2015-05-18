/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.RunOrient;
import gov.sandia.umf.platform.plugins.RunState;
import gov.sandia.umf.platform.plugins.Simulation;
import gov.sandia.umf.platform.ui.ensemble.domains.Parameter;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;

import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Iterator;
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
    public void submit () throws Exception
    {
        Euler simulator = new Euler ();
        Wrapper wrapper = new Wrapper (runState.digestedModel);
        simulator.enqueue (wrapper);
        wrapper.init (simulator);
        simulator.run ();

        /*
        Runnable run = new Runnable ()
        {
            public void run ()
            {
                try
                {
                    Euler simulator = new Euler ();
                    Wrapper wrapper = new Wrapper (runState.digestedModel);
                    simulator.enqueue (wrapper);
                    wrapper.init (simulator);
                    simulator.run ();
                }
                catch (Exception e)
                {
                    System.err.println (e);
                }
            }
        };
        new Thread (run).start ();
        */
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

        e.flatten ();
        e.addSpecials ();  // $dt, $index, $init, $live, $n, $t, $type
        e.fillIntegratedVariables ();
        e.findIntegrated ();
        e.resolveLHS ();
        e.resolveRHS ();
        e.findConstants ();
        e.removeUnused ();  // especially get rid of unneeded $variables created by addSpecials()
        e.collectSplits ();
        e.findAccountableConnections ();
        e.findTemporary ();
        e.determineOrder ();
        e.findDerivative ();
        e.addAttribute ("global",    0, false, new String[] {"$max", "$min", "$k", "$n", "$radius"});
        e.addAttribute ("simulator", 0, true,  new String[] {"$dt", "$t"});
        e.findInitOnly ();
        e.findDeath ();
        e.setAttributesLive ();
        e.determineTypes ();

        env.setFileContents (sourceFileName, e.flatList (false));

        createBackendData (e);
        analyze (e);
        prepareVariables (e);
        runState.digestedModel = e;

        return runState;
    }

    @Override
    public RunState execute (Object run, ParameterSpecGroupSet groups, ExecutionEnv env) throws Exception
    {
        RunState result = prepare (run, groups, env);
        submit ();
        return result;
    }

    public void createBackendData (EquationSet s)
    {
        if (! (s.backendData instanceof InternalBackendData)) s.backendData = new InternalBackendData ();
        for (EquationSet p : s.parts) createBackendData (p);
    }

    public void analyze (EquationSet s)
    {
        ((InternalBackendData) s.backendData).analyze (s);
        for (EquationSet p : s.parts) analyze (p);
    }

    public void prepareVariables (EquationSet s)
    {
        for (EquationSet p : s.parts) prepareVariables (p);

        for (Variable v : s.variables)
        {
            v.type.clear ();  // So we can use these as backup when stored value is null.

            // Plan: replace the resolution path with a set of objects that will make the process fast at runtime.
            // There are 3 ways to leave a part
            // 1) Ascend to its container
            // 2) Descend into a contained population -- need the index of population
            // 3) Go to a connected part -- need the index of the endpoint
            // For simplicity, we only store a single integer.
            // i < 0  -- select endpoint index -i-1
            // i == 0 -- ascend to container
            // i > 0  -- select population index i-1
            if (v != v.reference.variable)
            {
                LinkedList<Object> newResolution = new LinkedList<Object> ();
                EquationSet current = s;
                Iterator<Object> it = v.reference.resolution.iterator ();
                while (it.hasNext ())
                {
                    Object o = it.next ();
                    if (o instanceof EquationSet)  // We are following the containment hierarchy.
                    {
                        EquationSet next = (EquationSet) o;
                        if (next == current.container)  // ascend to our container
                        {
                            newResolution.add (0);
                        }
                        else  // descend into one of our contained populations
                        {
                            if (! it.hasNext ()  &&  v.reference.variable.hasAttribute ("global"))  // descend to the population object itself
                            {
                                int i = 1;
                                for (EquationSet p : current.parts)
                                {
                                    if (p == next)
                                    {
                                        newResolution.add (i);
                                        break;
                                    }
                                    i++;
                                }
                                if (i > current.parts.size ()) throw new EvaluationException ("Could not find population.");
                            }
                            else  // descend to an instance of the population.
                            {
                                throw new EvaluationException ("Can't reference into specific instance of a population.");
                            }
                        }
                        current = next;
                    }
                    else if (o instanceof Entry<?,?>)  // We are following a part reference (which means we are a connection)
                    {
                        int i = 1;
                        for (Entry<String,EquationSet> c : current.connectionBindings.entrySet ())
                        {
                            if (c.equals (o))
                            {
                                newResolution.add (-i);
                                break;
                            }
                            i++;
                        }
                        if (i > current.connectionBindings.size ()) throw new EvaluationException ("Could not find connection.");
                        current = (EquationSet) ((Entry<?,?>) o).getValue ();
                    }
                }
                v.reference.resolution = newResolution;
            }
        }
    }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.language.type.Text;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.RunOrient;
import gov.sandia.umf.platform.plugins.RunState;
import gov.sandia.umf.platform.plugins.Simulation;
import gov.sandia.umf.platform.ui.ensemble.domains.Parameter;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;

import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class InternalSimulation implements Simulation
{
    public TreeMap<String,String> metadata = new TreeMap<String, String> ();

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
        InternalRunState result = new InternalRunState ();
        result.model = ((RunOrient) run).getModel ();

        // Create file for final model
        result.jobDir = env.createJobDir ();
        String sourceFileName = env.file (result.jobDir, "model");

        EquationSet e = new EquationSet (result.model);
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
        return result;
    }

    @Override
    public RunState execute (Object run, ParameterSpecGroupSet groups, ExecutionEnv env) throws Exception
    {
        RunState result = prepare (run, groups, env);
        submit ();
        return result;
    }

    public void Analyze (EquationSet s)
    {
        // Sub-parts
        for (EquationSet p : s.parts) Analyze (p);

        // Analyze variables
        if (! (s.backendData instanceof BackendData)) s.backendData = new BackendData ();
        BackendData bed = (BackendData) s.backendData;

        System.out.println (s.name);
        for (Variable v : s.ordered)  // we want the sub-lists to be ordered correctly
        {
            System.out.println ("  " + v.nameString () + " " + v.attributeString ());

            if      (v.name.equals ("$type" )) bed.type  = v;
            else if (v.name.equals ("$init" )) bed.init  = v;
            else if (v.name.equals ("$live" )) bed.live  = v;
            else if (v.name.equals ("$t"    )) bed.t     = v;
            else if (v.name.equals ("$dt"   )) bed.dt    = v;
            else if (v.name.equals ("$index")) bed.index = v;
            else if (v.name.equals ("$n")  &&  v.order == 0) bed.n = v;

            if (v.hasAttribute ("global"))
            {
                if (! v.hasAny (new String[] {"constant", "accessor"}))
                {
                    boolean initOnly = v.hasAttribute ("initOnly");
                    if (! initOnly) bed.globalUpdate.add (v);
                    if (! v.hasAttribute ("reference"))
                    {
                        boolean temporary = v.hasAttribute ("temporary");
                        boolean unusedTemporary = temporary  &&  ! v.hasUsers;
                        if (! unusedTemporary) bed.globalInit.add (v);
                        if (! temporary)
                        {
                            if (! v.hasAny (new String [] {"preexistent", "dummy"}))
                            {
                                bed.globalMembers.add (v);
                            }

                            boolean external = false;
                            if (v.hasAttribute ("externalWrite"))
                            {
                                external = true;
                                bed.globalBufferedExternalWrite.add (v);
                            }
                            if (external  ||  (v.hasAttribute ("externalRead")  &&  v.equations.size () > 0  &&  ! initOnly))
                            {
                                external = true;
                                bed.globalBufferedExternal.add (v);
                            }
                            if (external  ||  v.hasAttribute ("cycle"))
                            {
                                bed.globalBuffered.add (v);
                                if (! external)
                                {
                                    bed.globalBufferedInternal.add (v);
                                    if (! initOnly) bed.globalBufferedInternalUpdate.add (v);
                                }
                            }
                        }
                    }
                }
            }
            else
            {
                if (! v.hasAny (new String[] {"constant", "accessor"}))
                {
                    boolean initOnly = v.hasAttribute ("initOnly");
                    if (! initOnly) bed.localUpdate.add (v);
                    if (! v.hasAttribute ("reference"))
                    {
                        boolean temporary = v.hasAttribute ("temporary");
                        boolean unusedTemporary = temporary  &&  ! v.hasUsers;
                        if (! unusedTemporary  &&  ! v.name.equals ("$index")) bed.localInit.add (v);
                        if (! temporary)
                        {
                            if (! v.hasAny (new String [] {"preexistent", "dummy"})) bed.localMembers.add (v);

                            boolean external = false;
                            if (v.hasAttribute ("externalWrite"))
                            {
                                external = true;
                                bed.localBufferedExternalWrite.add (v);
                            }
                            if (external  ||  (v.hasAttribute ("externalRead")  &&  v.equations.size () > 0  &&  ! initOnly))
                            {
                                external = true;
                                bed.localBufferedExternal.add (v);
                            }
                            if (external  ||  v.hasAttribute ("cycle"))
                            {
                                bed.localBuffered.add (v);
                                if (! external)
                                {
                                    bed.localBufferedInternal.add (v);
                                    if (! initOnly) bed.localBufferedInternalUpdate.add (v);
                                }
                            }
                        }
                    }
                }
            }
        }
        for (Variable v : s.variables)  // we need these to be in order by differential level, not by dependency
        {
            if (v.hasAttribute ("integrated"))  // Do we need to guard against reference, constant, transient?
            {
                v.derivative = s.variables.floor (new Variable (v.name, v.order + 1));  // cache our derivative, so we don't need to look it up repeatedly at runtime
                if (v.hasAttribute ("global"))
                {
                    bed.globalIntegrated.add (v);
                }
                else
                {
                    bed.localIntegrated.add (v);
                }
            }
        }

        if (s.connectionBindings != null)
        {
            bed.connectionTargets = new int[s.connectionBindings.size ()];
            int i = 0;
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                int j = 0;
                for (EquationSet t : s.parts)  // TODO: this assumes that all connection bindings are to peer populations under same part; need a more flexible way of locating target populations
                {
                    if (t == n.getValue ())
                    {
                        bed.connectionTargets[i] = j;
                        break;
                    }
                    j++;
                }
                i++;

                // TODO: create extra variables in the endpoint parts to count number of connections
                // TODO: the following should be a list of VariableReferences to the added variables
                String alias = n.getKey ();
                Variable       v = s.find (new Variable (alias + ".$max"));
                if (v == null) v = s.find (new Variable (alias + ".$min"));
                if (v != null) bed.accountableEndpoints.add (alias);
            }
        }

        // Set index on variables
        // Initially readIndex = writeIndex = 0, and readTemp = writeTemp = false
        //   Locals
        for (Variable v : bed.localMembers)
        {
            if (v.type instanceof Scalar  &&  v.reference.variable == v) v.readIndex = v.writeIndex = bed.countLocalFloat++;
            else                                                         v.readIndex = v.writeIndex = bed.countLocalType++;
        }
        for (Variable v : bed.localBufferedExternal)
        {
            if (v.type instanceof Scalar  &&  v.reference.variable == v) v.writeIndex = bed.countLocalFloat++;
            else                                                         v.writeIndex = bed.countLocalType++;
        }
        for (Variable v : bed.localBufferedInternal)
        {
            v.writeTemp = true;
            if (v.type instanceof Scalar  &&  v.reference.variable == v) v.writeIndex = bed.countLocalTempFloat++;
            else                                                         v.writeIndex = bed.countLocalTempType++;
        }
        //   Globals
        for (Variable v : bed.globalMembers)
        {
            if (v.type instanceof Scalar  &&  v.reference.variable == v) v.readIndex = v.writeIndex = bed.countGlobalFloat++;
            else                                                         v.readIndex = v.writeIndex = bed.countGlobalType++;
        }
        for (Variable v : bed.globalBufferedExternal)
        {
            if (v.type instanceof Scalar  &&  v.reference.variable == v) v.writeIndex = bed.countGlobalFloat++;
            else                                                         v.writeIndex = bed.countGlobalType++;
        }
        for (Variable v : bed.globalBufferedInternal)
        {
            v.writeTemp = true;
            if (v.type instanceof Scalar  &&  v.reference.variable == v) v.writeIndex = bed.countGlobalTempFloat++;
            else                                                         v.writeIndex = bed.countGlobalTempType++;
        }
        //   fully temporary values
        for (Variable v : s.ordered)
        {
            v.type.clear ();  // So we can use these as backup when stored value is null.
            if (! v.hasAttribute ("temporary")) continue;
            v.readTemp = v.writeTemp = true;
            if (v.hasAttribute ("global"))
            {
                if (v.type instanceof Scalar  &&  v.reference.variable == v) v.readIndex = v.writeIndex = bed.countGlobalTempFloat++;
                else                                                         v.readIndex = v.writeIndex = bed.countGlobalTempType++;
            }
            else
            {
                if (v.type instanceof Scalar  &&  v.reference.variable == v) v.readIndex = v.writeIndex = bed.countLocalTempFloat++;
                else                                                         v.readIndex = v.writeIndex = bed.countLocalTempType++;
            }
        }

        bed.countChangesWithoutN = s.lethalP  ||  s.lethalType  ||  s.canGrow (); 
    }

    public class BackendData
    {
        public List<Variable>          localUpdate                           = new ArrayList<Variable> ();  // updated during regular call to update()
        public List<Variable>          localInit                             = new ArrayList<Variable> ();  // set by init()
        public List<Variable>          localMembers                          = new ArrayList<Variable> ();  // stored inside the object
        public List<Variable>          localBuffered                         = new ArrayList<Variable> ();  // needs buffering (temporaries)
        public List<Variable>          localBufferedInternal                 = new ArrayList<Variable> ();  // subset of buffered that are due to dependencies strictly within the current equation-set
        public List<Variable>          localBufferedInternalUpdate           = new ArrayList<Variable> ();  // subset of buffered internal that can execute outside of init()
        public List<Variable>          localBufferedExternal                 = new ArrayList<Variable> ();  // subset of buffered that are due to some external access
        public List<Variable>          localBufferedExternalWrite            = new ArrayList<Variable> ();  // subset of external that are due to external write
        public List<Variable>          localIntegrated                       = new ArrayList<Variable> ();  // store the result of integration of some other variable (the derivative)
        public List<Variable>          globalUpdate                          = new ArrayList<Variable> ();
        public List<Variable>          globalInit                            = new ArrayList<Variable> ();
        public List<Variable>          globalMembers                         = new ArrayList<Variable> ();
        public List<Variable>          globalBuffered                        = new ArrayList<Variable> ();
        public List<Variable>          globalBufferedInternal                = new ArrayList<Variable> ();
        public List<Variable>          globalBufferedInternalUpdate          = new ArrayList<Variable> ();
        public List<Variable>          globalBufferedExternal                = new ArrayList<Variable> ();
        public List<Variable>          globalBufferedExternalWrite           = new ArrayList<Variable> ();
        public List<Variable>          globalIntegrated                      = new ArrayList<Variable> ();

        public Variable n;
        public Variable init;
        public Variable live;
        public Variable index;
        public Variable type;
        public Variable t;
        public Variable dt;
        public boolean countChangesWithoutN;

        public List<String> accountableEndpoints = new ArrayList<String> ();
        public int[] connectionTargets;

        public int countLocalTempFloat;
        public int countLocalTempType;
        public int countLocalFloat;
        public int countLocalType;
        public int countGlobalTempFloat;
        public int countGlobalTempType;
        public int countGlobalFloat;
        public int countGlobalType;
    }
}

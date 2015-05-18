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
import gov.sandia.n2a.language.type.Scalar;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class InternalBackendData
{
    public Object backendData;  ///< Other backends may use Internal as a preprocessor, and may need to store additional data not covered here.

    public List<Variable> localUpdate                  = new ArrayList<Variable> ();  // updated during regular call to update()
    public List<Variable> localInit                    = new ArrayList<Variable> ();  // set by init()
    public List<Variable> localMembers                 = new ArrayList<Variable> ();  // stored inside the object
    public List<Variable> localBuffered                = new ArrayList<Variable> ();  // needs buffering (temporaries)
    public List<Variable> localBufferedInternal        = new ArrayList<Variable> ();  // subset of buffered that are due to dependencies strictly within the current equation-set
    public List<Variable> localBufferedInternalUpdate  = new ArrayList<Variable> ();  // subset of buffered internal that can execute outside of init()
    public List<Variable> localBufferedExternal        = new ArrayList<Variable> ();  // subset of buffered that are due to some external access
    public List<Variable> localBufferedExternalWrite   = new ArrayList<Variable> ();  // subset of external that are due to external write
    public List<Variable> localIntegrated              = new ArrayList<Variable> ();  // store the result of integration of some other variable (the derivative)
    public List<Variable> localReference               = new ArrayList<Variable> ();  // variables that point to storage external to their part
    public List<Variable> globalUpdate                 = new ArrayList<Variable> ();
    public List<Variable> globalInit                   = new ArrayList<Variable> ();
    public List<Variable> globalMembers                = new ArrayList<Variable> ();
    public List<Variable> globalBuffered               = new ArrayList<Variable> ();
    public List<Variable> globalBufferedInternal       = new ArrayList<Variable> ();
    public List<Variable> globalBufferedInternalUpdate = new ArrayList<Variable> ();
    public List<Variable> globalBufferedExternal       = new ArrayList<Variable> ();
    public List<Variable> globalBufferedExternalWrite  = new ArrayList<Variable> ();
    public List<Variable> globalIntegrated             = new ArrayList<Variable> ();
    public List<Variable> globalReference              = new ArrayList<Variable> ();

    // The following arrays have exactly the same order as EquationSet.connectionBindings
    // This includes the $variables in the next group below.
    // In all cases, there may be null entries if they are not applicable.
    public int[]      connectionTargets;
    public Variable[] accountableEndpoints;  ///< These are structured as direct members of the endpoint. No resolution. Instead, we use the Connection.endpoint array.

    public Variable   dt;
    public Variable   index;
    public Variable   init;
    public Variable[] k;
    public Variable   live;
    public Variable[] max;
    public Variable[] min;
    public Variable   n;
    public Variable   p;
    public Variable[] radius;
    public Variable   t;
    public Variable   type;
    public Variable   xyz;

    public boolean populationChangesWithoutN;
    public int     populationIndex;  // in container.populations

    // Size of storage blocks to allocate for part.
    // This is made more complicated by the fact that not everything is a simple float.
    // Some variables must be stored as objects. Thus there are two kinds of blocks.
    // Then there's temp versus permanent and local versus global, for 8 variants.
    public int countLocalTempFloat;
    public int countLocalTempType;
    public int countLocalFloat;
    public int countLocalType;
    public int countGlobalTempFloat;
    public int countGlobalTempType;
    public int countGlobalFloat;
    public int countGlobalType;

    public class Conversion
    {
        // These two arrays are filled in parallel, such that index i in one matches i in the other.
        ArrayList<Variable> from = new ArrayList<Variable> ();
        ArrayList<Variable> to   = new ArrayList<Variable> ();
        int[] bindings;  // to index = bindings[from index]
    }
    public Map<EquationSet,Conversion> conversions = new TreeMap<EquationSet,Conversion> ();  // maps from new part type to appropriate conversion record

    public void analyze (EquationSet s)
    {
        System.out.println (s.name);
        for (Variable v : s.ordered)  // we want the sub-lists to be ordered correctly
        {
            String className = "null";
            if (v.type != null) className = v.type.getClass ().getSimpleName ();
            System.out.println ("  " + v.nameString () + " " + v.attributeString () + " " + className);

            if      (v.name.equals ("$dt"   )                  ) dt    = v;
            else if (v.name.equals ("$index")                  ) index = v;
            else if (v.name.equals ("$init" )                  ) init  = v;
            else if (v.name.equals ("$live" )                  ) live  = v;
            else if (v.name.equals ("$n"    )  &&  v.order == 0) n     = v;
            else if (v.name.equals ("$p"    )                  ) p     = v;
            else if (v.name.equals ("$t"    )                  ) t     = v;
            else if (v.name.equals ("$type" )                  ) type  = v;
            else if (v.name.equals ("$xyz"  )                  ) xyz   = v;

            if (v.hasAttribute ("global"))
            {
                v.global = true;
                if (! v.hasAny (new String[] {"constant", "accessor"}))
                {
                    boolean initOnly = v.hasAttribute ("initOnly");
                    if (! initOnly) globalUpdate.add (v);
                    if (v.hasAttribute ("reference"))
                    {
                        globalReference.add (v);
                    }
                    else
                    {
                        boolean temporary = v.hasAttribute ("temporary");
                        boolean unusedTemporary = temporary  &&  ! v.hasUsers;
                        if (! unusedTemporary) globalInit.add (v);
                        if (! temporary)
                        {
                            if (! v.hasAny (new String [] {"preexistent", "dummy"}))
                            {
                                globalMembers.add (v);
                            }

                            boolean external = false;
                            if (v.hasAttribute ("externalWrite"))
                            {
                                external = true;
                                globalBufferedExternalWrite.add (v);
                            }
                            if (external  ||  (v.hasAttribute ("externalRead")  &&  v.equations.size () > 0  &&  ! initOnly))
                            {
                                external = true;
                                globalBufferedExternal.add (v);
                            }
                            if (external  ||  v.hasAttribute ("cycle"))
                            {
                                globalBuffered.add (v);
                                if (! external)
                                {
                                    globalBufferedInternal.add (v);
                                    if (! initOnly) globalBufferedInternalUpdate.add (v);
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
                    if (! initOnly) localUpdate.add (v);
                    if (v.hasAttribute ("reference"))
                    {
                        localReference.add (v);
                    }
                    else
                    {
                        boolean temporary = v.hasAttribute ("temporary");
                        boolean unusedTemporary = temporary  &&  ! v.hasUsers;
                        if (! unusedTemporary  &&  ! v.name.equals ("$index")) localInit.add (v);
                        if (! temporary)
                        {
                            if (! v.hasAny (new String [] {"preexistent", "dummy"})) localMembers.add (v);

                            boolean external = false;
                            if (v.hasAttribute ("externalWrite"))
                            {
                                external = true;
                                localBufferedExternalWrite.add (v);
                            }
                            if (external  ||  (v.hasAttribute ("externalRead")  &&  v.equations.size () > 0  &&  ! initOnly))
                            {
                                external = true;
                                localBufferedExternal.add (v);
                            }
                            if (external  ||  v.hasAttribute ("cycle"))
                            {
                                localBuffered.add (v);
                                if (! external)
                                {
                                    localBufferedInternal.add (v);
                                    if (! initOnly) localBufferedInternalUpdate.add (v);
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
                    globalIntegrated.add (v);
                }
                else
                {
                    localIntegrated.add (v);
                }
            }
        }

        if (s.connectionBindings == null)  // compartment-specific stuff
        {
            populationIndex = 0;
            if (s.container != null  &&  s.container.parts != null)  // check for null specifically to guard against the Wrapper equation set (which is not fully constructed)
            {
                for (EquationSet p : s.container.parts)
                {
                    if (p == s) break;
                    populationIndex++;
                }
            }
        }
        else  // connection-specific stuff
        {
            int count = s.connectionBindings.size ();
            k      = new Variable[count];
            max    = new Variable[count];
            min    = new Variable[count];
            radius = new Variable[count];
            connectionTargets = new int[count];
            accountableEndpoints = new Variable[count];
            int i = 0;
            for (Entry<String, EquationSet> n : s.connectionBindings.entrySet ())
            {
                k     [i] = s.find (new Variable (n.getKey () + ".$k"     ));
                max   [i] = s.find (new Variable (n.getKey () + ".$max"   ));
                min   [i] = s.find (new Variable (n.getKey () + ".$min"   ));
                radius[i] = s.find (new Variable (n.getKey () + ".$radius"));

                int j = 0;
                for (EquationSet t : s.parts)  // TODO: this assumes that all connections to peer populations under same container; need a more flexible way of locating target populations
                {
                    if (t == n.getValue ())
                    {
                        connectionTargets[i] = j;
                        break;
                    }
                    j++;
                }

                String alias = n.getKey ();
                Variable       v = s.find (new Variable (alias + ".$max"));
                if (v == null) v = s.find (new Variable (alias + ".$min"));
                if (v != null)
                {
                    InternalBackendData endpointBed = (InternalBackendData) n.getValue ().backendData;
                    Variable ae = new Variable ("");  // identity doesn't matter at this point
                    ae.type = new Scalar (0);
                    ae.readIndex = ae.writeIndex = endpointBed.countLocalFloat++;
                    accountableEndpoints[i] = ae;
                }

                i++;
            }
        }

        // Set index on variables
        // Initially readIndex = writeIndex = 0, and readTemp = writeTemp = false
        //   Locals
        for (Variable v : localMembers)
        {
            if (v.type instanceof Scalar  &&  v.reference.variable == v) v.readIndex = v.writeIndex = countLocalFloat++;
            else                                                         v.readIndex = v.writeIndex = countLocalType++;
        }
        for (Variable v : localBufferedExternal)
        {
            if (v.type instanceof Scalar  &&  v.reference.variable == v) v.writeIndex = countLocalFloat++;
            else                                                         v.writeIndex = countLocalType++;
        }
        for (Variable v : localBufferedInternal)
        {
            v.writeTemp = true;
            if (v.type instanceof Scalar  &&  v.reference.variable == v) v.writeIndex = countLocalTempFloat++;
            else                                                         v.writeIndex = countLocalTempType++;
        }
        //   Globals
        for (Variable v : globalMembers)
        {
            if (v.type instanceof Scalar  &&  v.reference.variable == v) v.readIndex = v.writeIndex = countGlobalFloat++;
            else                                                         v.readIndex = v.writeIndex = countGlobalType++;
        }
        for (Variable v : globalBufferedExternal)
        {
            if (v.type instanceof Scalar  &&  v.reference.variable == v) v.writeIndex = countGlobalFloat++;
            else                                                         v.writeIndex = countGlobalType++;
        }
        for (Variable v : globalBufferedInternal)
        {
            v.writeTemp = true;
            if (v.type instanceof Scalar  &&  v.reference.variable == v) v.writeIndex = countGlobalTempFloat++;
            else                                                         v.writeIndex = countGlobalTempType++;
        }
        //   fully temporary values
        for (Variable v : s.variables)
        {
            if (! v.hasAttribute ("temporary")) continue;
            v.readTemp = v.writeTemp = true;
            if (v.hasAttribute ("global"))
            {
                if (v.type instanceof Scalar  &&  v.reference.variable == v) v.readIndex = v.writeIndex = countGlobalTempFloat++;
                else                                                         v.readIndex = v.writeIndex = countGlobalTempType++;
            }
            else
            {
                if (v.type instanceof Scalar  &&  v.reference.variable == v) v.readIndex = v.writeIndex = countLocalTempFloat++;
                else                                                         v.readIndex = v.writeIndex = countLocalTempType++;
            }
        }

        populationChangesWithoutN = s.lethalP  ||  s.lethalType  ||  s.canGrow (); 

        // Type conversions
        String [] forbiddenAttributes = new String [] {"global", "constant", "accessor", "reference", "temporary", "dummy", "preexistent"};
        for (EquationSet target : s.getConversionTargets ())
        {
            if (s.connectionBindings == null)
            {
                if (target.connectionBindings != null) throw new EvaluationException ("Can't change $type from compartment to connection.");
            }
            else
            {
                if (target.connectionBindings == null) throw new EvaluationException ("Can't change $type from connection to compartment.");
            }

            Conversion conversion = new Conversion ();
            conversions.put (target, conversion);

            // Match variables
            for (Variable v : target.variables)
            {
                if (v.name.equals ("$type")) continue;
                if (v.hasAny (forbiddenAttributes)) continue;
                Variable v2 = s.find (v);
                if (v2 != null  &&  v2.equals (v))
                {
                    conversion.to  .add (v);
                    conversion.from.add (v2);
                }
            }

            // Match connection bindings
            if (s.connectionBindings != null)  // Since we checked above, we know that target is also a connection.
            {
                conversion.bindings = new int[s.connectionBindings.size ()];
                int i = 0;
                for (Entry<String, EquationSet> c : s.connectionBindings.entrySet ())
                {
                    String name = c.getKey ();
                    conversion.bindings[i] = -1;
                    int j = 0;
                    for (Entry<String, EquationSet> d : target.connectionBindings.entrySet ())
                    {
                        if (name.equals (d.getKey ()))
                        {
                            conversion.bindings[i] = j;
                            break;
                        }
                        j++;
                    }
                    // Note: ALL bindings must match. There is no other mechanism for initializing the endpoints.
                    if (conversion.bindings[i] < 0) throw new EvaluationException ("Unfulfilled connection binding during $type change.");
                    i++;
                }
            }

            // TODO: Match populations?
            // Currently, any contained populations do not carry over to new instance. Instead, it must create them from scratch.
        }
    }
}

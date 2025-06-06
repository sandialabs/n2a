/*
Copyright 2018-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import gov.sandia.n2a.backend.internal.InternalBackendData;
import gov.sandia.n2a.backend.internal.InternalBackendData.EventSource;
import gov.sandia.n2a.backend.internal.InternalBackendData.EventTarget;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.language.function.Delay;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.eqset.EquationSet.ConnectionBinding;

public class BackendDataC
{
    public List<Variable>          localUpdate                           = new ArrayList<Variable> ();  // updated during regular call to update()
    public List<Variable>          localInit                             = new ArrayList<Variable> ();  // set by init()
    public List<Variable>          localMembers                          = new ArrayList<Variable> ();  // stored inside the object
    public List<Variable>          localBuffered                         = new ArrayList<Variable> ();  // needs buffering (temporaries)
    public List<Variable>          localBufferedInternalDerivative       = new ArrayList<Variable> ();  // subset of buffered internal (due to dependencies strictly within the current equation-set) that are derivatives or their dependencies
    public List<Variable>          localBufferedInternalUpdate           = new ArrayList<Variable> ();  // subset of buffered internal that can execute outside of init()
    public List<Variable>          localBufferedExternal                 = new ArrayList<Variable> ();  // subset of buffered that are due to some external access
    public List<Variable>          localBufferedExternalDerivative       = new ArrayList<Variable> ();  // subset of external that are derivatives
    public List<Variable>          localBufferedExternalWrite            = new ArrayList<Variable> ();  // subset of external that are due to external write
    public List<Variable>          localBufferedExternalWriteDerivative  = new ArrayList<Variable> ();  // subset of external write that are derivatives
    public List<Variable>          localIntegrated                       = new ArrayList<Variable> ();  // variables that have derivatives, and thus change their value via integration
    public List<Variable>          localDerivative                       = new ArrayList<Variable> ();  // variables that are derivatives of other variables
    public List<Variable>          localDerivativeUpdate                 = new ArrayList<Variable> ();  // every variable that must be calculated to update derivatives, including their dependencies
    public List<Variable>          localDerivativePreserve               = new ArrayList<Variable> ();  // subset of derivative update that must be restored after integration is done
    public List<VariableReference> localReference                        = new ArrayList<VariableReference> ();  // references to other equation sets which can die
    public List<Variable>          globalUpdate                          = new ArrayList<Variable> ();
    public List<Variable>          globalInit                            = new ArrayList<Variable> ();
    public List<Variable>          globalMembers                         = new ArrayList<Variable> ();
    public List<Variable>          globalBuffered                        = new ArrayList<Variable> ();
    public List<Variable>          globalBufferedInternalDerivative      = new ArrayList<Variable> ();
    public List<Variable>          globalBufferedInternalUpdate          = new ArrayList<Variable> ();
    public List<Variable>          globalBufferedExternal                = new ArrayList<Variable> ();
    public List<Variable>          globalBufferedExternalDerivative      = new ArrayList<Variable> ();
    public List<Variable>          globalBufferedExternalWrite           = new ArrayList<Variable> ();
    public List<Variable>          globalBufferedExternalWriteDerivative = new ArrayList<Variable> ();
    public List<Variable>          globalIntegrated                      = new ArrayList<Variable> ();
    public List<Variable>          globalDerivative                      = new ArrayList<Variable> ();
    public List<Variable>          globalDerivativeUpdate                = new ArrayList<Variable> ();
    public List<Variable>          globalDerivativePreserve              = new ArrayList<Variable> ();

    // Ready-to-use handles for common $variables
    public Variable index;
    public Variable live;
    public Variable n;
    public Variable p;
    public Variable t;
    public Variable dt;  // $t'
    public Variable type;
    public Variable xyz;

    public boolean copyDt;         // Must copy $t' from container during init. The local value could then vary during simulation, or it could be "initOnly".
    public boolean dtCanChange;    // $t' can change between integration cycles, such that the current value does not equal time since last integration. This is one precondition for lastT.
    public boolean lastT;          // Remember last integration time.
    public int     duplicate = -1; // Index of flag used to detect duplicate entries in queue.

    public boolean needGlobalDerivative;
    public boolean needGlobalPreserve;
    public boolean needGlobalCtor;
    public boolean needGlobalDtor;
    public boolean needGlobalClear;
    public boolean needGlobalInit;
    public boolean needGlobalIntegrate;
    public boolean needGlobalUpdate;
    public boolean needGlobalFinalize;
    public boolean needGlobalFinalizeN;  // population finalize() should return live status based on $n
    public boolean needGlobalUpdateDerivative;
    public boolean needGlobalFinalizeDerivative;
    public boolean needGlobalPath;  // need the path() function, which returns a unique string identifying the current instance

    public boolean needLocalDerivative;
    public boolean needLocalPreserve;
    public boolean needLocalCtor;
    public boolean needLocalDtor;
    public boolean needLocalClear;
    public boolean needLocalDie;
    public boolean needLocalInit;
    public boolean needLocalFlush;
    public boolean needLocalIntegrate;
    public boolean needLocalUpdate;
    public boolean needLocalFinalize;
    public boolean needLocalUpdateDerivative;
    public boolean needLocalFinalizeDerivative;
    public boolean needLocalEventDelay;
    public boolean needLocalPath;

    public String pathToContainer;
    public List<String> accountableEndpoints = new ArrayList<String> ();
    public boolean refcount;
    public boolean trackInstances;          // keep a list of instances
    public boolean hasProject;
    public boolean canGrow;                 // $type splits can increase the number of instances. This is the cached result of EquationSet.canGrow()
    public boolean canGrowOrDie;            // via $p or $type
    public boolean canResize;               // via $n
    public boolean nInitOnly;               // $n is "initOnly"; Can only be true when $n exists.
    public boolean singleton;               // $n=1
    public boolean trackN;                  // keep a count of current instances; different than trackInstances
    public double  poll = -1;               // For connections, how much time is allowed to check full set of latent connections. Zero means every cycle. Negative means don't poll.

    // See InternalBackendData for description of the "inactive" mechanism.
    public boolean populationCanBeInactive; // Indicates that part satisfies all the compile-time conditions for an inactive population.
    public boolean connectionCanBeInactive; // Indicates that part satisfies all the compile-time conditions for an inactive connection instance.

    public List<String> globalColumns = new ArrayList<String> ();
    public List<String> localColumns  = new ArrayList<String> ();

    public List<EventTarget> eventTargets    = new ArrayList<EventTarget> ();
    public List<EventSource> eventSources    = new ArrayList<EventSource> ();
    public List<Variable>    eventReferences = new ArrayList<Variable> ();
    public List<Delay>       delays          = new ArrayList<Delay> ();

    public int    localFlagCount;
    public String localFlagType = ""; // empty string indicates that flags are not required
    public int    liveFlag = -1;      // -1 means $live is not stored
    public int    newborn  = -1;

    public int    globalFlagCount;
    public String globalFlagType = "";
    public int    clearNew = -1;      // guard so that we only add population to clearNew queue once
    public int    inactive = -1;      // indicates that population was explicitly tested and found to be inactive

    public void analyzeEvents (EquationSet s)
    {
        InternalBackendData.analyzeEvents (s, eventTargets, eventReferences, delays);

        int eventIndex = 0;
        for (EventTarget et : eventTargets)
        {
            et.valueIndex = eventIndex;

            if (! et.trackOne  &&  et.edge != EventTarget.NONZERO)  // We have an auxiliary variable.
            {
                // Preemptively add this, because the main analyze() routine won't see it.
                // We put the aux in init so that it can pick up the initial value without a call
                // to eventTest(). Note that dependencies have been set when the aux was
                // created, so it will execute in the correct order with all the other init variables.
                localInit.add (et.track);
            }

            // Enforce only one event in a given cycle.
            // See InternalBackendData.analyzeEvents() for more detailed comments.
            if (   et.edge == EventTarget.NONZERO
                && (   et.sources.size () > 1
                    || et.sources.get (0).container == et.container  &&  (et.delay == 0  ||  et.delay == -2)))
            {
                et.timeIndex = eventIndex;
            }

            for (EventSource es : et.sources)
            {
                BackendDataC sourceBed = (BackendDataC) es.container.backendData;
                // Disambiguate multiple event sources that share same target equation set.
                // This can happen, for example, with an STDP synapse from a population to itself.
                // Each event source gets its own instance collection.
                // Ideally there would be a single instance collection for each target equation set.
                // However, these could potentially have different trigger conditions, which would require more careful processing.
                // Rather than resolve whether they can share the same trigger, we simply make multiple collections.
                for (EventSource es2 : sourceBed.eventSources) if (es2.target.container == s) es.monitorIndex++;
                sourceBed.eventSources.add (es);
            }

            eventIndex++;
        }
    }

    /**
        Determine if $t' should be stored. The alternative is to offer its value via an accessor.
        - If $t' is written to (at init, in updates, or by external parts), then it must be stored.
        - "initOnly" -- Special case of the above, where the value is only written once.
          By default, a part gets $t' from its container if it isn't otherwise specified.
        - "constant" -- If $t' is defined constant locally, that value will simply be
          substituted into generated code, so no special action is needed.
        - "accessor" -- If $t' is read but not written, then we can get its value from the
          closest container where the value is "constant", "initOnly" or "accessor".

        Noteworthy preconditions:
        * Constant $t' values are propagated from parent to child in EquationSet.findConstants().
        * These are left in place by EquationSet.removeUnused(). (Formerly, it collapsed them again.)
          This implies that dt is always non-null.
        * An empty equation list and no external writers is NOT marked as "initOnly" in EquationSet.findInitOnly().
    **/
    public void analyzeDt (EquationSet s)
    {
        dt = s.find (new Variable ("$t", 1));  // This should always exist.
        if (dt.equations.isEmpty ()  &&  ! dt.hasAttribute ("externalWrite"))  // Pure read. Since equations is empty, also not "constant".
        {
            // Strictly speaking, this should be "initOnly". $t' acquires its value from container,
            // then retains that value for the rest of the sim. By construction, container $t' is not constant.
            // (Otherwise, the constant would have been copied here, and equations would not be empty.)
            // However, if container is "initOnly" or "accessor", then we don't need to store the value
            // locally, just access it. We will add one tag:
            //   "accessor" -- The value can be obtained from container, every time it is needed.
            //   "initOnly" -- The value is obtained from container once and stored locally.
            dt.removeAttribute ("initOnly");  // Just to be sure.
            dt.removeAttribute ("accessor");
            if (s.container == null)  // Top-level model, so our container is Wrapper.
            {
                // Since we are the only source of change for Wrapper, it won't change.
                dt.addAttribute ("accessor");  // Get the default $t' generated by runtime code.
            }
            else
            {
                Variable pdt = ((BackendDataC) s.container.backendData).dt;
                if (pdt.hasAny ("initOnly", "accessor")) dt.addAttribute ("accessor");  // No need to check for pdt "constant", because in that case we'd already be "constant".
                else                                     dt.addAttribute ("initOnly");  // Since pdt is not "constant", we must assume it can change after our init cycle.
            }
        }
        else if (! dt.hasAny ("constant", "initOnly"))  // $t' gets written to in some form, beyond the init cycle.
        {
            // The case we're trying to trap is where the current value of $t' does not equal the amount of time
            // since last integration.
            // $t' could also change if we have a variable-step integrator. In that case, $t' would have to come from
            // the integrator rather than a stored or calculated value.
            dtCanChange = true;
        }

        copyDt =  dt.equations.isEmpty ()  &&  ! dt.hasAttribute ("accessor");  // Implicitly, also not "constant".
        if (! dt.equations.isEmpty ()  &&  ! dt.hasAny ("constant", "accessor")) dt.addAttribute ("externalWrite");  // Hack to force $t' check to be in finalize() rather than update().
    }

    public void analyze (EquationSet s)
    {
        boolean headless = AppData.properties.getBoolean ("headless");
        if (! headless) System.out.println (s.container == null ? s.name : s.prefix ());

        populationCanBeInactive =  s.container != null  &&  s.container.connectionBindings != null;
        connectionCanBeInactive =  s.connectionBindings != null;

        for (Variable v : s.ordered)  // we want the sub-lists to be ordered correctly
        {
            if (! headless)
            {
                String className = "null";
                if (v.type != null) className = v.type.getClass ().getSimpleName ();
                System.out.println ("  " + v.nameString () + " " + v.attributeString () + " " + className);
            }

            if      (v.name.equals ("$p"    )  &&  v.order == 0) p     = v;
            else if (v.name.equals ("$type" )                  ) type  = v;
            else if (v.name.equals ("$xyz"  )  &&  v.order == 0) xyz   = v;
            else if (v.name.equals ("$n"    )  &&  v.order == 0)
            {
                if (s.connectionBindings != null)
                {
                    // It is an error to explicitly define $n on a connection,
                    // which is the only way we can get to this point.
                    Backend.err.get ().println ("$n is not applicable to connections");
                    throw new Backend.AbortRun ();
                }

                n = v;
                nInitOnly = n.hasAttribute ("initOnly");
                if (nInitOnly) n.addAttribute ("preexistent");  // In this special case we will directly use the current population count.
            }
            else if (v.name.equals ("$index"))
            {
                index = v;
                continue;  // Don't let $index enter into any variable lists. Instead, always give it special treatment. In effect, it is a list of one.
            }
            else if (v.name.equals ("$live"))
            {
                live = v;
                continue;  // $live can never function as a regular variable because it is stored as a bit flag
            }
            else if (v.name.equals ("$t"))
            {
                if      (v.order == 0) t  = v;
                else if (v.order == 1) dt = v;
            }

            if (v.hasAny (new String[] {"constant", "accessor"})  &&  ! v.hasAll (new String[] {"constant", "reference"})) continue;

            boolean initOnly               = v.hasAttribute ("initOnly");
            boolean emptyCombiner          = v.isEmptyCombiner ();
            boolean updates                = ! initOnly  &&  v.equations.size () > 0  &&  ! emptyCombiner  &&  (v.derivative == null  ||  v.hasAttribute ("updates"));
            boolean temporary              = v.hasAttribute ("temporary");
            boolean unusedTemporary        = temporary  &&  ! v.hasUsers ();
            boolean derivativeOrDependency = v.hasAttribute ("derivativeOrDependency");
            boolean hasNonchildReferences  = InternalBackendData.hasNonchildReferences (s, v);

            if (v.hasAttribute ("global"))
            {
                if (hasNonchildReferences) populationCanBeInactive = false;
                if (updates  &&  ! unusedTemporary) globalUpdate.add (v);
                if (derivativeOrDependency) globalDerivativeUpdate.add (v);
                if (! unusedTemporary  &&  ! emptyCombiner) globalInit.add (v);
                if (! v.hasAttribute ("reference"))
                {
                    if (! temporary  &&  ! v.hasAttribute ("dummy"))
                    {
                        if (! v.hasAttribute ("preexistent"))
                        {
                            globalMembers.add (v);

                            // If v is merely a derivative, not used by anything but its own integral, then no reason to preserve it.
                            // check if v.usedBy contains any variable that is not v's integral
                            if (derivativeOrDependency  &&  v.derivative == null  &&  v.usedBy != null)
                            {
                                for (Object o : v.usedBy)
                                {
                                    if (o instanceof Variable  &&  ((Variable) o).derivative != v)
                                    {
                                        globalDerivativePreserve.add (v);
                                        break;
                                    }
                                }
                            }
                        }

                        boolean external = false;
                        if (! initOnly)
                        {
                            if (v.order > 0) globalDerivative.add (v);  // No need to guard against $t' being integrated (see local case below), since it is never a global variable.

                            // Sometimes we want to both integrate a value (say V' -> V) and add to it in the same cycle.
                            // The integration step has roughly the same effect as an external write.
                            if (v.hasAttribute ("externalWrite")  ||  v.assignment != Variable.REPLACE)
                            {
                                external = true;
                                globalBufferedExternalWrite.add (v);
                                if (derivativeOrDependency) globalBufferedExternalWriteDerivative.add (v);
                            }
                            if (external  ||  (v.hasAttribute ("externalRead")  &&  updates))
                            {
                                external = true;
                                globalBufferedExternal.add (v);
                                if (derivativeOrDependency) globalBufferedExternalDerivative.add (v);
                            }
                        }
                        if (external  ||  v.hasAttribute ("cycle"))
                        {
                            globalBuffered.add (v);
                            if (! external  &&  ! initOnly)
                            {
                                globalBufferedInternalUpdate.add (v);
                                if (derivativeOrDependency) globalBufferedInternalDerivative.add (v);
                            }
                        }
                    }
                }
            }
            else  // local
            {
                if (hasNonchildReferences) connectionCanBeInactive = false;
                if (updates  &&  ! unusedTemporary) localUpdate.add (v);
                if (derivativeOrDependency) localDerivativeUpdate.add (v);
                if (! unusedTemporary  &&  ! emptyCombiner  &&  v != type) localInit.add (v);
                if (v.hasAttribute ("reference"))
                {
                    if (v.reference.variable.container.canDie ()) localReference.add (v.reference);  // These are filtered for duplicates at code-generation time, just because it is easier to do there.
                }
                else
                {
                    if (! temporary  &&  ! v.hasAttribute ("dummy"))
                    {
                        if (! v.hasAttribute ("preexistent"))
                        {
                            localMembers.add (v);

                            if (derivativeOrDependency  &&  v.derivative == null  &&  v.usedBy != null)
                            {
                                for (Object o : v.usedBy)
                                {
                                    if (o instanceof Variable  &&  ((Variable) o).derivative != v)
                                    {
                                        localDerivativePreserve.add (v);
                                        break;
                                    }
                                }
                            }
                        }

                        boolean external = false;
                        if (! initOnly)
                        {
                            if (v.name.equals ("$t"))
                            {
                                if (v.order > 1) localDerivative.add (v);
                            }
                            else
                            {
                                if (v.order > 0) localDerivative.add (v);
                            }

                            if (v.hasAttribute ("externalWrite")  ||  v.assignment != Variable.REPLACE)
                            {
                                external = true;
                                localBufferedExternalWrite.add (v);
                                if (derivativeOrDependency) localBufferedExternalWriteDerivative.add (v);
                            }
                            if (external  ||  (v.hasAttribute ("externalRead")  &&  updates))
                            {
                                external = true;
                                localBufferedExternal.add (v);
                                if (derivativeOrDependency) localBufferedExternalDerivative.add (v);
                            }
                        }
                        if (external  ||  v.hasAttribute ("cycle"))
                        {
                            localBuffered.add (v);
                            if (! external  &&  ! initOnly)
                            {
                                localBufferedInternalUpdate.add (v);
                                if (derivativeOrDependency) localBufferedInternalDerivative.add (v);
                            }
                        }
                    }
                }
            }
        }
        for (Variable v : s.variables)  // we need these to be in order by differential level, not by dependency
        {
            if (v.derivative != null  &&  ! v.hasAny ("constant", "initOnly"))
            {
                if (v.hasAttribute ("global")) globalIntegrated.add (v);
                else                            localIntegrated.add (v);
            }
        }

        // Purge any lists that consist solely of temporaries, as they accomplish nothing.
        for (List<Variable> list : Arrays.asList (globalUpdate, globalDerivativeUpdate, globalInit, globalIntegrated, localUpdate, localDerivativeUpdate, localInit, localIntegrated))
        {
            boolean allTemporary = true;
            for (Variable v : list) if (! v.hasAttribute ("temporary")) allTemporary = false;
            if (allTemporary) list.clear ();
        }

        if (s.connectionBindings != null)
        {
            for (ConnectionBinding c : s.connectionBindings)
            {
                Variable       v = s.find (new Variable (c.alias + ".$max", -1));
                if (v == null) v = s.find (new Variable (c.alias + ".$min", -1));
                if (v != null) accountableEndpoints.add (c.alias);

                if (s.find (new Variable (c.alias + ".$project")) != null) hasProject = true;
            }

            // Polling
            if (p != null  &&  p.metadata != null)
            {
                String pollString = p.metadata.getOrDefault ("-1", "poll");  // Assumes that EquationSet.determinePoll() has been run, so poll flag has been cleared if polling is not needed.
                poll = new UnitValue (pollString).get ();
            }

            // Checks if connection instance can be inactive
            //   Must not have dynamics
            //   Should not specify $p -- Optimizing away an inactive connection is only useful when the connection is unconditional.
            if (! localUpdate.isEmpty ()  ||  ! localIntegrated.isEmpty ()  ||  p != null) connectionCanBeInactive = false;
            //   Target populations must not change.
            //   References to aliases must only come from descendants.
            if (connectionCanBeInactive)
            {
                for (ConnectionBinding cb : s.connectionBindings)
                {
                    BackendDataC bed = (BackendDataC) cb.endpoint.backendData;
                    if (bed.canGrowOrDie  ||  bed.canResize  ||  InternalBackendData.hasNonchildReferences (s, cb.variable))
                    {
                        connectionCanBeInactive = false;
                        break;
                    }
                }
            }
            //   All sub-populations must capable of being inactive.
            if (connectionCanBeInactive)
            {
                for (EquationSet p : s.parts)
                {
                    BackendDataC bed = (BackendDataC) p.backendData;
                    if (! bed.populationCanBeInactive)
                    {
                        connectionCanBeInactive = false;
                        break;
                    }
                }
            }
        }

        if (connectionCanBeInactive)
        {
            // Retroactively activate instance counting in child populations.
            for (EquationSet p : s.parts)
            {
                BackendDataC bed = (BackendDataC) p.backendData;
                // If trackN is already true, the following will already be true.
                // TODO: What if p is a singleton? Can that even happen?
                bed.trackN                        = true;
                bed.needGlobalCtor                = true;
                if (p.canDie ()) bed.needLocalDie = true;
                bed.needLocalInit                 = true;
            }
        }
        else
        {
            // We can't remove inactive populations because they are directly embedded in their container class.
            // We can only remove inactive connection instances. Thus, there is no point in even checking a
            // population unless the container is a connection that can be inactive. We go back and set
            // the population flag false once we determine that the container won't ever be an inactive connection.
            // Then code generation only needs to look at flags in the current part.
            for (EquationSet p : s.parts)
            {
                BackendDataC bed = (BackendDataC) p.backendData;
                bed.populationCanBeInactive = false;
            }
        }

        if (eventTargets.size () > 0)
        {
            for (EventTarget et : eventTargets)
            {
                if (et.delay < -1)
                {
                    needLocalEventDelay = true;
                    break;
                }
            }
        }

        boolean haveRingBuffers = false;
        for (Delay d : delays)
        {
            boolean constDepth   = dt.hasAttribute ("constant")  &&  d.operands[1] instanceof Constant;
            boolean constInitial = d.operands.length <= 2  ||  d.operands[2] instanceof Constant;
            if (constDepth  &&  constInitial)
            {
                d.depth = (int) Math.round (d.operands[1].getDouble () / dt.equations.first ().expression.getDouble ());
                if (d.depth < 1) d.depth = 1;
                haveRingBuffers = true;
            }
        }

        boolean canDie = s.canDie ();
        singleton      = s.isSingleton ();
        canResize      = globalMembers.contains (n);  // This search works even if n is null.
        refcount       = s.referenced  &&  (canDie  ||  canResize);
        trackN         = n != null  &&  ! singleton;  // Should always be true when canResize is true.
        trackInstances = s.connected > 0  ||  s.needInstanceTracking  ||  canResize;
        canGrow        = s.canGrow ();
        canGrowOrDie   = s.lethalP  ||  s.lethalType  ||  canGrow;
        boolean Euler  = s.getRoot ().metadata.getOrDefault ("Euler", "backend", "all", "integrator").equals ("Euler");

        if (! canResize  &&  canGrowOrDie  &&  n != null  &&  n.hasUsers ())
        {
            // This is a flaw in the analysis process that needs to be fixed.
            // See note in InternalBackendData for details.
            Backend.err.get ().println ("WARNING: $n can change (due to structural dynamics) but it was detected as a constant. Equations that depend on $n may give incorrect results.");
        }
        if (! globalUpdate.isEmpty ()  ||  ! globalIntegrated.isEmpty ()  ||  singleton  ||  canGrowOrDie  ||  canResize  &&  ! nInitOnly) populationCanBeInactive = false;

        localFlagCount = eventTargets.size ();
        if (live != null  &&  ! live.hasAny (new String[] {"constant", "accessor"})) liveFlag  = localFlagCount++;
        if (trackInstances  &&  s.connected > 0)                                     newborn   = localFlagCount++;
        if (eventTargets.size () > 0  &&  dtCanChange)                               duplicate = localFlagCount++;
        localFlagType = determineFlagType (localFlagCount, "local");

        globalFlagCount = 0;
        if (trackInstances  &&  s.connected > 0) clearNew = globalFlagCount++;
        if (populationCanBeInactive)             inactive = globalFlagCount++;
        globalFlagType = determineFlagType (globalFlagCount, "global");

        needGlobalDerivative         = ! Euler  &&  globalDerivative.size () > 0;
        needGlobalIntegrate          = globalIntegrated.size () > 0;
        needGlobalPreserve           = ! Euler  &&  (needGlobalIntegrate  ||  globalDerivativePreserve.size () > 0  ||  globalBufferedExternalWriteDerivative.size () > 0);
        needGlobalClear              =    ! singleton  &&  (trackN  ||  trackInstances  ||  index != null  ||  newborn >= 0)
                                       || ! globalFlagType.isEmpty ()
                                       || globalMembers.size () > 0;
        needGlobalDtor               = needGlobalPreserve  ||  needGlobalDerivative;
        needGlobalCtor               = needGlobalDtor  ||  needGlobalClear;
        needGlobalInit               =    globalInit.size () > 0
                                       || singleton
                                       || n != null
                                       || s.connectionBindings != null;
        needGlobalUpdate             = globalUpdate.size () > 0;
        needGlobalFinalizeN          = s.container == null  &&  (canResize  ||  canGrowOrDie);
        needGlobalFinalize           =    globalBufferedExternal.size () > 0
                                       || needGlobalFinalizeN
                                       || s.connectionBindings != null
                                       || (canResize  &&  (canGrowOrDie  ||  ! nInitOnly));
        needGlobalUpdateDerivative   = ! Euler  &&  globalDerivativeUpdate.size () > 0;
        needGlobalFinalizeDerivative = ! Euler  &&  globalBufferedExternalDerivative.size () > 0;

        // Created simplified localInit to check if init is needed.
        // This is only temporary, because the proper simplification should only be done after I/O operators have names generated.
        List<Variable> simplifiedLocalInit = new ArrayList<Variable> (localInit);
        s.simplify ("$init", simplifiedLocalInit);

        needLocalDerivative         = ! Euler  &&  localDerivative.size () > 0;
        needLocalIntegrate          = localIntegrated.size () > 0;
        needLocalPreserve           = ! Euler  &&  (needLocalIntegrate  ||  localDerivativePreserve.size () > 0  ||  localBufferedExternalWriteDerivative.size () > 0);
        needLocalClear              = s.accountableConnections != null  ||  refcount  ||  localMembers.size () > 0  ||  haveRingBuffers;
        needLocalDtor               = needLocalPreserve  ||  needLocalDerivative;
        needLocalCtor               = needLocalDtor  ||  needLocalClear  ||  s.parts.size () > 0;
        needLocalDie                =    canDie
                                      && (   liveFlag >= 0
                                          || accountableEndpoints.size () > 0
                                          || localReference.size () > 0
                                          || eventTargets.size () > 0);
        needLocalInit               =    localBufferedExternal.size () > 0
                                      || eventTargets.size () > 0
                                      || ! localFlagType.isEmpty ()
                                      || lastT
                                      || simplifiedLocalInit.size () > 0
                                      || trackN
                                      || accountableEndpoints.size () > 0
                                      || localReference.size () > 0
                                      || eventTargets.size () > 0
                                      || s.parts.size () > 0;
        needLocalFlush              =    eventTargets.size () > 0  &&  (canDie  ||  dtCanChange)  // The implication is that it can die or $t' change during an event (off main EventStep), but we don't actually test that specifically.
                                      || connectionCanBeInactive
                                      || canResize;
        needLocalUpdate             = localUpdate.size () > 0;
        needLocalFinalize           = localBufferedExternal.size () > 0  ||  type != null  ||  canDie;
        needLocalUpdateDerivative   = ! Euler  &&  localDerivativeUpdate.size () > 0;
        needLocalFinalizeDerivative = ! Euler  &&  localBufferedExternalDerivative.size () > 0;

        // Ensure that functions are emitted to update child populations.
        for (EquationSet p : s.parts)
        {
            BackendDataC pbed = (BackendDataC) p.backendData;
            if (pbed.needGlobalClear)              needLocalClear              = true;
            if (pbed.needGlobalInit)               needLocalInit               = true;
            if (pbed.needGlobalIntegrate)          needLocalIntegrate          = true;
            if (pbed.needGlobalUpdate)             needLocalUpdate             = true;
            if (pbed.needGlobalFinalize)           needLocalFinalize           = true;
            if (pbed.needGlobalUpdateDerivative)   needLocalUpdateDerivative   = true;
            if (pbed.needGlobalFinalizeDerivative) needLocalFinalizeDerivative = true;
            if (pbed.needGlobalPreserve)           needLocalPreserve           = true;
            if (pbed.needGlobalDerivative)         needLocalDerivative         = true;
        }

        lastT = needLocalIntegrate  &&  (dtCanChange  ||  eventTargets.size () > 0);
    }

    public static String determineFlagType (int flagCount, String category)
    {
        if (flagCount == 0 ) return "";
        if (flagCount <= 8 ) return "uint8_t";
        if (flagCount <= 16) return "uint16_t";
        if (flagCount <= 32) return "uint32_t";
        if (flagCount <= 64) return "uint64_t";
        Backend.err.get ().println ("ERROR: Too many " + category + " flags to fit in basic integer type");
        throw new Backend.AbortRun ();
    }

    /**
        @param s The equation set directly associated with this backend data.
    **/
    public void setGlobalNeedPath (EquationSet s)
    {
        EquationSet c = s.container;
        if (c == null) return;  // Don't set flag, because we know that path() will return "".
        needGlobalPath = true;
        setParentNeedPath (s);
    }

    public void setLocalNeedPath (EquationSet s)
    {
        EquationSet c = s.container;
        if (c == null  &&  s.isSingleton ()) return;  // Don't set flag, because we know that path() will return "".
        needLocalPath = true;
        setParentNeedPath (s);
    }

    public void setParentNeedPath (EquationSet s)
    {
        if (s.connectionBindings == null)
        {
            EquationSet c = s.container;
            if (c != null) ((BackendDataC) c.backendData).setLocalNeedPath (c);
        }
        else
        {
            for (ConnectionBinding c : s.connectionBindings) ((BackendDataC) c.endpoint.backendData).setLocalNeedPath (c.endpoint);
        }
    }

    public String getFlag (String flag, boolean global, int shift)
    {
        return flag + " & (" + (global ? globalFlagType : localFlagType) + ") 0x1" + RendererC.printShift (shift);
    }

    public String setFlag (String flag, boolean global, int shift)
    {
        return flag + " |= (" + (global ? globalFlagType : localFlagType) + ") 0x1" + RendererC.printShift (shift);
    }

    public String clearFlag (String flag, boolean global, int shift)
    {
        return flag + " &= ~((" + (global ? globalFlagType : localFlagType) + ") 0x1" + RendererC.printShift (shift) + ")";
    }

    public void dump ()
    {
        dumpVariableList ("localUpdate                    ", localUpdate);
        dumpVariableList ("localInit                      ", localInit);
        dumpVariableList ("localMembers                   ", localMembers);
        dumpVariableList ("localBufferedInternalDerivative", localBufferedInternalDerivative);
        dumpVariableList ("localBufferedInternalUpdate    ", localBufferedInternalUpdate);
        dumpVariableList ("localBufferedExternal          ", localBufferedExternal);
        dumpVariableList ("localBufferedExternalWrite     ", localBufferedExternalWrite);
        dumpVariableList ("localIntegrated                ", localIntegrated);

        dumpVariableList ("globalUpdate                    ", globalUpdate);
        dumpVariableList ("globalInit                      ", globalInit);
        dumpVariableList ("globalMembers                   ", globalMembers);
        dumpVariableList ("globalBufferedInternalDerivative", globalBufferedInternalDerivative);
        dumpVariableList ("globalBufferedInternalUpdate    ", globalBufferedInternalUpdate);
        dumpVariableList ("globalBufferedExternal          ", globalBufferedExternal);
        dumpVariableList ("globalBufferedExternalWrite     ", globalBufferedExternalWrite);
        dumpVariableList ("globalIntegrated                ", globalIntegrated);

        dumpReferenceSet ("localReference", localReference);

        System.out.println ("  eventReferences:");
        for (Variable v : eventReferences) System.out.println ("    " + v.nameString () + " in " + v.container.name);

        System.out.println ("  poll=" + poll);
        System.out.println ("  populationCanBeInactive=" + populationCanBeInactive);
        System.out.println ("  connectionCanBeInactive=" + connectionCanBeInactive);
    }

    public void dumpVariableList (String name, List<Variable> list)
    {
        System.out.print ("  " + name + ":");
        for (Variable v : list) System.out.print (" " + v.nameString ());
        System.out.println ();
    }

    public void dumpReferenceSet (String name, List<VariableReference> list)
    {
        System.out.println ("  " + name);
        for (VariableReference r : list)
        {
            System.out.println ("    " + r.variable.nameString () + " in " + r.variable.container.name + " " + r.resolution.size ());
            //for (Object o : r.resolution) System.out.println ("      " + o);
        }
    }
}

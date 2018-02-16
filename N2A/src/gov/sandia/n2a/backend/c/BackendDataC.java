/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import java.util.ArrayList;
import java.util.List;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.eqset.EquationSet.ConnectionBinding;

public class BackendDataC
{
    public List<Variable>          localUpdate                           = new ArrayList<Variable> ();  // updated during regular call to update()
    public List<Variable>          localInit                             = new ArrayList<Variable> ();  // set by init()
    public List<Variable>          localMembers                          = new ArrayList<Variable> ();  // stored inside the object
    public List<Variable>          localBuffered                         = new ArrayList<Variable> ();  // needs buffering (temporaries)
    public List<Variable>          localBufferedInternal                 = new ArrayList<Variable> ();  // subset of buffered that are due to dependencies strictly within the current equation-set
    public List<Variable>          localBufferedInternalDerivative       = new ArrayList<Variable> ();  // subset of buffered internal that are derivatives or their dependencies
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
    public List<Variable>          globalBufferedInternal                = new ArrayList<Variable> ();
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
    public Variable n;  // only non-null if $n is actually stored as a member
    public Variable p;
    public Variable type;
    public Variable xyz;

    public boolean needGlobalCtor;
    public boolean needGlobalDtor;
    public boolean needGlobalPreserve;
    public boolean needGlobalFinalize;
    public boolean needGlobalFinalizeN;  // population finalize() should return live status based on $n
    public boolean needGlobalPath;  // need the path() function, which returns a unique string identifying the current instance
    public boolean needLocalCtor;
    public boolean needLocalDtor;
    public boolean needLocalInit;
    public boolean needLocalPreserve;
    public boolean needLocalFinalize;
    public boolean needLocalPath;

    public String pathToContainer;
    public List<String> accountableEndpoints = new ArrayList<String> ();
    public boolean refcount;
    public boolean trackInstances;
    public boolean hasProjectFrom;
    public boolean hasProjectTo;
    public boolean canGrowOrDie;  // via $p or $type
    public boolean canResize;     // via $n
    public boolean needK;
    public boolean needMax;
    public boolean needMin;
    public boolean needRadius;

    public List<String> globalColumns = new ArrayList<String> ();
    public List<String> localColumns  = new ArrayList<String> ();

    public void analyze (final EquationSet s, final JobC job)
    {
        System.out.println (s.name);
        for (Variable v : s.ordered)  // we want the sub-lists to be ordered correctly
        {
            String className = "null";
            if (v.type != null) className = v.type.getClass ().getSimpleName ();
            System.out.println ("  " + v.nameString () + " " + v.attributeString () + " " + className);

            if      (v.name.equals ("$live" )                  ) live  = v;
            else if (v.name.equals ("$n"    )  &&  v.order == 0) n     = v;
            else if (v.name.equals ("$p"    )  &&  v.order == 0) p     = v;
            else if (v.name.equals ("$type" )                  ) type  = v;
            else if (v.name.equals ("$xyz"  )  &&  v.order == 0) xyz   = v;
            else if (v.name.equals ("$index"))
            {
                index = v;
                continue;  // Don't let $index enter into any variable lists. Instead, always give it special treatment. In effect, it is a list of one.
            }

            boolean global = v.hasAttribute ("global");
            if (global)
            {
                if (! v.hasAny (new String[] {"constant", "accessor"}))
                {
                    boolean initOnly               = v.hasAttribute ("initOnly");
                    boolean derivativeOrDependency = v.hasAttribute ("derivativeOrDependency");
                    if (! initOnly) globalUpdate.add (v);
                    if (derivativeOrDependency) globalDerivativeUpdate.add (v);
                    if (! v.hasAttribute ("reference"))
                    {
                        boolean temporary = v.hasAttribute ("temporary");
                        boolean unusedTemporary = temporary  &&  ! v.hasUsers ();  // here only to make the following line of code more clear
                        if (! unusedTemporary) globalInit.add (v);
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
                                if (v.name.equals ("$t"))
                                {
                                    if (v.order > 1) globalDerivative.add (v);
                                }
                                else
                                {
                                    if (v.order > 0) globalDerivative.add (v);
                                }

                                if (v.hasAttribute ("externalWrite"))
                                {
                                    external = true;
                                    globalBufferedExternalWrite.add (v);
                                    if (derivativeOrDependency) globalBufferedExternalWriteDerivative.add (v);
                                }
                                if (external  ||  (v.hasAttribute ("externalRead")  &&  v.equations.size () > 0))
                                {
                                    external = true;
                                    globalBufferedExternal.add (v);
                                    if (derivativeOrDependency) globalBufferedExternalDerivative.add (v);
                                }
                            }
                            if (external  ||  v.hasAttribute ("cycle"))
                            {
                                globalBuffered.add (v);
                                if (! external)
                                {
                                    globalBufferedInternal.add (v);
                                    if (! initOnly)
                                    {
                                        globalBufferedInternalUpdate.add (v);
                                        if (derivativeOrDependency) globalBufferedInternalDerivative.add (v);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else  // local
            {
                if (! v.hasAny (new String[] {"constant", "accessor"}))
                {
                    boolean initOnly               = v.hasAttribute ("initOnly");
                    boolean derivativeOrDependency = v.hasAttribute ("derivativeOrDependency");
                    if (! initOnly) localUpdate.add (v);  // TODO: ensure that initOnly temporary variables are handled correctly. IE: their value doesn't change after init, but they still need to be calculated on the fly.
                    if (derivativeOrDependency) localDerivativeUpdate.add (v);
                    if (v.hasAttribute ("reference"))
                    {
                        if (v.reference.variable.container.canDie ()) localReference.add (v.reference);
                    }
                    else
                    {
                        boolean temporary = v.hasAttribute ("temporary");
                        boolean unusedTemporary = temporary  &&  ! v.hasUsers ();
                        if (! unusedTemporary) localInit.add (v);
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
                                else  // any other variable
                                {
                                    if (v.order > 0) localDerivative.add (v);
                                }

                                if (v.hasAttribute ("externalWrite"))
                                {
                                    external = true;
                                    localBufferedExternalWrite.add (v);
                                    if (derivativeOrDependency) localBufferedExternalWriteDerivative.add (v);
                                }
                                if (external  ||  (v.hasAttribute ("externalRead")  &&  v.equations.size () > 0))
                                {
                                    external = true;
                                    localBufferedExternal.add (v);
                                    if (derivativeOrDependency) localBufferedExternalDerivative.add (v);
                                }
                            }
                            if (external  ||  v.hasAttribute ("cycle"))
                            {
                                localBuffered.add (v);
                                if (! external)
                                {
                                    localBufferedInternal.add (v);
                                    if (! initOnly)
                                    {
                                        localBufferedInternalUpdate.add (v);
                                        if (derivativeOrDependency) localBufferedInternalDerivative.add (v);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (Variable v : s.variables)  // we need these to be in order by differential level, not by dependency
        {
            if (v.derivative != null  &&  ! v.hasAny (new String[] {"constant", "initOnly"}))
            {
                if (v.hasAttribute ("global")) globalIntegrated.add (v);
                else                            localIntegrated.add (v);
            }
        }

        if (s.connectionBindings != null)
        {
            for (ConnectionBinding c : s.connectionBindings)
            {
                Variable       v = s.find (new Variable (c.alias + ".$max"));
                if (v == null) v = s.find (new Variable (c.alias + ".$min"));
                if (v != null) accountableEndpoints.add (c.alias);
            }
        }

        refcount = s.referenced  &&  s.canDie ();
        needGlobalPreserve =  globalIntegrated.size () > 0  ||  globalDerivativePreserve.size () > 0  ||  globalBufferedExternalWriteDerivative.size () > 0;
        needGlobalDtor = needGlobalPreserve  ||  globalDerivative.size () > 0;
        needGlobalCtor = needGlobalDtor  ||  index != null  ||  n != null;
        canResize = globalMembers.contains (n);  // Works correctly even if n is null.
        trackInstances = s.connected  ||  s.needInstanceTracking  ||  canResize;
        canGrowOrDie =  s.lethalP  ||  s.lethalType  ||  s.canGrow ();
        needGlobalFinalizeN =  s.container == null  &&  (canResize  ||  canGrowOrDie);
        needGlobalFinalize =  globalBufferedExternal.size () > 0  ||  needGlobalFinalizeN  ||  (canResize  &&  (canGrowOrDie  ||  ! n.hasAttribute ("initOnly")));

        if (s.connectionBindings != null)
        {
            for (ConnectionBinding c : s.connectionBindings)
            {
                Variable v = s.find (new Variable (c.alias + ".$k"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    needK = true;
                    break;
                }
            }

            for (ConnectionBinding c : s.connectionBindings)
            {
                Variable v = s.find (new Variable (c.alias + ".$max"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    needMax = true;
                    break;
                }
            }

            for (ConnectionBinding c : s.connectionBindings)
            {
                Variable v = s.find (new Variable (c.alias + ".$min"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    needMin = true;
                    break;
                }
            }

            for (ConnectionBinding c : s.connectionBindings)
            {
                Variable v = s.find (new Variable (c.alias + ".$radius"));
                EquationEntry e = null;
                if (v != null) e = v.equations.first ();
                if (e != null)
                {
                    needRadius = true;
                    break;
                }
            }
        }

        needLocalPreserve =  localIntegrated.size () > 0  ||  localDerivativePreserve.size () > 0  ||  localBufferedExternalWriteDerivative.size () > 0;
        needLocalDtor =  needLocalPreserve  ||  localDerivative.size () > 0;
        needLocalCtor =  needLocalDtor  ||  s.accountableConnections != null  ||  refcount;
        needLocalInit =  s.connectionBindings == null  ||  localInit.size () > 0  ||  accountableEndpoints.size () > 0;
        needLocalFinalize =  localBufferedExternal.size () > 0  ||  type != null  ||  s.canDie ();
        if (s.connectionBindings != null)
        {
            for (ConnectionBinding c : s.connectionBindings)
            {
                if (s.find (new Variable (c.alias + ".$projectFrom")) != null) hasProjectFrom = true;
                if (s.find (new Variable (c.alias + ".$projectTo"  )) != null) hasProjectTo   = true;
            }
        }
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
}

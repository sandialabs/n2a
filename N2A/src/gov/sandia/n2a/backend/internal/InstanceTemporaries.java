/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

/**
    Provides a set of temporary values for use within a single function.
    This handles formal temporaries (those with =: as an assignment), as well as buffering for cyclic dependencies.
    Note: buffered variables that are accessed by external equation sets ("externalRead" and "externalWrite") have two entries in the
    main table of values, since many different function invocations may access them before they are finalized.
**/
public class InstanceTemporaries extends Instance
{
    public Instance wrapped;
    public Simulator simulator;
    public InternalBackendData bed;

    /**
        For use by Internal simulator, where EquationSet.backendData is guaranteed to be InternalBackendData.
    **/
    public InstanceTemporaries (Instance wrapped, Simulator simulator)
    {
        this (wrapped, simulator, (InternalBackendData) wrapped.equations.backendData);
    }

    /**
        For use by other backends, which may have chained EquationSet.backendData.
    **/
    public InstanceTemporaries (Instance wrapped, Simulator simulator, InternalBackendData bed)
    {
        this.wrapped   = wrapped;
        this.simulator = simulator;
        this.bed       = bed;
        if (wrapped instanceof Population) allocate (bed.countGlobalTempFloat, bed.countGlobalTempObject);
        else                               allocate (bed.countLocalTempFloat,  bed.countLocalTempObject);
    }

    public Type get (VariableReference r)
    {
        if (r.index >= 0) return ((Instance) wrapped.valuesObject[r.index]).get (r.variable);
        return get (r.variable);
    }

    public Type get (Variable v)
    {
        if (v == bed.init  ||  v == bed.connect) return new Scalar (0);
        if (v == bed.t) return new Scalar (simulator.currentEvent.t);
        if (v == bed.dt)
        {
            if      (wrapped instanceof Part      ) return new Scalar (((Part) wrapped          ).event.dt);
            else if (wrapped instanceof Population) return new Scalar (((Part) wrapped.container).event.dt);
        }

        if (v.readTemp) return super.get (v);
        return               wrapped.get (v);
    }

    public void set (Variable v, Type value)
    {
        if (v.writeTemp) super.set (v, value);
        else           wrapped.set (v, value);
    }

    public Type getFinal (VariableReference r)
    {
        if (r.index >= 0) return ((Instance) wrapped.valuesObject[r.index]).getFinal (r.variable);
        return getFinal (r.variable);
    }

    public Type getFinal (Variable v)
    {
        if (v.writeTemp) return super.getFinal (v);
        return                wrapped.getFinal (v);
    }

    public void setFinal (Variable v, Type value)
    {
        if (v == bed.dt)
        {
            // $t' is a local variable, so this case only occurs for Parts, not Populations.
            simulator.move ((Part) wrapped, ((Scalar) value).value);
        }
        else
        {
            if (v.readTemp) super.setFinal (v, value);
            else          wrapped.setFinal (v, value);
        }
    }

    public String toString ()
    {
        return "temp:" + wrapped.toString ();
    }

    public String path ()
    {
        return wrapped.path ();
    }
}

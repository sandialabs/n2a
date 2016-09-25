/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
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
    public Scalar init;
    public InternalBackendData bed;

    /**
        For use by Internal simulator, where EquationSet.backendData is guaranteed to be InternalBackendData.
    **/
    public InstanceTemporaries (Instance wrapped, Simulator simulator, boolean init)
    {
        this (wrapped, simulator, init, (InternalBackendData) wrapped.equations.backendData);
    }

    /**
        For use by other backends, which may have chained EquationSet.backendData.
    **/
    public InstanceTemporaries (Instance wrapped, Simulator simulator, boolean init, InternalBackendData bed)
    {
        this.wrapped   = wrapped;
        this.simulator = simulator;
        this.init      = new Scalar (init ? 1 : 0);
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
        if (v == bed.init) return init;
        if (v == bed.t   ) return new Scalar (simulator.currentEvent.t);
        // By construction, there should never bet a get() request for $t' unless bed.storeDt is true.
        // In this case the following code will satisfy the request.

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
            simulator.move (wrapped, ((Scalar) value).value);
            if (! bed.storeDt) return;  // don't try to store $t' (below) unless necessary
        }

        if (v.readTemp) super.setFinal (v, value);
        else          wrapped.setFinal (v, value);
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

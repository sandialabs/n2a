/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

/**
    Provides a set of temporary values for use within a single function.
    This handles formal temporaries (those with := as an assignment), as well as buffering for cyclic dependencies.
    Note: buffered variables that are accessed by external equation sets ("externalRead" and "externalWrite") have two entries in the
    main table of values, since many different function invocations may access them before they are finalized.
**/
public class InstanceTemporaries extends Instance
{
    public Instance wrapped;
    public Euler simulator;
    public Scalar init;
    public InternalBackendData bed;

    public InstanceTemporaries (Instance wrapped, Euler simulator, boolean global, boolean init)
    {
        this.wrapped = wrapped;
        this.simulator = simulator;
        this.init = new Scalar (init ? 1 : 0);
        bed = (InternalBackendData) wrapped.equations.backendData;
        if (global) allocate (bed.countGlobalTempFloat, bed.countGlobalTempType);
        else        allocate (bed.countLocalTempFloat,  bed.countLocalTempType);
    }

    public Type get (Variable v)
    {
        if (v == bed.init) return init;
        if (v == bed.t   ) return simulator.t;
        if (v == bed.dt  ) return simulator.dt;

        if (v.readTemp) return super.get (v);
        return               wrapped.get (v);
    }

    public void set (Variable v, Type value)
    {
        if (v.writeTemp) super.set (v, value);
        else           wrapped.set (v, value);
    }

    public Type getFinal (Variable v)
    {
        if (v.writeTemp) return super.getFinal (v);
        return                wrapped.getFinal (v);
    }

    public void setFinal (Variable v, Type value)
    {
        if (v == bed.dt) simulator.move (((Scalar) value).value);
        else if (v.readTemp) super.setFinal (v, value);
        else               wrapped.setFinal (v, value);
    }
}

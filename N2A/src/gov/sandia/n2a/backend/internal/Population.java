/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

/**
    An Instance which contains the global variables for a given kind of part,
    and which manages the group of instances as a whole.
**/
public class Population extends Instance
{
    public Population (EquationSet equations, Part container)
    {
        this.equations = equations;
        this.container = container;
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        allocate (bed.countGlobalFloat, bed.countGlobalType);
    }

    public void init (Euler simulator)
    {
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator, true, true);
        for (Variable v : temp.bed.globalReference) resolve (v);
        for (Variable v : temp.bed.globalInit)
        {
            Type result = v.eval (temp);
            if (result != null) temp.set (v, result);
        }
        for (Variable v : temp.bed.globalBuffered)
        {
            temp.setFinal (v, temp.getFinal (v));
        }
    }

    public void integrate (Euler simulator)
    {
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator, true, false);
        for (Variable v : temp.bed.globalIntegrated)
        {
            double a  = ((Scalar) temp.get (v           )).value;
            double aa = ((Scalar) temp.get (v.derivative)).value;
            temp.setFinal (v, new Scalar (a + aa * simulator.dt.value));
        }
    }

    public void prepare ()
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        for (Variable v : bed.globalBufferedExternalWrite)
        {
            set (v, v.type);  // v.type should be pre-loaded with zero-equivalent values
        }
    }

    public void update (Euler simulator)
    {
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator, true, false);
        for (Variable v : temp.bed.globalUpdate)
        {
            Type result = v.eval (temp);
            if (result == null)  // no condition matched
            {
                if (v.readIndex != v.writeIndex) temp.set (v, temp.get (v));  // default action for buffered vars is to copy old value
                continue;
            }
            temp.set (v, result);
        }
        for (Variable v : temp.bed.globalBufferedInternalUpdate)
        {
            temp.setFinal (v, temp.getFinal (v));
        }
    }

    public boolean finish (Euler simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        for (Variable v : bed.globalBufferedExternal)
        {
            setFinal (v, getFinal (v));
        }
        return true;
    }
}

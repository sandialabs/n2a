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
    protected Population (EquationSet equations, Part container)
    {
        this.equations = equations;
        this.container = container;
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        allocate (bed.countGlobalFloat, bed.countGlobalType);
    }

    public void init (Euler simulator)
    {
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator, true);
        resolve (temp.bed.globalReference);
        for (Variable v : temp.bed.globalInit)
        {
            Type result = v.eval (temp);
            if (result != null  &&  v.writeIndex >= 0) temp.set (v, result);
        }
        for (Variable v : temp.bed.globalBuffered)
        {
            temp.setFinal (v, temp.getFinal (v));
        }
    }

    public void integrate (Euler simulator, double dt)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        for (Variable v : bed.globalIntegrated)
        {
            double a  = ((Scalar) get (v           )).value;
            double aa = ((Scalar) get (v.derivative)).value;
            setFinal (v, new Scalar (a + aa * dt));
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
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator, false);
        for (Variable v : temp.bed.globalUpdate)
        {
            Type result = v.eval (temp);
            if (result == null)  // no condition matched
            {
                if (v.reference.variable == v  &&  v.equations.size () > 0  &&  v.readIndex != v.writeIndex) temp.set (v, temp.get (v));
            }
            else if (v.reference.variable.writeIndex >= 0)  // ensure this is not a "dummy" variable
            {
                if (v.assignment == Variable.REPLACE)
                {
                    temp.set (v, result);
                }
                else
                {
                    // the rest of these require knowing the current value of the working result, which is most likely external buffered
                    Type current = temp.getFinal (v.reference);
                    if      (v.assignment == Variable.ADD)
                    {
                        temp.set (v, current.add (result));
                    }
                    else if (v.assignment == Variable.MULTIPLY)
                    {
                        temp.set (v, current.multiply (result));
                    }
                    else if (v.assignment == Variable.MAX)
                    {
                        if (((Scalar) result.GT (current)).value != 0) temp.set (v, result);
                    }
                    else if (v.assignment == Variable.MIN)
                    {
                        if (((Scalar) result.LT (current)).value != 0) temp.set (v, result);
                    }
                }
            }
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

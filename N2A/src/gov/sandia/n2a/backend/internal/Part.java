/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import java.util.ArrayList;

import gov.sandia.n2a.backend.internal.InternalBackendData.Conversion;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;

/**
    An Instance that is capable of holding sub-populations.
    Generally, this is any kind of Instance except a Population.
**/
public class Part extends Instance
{
    public Population[] populations;

    /// An empty constructor, specifically for use by Wrapper. If you're not Wrapper, don't use this!
    protected Part ()
    {
    }

    protected Part (EquationSet equations, Population container)
    {
        this.equations = equations;
        this.container = container;
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        allocate (bed.countLocalFloat, bed.countLocalType);
        if (equations.parts.size () > 0)
        {
            populations = new Population[equations.parts.size ()];
            int i = 0;
            for (EquationSet s : equations.parts)
            {
                populations[i++] = new Population (s, this);
            }
        }
    }

    public Type get (Variable v)
    {
        if (v.global) return container.get (v);  // forward global variables to our population object
        return super.get (v);
    }

    public void die ()
    {
        // set $live to false, if it is stored in this part
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (! bed.live.hasAny (new String[] {"constant", "accessor"}))
        {
            set (bed.live, new Scalar (0));
        }
    }

    public void init (Euler simulator)
    {
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator, false, true);

        for (Variable v : temp.bed.localReference) resolve (v);

        // $variables
        for (Variable v : temp.bed.localInit)
        {
            if (! v.name.startsWith ("$")) continue;
            Type result = v.eval (temp);
            if (result != null) temp.set (v, result);
        }
        for (Variable v : temp.bed.localBuffered)
        {
            if (! v.name.startsWith ("$")) continue;
            temp.setFinal (v, temp.getFinal (v));
        }

        // non-$variables
        for (Variable v : temp.bed.localInit)
        {
            if (v.name.startsWith ("$")) continue;
            Type result = v.eval (temp);
            if (result != null) temp.set (v, result);
        }
        for (Variable v : temp.bed.localBuffered)
        {
            if (v.name.startsWith ("$")) continue;
            temp.setFinal (v, temp.getFinal (v));
        }

        // Note: instance counting is handled directly by PopulationCompartment.add()

        if (populations != null) for (Population p : populations) p.init (simulator);
    }

    public void integrate (Euler simulator)
    {
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator, true, false);
        for (Variable v : temp.bed.localIntegrated)
        {
            double a  = ((Scalar) temp.get (v           )).value;
            double aa = ((Scalar) temp.get (v.derivative)).value;
            temp.setFinal (v, new Scalar (a + aa * simulator.dt.value));
        }

        if (populations != null) for (Population p : populations) p.integrate (simulator);
    }

    public void prepare ()
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        for (Variable v : bed.localBufferedExternalWrite)
        {
            set (v, v.type);  // v.type should be pre-loaded with zero-equivalent values
        }

        if (populations != null) for (Population p : populations) p.prepare ();
    }

    public void update (Euler simulator)
    {
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator, true, false);
        for (Variable v : temp.bed.localUpdate)
        {
            Type result = v.eval (temp);
            if (result == null)  // no condition matched
            {
                if (v.readIndex != v.writeIndex) temp.set (v, temp.get (v));  // default action for buffered vars is to copy old value
                continue;
            }
            temp.set (v, result);
        }
        for (Variable v : temp.bed.localBufferedInternalUpdate)
        {
            temp.setFinal (v, temp.getFinal (v));
        }

        if (populations != null) for (Population p : populations) p.update (simulator);
    }

    public boolean finish (Euler simulator)
    {
        // TODO: be sure to set $type to the position in the split for each new part. Make sure $type gets zeroed after one cycle
        if (populations != null) for (Population p : populations) p.finish (simulator);

        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (! bed.live.hasAny (new String[] {"constant", "accessor"}))  // $live is stored in this part
        {
            if (((Scalar) get (bed.live)).value == 0) return false;  // early-out if we are already dead, to avoid another call to die()
        }

        for (Variable v : bed.localBufferedExternal)
        {
            setFinal (v, getFinal (v));
        }

        if (bed.type != null)
        {
            int type = (int) ((Scalar) get (bed.type)).value;
            if (type > 0)
            {
                ArrayList<EquationSet> split = equations.splits.get (type - 1);
                if (split.size () > 1  ||  split.get (0) != equations)  // Make sure $type != me. Otherwise it's a null operation
                {
                    boolean used = false;  // indicates that this instance is one of the resulting parts
                    int countParts = split.size ();
                    for (int i = 0; i < countParts; i++)
                    {
                        EquationSet other = split.get (i);
                        Scalar splitPosition = new Scalar (i+1);
                        if (other == equations  &&  ! used)
                        {
                            used = true;
                            setFinal (bed.type, splitPosition);
                        }
                        else
                        {
                            Part p = convert (other);

                            simulator.enqueue (p);
                            p.init (simulator);  // accountable connections are updated here

                            // Copy over variables
                            Conversion conversion = bed.conversions.get (other);
                            int count = conversion.from.size ();
                            for (int v = 0; v < count; v++)
                            {
                                Variable from = conversion.from.get (v);
                                Variable to   = conversion.to  .get (v);
                                p.setFinal (to, get (from));
                            }

                            // Set $type to be our position in the split
                            InternalBackendData otherBed = (InternalBackendData) other.backendData;
                            p.setFinal (otherBed.type, splitPosition);
                        }
                    }
                    if (! used)
                    {
                        die ();
                        return false;
                    }
                }
            }
        }

        if (equations.lethalP)
        {
            double p;
            if (bed.p.hasAttribute ("temporary"))
            {
                InstanceTemporaries temp = new InstanceTemporaries (this, simulator, false, false);
                p = ((Scalar) bed.p.eval (temp)).value;
            }
            else
            {
                p = ((Scalar) get (bed.p)).value;
            }
            if (p == 0  ||  p < 1  &&  p < simulator.uniform.nextDouble ())
            {
                die ();
                return false;
            }
        }

        if (equations.lethalConnection)
        {
            int count = equations.connectionBindings.size ();
            for (int i = 0; i < count; i++)
            {
                Part endpoint = getPart (i);
                EquationSet other = endpoint.equations;
                InternalBackendData otherBed = (InternalBackendData) other.backendData;
                if (! otherBed.live.hasAttribute ("constant"))
                {
                    if (((Scalar) endpoint.get (otherBed.live)).value == 0)
                    {
                        die ();
                        return false;
                    }
                }
            }
        }

        if (equations.lethalContainer)
        {
            Part containerPart = (Part) container.container;
            InternalBackendData containerBed = (InternalBackendData) containerPart.equations.backendData;
            if (! containerBed.live.hasAttribute ("constant"))  // Should we also guard against null here?
            {
                if (((Scalar) containerPart.get (containerBed.live)).value == 0)
                {
                    die ();
                    return false;
                }
            }
        }

        return true;
    }

    /**
        Hack to allow testing of lethConnection at this level of the class hierarchy.
    **/
    public Part getPart (int i)
    {
        throw new EvaluationException ("Internal error: only Connections can hold references to other Parts.");
    }

    public Matrix getXYZ (Euler simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.xyz != null)
        {
            if (bed.xyz.hasAny (new String[] {"constant", "temporary"}))
            {
                InstanceTemporaries temp = new InstanceTemporaries (this, simulator, false, true);  // getXYZ() calls occur only during the init cycle, specifically when testing a connection
                return (Matrix) bed.xyz.eval (temp);
            }
            return (Matrix) get (bed.xyz);
        }
        return new Matrix (3, 1);  // default is ~[0,0,0]
    }

    /**
        Create a new part based on a different equation set (or perhaps even the same one)
        and copy over all matching variables. Places result directly onto sim queue.
    **/
    public Part convert (EquationSet other)
    {
        throw new EvaluationException ("Internal error: convert() must be implemented by a specific subclass of Part");
    }
}

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
                if (s.connectionBindings == null) populations[i++] = new PopulationCompartment (s, this);
                else                              populations[i++] = new PopulationConnection  (s, this);
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
        if (bed.liveStorage == InternalBackendData.LIVE_STORED)
        {
            set (bed.live, new Scalar (0));
        }
    }

    public void resolve ()
    {
        resolve (((InternalBackendData) equations.backendData).localReference);
    }

    /**
        Note: specifically for Parts, call resolve() before calling init(). This is to
        accommodate the connection process, which must probe values in a part (which
        may include references) before calling init().
    **/
    public void init (Euler simulator)
    {
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator, true);

        // $variables
        if (temp.bed.liveStorage == InternalBackendData.LIVE_STORED) set (temp.bed.live, new Scalar (1));  // force $live to be set before anything else
        for (Variable v : temp.bed.localInitSpecial)
        {
            Type result = v.eval (temp);
            if (result != null  &&  v.writeIndex >= 0) temp.set (v, result);
        }
        for (Variable v : temp.bed.localBufferedSpecial)
        {
            temp.setFinal (v, temp.getFinal (v));
        }

        // non-$variables
        for (Variable v : temp.bed.localInitRegular)
        {
            Type result = v.eval (temp);
            if (result != null  &&  v.writeIndex >= 0) temp.set (v, result);
        }
        for (Variable v : temp.bed.localBufferedRegular)
        {
            temp.setFinal (v, temp.getFinal (v));
        }

        // Note: instance counting is handled directly by PopulationCompartment.add()

        if (populations != null) for (Population p : populations) p.init (simulator);
    }

    public void integrate (Euler simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.localIntegrated.isEmpty ()  &&  populations == null) return;  // nothing to do

        double dt;
        if (bed.lastT == null) dt = ((EventStep) simulator.currentEvent).dt;
        else                   dt = simulator.currentEvent.t - ((Scalar) get (bed.lastT)).value;
        if (dt <= 0) return;  // nothing to do

        // Integrate variables
        for (Variable v : bed.localIntegrated)
        {
            double a  = ((Scalar) get (v           )).value;
            double aa = ((Scalar) get (v.derivative)).value;
            setFinal (v, new Scalar (a + aa * dt));
        }

        if (populations != null) for (Population p : populations) p.integrate (simulator, dt);
    }

    public void update (Euler simulator)
    {
        InstanceTemporaries temp = new InstanceTemporaries (this, simulator, false);
        for (Variable v : temp.bed.localUpdate)
        {
            Type result = v.eval (temp);
            if (result == null)  // no condition matched
            {
                // Note: $type is explicitly evaluated to 0 in Variable.eval(), so it never returns null, even when no conditions match.

                // If variable is buffered, then we must copy its value to ensure it gets copied back
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
        for (Variable v : temp.bed.localBufferedInternalUpdate)
        {
            temp.setFinal (v, temp.getFinal (v));
        }

        if (populations != null) for (Population p : populations) p.update (simulator);
    }

    public boolean finish (Euler simulator)
    {
        if (populations != null) for (Population p : populations) p.finish (simulator);

        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.liveStorage == InternalBackendData.LIVE_STORED)
        {
            if (((Scalar) get (bed.live)).value == 0) return false;  // early-out if we are already dead, to avoid another call to die()
        }

        if (bed.lastT != null) setFinal (bed.lastT, new Scalar (simulator.currentEvent.t));
        for (Variable v : bed.localBufferedExternal) setFinal (v, getFinal (v));
        for (Variable v : bed.localBufferedExternalWrite) set (v, v.type);  // v.type should be pre-loaded with zero-equivalent values

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

                            enqueue (p);
                            p.resolve ();
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
                InstanceTemporaries temp = new InstanceTemporaries (this, simulator, false);
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
                if (! getPart (i).getLive ())
                {
                    die ();
                    return false;
                }
            }
        }

        if (equations.lethalContainer)
        {
            if (! ((Part) container.container).getLive ())
            {
                die ();
                return false;
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

    public boolean getLive ()
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.liveStorage == InternalBackendData.LIVE_CONSTANT) return true;  // constant implies always live
        if (bed.liveStorage == InternalBackendData.LIVE_STORED)
        {
            if (((Scalar) get (bed.live)).value == 0) return false;
        }

        if (equations.lethalConnection)
        {
            int count = equations.connectionBindings.size ();
            for (int i = 0; i < count; i++)
            {
                if (! getPart (i).getLive ())
                {
                    die ();
                    return false;
                }
            }
        }

        if (equations.lethalContainer)
        {
            if (! ((Part) container.container).getLive ())
            {
                die ();
                return false;
            }
        }

        return true;
    }

    public Matrix getXYZ (Euler simulator)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        if (bed.xyz != null)
        {
            if (bed.xyz.hasAny (new String[] {"constant", "temporary"}))
            {
                InstanceTemporaries temp = new InstanceTemporaries (this, simulator, true);  // getXYZ() calls occur only during the init cycle, specifically when testing a connection
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

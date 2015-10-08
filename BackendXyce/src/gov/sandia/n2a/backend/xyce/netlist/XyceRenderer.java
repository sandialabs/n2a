/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.netlist;

import gov.sandia.n2a.backend.internal.Euler;
import gov.sandia.n2a.backend.xyce.XyceBackendData;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.language.function.Gaussian;
import gov.sandia.n2a.language.function.Uniform;
import gov.sandia.n2a.language.operator.Power;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

import java.util.Collection;

public class XyceRenderer extends Renderer
{
    public Euler                         simulator;
    public Instance                      pi;
    public Collection<VariableReference> exceptions;

    public XyceRenderer (Euler simulator)
    {
        this.simulator = simulator;
    }

    public boolean render (Operator op)
    {
        if (op instanceof AccessVariable)
        {
            AccessVariable av = (AccessVariable) op;
            result.append (change (av.reference));
            return true;
        }
        // TODO: may need to write our own random number generator as function.
        // There is a footnote in the Xyce documentation that a given call to rand(), agauss() or gauss() produces only one random number *per run*.
        // IE: they are not an ongoing source of randomness during execution.
        if (op instanceof Uniform)
        {
            Uniform u = (Uniform) op;
            if (u.operands.length > 0)
            {
                int dimension = (int) Math.round (((Scalar) u.operands[0].eval (pi)).value);
                if (dimension != 1) System.err.println ("WARNING: Xyce does not support multivariate form of uniform()");
            }
            result.append ("rand()");
            return true;
        }
        if (op instanceof Gaussian)
        {
            Gaussian g = (Gaussian) op;
            if (g.operands.length > 0)
            {
                int dimension = (int) Math.round (((Scalar) g.operands[0].eval (pi)).value);
                if (dimension != 1) System.err.println ("WARNING: Xyce does not support multivariate form of gaussian()");
            }
            result.append ("agauss(0,6,6)");
            return true;
        }
        if (op instanceof Power)
        {
            Power p = (Power) op;
            result.append ("(");
            p.operand0.render (this);
            result.append (") ** (");
            p.operand1.render (this);
            result.append (")");
            return true;
        }
        return false;
    }

    public String change (VariableReference r)
    {
        // exceptions are strings we don't want to translate
        // e.g. parameters to a function - need to use name function knows it by,
        // not the name of a specific state variable
        if (exceptions != null  &&  exceptions.contains (r)) return r.variable.name;

        // Pass an external reference to its own context
        // Do this as soon as possible in this function, to minimize repeated evaluation of other if-statements
        // However, it need to come after the exceptions check. Those exceptions won't be passed into the new context.
        if (r.index >= 0)
        {
            // Evaluate in referenced equation set's context
            XyceRenderer context = new XyceRenderer (simulator);
            context.pi = (Instance) pi.valuesObject[r.index];
            return context.change (r.variable.reference);   // this should be a self-reference within r.variable
        }

        // special variables don't get SymbolDefs; just translate directly
        if (r.variable.name.equals ("$t" ))
        {
            if (r.variable.order == 0) return "TIME";
            if (r.variable.order == 1) return new Double (simulator.getNextDt ()).toString ();
        }

        if (r.variable.hasAttribute ("initOnly")) return pi.get (r).toString ();
        if (r.variable.hasAttribute ("constant")) return r.variable.equations.first ().expression.toString ();

        // finally, actual translation of some user-defined symbol!
        XyceBackendData bed = (XyceBackendData) pi.equations.backendData;
        if (bed.deviceSymbol != null)  // A device may have some variable that require special handling
        {
            if (bed.deviceSymbol.ivars.containsKey (r.variable))
            {
                // example n(y%synapse%syn_w), where 'w' is the varname passed in
                return "n(y%" + bed.deviceSymbol.device.getDeviceTypeName () + "%" + pi.equations.name + "-" + pi.hashCode () + "_" + bed.deviceSymbol.ivars.get (r.variable) + ")";
            }
            if (bed.deviceSymbol.varnames.contains (r.variable))
            {
                return Xyceisms.referenceStateVar (r.variable.name, pi.hashCode ());
            }
            // Any variables not in the above 2 categories fall through to regular processing ...
        }
        Symbol def = bed.variableSymbols.get (r.variable.name);
        return def.getReference (this);
    }

    /**
        Convenience function to reset the result field (a StringBuilder) and render an Operator and its descendents.
        This should never be called in the middle of another render.
    **/
    public String change (Operator op)
    {
        result = new StringBuilder ();
        op.render (this);
        return result.toString ();
    }
}

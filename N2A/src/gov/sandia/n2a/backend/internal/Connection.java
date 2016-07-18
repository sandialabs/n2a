/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.backend.internal.InternalBackendData.Conversion;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Matrix;
import gov.sandia.n2a.language.type.Scalar;

public class Connection extends Part
{
    public Part[] endpoints;  /// Parts connected by this object

    public Connection (EquationSet equations, PopulationConnection container)
    {
        super (equations, container);
        endpoints = new Part[equations.connectionBindings.size ()];
    }

    public void die ()
    {
        super.die ();

        // update accountable endpoints
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        int count = bed.accountableEndpoints.length;
        for (int i = 0; i < count; i++)
        {
            Variable ae = bed.accountableEndpoints[i];
            if (ae != null)
            {
                Part p = endpoints[i];
                Scalar m = (Scalar) p.get (ae);
                m.value--;
                p.set (ae, m);
            }
        }
    }

    public void init (Euler simulator)
    {
        // update accountable endpoints
        // Note: these do not require resolve(). Instead, they access their target directly through the endpoints array.
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        int count = bed.accountableEndpoints.length;
        for (int i = 0; i < count; i++)
        {
            Variable ae = bed.accountableEndpoints[i];
            if (ae != null)
            {
                Part p = endpoints[i];
                Scalar m = (Scalar) p.get (ae);
                m.value++;
                p.set (ae, m);
            }
        }

        super.init (simulator);
    }

    public String path ()
    {
        String result = endpoints[0].path ();
        for (int i = 1; i < endpoints.length; i++) result += "-" + endpoints[i].path ();
        return result;
    }

    public void setPart (int i, Part p)
    {
        endpoints[i] = p;
    }

    public Part getPart (int i)
    {
        return endpoints[i];
    }

    public int getCount (int i)
    {
        InternalBackendData bed = (InternalBackendData) equations.backendData;
        Variable ae = bed.accountableEndpoints[i];
        if (ae == null) return 0;
        return (int) ((Scalar) endpoints[i].get (ae)).value;
    }

    public double getP (Euler simulator)
    {
        InstancePreLive temp = new InstancePreLive (this, simulator);
        if (temp.bed.p == null) return 1;  // N2A language defines default to be 1 (always create)
        Type result = temp.bed.p.eval (temp);
        if (result == null) return 1;
        return ((Scalar) result).value;
    }

    public Matrix project (int i, int j)
    {
        // TODO: as part of spatial filtering, implement project()
        return new Matrix (3, 1);
    }

    public Part convert (EquationSet other)
    {
        InternalBackendData otherBed = (InternalBackendData) equations.backendData;
        PopulationConnection otherPopulation = (PopulationConnection) ((Part) container.container).populations[otherBed.populationIndex];
        Connection result = new Connection (other, otherPopulation);

        InternalBackendData bed = (InternalBackendData) equations.backendData;
        Conversion conversion = bed.conversions.get (other);
        for (int i = 0; i < conversion.bindings.length; i++) result.endpoints[conversion.bindings[i]] = endpoints[i];
        return result;
    }
}

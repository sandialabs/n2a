/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.language.EvaluationException;

public class Wrapper extends Part
{
    // Some global data associated with this run
    public Map<String,Integer> columnMap    = new HashMap<String,Integer> ();  ///< For trace(). Maps from column name to column position.
    public List<Float>         columnValues = new ArrayList<Float> ();         ///< For trace(). Holds current value for each column.
    public PrintStream         out = System.out;
    public PrintStream         err = System.err;

    public Wrapper (EquationSet model)
    {
        if (model.connectionBindings != null) throw new EvaluationException ("Only compartments may be top-level models.");  // What are you going to connect, anyway?
        populations = new Population[1];
        populations[0] = new PopulationCompartment (model, this);
    }

    public void init (Euler simulator)
    {
        populations[0].init (simulator);
        writeTrace ();
    }

    public void integrate (Euler simulator)
    {
        populations[0].integrate (simulator);
    }

    public void prepare ()
    {
        populations[0].prepare ();
    }

    public void update (Euler simulator)
    {
        populations[0].update (simulator);
    }

    public boolean finish (Euler simulator)
    {
        populations[0].finish (simulator);
        writeTrace ();
        return ((PopulationCompartment) populations[0]).n > 0;
    }

    public void writeTrace ()
    {
        int last = columnValues.size () - 1;
        for (int i = 0; i <= last; i++)
        {
            Float c = columnValues.get (i);
            if (! c.isNaN ()) out.print (c);
            if (i < last) out.print ("\t");
            columnValues.set (i, Float.NaN);
        }
        if (last >= 0) out.println ();
    }

    public void writeHeaders ()
    {
        int count = columnMap.size ();
        int last = count - 1;
        String headers[] = new String[count];
        for (Entry<String,Integer> i : columnMap.entrySet ())
        {
            headers[i.getValue ()] = i.getKey ();
        }
        for (int i = 0; i < count; i++)
        {
            out.print (headers[i]);
            if (i < last) out.print ("\t");
        }
        if (last >= 0) out.println ();
    }
}

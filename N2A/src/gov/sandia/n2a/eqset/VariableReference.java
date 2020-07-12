/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.eqset;

import java.util.ArrayList;

import gov.sandia.n2a.eqset.EquationSet.ConnectionBinding;

public class VariableReference
{
    public Variable          variable;
    public ArrayList<Object> resolution = new ArrayList<Object> ();  // Trail of objects followed to resolve the variable. The first one is always variable.container, so it is not included in the list.
    public int               index      = -1;   // Internal backend data, for looking up resolved Instance. -1 means unresolved

    /**
        @param v The source variable, as opposed to the target which is stored in this class.
    **/
    public void addDependencies (Variable v)
    {
        for (Object o : resolution)
        {
            if (o instanceof ConnectionBinding) v.addDependencyOn (((ConnectionBinding) o).variable);
        }
    }

    /**
        @param v The source variable, as opposed to the target which is stored in this class.
    **/
    public void removeDependencies (Variable v)
    {
        for (Object o : resolution)
        {
            if (o instanceof ConnectionBinding) v.removeDependencyOn (((ConnectionBinding) o).variable);
        }
    }

    /**
        Chains two resolution paths together, keeping the resulting path as simple as possible.
        Let v be our current target, and v2 be the new target.
        Our current resolution path should end with the equation set that contains v.
        Thus, any resolution from v to v2 could simply be tacked onto the end.
        However, we want to avoid doubling back. This occurs if our penultimate
        resolution step matches the first step of v2's path. In that case,
        delete both our last step and the first step from v2's path.
    **/
    public void mergeResolutionPath (VariableReference r2)
    {
        int last = resolution.size () - 1;
        for (Object o2 : r2.resolution)
        {
            if (last > 0)
            {
                Object o = resolution.get (last - 1);
                if (o instanceof ConnectionBinding) o = ((ConnectionBinding) o).endpoint;
                if (o == o2)
                {
                    resolution.remove (last--);
                    continue;  // Keeps from adding the next step of r2.
                }
                last = 0;  // Stop checking
            }
            resolution.add (o2);
        }
    }

    public String dumpResolution ()
    {
        String result = "";
        for (Object o : resolution)
        {
            result += ", ";
            if (o instanceof EquationSet) result += ((EquationSet) o).name;
            else if (o instanceof ConnectionBinding) result += ((ConnectionBinding) o).alias;
        }
        if (! result.isEmpty ()) result = result.substring (2);
        return "[" + result + "]";
    }

    public String toString ()
    {
        String result = "";
        for (Object o : resolution)
        {
            result += ", ";
            if (o instanceof EquationSet) result += ((EquationSet) o).name;
            else if (o instanceof ConnectionBinding) result += ((ConnectionBinding) o).alias;
        }
        if (! result.isEmpty ()) result = result.substring (2);
        return variable.fullName () + " " + dumpResolution ();
    }

    public boolean equals (Object o)
    {
        if (! (o instanceof VariableReference)) return false;
        VariableReference that = (VariableReference) o;
        if (variable != that.variable) return false;
        if (! resolution.equals (that.resolution)) return false;
        return true;
    }
}

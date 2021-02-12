/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.eqset;

import java.util.ArrayList;
import java.util.List;

import gov.sandia.n2a.eqset.EquationSet.ConnectionBinding;

public class VariableReference implements Comparable<VariableReference>
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
        mergeResolutionPaths (resolution, r2.resolution);
    }

    /**
        Modifies resolution1 by merging in resolution2. See mergeResolutionPath(VariableReference)
        for a detailed explanation of the motivation for this processing.
        @param resolution1 Receives the merged path.
        @param resolution2 Remains unchanged.
    **/
    public static void mergeResolutionPaths (List<Object> resolution1, List<Object> resolution2)
    {
        int last = resolution1.size () - 1;
        for (Object o2 : resolution2)
        {
            if (last > 0)
            {
                Object o = resolution1.get (last - 1);
                if (o instanceof ConnectionBinding) o = ((ConnectionBinding) o).endpoint;
                if (o == o2)
                {
                    resolution1.remove (last--);
                    continue;  // Keeps from adding the next step of r2.
                }
                last = 0;  // Stop checking
            }
            resolution1.add (o2);
        }
    }

    /**
        Remove loops in the resolution path, where it passes through part A, then through
        one or more other parts, and comes back to part A again. Loops can be created under
        normal name resolution, but ideally a resolution path should consist only of unique
        steps from source to destination. This routine simplifies the path.
    **/
    public void removeLoops ()
    {
        for (int i = 0; i < resolution.size () - 1; i++)  // Note: this is a case where checking size each time actually matters.
        {
            Object A = resolution.get (i);
            for (int j = i + 1; j < resolution.size ();)
            {
                Object B = resolution.get (j);
                if (B == A)
                {
                    int count = j - i;
                    for (int k = 0; k < count; k++) resolution.remove (i);
                    j = i + 1;
                }
                else
                {
                    j++;
                }
            }
        }
    }

    /**
        Ensure the path does not rely on a connection endpoint as its first step.
        This is needed when a reference within a connection part is executed at
        the global scope, but also uses a connection binding to find the target population.
        The connection binding is not available at the global scope and also not necessary. 
        @param v The variable where the path starts. We remove the connection as
        a dependency of this variable. See removeDependencies(Variable)
    **/
    public void convertToGlobal (Variable v)
    {
        int count = resolution.size ();
        if (count == 0) return;
        Object o = resolution.get (0);
        if (! (o instanceof ConnectionBinding)) return;

        ConnectionBinding cb = (ConnectionBinding) o;
        removeDependencies (v);
        ArrayList<Object> newResolution = new ArrayList<Object> (cb.resolution);
        if (count > 1) mergeResolutionPaths (newResolution, resolution.subList (1, count));
        resolution = newResolution;
        addDependencies (v);
    }

    /**
        Walks the resolution path and determines what container we are in just before applying
        the nth step.
        @param start Container we are in before first step of resolution.
        @param n Step at which to stop resolution and return current container.
    **/
    public EquationSet containerAt (EquationSet start, int n)
    {
        n = Math.min (n, resolution.size ());
        for (int i = 0; i < n; i++)
        {
            Object o = resolution.get (i);
            if (o instanceof EquationSet) start = (EquationSet) o;
            else if (o instanceof ConnectionBinding) start = ((ConnectionBinding) o).endpoint;
        }
        return start;
    }

    public EquationSet penultimateContainer (EquationSet start)
    {
        return containerAt (start, resolution.size () - 1);
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

    public int compareTo (VariableReference that)
    {
        int count = resolution.size ();
        int result = count - that.resolution.size ();
        if (result != 0) return result;

        for (int i = 0; i < count; i++)
        {
            Object o0 =      resolution.get (i);
            Object o1 = that.resolution.get (i);
            if (! o0.getClass ().equals (o1.getClass ())) return o0.getClass ().hashCode () - o1.getClass ().hashCode ();

            if (o0 instanceof EquationSet)
            {
                result = ((EquationSet) o0).compareTo ((EquationSet) o1);
                if (result != 0) return result;
            }
            else if (o0 instanceof ConnectionBinding)
            {
                ConnectionBinding c0 = (ConnectionBinding) o0;
                ConnectionBinding c1 = (ConnectionBinding) o1;
                result = c0.alias.compareTo (c1.alias);
                if (result != 0) return result;
                result = c0.endpoint.compareTo (c1.endpoint);
                if (result != 0) return result;
            }
        }

        return 0;
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

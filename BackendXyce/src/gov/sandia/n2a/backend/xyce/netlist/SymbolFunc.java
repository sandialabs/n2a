/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.netlist;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Visitor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SymbolFunc extends Symbol
{
    public List<VariableReference> args = new ArrayList<VariableReference> ();

    public SymbolFunc (final EquationEntry eq)
    {
        super (eq);

        // Determine what variables this function depends on.
        // TODO make this code create a collection without duplicates, instead of removing them in functions below
        eq.visit (new Visitor ()
        {
            public boolean visit (Operator op)
            {
                if (op instanceof AccessVariable)
                {
                    AccessVariable av = (AccessVariable) op;
                    if (   ! av.name.equals (eq.variable.name)
                        && av.reference != null
                        && av.reference.variable != null
                        && ! av.reference.variable.hasAttribute ("constant")  // since constants are directly substituted
                        && ! av.name.equals ("$t"))  // since $t is always directly available as TIME
                    {
                        args.add (av.reference);
                    }
                }
                return true;
            }
        });

        if (args.size () == 0)
        {
            // if there aren't any arguments, something's wrong - this could have been a constant
            throw new EvaluationException ("Trying to create .func for " + eq.variable.name + " but there are no arguments");
        }
    }

    @Override
    public String getDefinition (XyceRenderer renderer) 
    {
        // use a set to avoid duplicate entries for variables that appear multiple times
        Set<String> formalArguments = new LinkedHashSet<String> ();
        for (VariableReference r : args) formalArguments.add (r.variable.name);
        // don't render the equation; defineFunction needs original version using formal parameters, not instance-based arguments
        // but Xyce doesn't understand 'trace', and XyceSimulation code handles the .print statement for any use of 'trace',
        // so just remove use of 'trace' while keeping any expression involved
        String RHS = eq.toString ().replaceAll ("trace", "");
        return Xyceisms.defineFunction (eq.variable.name, renderer.pi.hashCode (), formalArguments, RHS);
    }

    @Override
    public String getReference (XyceRenderer renderer)
    {
        // need to know what argument(s) this function
        // takes, and translate those as well -
        // unless they're in the exception list, as for defining the function
        // use a set to avoid duplicate entrires for variables that appear multiple times
        Set<String> newArgs = new LinkedHashSet<String> ();
        for (VariableReference r : args)
        {
            newArgs.add (renderer.change (r));
        }
        return Xyceisms.referenceFunction (eq.variable.name, newArgs, renderer.pi.hashCode ());
    }
}

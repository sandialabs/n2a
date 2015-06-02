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
import java.util.List;

public class SymbolFunc extends Symbol
{
    public List<VariableReference> args = new ArrayList<VariableReference> ();

    public SymbolFunc (final EquationEntry eq)
    {
        super (eq);

        // Determine what variables this function depends on.
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
        List<String> formalArguments = new ArrayList<String> ();
        for (VariableReference r : args) formalArguments.add (r.variable.name);  // TODO: will this produce a list of unique formal arguments? Do the var names need to be fully qualified?
        return Xyceisms.defineFunction (eq.variable.name, renderer.pi.hashCode (), formalArguments, renderer.change (eq.expression));
    }

    @Override
    public String getReference (XyceRenderer renderer)
    {
        // need to know what argument(s) this function
        // takes, and translate those as well -
        // unless they're in the exception list, as for defining the function
        List<String> newArgs = new ArrayList<String> ();
        for (VariableReference r : args)
        {
            newArgs.add (renderer.change (r));
        }
        return Xyceisms.referenceFunction (eq.variable.name, newArgs, renderer.pi.hashCode ());
    }
}

/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import java.util.ArrayList;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.language.parse.ASTIdentifier;
import gov.sandia.n2a.language.parse.ASTList;
import gov.sandia.n2a.language.parse.SimpleNode;
import gov.sandia.n2a.language.parse.ParseException;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

public class Split extends Operator
{
    public String[]               names;  ///< Untranslated part names
    public ArrayList<EquationSet> parts;  ///< List of parts to split the current one into

    public void getOperandsFrom (SimpleNode node) throws ParseException
    {
        if (! (node instanceof ASTList)) throw new ParseException ("AST for type list has unexpected form");
        ASTList l = (ASTList) node;
        int count = l.jjtGetNumChildren ();
        names = new String[count];
        for (int i = 0; i < count; i++)
        {
            SimpleNode n = (SimpleNode) l.jjtGetChild (i);
            if (! (n instanceof ASTIdentifier)) throw new ParseException ("AST for type list has unexpected form");
            names[i] = n.jjtGetValue ().toString ();
        }
    }

    public Type eval (Instance context) throws EvaluationException
    {
        int index = context.equations.splits.indexOf (parts);
        return new Scalar (index + 1);
    }

    public String toString ()
    {
        StringBuilder result = new StringBuilder ();
        int last = names.length - 1;
        for (int i = 0; i <= last; i++)
        {
            result.append (names[i]);
            if (i < last) result.append (", ");
        }
        return result.toString ();
    }

    public int compareTo (Operator that)
    {
        Class<? extends Operator> thisClass = getClass ();
        Class<? extends Operator> thatClass = that.getClass ();
        if (! thisClass.equals (thatClass)) return thisClass.hashCode () - thatClass.hashCode ();

        // Same class as us, so compare split targets
        Split s = (Split) that;
        int difference = names.length - s.names.length;
        if (difference != 0) return difference;
        for (int i = 0; i < names.length; i++)
        {
            difference = names[i].compareTo (s.names[i]);
            if (difference != 0) return difference;
        }

        return 0;
    }
}

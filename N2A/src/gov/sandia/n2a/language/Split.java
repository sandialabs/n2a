/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import java.util.ArrayList;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.parse.ASTIdentifier;
import gov.sandia.n2a.language.parse.ASTList;
import gov.sandia.n2a.language.parse.SimpleNode;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;
import tech.units.indriya.AbstractUnit;

public class Split extends Operator
{
    public String[]               names;  // Untranslated part names
    public ArrayList<EquationSet> parts;  // List of parts to split the current one into
    public int                    index;  // of parts in EquationSet.splits

    public void getOperandsFrom (SimpleNode node) throws ParseException
    {
        if (! (node instanceof ASTList)) throw new Error ("AST for type list has unexpected form");
        ASTList l = (ASTList) node;
        int count = l.jjtGetNumChildren ();
        names = new String[count];
        for (int i = 0; i < count; i++)
        {
            SimpleNode n = (SimpleNode) l.jjtGetChild (i);
            if (! (n instanceof ASTIdentifier)) throw new ParseException ("Items in $type list must all be part names.");
            names[i] = n.jjtGetValue ().toString ();
        }
    }

    public void determineExponent (Variable from)
    {
        updateExponent (from, MSB, 0);  // integer
    }

    public void determineUnit (boolean fatal) throws Exception
    {
        unit = AbstractUnit.ONE;
    }

    public Type getType ()
    {
        return new Scalar (0);
    }

    public Type eval (Instance context) throws EvaluationException
    {
        return new Scalar (index + 1);
    }

    public String toString ()
    {
        String result = new String ();
        int last = names.length - 1;
        for (int i = 0; i <= last; i++) result += "," + names[i];
        return result.substring (1);
    }
}

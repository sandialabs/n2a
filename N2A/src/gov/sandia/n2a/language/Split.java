/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language;

import java.util.ArrayList;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.language.parse.ASTListNode;
import gov.sandia.n2a.language.parse.ASTNodeBase;
import gov.sandia.n2a.language.parse.ASTVarNode;
import gov.sandia.n2a.language.parse.ParseException;

public class Split extends Operator
{
    public String[]               names;  ///< Untranslated part names
    public ArrayList<EquationSet> parts;  ///< List of parts to split the current one into

    public void getOperandsFrom (ASTNodeBase node) throws ParseException
    {
        if (! (node instanceof ASTListNode)) throw new ParseException ("AST for type list has unexpected form");
        ASTListNode l = (ASTListNode) node;
        int count = l.jjtGetNumChildren ();
        names = new String[count];
        for (int i = 0; i < count; i++)
        {
            ASTNodeBase n = (ASTNodeBase) l.jjtGetChild (i);
            if (! (n instanceof ASTVarNode)) throw new ParseException ("AST for type list has unexpected form");
            names[i] = n.jjtGetValue ().toString ();
        }
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
}

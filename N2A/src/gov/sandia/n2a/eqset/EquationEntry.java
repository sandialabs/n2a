/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.eqset;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.ParseException;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.language.Visitor;

public class EquationEntry implements Comparable<EquationEntry>
{
    public Variable variable;    // Our container
    public String   ifString;    // only for sorting. TODO: get rid of ifString. Instead, convert conditional to a canonical form with well-defined sort order. This will enable us to combine logically equivalent conditions, as well prioritize more restrictive conditions.
    public Operator expression;
    public Operator condition;

    /**
        @param variable This equation must be explicitly added to variable.equations 
    **/
    public EquationEntry (Variable variable, String ifString)
    {
        this.variable = variable;
        this.ifString = ifString;
    }

    /**
        Construct the equation for a sub-node of a variable, that is, an equation
        that is part of a multiconditional statement.
        We can safely assume that the variable already exists, and that the caller
        will add us to it.
        Note that the formatting of a multiconditional statement is different from
        a single line, in that the condition itself serves as the index. The @
        symbol is included in the stored index (to allow commingling with $metadata
        and $reference).
    **/
    public EquationEntry (MNode source) throws ParseException
    {
        ifString                = source.key ();
        String expressionString = source.get ();
        try
        {
            expression = Operator.parse (expressionString);
        }
        catch (ParseException e)
        {
            e.line += ifString;
            throw e;
        }

        ifString = ifString.substring (1);  // The key should always begin with @
        if (! ifString.isEmpty ())
        {
            try
            {
                condition = Operator.parse (ifString);
            }
            catch (ParseException e)
            {
                e.line = expressionString + "@" + e.line;
                e.column += expressionString.length () + 1;
                throw e;
            }
            ifString  = condition.render ();  // to normalize formatting, since we rely on this string for sorting and comparison
        }
    }

    /**
        Parses the right-hand side of an equation and converts it into an EquationEntry.
        The caller is responsible for adding the equation object to the correct variable.
    **/
    public EquationEntry (String rhs) throws ParseException
    {
        String[] parts = rhs.split ("@");
        try
        {
            expression = Operator.parse (parts[0]);
        }
        catch (ParseException e)
        {
            e.line = rhs;
            throw e;
        }

        ifString = "";
        if (parts.length > 1)
        {
            try
            {
                condition = Operator.parse (parts[1]);
            }
            catch (ParseException e)
            {
                e.line = rhs;
                e.column += parts[0].length () + 1;
                throw e;
            }
            ifString = condition.render ();
        }
    }

    public void visit (Visitor visitor)
    {
        if (expression != null) expression.visit (visitor);
        if (condition  != null) condition .visit (visitor);
    }

    public void render (Renderer renderer)
    {
        if (expression != null)
        {
            expression.render (renderer);
        }
        if (condition != null)
        {
            renderer.result.append (" @ ");
            condition.render (renderer);
        }
    }

    @Override
    public String toString ()
    {
        Renderer renderer = new Renderer ();
        render (renderer);
        return renderer.result.toString ();
    }

    public int compareTo (EquationEntry that)
    {
        if (ifString.equals (that.ifString)) return  0;
        if (     ifString.isEmpty ())        return  1;
        if (that.ifString.isEmpty ())        return -1;

        boolean thisInit =      ifString.contains ("$init");
        boolean thatInit = that.ifString.contains ("$init");
        if (thisInit)
        {
            if (! thatInit)              return -1;
            if (ifString.length () == 5) return  1;  // ifString is exactly "$init"
        }
        if (thatInit)
        {
            if (! thisInit)                   return  1;
            if (that.ifString.length () == 5) return -1;
        }

        int diff = that.ifString.length () - ifString.length ();  // as a heuristic, sort longer ifStrings first
        if (diff == 0) return ifString.compareTo (that.ifString);  // If they are the same length, use lexical order instead.
        return diff;
    }

    @Override
    public boolean equals (Object that)
    {
        if (this == that) return true;
        EquationEntry e = (EquationEntry) that;
        if (e == null) return false;
        return compareTo (e) == 0;
    }
}

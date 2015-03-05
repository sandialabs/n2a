/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.eqset;

import gov.sandia.n2a.language.gen.ASTNodeBase;
import gov.sandia.n2a.language.gen.ASTRenderingContext;
import gov.sandia.n2a.language.gen.ExpressionParser;
import gov.sandia.n2a.language.gen.ParseException;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;

import java.util.TreeMap;

public class EquationEntry implements Comparable<EquationEntry>
{
    public NDoc                    source;  // Reference to the source DB object
    public Variable                variable;
    public String                  ifString;  // only for sorting
    public String                  assignment;
    public ASTNodeBase             expression;
    public ASTNodeBase             conditional;
    public TreeMap<String, String> metadata;

    public EquationEntry (String name, int order)
    {
        this (new Variable (name, order), "");
        variable.add (this);
    }

    /**
        @param variable This equation must be explicitly added to variable.equations 
    **/
    public EquationEntry (Variable variable, String ifString)
    {
        this.variable = variable;
        this.ifString = ifString;
    }

    public EquationEntry (NDoc source) throws Exception
    {
        this ((String) source.get ("value"));
        this.source = source;
    }

    public EquationEntry (String raw) throws Exception
    {
        String[] parts = raw.split ("@");
        String temp = parts[0].trim ();
        ifString = "";
        if (parts.length > 1)
        {
            int convertFrom = 2;
            if (parts[1].contains ("xyce."))
            {
                convertFrom = 1;
            }
            else
            {
                conditional = ExpressionParser.parse (parts[1]);
                ifString = conditional.toReadableShort ();
            }
            if (convertFrom < parts.length)  // there exists some metadata to convert
            {
                metadata = new TreeMap<String, String> ();
            }
            for (int i = convertFrom; i < parts.length; i++)
            {
                String[] nv = parts[i].split ("=", 2);
                nv[0].trim ();
                if (nv.length > 1)
                {
                    metadata.put (nv[0], nv[1].trim ());
                }
                else
                {
                    metadata.put (nv[0], "");
                }
            }
        }
        parts = temp.split ("=", 2);
        if (parts.length > 1)
        {
            String name = parts[0];
            expression = ExpressionParser.parse (parts[1]);
            if (name.endsWith ("*") || name.endsWith ("/") || name.endsWith ("-"))
            {
                throw new ParseException ("Only += and := are allowed");
            }
            if (name.endsWith ("+") || name.endsWith (":"))
            {
                int last = name.length () - 1;
                assignment = name.substring (last) + "=";
                name = name.substring (0, last);
            }
            else
            {
                assignment = "=";
            }
            name = name.trim ();
            int order = 0;
            while (name.endsWith ("'"))
            {
                order++;
                name = name.substring (0, name.length () - 1);
            }
            variable = new Variable (name, order);
        }
        else  // naked expression
        {
            variable = new Variable ("", 0);
            assignment = "";
            expression = ExpressionParser.parse (parts[0]);
        }
        variable.add (this);
    }

    public String getNamedValue (String name)
    {
        return getNamedValue (name, "");
    }

    public String getNamedValue (String name, String defaultValue)
    {
        if (metadata == null) return defaultValue;
        if (metadata.containsKey (name)) return metadata.get (name);
        return defaultValue;
    }

    public void setNamedValue (String name, String value)
    {
        if (metadata == null)
        {
            metadata = new TreeMap<String, String> ();
        }
        metadata.put (name, value);
    }

    public String render (ASTRenderingContext context)
    {
        String result = variable.nameString ();
        result = result + " " + assignment;
        if (expression  != null)
        {
            result = result + " "   + context.render (expression);
        }
        if (conditional != null)
        {
            result = result + " @ " + context.render (conditional);
        }
        return result;
    }

    @Override
    public String toString ()
    {
        return render (new ASTRenderingContext (true));
    }

    public int compareTo (EquationEntry that)
    {
        return ifString.compareTo (that.ifString);
    }

    @Override
    public boolean equals (Object that)
    {
        if (this == that)
        {
            return true;
        }
        EquationEntry e = (EquationEntry) that;
        if (e == null)
        {
            return false;
        }
        return compareTo (e) == 0;
    }
}

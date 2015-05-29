/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.eqset;

import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.language.Visitor;
import gov.sandia.n2a.language.parse.ParseException;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

public class EquationEntry implements Comparable<EquationEntry>
{
    public NDoc                    source;  // Reference to the source DB object
    public Variable                variable;
    public String                  ifString;  // only for sorting. TODO: get rid of ifString. Instead, convert conditional to a canonical form with well-defined sort order. This will enable us to combine logically equivalent conditions, as well prioritize more restrictive conditions.
    public String                  assignment;
    public Operator                expression;
    public Operator                conditional;
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
        Map<String, String> namedValues = source.getValid ("$metadata", new TreeMap<String, String> (), Map.class);
        if (namedValues.size () > 0)
        {
            metadata = new TreeMap<String,String> ();
            metadata.putAll (namedValues);
        }
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
                conditional = Operator.parse (parts[1]);
                ifString = conditional.render ();
            }
            if (convertFrom < parts.length)  // there exists some metadata to convert
            {
                metadata = new TreeMap<String, String> ();
                for (int i = convertFrom; i < parts.length; i++)
                {
                    String[] nv = parts[i].split ("=", 2);
                    nv[0].trim ();
                    if (nv.length > 1) metadata.put (nv[0], nv[1].trim ());
                    else               metadata.put (nv[0], "");
                }
            }
        }
        parts = temp.split ("=", 2);
        if (parts.length > 1)
        {
            String name = parts[0];
            expression = Operator.parse (parts[1]);
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
            expression = Operator.parse (parts[0]);
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
        if (metadata == null) metadata = new TreeMap<String, String> ();
        metadata.put (name, value);
    }

    /**
        Safe method to access metadata for iteration
    **/
    public Set<Entry<String,String>> getMetadata ()
    {
        if (metadata == null) metadata = new TreeMap<String, String> ();
        return metadata.entrySet ();
    }

    public void visit (Visitor visitor)
    {
        if (expression  != null) expression .visit (visitor);
        if (conditional != null) conditional.visit (visitor);
    }

    public void render (Renderer renderer)
    {
        renderer.result.append (variable.nameString () + " " + assignment);
        if (expression  != null)
        {
            renderer.result.append (" ");
            expression.render (renderer);
        }
        if (conditional != null)
        {
            renderer.result.append (" @ ");
            conditional.render (renderer);
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
        if (     ifString.equals ("$init"))  return  1;
        if (that.ifString.equals ("$init"))  return -1;
        return that.ifString.length () - ifString.length ();  // as a heuristic, sort longer ifStrings first
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

/*
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

/**
    Utility class for capturing names of functions and variables during parsing.
**/
public class Identifier
{
    public String  name;
    public boolean hadParens;
    public int     columnBegin; // position of first character in source line
    public int     columnEnd;   // position of last character in source line. Not necessarily same as columnBegin+name.length()-1, since original text might not be canonical.

    /**
        Parses the given string, which must be properly-formatted number with optional unit specified at end.
    **/
    public Identifier (String name, int columnBegin, int columnEnd)
    {
        this.name        = canonical (name);
        this.columnBegin = columnBegin;
        this.columnEnd   = columnEnd;
    }

    public static String canonical (String name)
    {
        String primes = "";
        String path   = name;
        int pos = name.indexOf ("'");
        if (pos >= 0)
        {
            primes = name.substring (pos).trim ();
            path   = name.substring (0, pos);
        }
        path = path.trim ();

        String[] pieces = path.split ("\\.");
        String result = pieces[0].trim ();
        for (int i = 1; i < pieces.length; i++)
        {
            result += "." + pieces[i].trim ();
        }
        return result + primes;
    }
}

/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import java.io.PrintStream;

@SuppressWarnings("serial")
public class ParseException extends Exception
{
    public String line = "";
    public int column = -1;

    public ParseException ()
    {
    }

    public ParseException (String message)
    {
        super (message);
    }

    public ParseException (String message, String line, int column)
    {
        super (message);
        this.line   = line;
        this.column = column;
    }

    public void print (PrintStream ps)
    {
        ps.println (this.getMessage ());
        ps.println (line);
        for (int i = 0; i < column; i++) ps.print (" ");
        ps.println ("^");
    }
}

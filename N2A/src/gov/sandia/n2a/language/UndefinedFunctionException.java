/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

@SuppressWarnings("serial")
public class UndefinedFunctionException extends Exception
{
    public String message;  // Initially the name of the function. Later, Variable will add a message and its own name as well.

    public UndefinedFunctionException (String name)
    {
        this.message = name;
    }
}

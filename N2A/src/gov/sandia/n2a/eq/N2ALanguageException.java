/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.eq;

public class N2ALanguageException extends Exception
{
    public N2ALanguageException(String msg) {
        super(msg);
    }
    public N2ALanguageException(String msg, Throwable cause) {
        super(msg, cause);
    }
}

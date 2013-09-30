/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce;

public class XyceTranslationException extends Exception {
    public XyceTranslationException(String msg) {
        super(msg);
    }
    public XyceTranslationException(String msg, Throwable cause) {
        super(msg, cause);
    }
}

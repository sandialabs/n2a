/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ensemble.params.groupset;

public class ParameterSpecGroupSetValidationException extends RuntimeException {
    public ParameterSpecGroupSetValidationException() {
        super();
    }
    public ParameterSpecGroupSetValidationException(String arg0, Throwable arg1) {
        super(arg0, arg1);
    }
    public ParameterSpecGroupSetValidationException(String arg0) {
        super(arg0);
    }
    public ParameterSpecGroupSetValidationException(Throwable arg0) {
        super(arg0);
    }
}

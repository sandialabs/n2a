/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
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

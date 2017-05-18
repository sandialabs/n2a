/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.parms;

import java.util.List;

public class ParameterBundle {
    private List<ParameterDomain> domains;
    private Parameter param;

    public ParameterBundle(List<ParameterDomain> d, Parameter p) {
        domains = d;
        param = p;
    }

    public List<ParameterDomain> getDomains() {
        return domains;
    }
    public Parameter getParameter() {
        return param;
    }
}
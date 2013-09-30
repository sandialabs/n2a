/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.ensemble.domains;

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
/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins;

import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;

public interface Parameterizable {

    // Any parameterizable object must be able to provide
    // a hierarchical tree of key-value pairs that represent
    // the possible parameters to the model and the default
    // values that correspond to them if no other value is
    // provided by the user / run ensemble framework.
    public ParameterDomain getAllParameters();

    // Any parameterizable object must be able to take in
    // a hierarchical tree of key-value pairs that represent
    // a small subset of parameters that should override the
    // model's default parameters.  This tree will not include
    // default values (values that were not changed in any
    // way from the values provided by the getInputParameters
    // method).
    public void setSelectedParameters(ParameterDomain domain);
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins;

import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;

public interface Parameterizable
{
    /**
        Provide a hierarchical tree of key-value pairs that represent the possible
        parameters of this object and their default values if not otherwise specified.
    **/
    public ParameterDomain getAllParameters ();

    /**
        Take in a hierarchical tree of key-value pairs that represent a small set
        of overrides to this object's parameters.  This tree will not contain
        default values (values that were not changed in any way from the values
        provided by the getInputParameters method).
    **/
    public void setSelectedParameters (ParameterDomain domain);
}

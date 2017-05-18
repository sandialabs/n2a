/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.plugins;

import gov.sandia.n2a.parms.ParameterDomain;

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

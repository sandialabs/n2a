/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins;

import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.runs.RunState;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;

public interface Simulation extends Parameterizable {
    public ParameterDomain getAllParameters(); // no impl needed for SN
    public void setSelectedParameters(ParameterDomain domain);
    public RunState prepare(Object run, ParameterSpecGroupSet groups, ExecutionEnv env) throws Exception;
    public boolean resourcesAvailable();
    public void submit() throws Exception;
    public RunState execute(Object run, ParameterSpecGroupSet groups, ExecutionEnv env) throws Exception;
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.internal;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.runs.RunState;

public class InternalRunState extends RunState
{
    public MNode model;
	public String jobDir;
	public EquationSet digestedModel;

	public String getNamedValue (String name, String defaultValue)
    {
        if (name == "jobDir")  return jobDir;
        return super.getNamedValue (name, defaultValue);
    }
}

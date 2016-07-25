/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.c;

import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.plugins.RunState;

public class RunStateC extends RunState
{
    public MNode model;
	public String jobDir;
	public String command;

	public String getNamedValue (String name, String defaultValue)
    {
        if (name == "jobDir")  return jobDir;
        if (name == "command") return command;
        return super.getNamedValue (name, defaultValue);
    }
}

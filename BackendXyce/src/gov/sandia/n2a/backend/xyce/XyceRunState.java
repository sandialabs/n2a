/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce;

import gov.sandia.umf.platform.runs.RunState;

// Represents the state of a given run at a given time.
// This object is serialized to XML to the database.
// A run panel can then inspect this object once it is
// deserialized from the run record and produce panels
// specific to the simulator.

public class XyceRunState extends RunState
{
    private String netlist;
    public String jobDir;
    public String command;

    public String getNetlist() {
        return netlist;
    }

    public String getNamedValue (String name, String defaultValue)
    {
        if (name == "jobDir")  return jobDir;
        if (name == "command") return command;
        return super.getNamedValue (name, defaultValue);
    }
}

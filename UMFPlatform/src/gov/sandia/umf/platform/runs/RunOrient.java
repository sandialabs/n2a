/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.runs;

import gov.sandia.umf.platform.db.AppData;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.db.MVolatile;
import gov.sandia.umf.platform.plugins.PlatformRecord;
import gov.sandia.umf.platform.plugins.extpoints.Backend;
import replete.plugins.PluginManager;
import replete.xstream.XStreamWrapper;

public class RunOrient implements Run
{
    private MNode source;
    private Backend simulator = null;

    public RunOrient (Double simDuration, String name, String notes, Backend sim, String owner, String status, String state, MNode modelSource)
    {
        source = new MVolatile ();
        source.set (name,               "name");
        source.set (simDuration,        "duration");
        source.set (notes,              "notes");
        source.set (sim.getName(),      "simulator");
        source.set (owner,              "$owner");
        source.set (status,             "status");
        source.set (state,              "state");
        source.set (modelSource.key (), "model");  // Since the model is most likely an MDoc, the value is the file name.
        simulator = sim;
    }

    public RunOrient (MNode doc)
    {
        source = doc;
    }

    // TODO - this method should get some of the same info as above:
    // run name (perhaps constructed from ensemble name and run #),
    // $owner, simulator, sim duration, status/state?
    public RunOrient (PlatformRecord modelRunCopy)
    {
        source = new MVolatile ();
        source.set (modelRunCopy.getSource ().key (), "model");
    }

    // TODO - not actually used/necessary
    @Override
    public void setSimulator(Backend sim) {
        simulator = sim;
        source.set("simulator", sim.getName());
    }

    // TODO - once we're off old RunDetailPanel, this won't be necessary
    @Override
    public Backend getSimulator() {
        if(simulator == null) {
        	// TODO - this is wrong
            simulator = (Backend) PluginManager.getExtensionById(getName());
        }
        return simulator;
    }

    public String getName() {
        return source.get("name");
    }

    @Override
    public void setName(String name) {
        source.set("name", name);
    }

    public MNode getModel ()
    {
        return AppData.models.child (source.get ("model"));
    }

    @Override
    public double getSimDuration() {
        return source.getOrDefault (0.0, "duration");
    }

    @Override
    public void setSimDuration(double dur) {
        source.set (dur, "duration");
    }

    @Override
    public void setState(String writeToString) {
        source.set("state", writeToString);
    }
    
    @Override
    public RunState getState() {
        return (RunState) XStreamWrapper.loadTargetFromString(
                (String) source.get("state"));
    }

    @Override
    public void save ()
    {
    }

    @Override
    public void delete ()
    {
    }

    // TODO - should be override
    public MNode getSource ()
    {
        return source;
    }
}

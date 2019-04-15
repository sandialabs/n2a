/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.runs;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.umf.platform.plugins.PlatformRecord;
import replete.xstream.XStreamWrapper;

public class RunOrient implements Run
{
    private MNode source;
    private Backend simulator = null;

    public RunOrient (Double simDuration, String name, String notes, Backend sim, String owner, String status, String state, MNode modelSource)
    {
        source = new MVolatile ();
        source.set ("name",      name);
        source.set ("duration",  simDuration);
        source.set ("notes",     notes);
        source.set ("simulator", sim.getName());
        source.set ("$owner",    owner);
        source.set ("status",    status);
        source.set ("state",     state);
        source.set ("model",     modelSource.key ());  // Since the model is most likely an MDoc, the value is the file name.
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
        source.set ("model", modelRunCopy.getSource ().key ());
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
        source.set ("duration", dur);
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

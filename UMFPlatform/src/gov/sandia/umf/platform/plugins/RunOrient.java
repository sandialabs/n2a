/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;
import replete.plugins.PluginManager;
import replete.xstream.XStreamWrapper;

public class RunOrient implements Run {
    private NDoc source;
    private Simulator simulator = null;
    // TODO - what info is really needed?
    // Xyce expects a 'name'
    // all backends need the model
    // state is set by UI; not sure what it's used for
    // duration now supposed to be part of Paramterization/Simulation,
    //    but Xyce NetlistOrient still expects it here, for now
    // $owner always supposed to be set?
    // status??

    public RunOrient(Double simDuration, String name, String notes, Simulator sim,
            String owner, String status, String state, NDoc modelSource) {
        source = new NDoc("gov.sandia.umf.platform$Run");
        source.set("name", name);
        source.set("duration", simDuration);
        source.set("notes", notes);
        source.set("simulator", sim.getName());
        source.set("$owner", owner);
        source.set("status", status);
        source.set("state", state);
        source.set("model", modelSource);
        simulator = sim;
    }

    public RunOrient(NDoc doc) {
        source = doc;
    }

    // TODO - this method should get some of the same info as above:
    // run name (perhaps constructed from ensemble name and run #),
    // $owner, simulator, sim duration, status/state?
    public RunOrient(PlatformRecord modelRunCopy) {
        source = new NDoc("gov.sandia.umf.platform$Run");
        source.set("templateModel", modelRunCopy.getSource());
        source.save();
    }

    // TODO - not actually used/necessary
    @Override
    public void setSimulator(Simulator sim) {
        simulator = sim;
        source.set("simulator", sim.getName());
    }

    // TODO - once we're off old RunDetailPanel, this won't be necessary
    @Override
    public Simulator getSimulator() {
        if(simulator == null) {
        	// TODO - this is wrong
            simulator = (Simulator) PluginManager.getExtensionById(getName());
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

    public NDoc getModel() {
        // old style Run, created from RunDetailPanel, uses "model"
        // new style created from RunQueue uses "templateModel"
        NDoc result = (NDoc) source.get("templateModel");
        if (result==null) {
            result = (NDoc) source.get("model");
        }
        return result;
    }

    @Override
    public double getSimDuration() {
        return (Double) source.get("duration");
    }

    @Override
    public void setSimDuration(double dur) {
        source.set("duration", dur);
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
    public void save() {
        source.save();
    }

    @Override
    public void delete() {
        source.delete();
    }

    // TODO - should be override
    public NDoc getSource() {
        return source;
    }
}

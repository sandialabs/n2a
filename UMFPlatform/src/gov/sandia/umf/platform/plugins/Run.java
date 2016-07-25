/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins;

import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;

public interface Run {

    String getName();
    
    void setName(String name);

    double getSimDuration();

    void setSimDuration(double dur);

    void setSimulator(Simulator sim);

    Simulator getSimulator();

    void setState(String writeToString);
    
    RunState getState();

    void save();

    void delete();
    
    MNode getSource();
}

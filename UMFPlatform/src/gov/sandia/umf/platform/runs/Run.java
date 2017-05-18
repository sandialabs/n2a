/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.runs;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Backend;

public interface Run {

    String getName();
    
    void setName(String name);

    double getSimDuration();

    void setSimDuration(double dur);

    void setSimulator(Backend sim);

    Backend getSimulator();

    void setState(String writeToString);
    
    RunState getState();

    void save();

    void delete();
    
    MNode getSource();
}

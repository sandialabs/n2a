/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ensemble.params.ParameterSet;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;

import java.util.List;

public interface RunEnsemble {
    String getLabel();
    NDoc getSource();
    NDoc getTemplateModelDoc();
    ExecutionEnv getEnvironment();
    Simulator getSimulator();
    List<String> getOutputExprs();
    long getTotalRunCount();
    long getFrameworkRunCount();
    List<Run> getRuns();
    ParameterSpecGroupSet getGroups();
    List<ParameterSet> getParameterSets();
    void addRun(Run run);
    ParameterSpecGroupSet getSimHandledGroups();
}

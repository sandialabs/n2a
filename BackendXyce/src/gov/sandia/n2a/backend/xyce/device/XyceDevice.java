/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.device;

import java.util.List;

public abstract class XyceDevice 
{
    protected String name;
    protected int level;
    protected List<String> defaultNodes;
    protected List<String> modelParameterNames;
    protected List<String> internalVariables;

    // determine whether the specified string is a valid model
    // parameter name for this device
    public boolean isAllowedModelParameter(String paramName) 
    {
        return modelParameterNames.contains(paramName.toUpperCase());
    }

    // determine whether the specified index corresponds to a node that
    // is supposed to be named according to a model variable
    public boolean isValidNodeIndex(int index) 
    {
        return (index >= 0) && (index < defaultNodes.size()) && (defaultNodes.get(index)!="0");
    }

    // get an initial/default list of node names
    // should just have placeholder "" for nodes that need to be named
    // according to state vars
    // should have "0" for nodes known to be ground
    public List<String> getDefaultNodes() 
    {
        return defaultNodes;
    }

    public String getDeviceTypeName()
    {
        return name;
    }
    
    public int getDeviceLevel()
    {
        return level;
    }

    public boolean isInternalVariable(String varname)
    {
        return internalVariables.contains(varname.toUpperCase());
    }
    
    // Necessary because the parameter names used for capacitance in Xyce neurons
    // are not all the same.
    public abstract String getCapacitanceVar();
}

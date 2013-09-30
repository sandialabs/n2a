/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce;

import javax.swing.JPanel;

import replete.util.Lay;

// Shouldn't need this anymore if using ParameterDomains
public class XyceRunParametersPanel extends JPanel /*extends SimulatorRunParametersPanel*/ {
    public XyceRunParametersPanel() {
        Lay.FLtg(this,
            "L", Lay.lb("xyce parameters")
        );
    }
/*
    public SimulatorRunParameters getParams() {
        return null;
    }

    public void setParams(SimulatorRunParameters params) {
    }
*/
    public String getParamsValidationMessage() {
        return null;
    }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.ensemble.specs;

import gov.sandia.umf.platform.ensemble.params.specs.GaussianParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.ParameterSpecification;
import gov.sandia.umf.platform.ui.ensemble.images.ImageUtil;

import javax.swing.JTextField;

import replete.gui.controls.SelectAllTextField;
import replete.util.Lay;
import replete.util.NumUtil;

public class GaussianParamSpecDefPanel extends ParamSpecDefEditPanel {


    ///////////
    // FIELD //
    ///////////

    private JTextField txtValue;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public GaussianParamSpecDefPanel() {
        // TODO: This is really more of a class-level thing, but
        // how to get polymorphism to work at that level?
        GaussianParameterSpecification spec = new GaussianParameterSpecification(null);

        Lay.BLtg(this,
            "N", Lay.BL(
                "W", Lay.lb(ImageUtil.getImage("inf.gif"), "valign=top,eb=5r"),
                "C", Lay.lb("<html>" + spec.getDescription() + "</html>")
            ),
            "C", Lay.BL(
                "N", Lay.FL("L",
                    Lay.lb("Value:", "eb=10r,dim=[50,30]"),
                    Lay.hn(txtValue = new SelectAllTextField(), "dim=[100,30],size=16")
                ),
                "eb=10t"
            )
        );
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    @Override
    public ParameterSpecification getSpecification() {
        return new GaussianParameterSpecification(
            Double.parseDouble(txtValue.getText().trim()));
    }

    @Override
    public String getValidationMsg() {
        if(!NumUtil.isDouble(txtValue.getText().trim())) {
            txtValue.requestFocusInWindow();
            return "Invalid value.  Constant value must be a number.";
        }
        return null;
    }

    @Override
    public void setSpecification(ParameterSpecification spec) {
        GaussianParameterSpecification xspec = (GaussianParameterSpecification) spec;
        txtValue.setText(xspec.getValue().toString());
    }
}

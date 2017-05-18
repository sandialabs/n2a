/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ui.ensemble.specs;

import gov.sandia.n2a.parms.ParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.StepParameterSpecification;
import gov.sandia.umf.platform.ui.ensemble.images.ImageUtil;

import javax.swing.JTextField;

import replete.gui.controls.SelectAllTextField;
import replete.util.Lay;
import replete.util.NumUtil;

public class StepParamSpecDefPanel extends ParamSpecDefEditPanel {


    ///////////
    // FIELD //
    ///////////

    private JTextField txtStart;
    private JTextField txtDelta;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public StepParamSpecDefPanel() {
        // TODO: This is really more of a class-level thing, but
        // how to get polymorphism to work at that level?
        StepParameterSpecification spec = new StepParameterSpecification(null, null);

        Lay.BLtg(this,
            "N", Lay.BL(
                "W", Lay.lb(ImageUtil.getImage("inf.gif"), "valign=top,eb=5r"),
                "C", Lay.lb("<html>" + spec.getDescription() + "</html>")
            ),
            "C", Lay.BL(
                "N", Lay.FL("L",
                    Lay.lb("Start:", "eb=10r,dim=[50,30]"),
                    Lay.hn(txtStart = new SelectAllTextField(), "dim=[100,30],size=16")
                ),
                "C", Lay.BL(
                    "N", Lay.FL("L",
                        Lay.lb("Delta:", "eb=10r,dim=[50,30]"),
                        Lay.hn(txtDelta = new SelectAllTextField(), "dim=[100,30],size=16")
                    )
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
        return new StepParameterSpecification(
            Double.parseDouble(txtStart.getText().trim()),
            Double.parseDouble(txtDelta.getText().trim()));
    }

    @Override
    public String getValidationMsg() {
        if(!NumUtil.isDouble(txtStart.getText().trim())) {
            txtStart.requestFocusInWindow();
            return "Invalid value.  Start value must be a number.";
        }
        if(!NumUtil.isDouble(txtDelta.getText().trim())) {
            txtDelta.requestFocusInWindow();
            return "Invalid value.  Delta value must be a number.";
        }
        return null;
    }

    @Override
    public void setSpecification(ParameterSpecification spec) {
        StepParameterSpecification xspec = (StepParameterSpecification) spec;
        txtStart.setText(xspec.getStart().toString());
        txtDelta.setText(xspec.getDelta().toString());
    }
}

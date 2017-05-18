/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ui.ensemble.specs;

import gov.sandia.n2a.parms.ParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.ConstantParameterSpecification;
import gov.sandia.umf.platform.ui.ensemble.images.ImageUtil;

import javax.swing.JTextField;

import replete.gui.controls.SelectAllTextField;
import replete.util.Lay;
import replete.util.NumUtil;

public class ConstParamSpecDefPanel extends ParamSpecDefEditPanel {


    ///////////
    // FIELD //
    ///////////

    private JTextField txtValue;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ConstParamSpecDefPanel() {
        // TODO: This is really more of a class-level thing, but
        // how to get polymorphism to work at that level?
        ConstantParameterSpecification spec = new ConstantParameterSpecification(null);

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
        String txt = txtValue.getText().trim();
        Object value;
        if(NumUtil.isDouble(txt)) {
            value = Double.parseDouble(txt);
        } else {
            value = txt;
        }
        return new ConstantParameterSpecification(value);
    }

    @Override
    public String getValidationMsg() {
//        if(!NumUtil.isDouble(txtValue.getText().trim())) {
//            txtValue.requestFocusInWindow();
//            return "Invalid value.  Constant value must be a number.";
//        }
        return null;
    }

    @Override
    public void setSpecification(ParameterSpecification spec) {
        ConstantParameterSpecification xspec = (ConstantParameterSpecification) spec;
        txtValue.setText(xspec.getValue().toString());
    }
}

/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ui.ensemble;

import gov.sandia.umf.platform.ui.ensemble.util.FadedBottomBorder;

import java.awt.Color;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import replete.util.Lay;

public class ConstantGroupInfoPanel extends RoundedSpecPanel {


    ////////////
    // FIELDS //
    ////////////

    public static Color defaultInnerColor = Lay.clr("FBC6BA");


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ConstantGroupInfoPanel() {
        pnlGroup.setBackground(defaultInnerColor);

        JPanel pnlTop;
        Lay.BLtg(pnlGroup,
            "N", pnlTop = Lay.BL(
                "C", Lay.FL("L", "hgap=0",
                    Lay.lb("Default Values", "size=16,eb=20r"),
                    "opaque=false"
                ),
                "opaque=false,eb=5"
            ),
            "C", Lay.BL(
                "C", Lay.lb("<html>Unless specified otherwise, parameters will<br>be set to their default values.</html>"),
                "opaque=false,eb=5br10l"
            ),
            "opaque=false"
        );

        pnlTop.setBorder(
            BorderFactory.createCompoundBorder(
                new FadedBottomBorder(2, Color.black),
                pnlTop.getBorder()));

        Lay.BLtg(this,
            "C", pnlGroup
        );
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public Color getDefaultInnerRoundedColor() {
        return defaultInnerColor;
    }
}

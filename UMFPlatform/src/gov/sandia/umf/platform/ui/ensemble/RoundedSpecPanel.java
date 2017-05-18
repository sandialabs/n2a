/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ui.ensemble;

import gov.sandia.umf.platform.ui.ensemble.util.RoundedPanel;

import java.awt.Color;

import javax.swing.JPanel;

public abstract class RoundedSpecPanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    protected RoundedPanel pnlGroup = new RoundedPanel();
    private Color regular = new JPanel().getBackground();

    public abstract Color getDefaultInnerRoundedColor();


    //////////////
    // MUTATORS //
    //////////////

    public void highlight0() {
        setBackground(regular);
        pnlGroup.setBackground(getDefaultInnerRoundedColor());
    }
    public void highlight1(Color surroundingHighlight) {
        setBackground(surroundingHighlight);
        pnlGroup.setBackground(getDefaultInnerRoundedColor());
    }
    public void highlight2(Color innerHighlight) {
        setBackground(regular);
        pnlGroup.setBackground(innerHighlight);
    }
}

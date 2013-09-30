/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.home;

import javax.swing.Box;
import javax.swing.JPanel;

import replete.util.Lay;

public class StatisticsPanel extends JPanel {
    public StatisticsPanel() {
//        Query query = Query.create().notsw("Name", "$");
        Lay.BxLtg(this, "Y",
            createNumberPanel("Parts", 238/*PartX.get(query).size()*/),  // Hardcoded numbers for examples.
            createNumberPanel("Models", 64/*Model.get(query).size()*/),
            createNumberPanel("References", 210/*ReferenceX.get().size()*/),
            createNumberPanel("Users", 36/*Profile.get().size()*/),
            Box.createVerticalGlue(),
            "bg=white,augb=mb(1,black)"
        );
    }

    private JPanel createNumberPanel(String name, Integer num) {
        return Lay.BL(
            "C", Lay.lb(name, "eb=5l"),
            "E", Lay.lb("<html><b>" + num + "</b></html>", "eb=5r"),
            "alignx=0.5,dimh=25,opaque=false"
        );
    }
}

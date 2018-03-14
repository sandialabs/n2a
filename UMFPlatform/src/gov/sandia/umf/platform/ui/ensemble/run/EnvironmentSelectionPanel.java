/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ui.ensemble.run;

import gov.sandia.n2a.execenvs.HostSystem;
import gov.sandia.umf.platform.ui.ensemble.UIController;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import replete.gui.controls.WComboBox;
import replete.util.Lay;

public class EnvironmentSelectionPanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    // Core

    private UIController uiController;

    // UI

    //private SimulatorRunParametersPanel pnlDetails;
    private JPanel pnlCenter;
    private JComboBox cboEnvironments;
    private DefaultComboBoxModel mdlEnvironments;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public EnvironmentSelectionPanel(CreateRunEnsembleDialog parentRef, UIController uic, HostSystem[] envs, HostSystem defaultEnv) {
        uiController = uic;

        // Populate simulator combo box model.
        mdlEnvironments = new DefaultComboBoxModel();

        for(HostSystem env : envs) {
            mdlEnvironments.addElement(env);
        }

        // Set up combo box.
        cboEnvironments = new WComboBox(mdlEnvironments);
        if(defaultEnv != null && mdlEnvironments.getIndexOf(defaultEnv) != -1) {
            cboEnvironments.setSelectedItem(defaultEnv);
        } else if(envs.length != 0) {
            cboEnvironments.setSelectedItem(envs[0]);
        }

        Lay.BLtg(this,
            "N", Lay.BL(
                "W", Lay.FL("L",
                    Lay.hn(HelpLabels.createLabelPanel(parentRef, "Environment", "part-name")),
                    cboEnvironments),
                "C", Lay.lb(" ")
            ),
            "C", Lay.p()/*Lay.BL(
                "N", Lay.hn(createLabelPanel("Parameters", "model-runs"), "pref=[10,25]"),
                "C", pnlCenter = Lay.BL(

                    // "C" Populated Later

                    "bg=[200,200,255]",
                    "augb=mb(1,black)"//,augb=eb(10lr)"
                ),
                "eb=5rlb"
            )*/
        );
    }

    public HostSystem getEnvironment() {
        return (HostSystem) cboEnvironments.getSelectedItem();
    }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.run;

import gov.sandia.umf.platform.plugins.extpoints.Backend;
import gov.sandia.umf.platform.ui.HelpLabels;
import gov.sandia.umf.platform.ui.MainFrame;
import gov.sandia.umf.platform.ui.UIController;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.gui.controls.WComboBox;
import replete.util.Lay;

public class SimulatorSelectionPanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    // Core

    private UIController uiController;

    // UI

    private JPanel pnlCenter;
    private JComboBox cboSimulators;
    private DefaultComboBoxModel mdlSimulators;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public SimulatorSelectionPanel(CreateRunEnsembleDialog parentRef, UIController uic, Backend[] simulators, Backend defaultSimulator) {
        uiController = uic;

        // Populate simulator combo box model.
        mdlSimulators = new DefaultComboBoxModel();
        for(Backend simulator : simulators) {
            mdlSimulators.addElement(new SimulatorWrapper(simulator));
        }

        // Set up combo box.
        cboSimulators = new WComboBox(mdlSimulators);
        if(defaultSimulator != null && mdlSimulators.getIndexOf(defaultSimulator) != -1) {
            cboSimulators.setSelectedItem(defaultSimulator);
        } else if(simulators.length != 0) {
            cboSimulators.setSelectedItem(simulators[0]);
        }
        cboSimulators.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                fireSimulatorChangeNotifier();
            }
        });

        Lay.BLtg(this,

            "N", Lay.BL(
                "W", Lay.FL("L",
                    Lay.hn(HelpLabels.createLabelPanel(uiController, parentRef, "Simulator", "part-name")),
                    cboSimulators),
                "C", Lay.lb(" ")
            )
        );
    }

    protected JPanel createLabelPanel(String text, String helpKey) {
        return HelpLabels.createLabelPanel(uiController, MainFrame.getInstance(), text, helpKey);
    }

    public Backend getSimulator() {
        return ((SimulatorWrapper) cboSimulators.getSelectedItem()).simulator;
    }


    //////////////
    // NOTIFIER //
    //////////////

    private ChangeNotifier simulatorChangedNotifier = new ChangeNotifier(this);
    public void addSimulatorChangeListener(ChangeListener listener) {
        simulatorChangedNotifier.addListener(listener);
    }
    public void fireSimulatorChangeNotifier() {
        simulatorChangedNotifier.fireStateChanged();
    }


    /////////////////
    // INNER CLASS //
    /////////////////

    public class SimulatorWrapper {
        public Backend simulator;
        public SimulatorWrapper(Backend sim) {
            simulator = sim;
        }
        @Override
        public String toString() {
            return simulator.getName();
        }
    }
}

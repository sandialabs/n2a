/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.ensemble.run;

import gov.sandia.n2a.execenvs.ExecutionEnv;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.ui.HelpLabels;
import gov.sandia.umf.platform.ui.ensemble.images.ImageUtil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.gui.controls.WComboBox;
import replete.util.DateUtil;
import replete.util.Lay;

public class SetupPanel extends JPanel
{
    private JTextField txtLabel;
    private JComboBox cboSimulators;
    private DefaultComboBoxModel mdlSimulators;
    private JComboBox cboEnvironments;
    private DefaultComboBoxModel mdlEnvironments;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public SetupPanel(CreateRunEnsembleDialog parentRef,
            /*TEMP*/String modName, String modOwner, long modLm,/*TEMP*/
            Backend[] simulators, Backend defaultSimulator,
            ExecutionEnv[] envs, ExecutionEnv defaultEnv) {
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

        // Populate simulator combo box model.
        mdlEnvironments = new DefaultComboBoxModel();

        for(ExecutionEnv env : envs) {
            mdlEnvironments.addElement(env);
        }

        // Set up combo box.
        cboEnvironments = new WComboBox(mdlEnvironments);
        if(defaultEnv != null && mdlEnvironments.getIndexOf(defaultEnv) != -1) {
            cboEnvironments.setSelectedItem(defaultEnv);
        } else if(envs.length != 0) {
            cboEnvironments.setSelectedItem(envs[0]);
        }

//        UIDebugUtil.enableColor();

//        EnvironmentSelectionPanel pnlEnvironment =
//            new EnvironmentSelectionPanel(parentRef, uic, envs, defaultEnv);
//        SimulatorSelectionPanel pnlSimulator =
//            new SimulatorSelectionPanel(parentRef, uic, simulators, defaultSimulator);

        Lay.BxLtg(this, "Y",
            Lay.p(
                Lay.BxL("Y",
                    Lay.FL("L",
                        Lay.lb("For Model:",
                            ImageUtil.getImage("model.gif")),
                        "hgap=0,vgap=0,alignx=0"
                    ),
                    Lay.BxL("Y",
                        Lay.BL("W", Lay.lb("Name:", "prefw=110"), "C", Lay.lb(modName)),
                        Lay.BL("W", Lay.lb("Owner:", "prefw=110"), "C", Lay.lb(modOwner)),
                        Lay.BL("W", Lay.lb("Last Modified:", "prefw=110"), "C", Lay.lb(DateUtil.toLongString(modLm))),
                        "eb=45l10t,alignx=0"
                    ),
                    "eb=10b,augb=mb(1b,black)"
                ),
                "maxh=300,eb=10tlr"
            ),
            Lay.p(
                Lay.BxL("Y",
                    Lay.FL("L",
                        Lay.hn(
                            HelpLabels.createLabelPanel(parentRef,
                                "<html>Run Label <i>(optional)</i>:</html>", "part-name", ImageUtil.getImage("rename2.gif")),
                            "prefw=155"
                        ),
                        txtLabel = Lay.tx("", "selectall,prefw=250,prefh=25"),
                        "hgap=0,vgap=0"
                    ),
                    "eb=10b,augb=mb(1b,black)"
                ),
                "maxh=300,eb=10tlr"
            ),
            Lay.p(
                Lay.BxL("Y",
                    Lay.FL("L",
                        Lay.hn(
                            HelpLabels.createLabelPanel(parentRef,
                                "Environment", "part-name", ImageUtil.getImage("world.gif")),
                            "prefw=155"),
                        Lay.hn(cboEnvironments, "prefw=250"),
                        "hgap=0,vgap=0"
                    ),
                    Lay.BL(
                        "W", Lay.lb(ImageUtil.getImage("inf.gif"),"valign=top,eb=3r"),
                        "C", Lay.lb("<html>Environments can be configured from...</html>"),
                        "eb=30l10t"
                    ),
                    "eb=10b,augb=mb(1b,black)"
                ),
                "maxh=300,eb=10tlr"
            ),
            Lay.p(
                Lay.BxL("Y",
                    Lay.FL("L",
                        Lay.hn(
                            HelpLabels.createLabelPanel(parentRef,
                                "Simulator", "part-name", ImageUtil.getImage("job.gif")),
                            "prefw=155"),
                        Lay.hn(cboSimulators, "prefw=250"),
                        "hgap=0,vgap=0"
                    ),
                    Lay.BL(
                        "W", Lay.lb(ImageUtil.getImage("inf.gif"),"valign=top,eb=3r"),
                        "C", Lay.lb("<html>Simulation parameters can be set from the <i>Parameterization</i> tab.  Also, the simulator determines which output parameters are available for this model.</html>"),
                        "eb=30l10t"
                    ),
                    "eb=10b,augb=mb(1b,black)"
                ),
                "maxh=300,eb=10tlr"
            ),
            Box.createVerticalGlue()
        );

    }

//    protected JPanel createLabelPanel(String text, String helpKey) {
//        return HelpLabels.createLabelPanel(uiController, MainFrame.getInstance(), text, helpKey);
//    }

    public String getLabel() {
        return txtLabel.getText();
    }
    public Backend getSimulator() {
        return ((SimulatorWrapper) cboSimulators.getSelectedItem()).simulator;
    }
    public ExecutionEnv getEnvironment() {
        return (ExecutionEnv) cboEnvironments.getSelectedItem();
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


    //////////
    // MISC //
    //////////

    public void focus() {
        txtLabel.requestFocusInWindow();
    }
}

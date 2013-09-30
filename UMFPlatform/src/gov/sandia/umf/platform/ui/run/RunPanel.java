/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.run;

import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.Parameterizable;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.ensemble.FixedParameterSpacePanel;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;
import gov.sandia.umf.platform.ui.ensemble.images.ImageUtil;

import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.gui.tabbed.AdvancedTabbedPane;
import replete.util.Lay;

public class RunPanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    private AdvancedTabbedPane tabRun;
    private SetupPanel pnlSetup;
    private FixedParameterSpacePanel pnlParams;
    private OutputParameterPanel pnlOutputs;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public RunPanel(CreateRunEnsembleDialog parentRef, UIController uic, long estDur,
            /*TEMP*/String modName, String modOwner, long modLm,/*TEMP*/
            final Object model, Simulator[] simulators, Simulator defaultSimulator,
            ExecutionEnv[] envs, ExecutionEnv defaultEnv) {

        ParameterDomain domains = ((Parameterizable) model).getAllParameters();

        pnlSetup = new SetupPanel(parentRef, uic, modName, modOwner, modLm, simulators, defaultSimulator, envs, defaultEnv);
        pnlParams = new FixedParameterSpacePanel(domains, estDur);
        pnlOutputs = new OutputParameterPanel();

        Lay.BLtg(this,
            "C", tabRun = Lay.TBL(JTabbedPane.LEFT,
                "Setup", ImageUtil.getImage("model.gif"), pnlSetup,
                "Parameterization", ImageUtil.getImage("paramz.gif"), pnlParams,
                "Outputs", ImageUtil.getImage("outputs.gif"), pnlOutputs,
                "borders"
            ),
            "bg=100,opaque=true"
        );

        pnlSetup.addSimulatorChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                Simulator sim = pnlSetup.getSimulator();
                pnlParams.setSimulationInputParameters(sim.getSimulatorParameters());
                ParameterDomain domain = sim.getOutputVariables(model);  // TODO?
                pnlOutputs.setOutputParameters(domain);
            }
        });
        pnlSetup.fireSimulatorChangeNotifier();

        pnlParams.addErrorListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                fireErrorNotifier();
            }
        });
    }


    //////////////
    // NOTIFIER //
    //////////////

    protected ChangeNotifier errorNotifier = new ChangeNotifier(this);
    public void addErrorListener(ChangeListener listener) {
        errorNotifier.addListener(listener);
    }
    protected void fireErrorNotifier() {
        errorNotifier.fireStateChanged();
    }


    ///////////////
    // ACCESSORS //
    ///////////////

    public String getLabel() {
        return pnlSetup.getLabel();
    }
    public ExecutionEnv getEnvironment() {
        return pnlSetup.getEnvironment();
    }
    public Simulator getSimulator() {
        return pnlSetup.getSimulator();
    }
    public ParameterSpecGroupSet getParameterSpecGroupSet() {
        return pnlParams.getParameterSpecGroupSet();
    }
    public List<String> getSelectedOutputExpressions() {
        return pnlOutputs.getSelectedOutputExpressions();
    }
    public Exception getError() {
        return pnlParams.getError();
    }


    //////////
    // MISC //
    //////////

    public void focus() {
        pnlSetup.focus();
    }
}

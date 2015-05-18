/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.model;

import gov.sandia.n2a.data.ModelOrient;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.plugins.Run;
import gov.sandia.umf.platform.plugins.RunOrient;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;
import gov.sandia.umf.platform.ui.HelpCapableWindow;
import gov.sandia.umf.platform.ui.HelpLabels;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;

import replete.gui.controls.SelectAllTextField;
import replete.gui.controls.WComboBox;
import replete.gui.controls.mnemonics.MButton;
import replete.gui.windows.EscapeDialog;
import replete.plugins.ExtensionPoint;
import replete.plugins.PluginManager;
import replete.util.Lay;
import replete.util.User;

public class CreateRunDialog extends EscapeDialog implements HelpCapableWindow {

    ////////////
    // FIELDS //
    ////////////

    // Const

    public static final int CREATE = 0;
    public static final int CANCEL = 1;

    // Core
    private UIController uiController;
    private ModelOrient model;

    // UI

    private JComboBox cboSimlulators;
    private JComboBox cboEnvironments;
    private JTextField txtName;
    private JTextField txtSimLength;

    // Misc
    public int result = CANCEL;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public CreateRunDialog(JFrame parent, UIController uic, final ModelOrient mdl) {
        super(parent, "Create New Run", true);
        uiController = uic;
        model = mdl;
        setIconImage(ImageUtil.getImage("runadd.gif").getImage());

        MButton btnCreate = new MButton("C&reate", ImageUtil.getImage("run.gif"));
        btnCreate.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                result = CREATE;
                closeDialog();
            }
        });

        MButton btnCancel = new MButton("&Cancel", ImageUtil.getImage("cancel.gif"));
        btnCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                closeDialog();
            }
        });

        List<ExtensionPoint> exts = PluginManager.getExtensionsForPoint(Simulator.class);
        DefaultComboBoxModel simModel = new DefaultComboBoxModel();
        SimWrapper selSW = null;
        for(ExtensionPoint ext : exts) {
        	SimWrapper sw = new SimWrapper((Simulator)ext);
        	simModel.addElement(sw);
        	if(sw.s.getName().contains("C")) {
        		selSW = sw;
        	}
        }

        Lay.BLtg(this,
            "C", Lay.BxL("Y",
                Lay.hn(createLabelPanel("Simulator", "model-run-sim"), "alignx=0,pref=[10,25],opaque=false"),
                Lay.hn(cboSimlulators = new WComboBox(simModel), "alignx=0,pref=[10,30]"),
                Lay.hn(createLabelPanel("Environment", "model-run-env"), "alignx=0,pref=[10,25],opaque=false"),
                Lay.hn(cboEnvironments = new WComboBox(new DefaultComboBoxModel(ExecutionEnv.envs.toArray())), "alignx=0,pref=[10,30]"),
                Lay.hn(createLabelPanel("Name", "model-run-name"), "alignx=0,pref=[10,25]"),
                Lay.hn(txtName = new SelectAllTextField(), "alignx=0,pref=[10,30]"),
                Lay.hn(createLabelPanel("Simulation Length", "model-run-duration"), "alignx=0,pref=[10,25]"),
                Lay.hn(txtSimLength = new SelectAllTextField(), "alignx=0,pref=[10,30]"),
                "bg=[183,224,255],eb=5lr7b,augb=mb(1,black)"
            ),
            "S", Lay.BL(
                "C", Lay.lb(" "),
                "E", Lay.FL(btnCreate, btnCancel),
                "eb=2"
            ),
            "size=[700,300],center"
        );

        if(selSW != null) {
        	cboSimlulators.setSelectedItem(selSW);
        }

        getRootPane().setDefaultButton(btnCreate);
        txtName.requestFocusInWindow();
    }

    public int getResult() {
        return result;
    }

    public ExecutionEnv getEnvironment() {
        return (ExecutionEnv) cboEnvironments.getSelectedItem();
    }

    public Run getRun ()
    {
        Double length;
        try
        {
            length = Double.parseDouble(txtSimLength.getText());
        }
        catch (NumberFormatException e)
        {
            length = 0.0;
        }
        Simulator sim = ((SimWrapper) cboSimlulators.getSelectedItem()).s;
        Run run = new RunOrient(length, txtName.getText(), null, sim, User.getName(), "Pending", null, model.getSource());
        return run;
    }

    protected JPanel createLabelPanel(String text, String helpKey) {
        return HelpLabels.createLabelPanel(uiController, this, text, helpKey);
    }

    @Override
    public void showHelp(String topic, String content) {
    }

    private class SimWrapper {
    	Simulator s;
    	public SimWrapper(Simulator t) {
    		s = t;
    	}
    	@Override
        public String toString() {
    		return s.getName();
    	}
    }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.ensemble;

import gov.sandia.n2a.parms.ParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.ArbitraryTransformParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.ConstantParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.EvenSpacingParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.GaussianParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.ListParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.StepParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.UniformParameterSpecification;
import gov.sandia.umf.platform.ui.ensemble.images.ImageUtil;
import gov.sandia.umf.platform.ui.ensemble.specs.ArbXformParamSpecDefPanel;
import gov.sandia.umf.platform.ui.ensemble.specs.ConstParamSpecDefPanel;
import gov.sandia.umf.platform.ui.ensemble.specs.EvenSpParamSpecDefPanel;
import gov.sandia.umf.platform.ui.ensemble.specs.GaussianParamSpecDefPanel;
import gov.sandia.umf.platform.ui.ensemble.specs.ListParamSpecDefPanel;
import gov.sandia.umf.platform.ui.ensemble.specs.ParamSpecDefEditPanel;
import gov.sandia.umf.platform.ui.ensemble.specs.StepParamSpecDefPanel;
import gov.sandia.umf.platform.ui.ensemble.specs.UniformParamSpecDefPanel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;

import replete.gui.controls.mnemonics.MButton;
import replete.gui.windows.Dialogs;
import replete.gui.windows.EscapeDialog;
import replete.util.Lay;

public class ParameterSpecEditDialog extends EscapeDialog {


    ////////////
    // FIELDS //
    ////////////

    public static final int CHANGE = 0;
    public static final int CANCEL = 1;

    private int result = CANCEL;
    private ParamSpecDefEditPanel pnlSpecDef;
    private JComboBox cboSpecs;
    private JPanel pnlContainer;
    private ParameterSpecification origSpec;

    private static Map<Class<? extends ParameterSpecification>, Class<? extends ParamSpecDefEditPanel>> specPanels =
        new LinkedHashMap<Class<? extends ParameterSpecification>, Class<? extends ParamSpecDefEditPanel>>();

    static {
        specPanels.put(ArbitraryTransformParameterSpecification.class, ArbXformParamSpecDefPanel.class);
        specPanels.put(ConstantParameterSpecification.class, ConstParamSpecDefPanel.class);
        specPanels.put(EvenSpacingParameterSpecification.class, EvenSpParamSpecDefPanel.class);
        specPanels.put(GaussianParameterSpecification.class, GaussianParamSpecDefPanel.class);
        specPanels.put(ListParameterSpecification.class, ListParamSpecDefPanel.class);
        specPanels.put(StepParameterSpecification.class, StepParamSpecDefPanel.class);
        specPanels.put(UniformParameterSpecification.class, UniformParamSpecDefPanel.class);
    }


    //////////////////
    // CONSTRUCTORS //
    //////////////////

    public ParameterSpecEditDialog(JDialog parent, ParameterSpecification spec) {
        super(parent, "Edit Parameter Specification", true);
        init(spec);
    }

    public ParameterSpecEditDialog(JFrame parent, ParameterSpecification spec) {
        super(parent, "Edit Parameter Specification", true);
        init(spec);
    }

    private void init(ParameterSpecification spec) {
        setIconImage(ImageUtil.getImage("edit.gif").getImage());

        MButton btnChange = new MButton("Ch&ange", ImageUtil.getImage("run.gif"));
        btnChange.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String validationMsg = pnlSpecDef.getValidationMsg();
                if(validationMsg != null) {
                    Dialogs.showError(ParameterSpecEditDialog.this, validationMsg);
                    return;
                }
                result = CHANGE;
                closeDialog();
            }
        });

        MButton btnCancel = new MButton("&Cancel", ImageUtil.getImage("cancel.gif"));
        btnCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                closeDialog();
            }
        });

        try {
            ParamSpecGlob selected = null;
            List<ParamSpecGlob> specChoices = new ArrayList<ParamSpecGlob>();
            for(Class<? extends ParameterSpecification> clazz : specPanels.keySet()) {
                ParamSpecGlob glob = new ParamSpecGlob(clazz);
                specChoices.add(glob);
                if(clazz.equals(spec.getClass())) {
                    selected = glob;
                }
            }
            cboSpecs = new JComboBox(specChoices.toArray());
            cboSpecs.setSelectedItem(selected);
            cboSpecs.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    ParamSpecGlob glob = (ParamSpecGlob) cboSpecs.getSelectedItem();
                    change(glob.paramSpecClass);
                }
            });
        } catch(Exception e) {
            e.printStackTrace();

        }

        Lay.BLtg(this,
            "N", Lay.FL("L", Lay.lb("Specification Type:", "fg=white"), cboSpecs, "mb=[1b,black],bg=5B8FFF"),
            "C", pnlContainer = Lay.p("eb=10"),
            "S", Lay.FL("R", btnChange, btnCancel),
            "size=[400,300],center"
        );

        setDefaultButton(btnChange);

        origSpec = spec;

        change(spec.getClass());
    }

    private void change(Class<? extends ParameterSpecification> specClass) {
        try {
            Class<? extends ParamSpecDefEditPanel> clazz2 = specPanels.get(specClass);
            pnlSpecDef = clazz2.getConstructor(new Class[0]).newInstance();
            if(specClass.equals(origSpec.getClass())) {
                pnlSpecDef.setSpecification(origSpec);
            }
            pnlContainer.removeAll();
            pnlContainer.add(pnlSpecDef);
            pnlContainer.updateUI();
        } catch(Exception e) {
            Dialogs.showDetails(this, "An error has occurred changing the parameter specification.", e);
        }
    }


    ///////////////
    // ACCESSORS //
    ///////////////

    public int getResult() {
        return result;
    }
    public ParameterSpecification getSpecification() {
        return pnlSpecDef.getSpecification();
    }


    private class ParamSpecGlob {
        public Class<? extends ParameterSpecification> paramSpecClass;
        public ParameterSpecification template;
        public ParamSpecGlob(Class<? extends ParameterSpecification> c) {
            paramSpecClass = c;
            try {
                template = paramSpecClass.getConstructor(new Class[0]).newInstance();
            } catch(Exception e) {

            }
        }
        @Override
        public String toString() {
            return template.getShortName();
        }
    }
}

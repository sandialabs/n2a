/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.ensemble;

import gov.sandia.umf.platform.ensemble.params.groups.ParameterSpecGroup;
import gov.sandia.umf.platform.ensemble.params.specs.ConstantParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.EvenSpacingParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.ParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.UniformParameterSpecification;
import gov.sandia.umf.platform.ui.ensemble.domains.Parameter;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterBundle;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;
import gov.sandia.umf.platform.ui.ensemble.images.ImageUtil;
import gov.sandia.umf.platform.ui.ensemble.util.FadedBottomBorder;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.gui.controls.IconButton;
import replete.gui.controls.TimedTextField;
import replete.gui.controls.TimedTextFieldValidator;
import replete.util.Lay;
import replete.util.NumUtil;

public class ParameterSpecGroupPanel extends RoundedSpecPanel {


    //////////
    // ENUM //
    //////////

    private enum GroupType {
        MIXED("Mixed"),
        MONTE_CARLO("Monte Carlo"),
        LATIN_HYPERCUBE("Latin Hypercube");
        private String label;
        private GroupType(String lbl) {
            label = lbl;
        }
        @Override
        public String toString() {
            return label;
        }
    }


    ////////////
    // FIELDS //
    ////////////

    private static Color defaultInnerColor = Lay.clr("B7C4FF");
    public Map<ParameterSpecPanel, JPanel> specPanels = new LinkedHashMap<ParameterSpecPanel, JPanel>();
    private JPanel pnlParams;
    private JLabel lblGroupNum;
    private TimedTextField txtCardinality;
    private int validCardinality = 1;
    private JComboBox cboGroupType;
    private boolean readOnly;


    //////////////
    // NOTIFIER //
    //////////////

    protected ChangeNotifier removeNotifier = new ChangeNotifier(this);
    public void addRemoveListener(ChangeListener listener) {
        removeNotifier.addListener(listener);
    }
    protected void fireRemoveNotifier() {
        removeNotifier.fireStateChanged();
    }
    protected ChangeNotifier changeNotifier = new ChangeNotifier(this);
    public void addChangeListener(ChangeListener listener) {
        changeNotifier.addListener(listener);
    }
    protected void fireChangeNotifier() {
        changeNotifier.fireStateChanged();
    }


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ParameterSpecGroupPanel() {
        this(null, false);
    }
    public ParameterSpecGroupPanel(ParameterSpecGroup group) {
        this(group, false);
    }
    public ParameterSpecGroupPanel(boolean readOnly) {
        this(null, readOnly);
    }
    public ParameterSpecGroupPanel(ParameterSpecGroup group, boolean readOnly) {
        this.readOnly = readOnly;
        pnlGroup.setBackground(defaultInnerColor);
        IconButton btnRemove = null;
        cboGroupType = new JComboBox(GroupType.values());
        JPanel pnlTop;
        JPanel pnlRemove = readOnly ? Lay.p() :
            Lay.p(btnRemove = new IconButton(
                ImageUtil.getImage("remove.gif"), "Remove Group"),
                "opaque=false,eb=5");

        JComponent cmpCard;
        JComponent cmpType;

        if(readOnly) {
            if(group != null) {
                boolean allUniform = true;
                boolean allEvenSp = true;
                for(ParameterSpecification spec : group.values()) {
                    if(!(spec instanceof EvenSpacingParameterSpecification)) {
                        allEvenSp = false;
                    }
                    if(!(spec instanceof UniformParameterSpecification)) {
                        allUniform = false;
                    }
                }
                String type = "Mixed";
                if(allUniform) {
                    type = "Monte Carlo";
                } else if(allEvenSp) {
                    type = "Latin Hypercube";
                }
                cmpType = Lay.lb(type);
                cmpCard = Lay.lb(group.getRunCount());
            } else {
                cmpType = Lay.lb("Mixed");
                cmpCard = Lay.lb("1");
            }
        } else {
            cmpCard = txtCardinality = new TimedTextField("1");
            cmpType = cboGroupType;
        }

        Lay.BLtg(pnlGroup,
            "N", pnlTop = Lay.BL(
                "C", Lay.FL("L", "hgap=0",
                    lblGroupNum = Lay.lb("Group #", "size=16,eb=20r"),
                    Lay.hn(cmpType, "dimw=120,size=14"),
                    Lay.lb("#", "size=16,eb=20l2r"),
                    Lay.hn(cmpCard, "dimw=50,dimh=25,size=16"),
                    "opaque=false"
                ),
                "E", pnlRemove,
                "opaque=false,eb=5"
            ),
            "C", pnlParams = Lay.BxL("Y", "opaque=false,eb=10l14rb"),
            "opaque=false"
        );

        pnlTop.setBorder(
            BorderFactory.createCompoundBorder(
                new FadedBottomBorder(2, Color.black),
                pnlTop.getBorder()));

        Lay.BLtg(this,
            "C", pnlGroup
        );

        if(!readOnly) {
            btnRemove.toImageOnly();
            btnRemove.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    fireRemoveNotifier();
                }
            });
            txtCardinality.setValidTimeout(1000);
            txtCardinality.setReturnToDefaultTimeout(500);
            txtCardinality.setValidator(new TimedTextFieldValidator() {
                public boolean accept(String text) {
                    return NumUtil.isInt(text);
                }
            });
            txtCardinality.addValidTimeoutListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    validCardinality = Integer.parseInt(txtCardinality.getText());
                    fireChangeNotifier();
                }
            });
            cboGroupType.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if(e.getStateChange() == ItemEvent.SELECTED) {
                        GroupType type = (GroupType) cboGroupType.getSelectedItem();
                        if(!type.equals(GroupType.MIXED)) {
                            for(ParameterSpecPanel pnlSpec : specPanels.keySet()) {
                                ParameterSpecification newSpec = null;
                                Class<? extends ParameterSpecification> mustBe = null;
                                if(type.equals(GroupType.MONTE_CARLO)) {
                                    newSpec = new UniformParameterSpecification(0, 10);
                                    mustBe = UniformParameterSpecification.class; //distribution in future?
                                } else if(type.equals(GroupType.LATIN_HYPERCUBE)) {
                                    newSpec = new EvenSpacingParameterSpecification(0, 10);
                                    mustBe = EvenSpacingParameterSpecification.class;
                                } else {
                                    break;  // ERROR STATE MOST LIKELY
                                }
                                if(!(mustBe.isAssignableFrom(pnlSpec.getSpecification().getClass()))) {
                                    pnlSpec.setSpecification(newSpec);
                                }
                            }
                        }
                        fireChangeNotifier();
                    }
                }
            });
        }

        if(group != null) {
            initPanel(group);
        }
    }

    private void initPanel(ParameterSpecGroup group) {
        for(Object key : group.keySet()) {
            ParameterSpecification spec = group.get(key);
            String keyStr = key.toString();
            String[] path = keyStr.split("\\.");
            List<ParameterDomain> d = new ArrayList<ParameterDomain>();
            for(int i = 0; i < path.length - 1; i++) {
                String seg = path[i];
                d.add(new ParameterDomain(seg));
            }
            Parameter param = new Parameter(path[path.length - 1]);//, dv, desc,icon);
            ParameterBundle bundle = new ParameterBundle(d, param);
            addParam(bundle, spec);
        }
    }


    //////////////
    // MUTATORS //
    //////////////

    public String getGroupLabel() {
        return lblGroupNum.getText();
    }
    public void setGroupLabel(int i) {
        lblGroupNum.setText("Group #" + i);
    }

    public ParameterSpecPanel addParam(ParameterBundle bundle) {
        return addParam(bundle, null);
    }
    public ParameterSpecPanel addParam(ParameterBundle bundle, ParameterSpecification useThisSpec) {
        ParameterSpecification spec;
        GroupType type = (GroupType) cboGroupType.getSelectedItem();
        Object defaultValue = bundle.getParameter().getDefaultValue();
        if(useThisSpec == null) {
            spec = chooseDefaultSpecification(type, defaultValue);
        } else {
            spec = useThisSpec;
        }
        final ParameterSpecPanel pnlSpec = new ParameterSpecPanel(bundle, spec, readOnly);
        pnlSpec.addRemoveListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                pnlParams.remove(specPanels.get(pnlSpec));
                specPanels.remove(pnlSpec);
                pnlParams.updateUI();
                fireChangeNotifier();
            }
        });
        pnlSpec.addEditListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                ParameterSpecification spec = pnlSpec.getSpecification();
                GroupType type = (GroupType) cboGroupType.getSelectedItem();
                switch(type) {
                    case LATIN_HYPERCUBE:
                        if(!(spec instanceof EvenSpacingParameterSpecification)) {
                            cboGroupType.setSelectedItem(GroupType.MIXED);
                        }
                        break;
                    case MONTE_CARLO:
                        if(!(spec instanceof EvenSpacingParameterSpecification)) {
                            cboGroupType.setSelectedItem(GroupType.MIXED);
                        }
                        break;
                }
                fireChangeNotifier();
            }
        });
        JPanel cont = Lay.p(pnlSpec, "eb=10t,opaque=false");
        specPanels.put(pnlSpec, cont);
        pnlParams.add(cont);

        updateUI();
        fireChangeNotifier();
        return pnlSpec;
    }

    public ParameterSpecification chooseDefaultSpecification(GroupType type, Object dv) {
        ParameterSpecification spec;
        switch(type) {
            case LATIN_HYPERCUBE:
                if(!(dv instanceof Number)) {
                    dv = 0;
                }
                spec = new EvenSpacingParameterSpecification((Number) dv, ((Number) dv).doubleValue() + 10);
                break;
            case MONTE_CARLO:
                if(!(dv instanceof Number)) {
                    dv = 0;
                }
                spec = new UniformParameterSpecification((Number) dv, ((Number) dv).doubleValue() + 10);
                break;
            default:
                spec = new ConstantParameterSpecification(dv);
                break;
        }
        return spec;
    }

    public boolean has(ParameterBundle bundle) {
        for(ParameterSpecPanel pnlSpec : specPanels.keySet()) {
            ParameterBundle specBundle = pnlSpec.getParamBundle();
            if(bundle.getDomains().equals(specBundle.getDomains()) &&
                            bundle.getParameter().getKey().equals(specBundle.getParameter().getKey())) {
                return true;
            }
        }
        return false;
    }

    public ParameterSpecGroup getParameterSpecGroup() {
        ParameterSpecGroup group = new ParameterSpecGroup(validCardinality);
        for(ParameterSpecPanel pnlSpec : specPanels.keySet()) {
            // TODO: hierarchical?
            Object paramKey = pnlSpec.getParamBundle().getParameter().getKey();
            List<ParameterDomain> domains = pnlSpec.getParamBundle().getDomains();
            ParameterKeyPath keyPath = new ParameterKeyPath();
            for(ParameterDomain domain : domains) {
                keyPath.add(domain.getName());
            }
            keyPath.add(paramKey);
            ParameterSpecification spec = pnlSpec.getSpecification();
            group.add(keyPath, spec);
        }
        return group;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public Color getDefaultInnerRoundedColor() {
        return defaultInnerColor;
    }
}

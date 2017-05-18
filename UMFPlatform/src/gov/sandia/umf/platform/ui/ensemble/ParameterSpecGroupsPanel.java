/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ui.ensemble;

import gov.sandia.n2a.parms.ParameterBundle;
import gov.sandia.umf.platform.ensemble.params.groups.ParameterSpecGroup;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;

import java.awt.Component;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.gui.uidebug.DebugPanel;
import replete.util.Lay;

public class ParameterSpecGroupsPanel extends DebugPanel {


    ////////////
    // FIELDS //
    ////////////

    // Simply maintain separate list of components for simplicity.
    public Map<RoundedSpecPanel, JPanel> groupPanels = new LinkedHashMap<RoundedSpecPanel, JPanel>();
    private Component glue = Box.createVerticalGlue();
    private Component strut = Box.createVerticalStrut(10);


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ParameterSpecGroupsPanel() {
        Lay.BxLtg(this, "Y");
        addPanel(new ConstantGroupInfoPanel());
    }

    public ParameterSpecGroupsPanel(ParameterSpecGroupSet groups) {
        Lay.BxLtg(this, "Y");
        for(ParameterSpecGroup group : groups) {
            if(group != groups.getDefaultValueGroup()) {
                ParameterSpecGroupPanel pnlGroup = new ParameterSpecGroupPanel(group, true);
                addGroupPanel(pnlGroup);
            }
        }
    }


    //////////////
    // NOTIFIER //
    //////////////

    private ChangeNotifier changeNotifier = new ChangeNotifier(this);
    public void addChangeListener(ChangeListener listener) {
        changeNotifier.addListener(listener);
    }
    private void fireChangeNotifier() {
        changeNotifier.fireStateChanged();
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public ParameterSpecGroupSet getParameterSpecGroupSet() {
        ParameterSpecGroupSet groupSet = new ParameterSpecGroupSet();
        for(RoundedSpecPanel pnlRounded : groupPanels.keySet()) {
            if(pnlRounded instanceof ParameterSpecGroupPanel) {
                ParameterSpecGroupPanel pnlGroup = (ParameterSpecGroupPanel) pnlRounded;
                ParameterSpecGroup group = pnlGroup.getParameterSpecGroup();
                groupSet.add(group);
            }
        }
        return groupSet;
    }

    public void addGroupPanel(final ParameterSpecGroupPanel pnlGroup) {
        pnlGroup.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                // Happens when the combo box, text field, or individual
                // specifications change.
                fireChangeNotifier();
            }
        });
        pnlGroup.addRemoveListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                remove(groupPanels.get(pnlGroup));
                updateUI();
                groupPanels.remove(pnlGroup);
                updateNumbers();
                fireChangeNotifier();
            }
        });
        addPanel(pnlGroup);
    }

    public void addPanel(RoundedSpecPanel pnlRounded) {
        JPanel pnlCont;
        remove(glue);
        remove(strut);
        String extra = pnlRounded instanceof ConstantGroupInfoPanel ? "dimh=120" : "maxh=90000,pxrefh=200";//"minh=140,prefh=140";// could be abstract method on RoundedSpecPanel
        add(pnlCont = Lay.p(pnlRounded, "eb=10ltr,opaque=false", extra));
        groupPanels.put(pnlRounded, pnlCont);
        add(strut);
        add(glue);
        updateUI();
        updateNumbers();
        fireChangeNotifier();
    }

    private void updateNumbers() {
        int g = 1;
        for(RoundedSpecPanel pnlRounded : groupPanels.keySet()) {
            if(pnlRounded instanceof ParameterSpecGroupPanel) {
                ParameterSpecGroupPanel pnlGroup = (ParameterSpecGroupPanel) pnlRounded;
                pnlGroup.setGroupLabel(g++);
            }
        }
    }

    public ParameterSpecGroupPanel getGroupPanelForParam(ParameterBundle bundle) {
        for(RoundedSpecPanel pnlRounded : groupPanels.keySet()) {
            if(pnlRounded instanceof ParameterSpecGroupPanel) {
                ParameterSpecGroupPanel pnlGroup = (ParameterSpecGroupPanel) pnlRounded;
                if(pnlGroup.has(bundle)) {
                    return pnlGroup;
                }
            }
        }
        return null;
    }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.ui;

import gov.sandia.umf.platform.ui.UIController;

import java.awt.Component;

import javax.swing.JTabbedPane;

import replete.gui.tabbed.AdvancedTabbedPane;

public abstract class TabbedRecordEditPanel extends RecordEditPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    protected AdvancedTabbedPane tabSections;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public TabbedRecordEditPanel(UIController uic, NDoc doc) {
        super(uic, doc);
        tabSections = new AdvancedTabbedPane(JTabbedPane.LEFT, false);
        tabSections.setOpaque(false);
    }


    /////////////
    // MUTATOR //
    /////////////

    protected void addTab(String name, Component cmp) {
        tabSections.add(name, cmp);
    }


    //////////
    // MISC //
    //////////

    // cew 130327
    @Override
    protected void reload() {
        for(int c = 0; c < tabSections.getTabCount(); c++) {
            Component cmp = tabSections.getComponentAt(c);
            if(cmp instanceof RecordEditDetailPanel) {
                ((RecordEditDetailPanel) cmp).reload();
            }
        }
        btnReload.setEnabled(getRecord().isPersisted());
    }

}
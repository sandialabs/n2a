/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.ui;

import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.HelpLabels;
import gov.sandia.umf.platform.ui.MainFrame;
import gov.sandia.umf.platform.ui.UIController;
import java.awt.Color;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;

public abstract class RecordEditDetailPanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    // Const

    protected static final Color DARK_BLUE = RecordEditPanel.DARK_BLUE;
    protected static final String SPC = RecordEditPanel.SPC;

    // Core

    protected UIController uiController;
    protected MNode record;

    // Every detail panel has a reference to the shared
    // part object and can edit it at will.


    //////////////
    // NOTIFIER //
    //////////////

    protected ChangeNotifier contentNotifier = new ChangeNotifier(this);
    public void addContentChangedListener(ChangeListener listener) {
        contentNotifier.addListener(listener);
    }
    protected void fireContentChangedNotifier() {
        contentNotifier.fireStateChanged();
    }


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public RecordEditDetailPanel(UIController uic, MNode record2) {
        uiController = uic;
        record = record2;
    }


    /////////////
    // MUTATOR //
    /////////////

    public void setRecord(MNode p) {
        record = p;
        reload();
    }


    /////////////////////////
    // ABSTRACT / SKELETON //
    /////////////////////////

    public abstract void reload();
    public void postLayout() {}


    ////////////////////////////
    // CONVENIENCE UI METHODS //
    ////////////////////////////

    // These methods allow all edit panels to have consistent controls.

    protected JPanel createLabelPanel(String text, String helpKey) {
        return HelpLabels.createLabelPanel(uiController, MainFrame.getInstance(), text, helpKey);
    }

    protected JButton createHelpIcon(String helpKey) {
        return HelpLabels.createHelpIcon(uiController, MainFrame.getInstance(), helpKey);
    }
}

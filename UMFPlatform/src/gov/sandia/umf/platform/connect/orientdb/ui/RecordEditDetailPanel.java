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
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;

public abstract class RecordEditDetailPanel extends JPanel
{
    protected UIController uiController;
    protected MNode record;

    protected ChangeNotifier contentNotifier = new ChangeNotifier (this);

    public void addContentChangedListener (ChangeListener listener)
    {
        contentNotifier.addListener (listener);
    }

    protected void fireContentChangedNotifier ()
    {
        contentNotifier.fireStateChanged ();
    }

    public RecordEditDetailPanel (UIController uic, MNode record2)
    {
        uiController = uic;
        record = record2;
    }

    public void setRecord (MNode p)
    {
        record = p;
        reload ();
    }

    public abstract void reload ();

    public void postLayout ()
    {
    }

    // These methods allow all edit panels to have consistent controls.

    protected JPanel createLabelPanel (String text, String helpKey)
    {
        return HelpLabels.createLabelPanel (uiController,
                MainFrame.getInstance (), text, helpKey);
    }

    protected JButton createHelpIcon (String helpKey)
    {
        return HelpLabels.createHelpIcon (uiController,
                MainFrame.getInstance (), helpKey);
    }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.ui;

import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import replete.gui.controls.IconButton;
import replete.util.Lay;


public abstract class RecordEditPanel extends JPanel
{
    public static final Color DARK_BLUE = new Color(0, 0, 150);
    public static final String SPC = "    ";

    protected UIController uiController;
    protected MNode record;
    protected JLabel lblTitle;

    public RecordEditPanel (UIController uic, MNode rec)
    {
        uiController = uic;
        record = rec;
    }

    protected JPanel createRecordControlsPanel ()
    {
        JButton btnDelete = new IconButton (ImageUtil.getImage ("remove.gif"), "Delete", 2);
        btnDelete.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                doDelete ();
            }
        });

        JPanel pnlRecordControls = Lay.BL (
            "C", lblTitle = Lay.lb ("", "fg=white,opaque=false"),
            "E", Lay.FL ("R", new JLabel (" "), btnDelete, "opaque=false"),
            "bg=100,augb=mb(3b,black)"
        );
        lblTitle.setFont (lblTitle.getFont ().deriveFont (20.0F).deriveFont (Font.ITALIC));

        updateRecordTitle ();

        return pnlRecordControls;
    }

    public void updateRecordTitle ()
    {
        lblTitle.setText (record.getOrDefault ("Untitled", "$metadata", "name"));
    }

    public void doInitialFocus ()
    {
    }

    protected void reload ()
    {
    }

    public void postLayout ()
    {
    }

    protected void doDelete ()
    {
        uiController.delete (record);
    }

    public void loadFromRecord (MNode doc)
    {
        record = doc;
        reload ();
        updateRecordTitle ();
    }

    public MNode getRecord ()
    {
        return record;
    }
}
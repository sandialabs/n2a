/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.ref;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditDetailPanel;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;

import replete.event.ChangeNotifier;
import replete.gui.controls.ChangedDocumentListener;
import replete.gui.controls.IconButton;
import replete.gui.controls.SelectAllTextField;
import replete.util.Lay;

public class GeneralDetailPanel extends RecordEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private JTextField txtTitle;
    private JTextField txtAuthors;
    private JTextField txtYear;
    private JLabel lblOwner;
    private JLabel lblUID;
    private IconButton btnOwnerDetails;
    private boolean noFire;


    //////////////
    // NOTIFIER //
    //////////////

    protected ChangeNotifier nameChangedFocusLostNotifier = new ChangeNotifier(this);
    public void addNameChangedFocusLostListener(ChangeListener listener) {
        nameChangedFocusLostNotifier.addListener(listener);
    }
    protected void fireNameChangedFocusLostNotifier() {
        nameChangedFocusLostNotifier.fireStateChanged();
    }


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public GeneralDetailPanel(UIController uic, NDoc r) {
        super(uic, r);

        btnOwnerDetails = new IconButton(ImageUtil.getImage("user.png"), "Show User Details");
        btnOwnerDetails.toImageOnly();
        btnOwnerDetails.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.notImpl();
            }
        });

        Lay.BLtg(this,
            "N", Lay.BxL("Y",
                Lay.hn(createLabelPanel("Title", "name"), "alignx=0,pref=[10,25]"),
                Lay.hn(txtTitle = new SelectAllTextField(), "alignx=0,pref=[10,30]"),
                Lay.hn(createLabelPanel("Author(s)", "name"), "alignx=0,pref=[10,25]"),
                Lay.hn(txtAuthors = new SelectAllTextField(), "alignx=0,pref=[10,30]"),
                Lay.hn(createLabelPanel("Year", "name"), "alignx=0,pref=[10,25]"),
                Lay.hn(txtYear = new SelectAllTextField(), "alignx=0,pref=[10,30]"),
                Lay.hn(createLabelPanel("Owner", "owner"), "alignx=0,pref=[10,25]"),
                Lay.BL(
                    "W", Lay.BL(
                        "C", lblOwner = Lay.lb("", "fg=" + Lay.clr(DARK_BLUE)),
                        "E", Lay.p(btnOwnerDetails, "eb=3l")
                    ),
                    "C", Lay.p(),
                    "alignx=0"
                ),
                Lay.hn(createLabelPanel("DB UID", "db-uid"), "alignx=0,pref=[10,25]"),
                lblUID = Lay.lb("", "fg=" + Lay.clr(DARK_BLUE))
            ),
            "C", Lay.p(),
            "eb=10"
        );

        txtTitle.getDocument().addDocumentListener(docListener);
        txtAuthors.getDocument().addDocumentListener(docListener);
        txtYear.getDocument().addDocumentListener(docListener);
    }

    ChangedDocumentListener docListener = new ChangedDocumentListener() {
        @Override
        public void documentChanged(DocumentEvent e) {
            if(!noFire) {
                record.set("title", txtTitle.getText());
                record.set("author", txtAuthors.getText());
                try {
                    record.set("year", Integer.parseInt(txtYear.getText()));
                } catch(NumberFormatException nfe) {
                    record.set("year", -1);
                }
                fireContentChangedNotifier();
            }
        }
    };

    @Override
    public void reload() {
        noFire = true;
        txtTitle.setText(((String) record.getValid("title", "", String.class)));
        txtAuthors.setText(((String) record.getValid("author", "", String.class)));
        txtYear.setText("" + record.getValid("year", "", Integer.class));
        noFire = false;
        lblOwner.setText(SPC + record.getOwner());
        lblUID.setText(SPC + (!record.isPersisted() ? "<NEW>" : record.getId()));
    }

    public void focusNameField() {
        txtTitle.requestFocusInWindow();
        txtTitle.setCaretPosition(txtTitle.getText().length());
    }
    public void focusAuthorsField() {
        txtAuthors.requestFocusInWindow();
        txtAuthors.setCaretPosition(txtAuthors.getText().length());
    }
    public void focusYearField() {
        txtYear.requestFocusInWindow();
        txtYear.setCaretPosition(txtYear.getText().length());
    }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.ref;

import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditDetailPanel;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.UIController;
import javax.swing.JTextField;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;

import replete.event.ChangeNotifier;
import replete.gui.controls.ChangedDocumentListener;
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

    public GeneralDetailPanel(UIController uic, MNode record)
    {
        super (uic, record);

        Lay.BLtg(this,
            "N", Lay.BxL("Y",
                Lay.hn(createLabelPanel("Title", "name"), "alignx=0,pref=[10,25]"),
                Lay.hn(txtTitle = new SelectAllTextField(), "alignx=0,pref=[10,30]"),
                Lay.hn(createLabelPanel("Author(s)", "name"), "alignx=0,pref=[10,25]"),
                Lay.hn(txtAuthors = new SelectAllTextField(), "alignx=0,pref=[10,30]"),
                Lay.hn(createLabelPanel("Year", "name"), "alignx=0,pref=[10,25]"),
                Lay.hn(txtYear = new SelectAllTextField(), "alignx=0,pref=[10,30]"),
                Lay.hn(createLabelPanel("Owner", "owner"), "alignx=0,pref=[10,25]")
            ),
            "C", Lay.p(),
            "eb=10"
        );

        txtTitle.getDocument().addDocumentListener(docListener);
        txtAuthors.getDocument().addDocumentListener(docListener);
        txtYear.getDocument().addDocumentListener(docListener);
    }

    ChangedDocumentListener docListener = new ChangedDocumentListener ()
    {
        @Override
        public void documentChanged (DocumentEvent e)
        {
            if(!noFire)
            {
                record.set (txtTitle  .getText(), "title");
                record.set (txtAuthors.getText(), "author");
                record.set (txtYear   .getText(), "year");
                fireContentChangedNotifier ();
            }
        }
    };

    @Override
    public void reload ()
    {
        noFire = true;
        txtTitle  .setText(record.get ("title"));
        txtAuthors.setText(record.get ("author"));
        txtYear   .setText(record.get ("year"));
        noFire = false;
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

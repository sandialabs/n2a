/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.model;

import gov.sandia.n2a.data.ModelOrient;
import gov.sandia.umf.platform.ui.UIController;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.JTextField;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;

import replete.event.ChangeNotifier;
import replete.gui.controls.ChangedDocumentListener;
import replete.util.Lay;

public class DimensionsDetailPanel extends ModelEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private JTextField txtLength;
    private JTextField txtWidth;
    private boolean widthFireOnLostFocus;
    private boolean noFire = false;


    //////////////
    // NOTIFIER //
    //////////////

    protected ChangeNotifier widthChangedFocusLostNotifier = new ChangeNotifier(this);
    public void addWidthChangedFocusLostListener(ChangeListener listener) {
        widthChangedFocusLostNotifier.addListener(listener);
    }
    protected void fireWidthChangedFocusLostNotifier() {
        widthChangedFocusLostNotifier.fireStateChanged();
    }


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public DimensionsDetailPanel(UIController uic, ModelOrient m) {
        super(uic, m);

        Lay.BLtg(this,
            "N", Lay.BxL("Y",
                Lay.hn(createLabelPanel("Width", "width"), "alignx=0,pref=[10,25]"),
                Lay.hn(txtWidth = new JTextField(), "alignx=0,pref=[10,30]"),
                Lay.hn(createLabelPanel("Length", "length"), "alignx=0,pref=[10,25]"),
                Lay.hn(txtLength = new JTextField(), "alignx=0,pref=[10,30]")
            ),
            "C", Lay.p(),
            "eb=10"
        );

        txtWidth.getDocument().addDocumentListener(new ChangedDocumentListener() {
            @Override
            public void documentChanged(DocumentEvent e) {
                if(!noFire) {
                    widthFireOnLostFocus = true;
                    fireContentChangedNotifier();
                }
            }
        });
        txtWidth.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if(widthFireOnLostFocus) {
                    widthFieldFocusLost();
                }
            }
        });
    }

    @Override
    public void reload() {
        noFire = true;
        txtWidth.setText(model.getName());
        noFire = false;

        noFire = true;
        txtLength.setText(model.getName());
        noFire = false;
    }

    public void focusNameField() {
        txtWidth.requestFocusInWindow();
        txtWidth.setCaretPosition(txtWidth.getText().length());
    }

    public void widthFieldFocusLost() {
        noFire = true;
        txtWidth.setText(txtWidth.getText().trim());
        noFire = false;
//        model.setWidth(txtWidth.getText());
        fireWidthChangedFocusLostNotifier();
        widthFireOnLostFocus = false;
    }

    public boolean widthFieldHasFocus() {
        return txtWidth.hasFocus();
    }
}

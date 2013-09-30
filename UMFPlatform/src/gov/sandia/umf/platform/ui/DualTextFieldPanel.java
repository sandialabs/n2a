/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui;

import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditPanel;

import java.awt.Color;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;

import replete.event.ChangeNotifier;
import replete.gui.controls.ChangedDocumentListener;
import replete.gui.controls.SelectAllTextField;
import replete.util.Lay;

public class DualTextFieldPanel extends JPanel {
    protected static final Color DARK_BLUE = RecordEditPanel.DARK_BLUE;
    protected static final String SPC = RecordEditPanel.SPC;

    private boolean readOnly;
    private JTextField txt;
    private JLabel lbl;


    protected ChangeNotifier textLostFocusNotifier = new ChangeNotifier(this);
    public void addTextLostFocusListener(ChangeListener listener) {
        textLostFocusNotifier.addListener(listener);
    }
    protected void fireTextLostFocusNotifier() {
        textLostFocusNotifier.fireStateChanged();
    }
    protected ChangeNotifier textChangeNotifier = new ChangeNotifier(this);
    public void addTextChangeListener(ChangeListener listener) {
        textChangeNotifier.addListener(listener);
    }
    protected void fireTextChangeNotifier() {
        textChangeNotifier.fireStateChanged();
    }

    public DualTextFieldPanel(boolean ro) {
        readOnly = ro;
        txt = new SelectAllTextField();
        lbl = Lay.lb("", "fg=" + Lay.clr(DARK_BLUE));
        Lay.BLtg(this, "C", readOnly ? lbl : txt);

        if(!readOnly) {
            txt.getDocument().addDocumentListener(new ChangedDocumentListener() {
                @Override
                public void documentChanged(DocumentEvent e) {
                    fireTextChangeNotifier();
                }
            });
            txt.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    fireTextLostFocusNotifier();
                }
            });
        }
    }

    public void setText(String text) {
        txt.setText(text);
        lbl.setText(SPC + text);
    }

    public String getText() {
        return txt.getText();
    }

    public void focus() {
        if(!readOnly) {
            txt.requestFocusInWindow();
            txt.setCaretPosition(txt.getText().length());
        }
    }

    public boolean hasDaFocus() {
        return !readOnly && txt.hasFocus();
    }
}

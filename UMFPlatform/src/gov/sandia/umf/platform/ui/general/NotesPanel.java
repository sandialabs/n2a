/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.general;

import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;

import replete.event.ChangeNotifier;
import replete.gui.controls.ChangedDocumentListener;
import replete.gui.controls.IconButton;
import replete.gui.controls.mnemonics.MButton;
import replete.gui.windows.EscapeDialog;
import replete.util.Lay;

public class NotesPanel extends JPanel {


    ///////////
    // FIELD //
    ///////////

    // UI

    private JTextArea txtNotes;
    private UIController uiController;


    //////////////
    // NOTIFIER //
    //////////////

    protected ChangeNotifier contentChangedNotifier = new ChangeNotifier(this);
    public void addContentChangedListener(ChangeListener listener) {
        contentChangedNotifier.addListener(listener);
    }
    protected void fireContentChangedNotifier() {
        contentChangedNotifier.fireStateChanged();
    }


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public NotesPanel(UIController uic) {
        this(uic, false);
    }
    public NotesPanel(UIController uic, boolean readOnly) {
        uiController = uic;

        txtNotes = new JTextArea();
        txtNotes.getDocument().addDocumentListener(new ChangedDocumentListener() {
            @Override
            public void documentChanged(DocumentEvent e) {
                fireContentChangedNotifier();
            }
        });
        txtNotes.setFont(new JTextField().getFont());

        JButton btnEdit = new IconButton(ImageUtil.getImage("edit.gif"), 2);
        btnEdit.setToolTipText("Edit Notes...");
        btnEdit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                editNotes();
            }
        });

        if(readOnly) {
            txtNotes.setEditable(false);
            Lay.BLtg(this,
                "C", Lay.p(Lay.sp(txtNotes))
            );
        } else {
            Lay.BLtg(this,
                "C", Lay.p(Lay.sp(txtNotes), "eb=5r"),
                "E", Lay.BxL("Y",
                    Lay.hn(btnEdit, "alignx=0.5"),
                    Box.createVerticalGlue()
                )
            );
        }
    }

    protected void editNotes() {
        final EscapeDialog editDialog = new EscapeDialog((JFrame) SwingUtilities.getRoot(this), "Edit Notes", true);
        final JTextArea txtDialog = new JTextArea();
        txtDialog.setText(txtNotes.getText());
        JButton btnOK = new MButton("&OK", ImageUtil.getImage("complete.gif"));
        btnOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                setNotes(txtDialog.getText());
                txtNotes.requestFocusInWindow();
                editDialog.dispose();
            }
        });
        JButton btnCancel = new MButton("&Cancel", ImageUtil.getImage("cancel.gif"));
        btnCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                editDialog.dispose();
            }
        });
        editDialog.getRootPane().setDefaultButton(btnOK);
        Lay.BLtg(editDialog,
            "C", Lay.sp(txtDialog, "augb=eb(10tlr)"),
            "S", Lay.FL("R", btnOK, btnCancel, "eb=5"),
            "size=[600,600],center"
        );
        editDialog.setVisible(true);
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public String getNotes() {
        return txtNotes.getText();
    }

    public void setNotes(String notes) {
        txtNotes.setText(notes);
        txtNotes.setCaretPosition(0);
    }
}

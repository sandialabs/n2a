/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.ui;

import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;
import replete.gui.controls.IconButton;
import replete.gui.controls.mnemonics.MButton;
import replete.gui.windows.Dialogs;
import replete.util.Lay;
import replete.util.ReflectionUtil;
import replete.util.User;

public abstract class RecordEditPanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    // Const

    public static final Color DARK_BLUE = new Color(0, 0, 150);
    public static final String SPC = "    ";

    // Core

    protected UIController uiController;
    protected NDoc record;

    // UI

    protected JLabel lblTitle1;
    protected JLabel lblTitle2;
    protected JButton btnSave;
    protected JButton btnCancel;
    protected JButton btnReload;

    // Misc

    protected boolean dirty = false;
    protected boolean dirtyNotifEnabled = false;


    //////////////
    // NOTIFIER //
    //////////////

    private ChangeNotifier dirtyChangedNotifier = new ChangeNotifier(this);
    public void addDirtyChangedNotifier(ChangeListener listener) {
        dirtyChangedNotifier.addListener(listener);
    }
    protected void fireDirtyChangedNotifier() {
        dirtyChangedNotifier.fireStateChanged();
    }


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public RecordEditPanel(UIController uic, NDoc rec) {
        uiController = uic;
        record = rec;
    }

    protected JPanel createRecordControlsPanel() {
        btnSave = new MButton("&Save", ImageUtil.getImage("save.gif"));
        btnSave.setToolTipText("Save " + record.getClassName());
        btnSave.setEnabled(false);
        btnSave.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                attemptToSaveDialogFail();
            }
        });
        //btnSave.setOpaque(false);

        btnCancel = new MButton("&Cancel", ImageUtil.getImage("cancel.gif"));
        btnCancel.setToolTipText("Cancel Changes");
        btnCancel.setEnabled(false);
        btnCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doCancel();
            }
        });

        final JToggleButton btnToggleMW = new JToggleButton(ImageUtil.getImage("mywork.gif"));
        btnToggleMW.setToolTipText("In My Work?");
        btnToggleMW.setVerticalTextPosition(SwingConstants.BOTTOM);
        btnToggleMW.setHorizontalTextPosition(SwingConstants.CENTER);
        Insets margins = new Insets(2, 2, 2, 2);
        btnToggleMW.setMargin(margins);
        btnToggleMW.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                //uiController.setInMyWork(record, btnToggleMW.isSelected());
            }
        });

        JButton btnDelete = new IconButton(ImageUtil.getImage("remove.gif"), "Delete " + record.getClassName(), 2);
        btnDelete.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doDelete();
            }
        });
        if(ReflectionUtil.hasMethod("getOwner", record)) {
            String owner = (String) ReflectionUtil.invoke("getOwner", record);
            if(!User.getName().equals("dtrumbo") && !User.getName().equals(owner)) {
                btnDelete.setEnabled(false);
            }
        }

        JButton btnDuplicate = new IconButton(ImageUtil.getImage("dup.gif"), "Duplicate " + record.getClassName(), 2);
        btnDuplicate.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doDuplicate();
            }
        });

        btnReload = new IconButton(ImageUtil.getImage("refresh.gif"), "Reload " + record.getClassName() + " From Database", 2);
        btnReload.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doReload();
            }
        });

        JPanel pnlRecordControls = Lay.BL(
            "W", lblTitle1 = Lay.lb("", "fg=white,opaque=false"),
            "C", lblTitle2 = Lay.lb("", "fg=white,opaque=false"),
            "E", Lay.FL("R", btnSave, btnCancel, new JLabel(" "), btnToggleMW, btnDelete, btnDuplicate, btnReload, "opaque=false"),
            "bg=100,augb=mb(3b,black)"
        );
        lblTitle1.setFont(lblTitle1.getFont().deriveFont(20.0F));
        lblTitle2.setFont(lblTitle2.getFont().deriveFont(20.0F).deriveFont(Font.ITALIC));

        updateRecordTitle();

        Lay.match(btnCancel, btnSave);

//        btnToggleMW.setSelected(uiController.isInMyWork(record));

        return pnlRecordControls;
    }

    public void updateRecordTitle() {
        String type = record.getHandler().getRecordTypeDisplayName(record);
        lblTitle1.setText("<html>&nbsp;" + type + ":&nbsp;</html>");
        if(record.isPersisted()) {
            String title =
                record.getTitle() == null ||
                record.getTitle().trim().equals("") ?
                "<html><i>(no title)</i></html>" :
                record.getTitle();
            lblTitle2.setText(title);
        } else {
            lblTitle2.setText("NEW");
        }
    }


    //////////
    // MISC //
    //////////

    public boolean isDirty() {
        return dirty;
    }

    public void makeDirty() {
        boolean prevDirty = dirty;
        dirty = true;
        if(dirtyNotifEnabled) {
            btnSave.setEnabled(true);
            btnCancel.setEnabled(true);
            if(prevDirty != dirty) {
                fireDirtyChangedNotifier();
            }
        }
    }

    public void makeClean() {
        boolean prevDirty = dirty;
        dirty = false;
        if(dirtyNotifEnabled) {
            btnSave.setEnabled(false);
            btnCancel.setEnabled(false);
            if(prevDirty != dirty) {
                fireDirtyChangedNotifier();
            }
        }
    }

    protected void enableDirtyNotifications() {
        dirtyNotifEnabled = true;
    }

    public boolean isValidPanel() {
        return validationMessage() == null;
    }

    /////////////////////////
    // ABSTRACT / SKELETON //
    /////////////////////////

    public abstract String validationMessage();
    public abstract void doInitialFocus();
    protected abstract void reloadWorker();
//cew 130327
    protected abstract void reload();
//    public void reload() {
//        GUIUtil.safe(new Runnable() {
//            public void run() {
//                reloadWorker();
//            }
//        });
//    }
    public void postLayout() {}

    public String attemptToSaveSynchronous() {
        String msg = validationMessage();
        if(msg != null) {
            return msg;
        }
        uiController.saveSynchronous(record);
        loadFromRecord();
        return null;
    }

    public void attemptToSaveDialogFail() {
        String msg = validationMessage();
        if(msg != null) {
            Dialogs.showWarning(msg);
            return;
        }
        uiController.save(record, new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                loadFromRecord();
            }
        });
    }

    protected void doCancel() {
        if(!record.isPersisted()) {
            uiController.discardNew(record);
        } else {
//            uiController.re
            record.revert();  // on thread
            loadFromRecord();
        }
    }
    protected void doDelete() {
        if(!record.isPersisted()) {
            uiController.discardNew(record);
        } else {
            uiController.delete(record);
        }
    }
    protected void doDuplicate() {
        uiController.openDuplicate(record);
    }
    protected boolean doReload() {
        if(isDirty() && !Dialogs.showConfirm("This record contains unsaved changes.  Are you sure you wish to discard those changes?")) {
            return false;
        }
        record.revert(); // on thread
        loadFromRecord();
        return true;
    }

    protected void loadFromRecord() { // should be private, protected because we override save methods to get recursive save.
        reload();
        makeClean();
        updateRecordTitle();
    }

    public NDoc getRecord() {
        return record;
    }
}
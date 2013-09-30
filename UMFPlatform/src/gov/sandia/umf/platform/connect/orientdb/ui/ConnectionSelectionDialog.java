/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.ui;

import gov.sandia.umf.platform.UMF;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import replete.gui.controls.IconButton;
import replete.gui.controls.cblist.CheckBoxList;
import replete.gui.controls.cblist.ListCheckedEvent;
import replete.gui.controls.cblist.ListCheckedListener;
import replete.gui.controls.mnemonics.MButton;
import replete.gui.form.FormPanel;
import replete.gui.windows.Dialogs;
import replete.gui.windows.EscapeDialog;
import replete.util.Lay;

import com.orientechnologies.orient.core.exception.OSecurityAccessException;

public class ConnectionSelectionDialog extends EscapeDialog {


    ////////////
    // FIELDS //
    ////////////

    // State

    public static final int SAVE = 0;
    public static final int CANCEL = 1;

    private int state = CANCEL;
    private boolean changedCxn = false;

    // UI

    private CheckBoxList lstCxns;
    private DefaultListModel mdlCxns;
    private JButton btnSave;
    private JButton btnCancel;
    private JButton btnAddCxn;
    private JButton btnRemoveCxn;
    private ConnectFormPanel pnlEdit = new ConnectFormPanel();

    // Details

    private OrientConnectDetails selDetails;
    private OrientConnectDetails checkedDetails;
    private OrientConnectDetails origCheckedDetailsRef;
    private OrientConnectDetails origCheckedDetailsCopy;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ConnectionSelectionDialog(JFrame parent, List<OrientConnectDetails> details, OrientConnectDetails selectedDetails) {
        super(parent, "Connections", true);
        setIconImage(ImageUtil.getImage("connect.gif").getImage());

        mdlCxns = new DefaultListModel();
        int sel = -1, i = 0;
        for(OrientConnectDetails detail : details) {
            OrientConnectDetails copy = new OrientConnectDetails(detail);
            if(selectedDetails == detail) {
                sel = i;
                origCheckedDetailsRef = copy;
                origCheckedDetailsCopy = new OrientConnectDetails(copy);
            }
            mdlCxns.addElement(copy);
            i++;
        }

        // Set up list box.
        lstCxns = new CheckBoxList(mdlCxns);
        lstCxns.setCheckCellInsets(new Insets(2, 2, 2, 2));
        lstCxns.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstCxns.addListCheckedListener(new ListCheckedListener() {
            public void valueChanged(ListCheckedEvent e) {
                if(lstCxns.isCheckedIndex(e.getIndex())) {
                    lstCxns.setCheckedIndex(e.getIndex());
                }
                updateColor();
            }
        });
        lstCxns.addDeleteListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                removeConnection();
            }
        });
        lstCxns.addEmptyDoubleClickListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                addConnection();
            }
        });
        lstCxns.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                saveDetails();
                selDetails = (OrientConnectDetails) lstCxns.getSelectedValue();
                showDetails();
                boolean en = lstCxns.getSelectedIndex() != -1;
                btnRemoveCxn.setEnabled(en);
            }
        });
        lstCxns.addFocusListener(saveOnFormFocusLost);

        // Set up buttons.
        btnSave = new MButton("Connect && &Save", ImageUtil.getImage("connect.gif"), new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                saveDetails();
                updateColor();
                lstCxns.repaint();
                state = SAVE;
                closeDialog();
            }
        });
        btnCancel = new MButton("&Cancel", ImageUtil.getImage("cancel.gif"), new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                closeDialog();
            }
        });
        btnAddCxn = new IconButton(ImageUtil.getImage("add.gif"), "Add Connection", 2,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    addConnection();
                }
            }
        );
        btnRemoveCxn = new IconButton(ImageUtil.getImage("remove.gif"), "Remove Connection", 2,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    removeConnection();
                }
            }
        );
        btnRemoveCxn.addFocusListener(saveOnFormFocusLost);
        btnRemoveCxn.setEnabled(false);

        // Lay out components.
        Lay.BLtg(this,
            "N", Lay.lb("<html>Select your active datasource connection by checking the desired item in the list.  Additionally add, edit, and remove any connections using the panel below.<html>"),
            "C", Lay.BL(
                "W", Lay.BL(
                    "C", Lay.sp(lstCxns, "pref=[160,20]"),
                    "S", Lay.BL(
                        "W", Lay.FL("L", btnAddCxn, Lay.p(btnRemoveCxn, "eb=5l"), "hgap=0"),
                        "C", Lay.p()
                    )
                ),
                "C", pnlEdit
            ),
            "S", Lay.FL("R", btnSave, btnCancel),
            "eb=5tl,vgap=5",
            "size=[680,400],center"
        );
        if(sel != -1) {
            lstCxns.setCheckedIndex(sel);
            lstCxns.setSelectedIndex(sel);
            updateColor();
        }

        showDetails();
    }

    protected void createSelLocation() {
        try {
            OrientDatasource source = new OrientDatasource(selDetails);
            source.disconnect();
        } catch(OSecurityAccessException ex){
            Dialogs.showWarning("The user name and password provided for this connection were rejected.", "Authentication Error");
            return;
        }
    }

    protected void testSelLocation() {
        try {
            OrientDatasource source = new OrientDatasource(selDetails);
            source.disconnect();
        } catch(Exception ex) {
            Dialogs.showWarning("The selected connection contains an invalid location URL.  The URL should have the form:\n    <engine>:<db-name>", "Could not connect");
            return;
        }
    }

    private FocusListener saveOnFormFocusLost = new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
            saveDetails();
            lstCxns.repaint();
        }
    };

    protected void addConnection() {
        File repos = new File(UMF.getAppResourceDir(), "repos");
        File dflt = new File(repos, "NEW");
        mdlCxns.addElement(new OrientConnectDetails("<New>", "local:" + dflt.getAbsolutePath(), "admin", "admin"));
        lstCxns.setSelectedIndex(mdlCxns.size() - 1);
        lstCxns.setCheckedIndex(mdlCxns.size() - 1);
        updateColor();
        pnlEdit.focusFirstComponent();
    }

    protected void removeConnection() {
        mdlCxns.remove(lstCxns.getSelectedIndex());
    }

    private void updateColor() {
        int index = lstCxns.getCheckedIndex();
        checkedDetails = null;
        lstCxns.clearItemForegrounds();
        lstCxns.clearItemBackgrounds();
        if(index != -1) {
            checkedDetails = (OrientConnectDetails) mdlCxns.get(index);
            lstCxns.setItemForeground(index, Lay.getColorFromHex("0B610B"));
            lstCxns.setItemBackground(index, Lay.getColorFromHex("E0F8F0"));
            changedCxn =
                origCheckedDetailsRef != checkedDetails ||
                !origCheckedDetailsCopy.equalsLimited(checkedDetails);
        }
    }

    public boolean isConnectionChanged() {
        return changedCxn;
    }

    public int getState() {
        return state;
    }

    public OrientConnectDetails getChosenConnection() {
        return checkedDetails;
    }

    public List<OrientConnectDetails> getConnections() {
        List<OrientConnectDetails> cxns = new ArrayList<OrientConnectDetails>();
        for(int i = 0; i < mdlCxns.size(); i++) {
            cxns.add((OrientConnectDetails) mdlCxns.get(i));
        }
        return cxns;
    }

    protected void saveDetails() {
        if(selDetails != null) {
            selDetails.setName(pnlEdit.txtName.getText());
            selDetails.setLocation(pnlEdit.txtLocation.getText());
            selDetails.setUser(pnlEdit.txtUser.getText());
            selDetails.setPassword(pnlEdit.txtPassword.getText());
        }
    }

    protected void showDetails() {
        if(selDetails != null) {
            pnlEdit.setVisible(true);
            pnlEdit.txtName.setText(selDetails.getName());
            pnlEdit.txtLocation.setText(selDetails.getLocation());
            pnlEdit.txtUser.setText(selDetails.getUser());
            pnlEdit.txtPassword.setText(selDetails.getPassword());
        } else {
            pnlEdit.setVisible(false);
        }
        pnlEdit.updateUI();
    }


    /////////////////
    // INNER CLASS //
    /////////////////

    private class ConnectFormPanel extends FormPanel {
        protected JTextField txtName;
        protected JTextField txtLocation;
        protected JTextField txtUser;
        protected JTextField txtPassword;

        public ConnectFormPanel() {
            init();
        }

        @Override
        protected void addFields() {
            addField("MAIN", "Name",     txtName = createTextField(), 40, false);
            addField("MAIN", "Location", txtLocation = createTextField(), 40, false, "(must be in form 'local:PATH' or 'remote:PATH')");
            addField("MAIN", "User",     txtUser = createTextField(), 40, false);
            addField("MAIN", "Password", txtPassword = createPasswordField(), 40, false);
        }

        @Override
        protected boolean showCancelButton() {
            return false;
        }
        @Override
        protected boolean showSaveButton() {
            return false;
        }

        protected JPasswordField createPasswordField() {
            final JPasswordField txt = new JPasswordField();
            txt.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    txt.selectAll();
                }
            });
            txt.getDocument().addDocumentListener(new DocumentListener() {
                public void removeUpdate(DocumentEvent e) {
                    makeDirty();
                }
                public void insertUpdate(DocumentEvent e) {
                    makeDirty();
                }
                public void changedUpdate(DocumentEvent e) {
                    makeDirty();
                }
            });
            return txt;
        }
        @Override
        public void focusFirstComponent() {
            txtName.requestFocusInWindow();
        }
    }


    //////////
    // TEST //
    //////////

    public static void main(String[] args) {
        List<OrientConnectDetails> connects = new ArrayList<OrientConnectDetails>();
        OrientConnectDetails sel;
        connects.add(sel = new OrientConnectDetails("ABC", "local:C:/Users/dtrumbo/Desktop/orient/testx2df", "admin", "admin"));
        connects.add(new OrientConnectDetails("XYZ", "123", "asd", "passwd"));
        ConnectionSelectionDialog dlg = new ConnectionSelectionDialog(null, connects, sel);
        dlg.setVisible(true);
        System.out.println(dlg.getState() == ConnectionSelectionDialog.SAVE ? "SAVE" : "CANCEL");
        System.out.println(dlg.getChosenConnection());
        List<OrientConnectDetails> c = dlg.getConnections();
        for(OrientConnectDetails cc : c) {
            System.out.println(cc);
        }
    }
}

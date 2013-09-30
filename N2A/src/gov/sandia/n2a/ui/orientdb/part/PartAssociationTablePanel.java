/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.part;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditDetailPanel;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;
import gov.sandia.umf.platform.ui.search.SearchType;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import replete.gui.controls.IconButton;
import replete.gui.windows.common.CommonFrame;
import replete.util.Lay;
import replete.util.NumUtil;

public class PartAssociationTablePanel extends RecordEditDetailPanel {


    //////////
    // ENUM //
    //////////

    public enum Type {
        INCLUDE("Include"),
        CONNECT("Connection");
        private String nm;
        private Type(String n) {
            nm = n;
        }
        public String getName() {
            return nm;
        }
    }


    ////////////
    // FIELDS //
    ////////////

    // UI

    private PartAssociationTableModel tableModel;
    private PartAssociationTable tblPartList;

    // Misc

    private Type partAssocType;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public PartAssociationTablePanel(UIController uic, NDoc p, Type type) {
        super(uic, p);
        partAssocType = type;

        tableModel = new PartAssociationTableModel(PartAssociationTableModel.Direction.DEST, true);
        tableModel.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                fireContentChangedNotifier();
            }
        });

        tblPartList = new PartAssociationTable(tableModel);
        tblPartList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblPartList.setFillsViewportHeight(true);
        tblPartList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(SwingUtilities.isLeftMouseButton(e) &&
                        e.getClickCount() == 2 &&
                        tblPartList.rowAtPoint(e.getPoint()) != -1 &&
                        tblPartList.getSelectedRowCount() != 0) {
                    doOpen();
                }
            }
        });

        tblPartList.addEmptyClickListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                doAdd();
            }
        });

        tblPartList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteKey");
        tblPartList.getActionMap().put("deleteKey", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                doRemove();
            }
        });

        JButton btnAdd = new IconButton(ImageUtil.getImage("add.gif"), "Add Compartment(s)...", 2,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    doAdd();
                }
            }
        );
        final JButton btnRemove = new IconButton(ImageUtil.getImage("remove.gif"), "Remove Compartment", 2,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    doRemove();
                }
            }
        );
        final JButton btnOpen = new IconButton(ImageUtil.getImage("open.gif"), "Open Compartment", 2,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    doOpen();
                }
            }
        );
        btnRemove.setEnabled(false);
        btnOpen.setEnabled(false);

        tblPartList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                btnRemove.setEnabled(tblPartList.getSelectedRowCount() != 0);
                btnOpen.setEnabled(tblPartList.getSelectedRowCount() != 0);
            }
        });

        Lay.BLtg(this,
            "C", Lay.p(Lay.sp(tblPartList), "eb=5r"),
            "E", Lay.BxL("Y",
                Lay.BL(btnAdd, "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL(btnRemove, "eb=20b,alignx=0.5,maxH=20"),
                Lay.hn(btnOpen, "alignx=0.5"),
                Box.createVerticalGlue()
            )
        );
    }


    /////////////
    // ACTIONS //
    /////////////

    private void doAdd() {
        String searchTitle = "Add Compartment(s) For " + record.get("name");
        CommonFrame parent = (CommonFrame) SwingUtilities.getRoot(this);
        List<NDoc> chosen = uiController.searchRecordOrient(parent,
            SearchType.REFERENCE, searchTitle, null, ImageUtil.getImage("complete.gif"), ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        // validate loops
        if(chosen != null) {
            for(NDoc chosenRecord : chosen) {
                String name;
                if(partAssocType == Type.CONNECT) {
                    name = getNextConnectName();
                } else {
                    name = getNextIncludeName(chosenRecord);
                }
                NDoc paRecord = new NDoc();
                paRecord.set("name", name);
                paRecord.set("type", (partAssocType == Type.CONNECT ? "connect" : "include"));
                paRecord.set("dest", chosenRecord);
                paRecord.set("source", record);
                tableModel.addPartAssociation(paRecord);
                int newSize = tableModel.getRowCount();
                tblPartList.getSelectionModel().setSelectionInterval(newSize - 1, newSize - 1);
            }
            fireContentChangedNotifier();
        }
    }

    protected void doRemove() {
        int row = tblPartList.getSelectedRow();
        if(row != -1) {
            tableModel.removePartAssociation(row);
            if(row == tableModel.getRowCount()) {
                row--;
            }
            tblPartList.getSelectionModel().setSelectionInterval(row, row);
            fireContentChangedNotifier();
        }
    }

    private void doOpen() {
        int row = tblPartList.getSelectedRow();
        if(row != -1) {
            NDoc assoc = tableModel.getPartAssociation(row);
            uiController.openRecord((NDoc) assoc.getValid("dest", null, NDoc.class));
        }
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public List<NDoc> getPartAssociations() {
        return tableModel.getPartAssociations();
    }

    public void setPartAssociations(List<NDoc> assocs) {
        tableModel.setPartAssociations(assocs);
    }

    @Override
    public void reload() {
    }


    //////////
    // MISC //
    //////////

    public String getNextConnectName() {
        List<NDoc> pas = record.getValid("associations", new ArrayList<NDoc>(), List.class);
        Set<String> letters = new TreeSet<String>();
        for(NDoc pa : pas) {
            String pan = pa.get("name");
            if(pan != null) {
                if(pan.length() == 1 && Character.isUpperCase(pan.charAt(0))) {
                    letters.add(pan);
                }
            }
        }
        int x = 65;
        for(String letter : letters) {
            if(letter.charAt(0) == x) {
                x++;
            } else {
                break;
            }
        }
        return "" + (char) x;
    }

    public String getNextIncludeName(NDoc newIncludePart) {
        List<NDoc> pas = record.getValid("associations", new ArrayList<NDoc>(), List.class);
        Set<Integer> digits = new TreeSet<Integer>();
        String incl = newIncludePart.get("name");
        boolean hasNonDigit = false;
        for(NDoc pa : pas) {
            String pan = pa.get("name");
            if(pan == null) {
                pan = ((NDoc) pa.get("dest")).get("name");
            }
            if(pan.equals(incl)) {
                hasNonDigit = true;
            }
            if(pan.startsWith(incl)) {
                String rest = pan.substring(incl.length());
                if(NumUtil.isInt(rest)) {
                    digits.add(Integer.parseInt(rest));
                }
            }
        }
        if(!hasNonDigit && digits.size() == 0) {
            return incl;
        }
        int x = 0;
        for(Integer digit : digits) {
            if(digit == x) {
                x++;
            } else {
                break;
            }
        }
        return incl + x;
    }
}

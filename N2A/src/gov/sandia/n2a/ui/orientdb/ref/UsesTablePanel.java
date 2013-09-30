/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.ref;

import gov.sandia.n2a.ui.orientdb.part.PartAssociationTable;
import gov.sandia.n2a.ui.orientdb.part.PartAssociationTableModel;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;
import gov.sandia.umf.platform.ui.search.SearchResultTableModelOrient;
import gov.sandia.umf.platform.ui.search.SearchTable;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import replete.gui.controls.IconButton;
import replete.util.Lay;

public class UsesTablePanel extends JPanel {

    //////////
    // ENUM //
    //////////

    public enum Type {
        PARENT,
        PART_ASSOC,
        MODEL
    }


    ////////////
    // FIELDS //
    ////////////

    // Core

    private UIController uiController;
    private Type type;

    // UI

    private SearchTable tblParent;
    private SearchResultTableModelOrient parentModel;
    private PartAssociationTable tblPartAssoc;
    private PartAssociationTableModel paModel;
    private SearchTable tblModels;
    private SearchResultTableModelOrient modelModel;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public UsesTablePanel(UIController uic, Type typ) {
        uiController = uic;
        type = typ;

        final JButton btnOpen = new IconButton(ImageUtil.getImage("open.gif"), "Open Compartment", 2,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    doOpen();
                }
            }
        );
        btnOpen.setEnabled(false);

        JComponent cmp;
        if(type == Type.PART_ASSOC) {
            paModel = new PartAssociationTableModel(PartAssociationTableModel.Direction.SOURCE, false);
            tblPartAssoc = new PartAssociationTable(paModel);
            tblPartAssoc.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            tblPartAssoc.setFillsViewportHeight(true);
            tblPartAssoc.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if(SwingUtilities.isLeftMouseButton(e) &&
                            e.getClickCount() == 2 &&
                            tblPartAssoc.rowAtPoint(e.getPoint()) != -1 &&
                            tblPartAssoc.getSelectedRowCount() != 0) {
                        doOpen();
                    }
                }
            });
            tblPartAssoc.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent e) {
                    btnOpen.setEnabled(tblPartAssoc.getSelectedRowCount() != 0);
                }
            });
            cmp = tblPartAssoc;
        } else if(type == Type.PARENT) {
            parentModel = new SearchResultTableModelOrient();
            tblParent = new SearchTable(parentModel, ListSelectionModel.SINGLE_SELECTION);
            tblParent.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if(SwingUtilities.isLeftMouseButton(e) &&
                            e.getClickCount() == 2 &&
                                tblParent.rowAtPoint(e.getPoint()) != -1 &&
                                tblParent.getSelectedRowCount() != 0) {
                        doOpen();
                    }
                }
            });
            tblParent.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent e) {
                    btnOpen.setEnabled(tblParent.getSelectedRowCount() != 0);
                }
            });
            cmp = tblParent;
        } else {
            modelModel = new SearchResultTableModelOrient();
            tblModels = new SearchTable(modelModel, ListSelectionModel.SINGLE_SELECTION);
            tblModels.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if(SwingUtilities.isLeftMouseButton(e) &&
                            e.getClickCount() == 2 &&
                                tblModels.rowAtPoint(e.getPoint()) != -1 &&
                                tblModels.getSelectedRowCount() != 0) {
                        doOpen();
                    }
                }
            });
            tblModels.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent e) {
                    btnOpen.setEnabled(tblModels.getSelectedRowCount() != 0);
                }
            });
            cmp = tblModels;
        }

        Lay.BLtg(this,
            "C", Lay.p(Lay.sp(cmp), "eb=5r"),
            "E", Lay.BxL("Y",
//                Lay.BL(btnAdd, "eb=5b,alignx=0.5,maxH=20"),
  //              Lay.BL(btnRemove, "eb=20b,alignx=0.5,maxH=20"),
                Lay.hn(btnOpen, "alignx=0.5"),
                Box.createVerticalGlue()
            )
        );
    }


    /////////////
    // ACTIONS //
    /////////////

    private void doOpen() {
        // TODO: this will change maybe
        if(type == Type.PART_ASSOC) {
            int row = tblPartAssoc.getSelectedRow();
            if(row != -1) {
                NDoc assoc = paModel.getPartAssociation(row);
//                uiController.openExisting(PartX.class, assoc.getSourceId());
                uiController.openRecord((NDoc) assoc.getValid("dest", null, NDoc.class));
            }
        } else if(type == Type.PARENT) {
            int row = tblParent.getSelectedRow();
            if(row != -1) {
                NDoc bean = parentModel.getResult(row);
                uiController.openRecord(bean);
            }
        } else {
            int row = tblModels.getSelectedRow();
            if(row != -1) {
                NDoc bean = modelModel.getResult(row);
                uiController.openRecord(bean);
            }
        }
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

//    public void setPartAssociations(List<PartAssociation> assocs) {
//        if(type == Type.PART_ASSOC) {
//            paModel.setPartAssociations(assocs);
//        }
//    }
//    public void setChildren(List<PartX> children) {
//        if(type == Type.PARENT) {
//            List<BeanBase> beans = new ArrayList<BeanBase>();
//            for(PartX child : children) {
//                beans.add(child);
//            }
//            parentModel.setResults(beans);
//        }
//    }
//    public void setModels(List<BeanBase> models) {
//        if(type == Type.PARENT) {
//            modelModel.setResults(models);
//        }
//    }
}

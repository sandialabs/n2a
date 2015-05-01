/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.part;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditDetailPanel;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;
import gov.sandia.umf.platform.ui.search.SearchType;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import replete.gui.controls.mnemonics.MButton;
import replete.gui.windows.common.CommonFrame;
import replete.util.Lay;

public class ParentDetailPanel extends RecordEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // Const

    private static final String NO_PARENT = "<None>";

    // UI

    private JPanel pnlParentEmpty;
    private JButton btnChangeParent;
    private JButton btnRemoveParent;
    private JButton btnJumpToParent;
    private JLabel lblParentOverview;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ParentDetailPanel(UIController uic, NDoc p) {
        super(uic, p);

        btnChangeParent = new MButton("Select &Parent", ImageUtil.getImage("parent.gif"));
        btnChangeParent.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                CommonFrame parentWin = (CommonFrame) SwingUtilities.getRoot(ParentDetailPanel.this);
                SearchType type =
                   // part.isCompartment() ?
                        SearchType.COMPARTMENT;// :
                     //   SearchType.CONNECTION;

                List<NDoc> chosen = uiController.searchRecordOrient(parentWin, type, "Select Parent", null/*new SearchSelectionValidator() {
                    public String validate(NDoc record) {
                        if(part.getParent() != null && part.getParent().getId().equals(record.getId())) {
                            return "This is already the current parent.";
                        }

                        // TODO: Technically this only checks against parent
                        // IDs currently loaded into the data model's cache.
                        // It is possible that another user has changed a
                        // different record on the hierarchy, creating a loop
                        // but each user's client couldn't independently recognize
                        // the loop.  This is why we need to do recursion detection
                        // later and why we could use some sort of mechanism at
                        // the persistence layer to notify clients of changes they're
                        // interested in.

                        PartX prevParent = part.getParent();
                        part.setParent((PartX) record);

                        try {
                            part.getAssembledEquations(true);
                        } catch(DataModelLoopException e) {
                            return "Using this parent would create a loop in the parent and/or include hierarchy.";
                        } finally {
                            part.setParent(prevParent);
                        }

                        return null;
                    }
                }*/, null, ListSelectionModel.SINGLE_SELECTION);
                if(chosen != null) {
                    parentSet(chosen.get(0));
                }
            }
        });

        btnRemoveParent = new MButton("&Remove Parent", ImageUtil.getImage("cancel.gif"));
        btnRemoveParent.setVisible(false);
        btnRemoveParent.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                parentClear();
            }
        });

        btnJumpToParent = new MButton("&Open Parent", ImageUtil.getImage("open.gif"));
        btnJumpToParent.setVisible(false);
        btnJumpToParent.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.openRecord((NDoc) record.get("parent"));
            }
        });

        Lay.BLtg(this,
            "N", Lay.BxL("Y",
                Lay.hn(createLabelPanel("Parent", "parent"), "alignx=0,pref=[10,25]"),
                lblParentOverview = Lay.lb(SPC + NO_PARENT, "fg=" + Lay.clr(DARK_BLUE)),
                Lay.lb(" "),
                Lay.FL("L",
                    Lay.p(btnChangeParent, "eb=5r"),
                    Lay.p(btnRemoveParent, "eb=5r"),
                    btnJumpToParent,
                    "alignx=0,hgap=0,vgap=0"
                ),
                Lay.lb(" ")
            ),
            "C", pnlParentEmpty = Lay.p(),
            "eb=10"
        );
    }

    private void parentSet(NDoc parent) {
        remove(pnlParentEmpty);

        btnChangeParent.setText("&Change Parent");
        lblParentOverview.setText(SPC + parent.get("name"));
        btnRemoveParent.setVisible(true);
        btnJumpToParent.setVisible(true);

        record.set("parent", parent);
        updateUI();
        fireContentChangedNotifier();
    }

    private void parentClear() {
        add(pnlParentEmpty, BorderLayout.CENTER);

        btnChangeParent.setText("Select &Parent");
        lblParentOverview.setText(SPC + NO_PARENT);
        btnRemoveParent.setVisible(false);
        btnJumpToParent.setVisible(false);

        record.set("parent", null);
        updateUI();
        fireContentChangedNotifier();
    }

    @Override
    public void reload() {
        NDoc parent = record.getValid("parent", NDoc.class);
        if(parent != null) {
            parentSet(parent);
        } else {
            parentClear();
        }
    }
}

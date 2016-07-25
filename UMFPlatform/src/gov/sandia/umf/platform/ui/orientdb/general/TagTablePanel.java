/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.orientdb.general;

import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditDetailPanel;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import replete.gui.controls.IconButton;
import replete.gui.windows.Dialogs;
import replete.util.Lay;

public class TagTablePanel extends RecordEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private TagTableModel mdlTags;
    private JTable tblTags;

    // Misc


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public TagTablePanel(UIController uic, MNode doc)
    {
        super(uic, doc);

        mdlTags = new TagTableModel();
        mdlTags.addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                fireContentChangedNotifier();
            }
        });
        tblTags = new JTable(mdlTags);
        tblTags.setFillsViewportHeight(true);
        tblTags.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblTags.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if(SwingUtilities.isLeftMouseButton(e) &&
                        e.getClickCount() == 2 &&
                        tblTags.rowAtPoint(e.getPoint()) == -1 /* && doesn't matter if selected or not */) {
                    addTag("NEWTAG", "NEWVALUE");
                    int idx = mdlTags.getRowCount() - 1;
                    tblTags.getSelectionModel().setSelectionInterval(idx, idx);
                    tblTags.editCellAt(idx, 0);
                    JTextField txtEditor = (JTextField) tblTags.getEditorComponent();
                    txtEditor.requestFocusInWindow();
                    txtEditor.selectAll();
                }
            }
        });

        tblTags.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteKey");
        tblTags.getActionMap().put("deleteKey", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                doRemove();
            }
        });

        JButton btnAdd = new IconButton(ImageUtil.getImage("add.gif"), "Add Tag...", 2,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    doAdd();
                }
            }
        );
        JButton btnRemove = new IconButton(ImageUtil.getImage("remove.gif"), "Remove Tag", 2,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    doRemove();
                }
            }
        );
        JButton btnOpen = new IconButton(ImageUtil.getImage("edit.gif"), "Edit Tags", 2,
            new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    doEdit();
                }
            }
        );

        Lay.BLtg(this,
            "C", Lay.p(Lay.sp(tblTags), "eb=5r"),
            "E", Lay.BxL("Y",
                Lay.BL(btnAdd, "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL(btnRemove, "eb=20b,alignx=0.5,maxH=20"),
                Lay.hn(btnOpen, "alignx=0.5"),
                Box.createVerticalGlue()
            )
        );
    }

    private void doAdd() {
//        CommonFrame parent = (CommonFrame) SwingUtilities.getRoot(this);
        String input = Dialogs.showInput("Enter 'tag = value' or just 'tag':", "New Values", "tag  [OR]  tag = value");
        if(input != null) {
            String[] parts = input.trim().split("\\s*=\\s*", 2);
            if(parts.length == 1) {
                parts = new String[] {parts[0], ""};
            }
            if(!parts[0].equals("")) {
                addTag(parts[0], parts[1]);
            } else {
                Dialogs.showWarning("Cannot have a blank tag name.");
            }
        }
    }

    private void addTag(String key, String value) {
        mdlTags.addTag(new TermValue(key, value));
    }

    protected void doRemove() {
        int row = tblTags.getSelectedRow();
        if(row != -1) {
            mdlTags.removeTag(row);
            if(row == mdlTags.getRowCount()) {
                row--;
            }
            tblTags.getSelectionModel().setSelectionInterval(row, row);
        }
    }

    private void doEdit() {
        uiController.notImpl();
    }

    public void setTags(List<TermValue> tags) {
        mdlTags.setTags(tags);
    }

    public List<TermValue> getTags() {
        return mdlTags.getTags();
    }

    public TagTableModel getMdlTags() {
        return mdlTags;
    }

    @Override
    public void reload() {}
}

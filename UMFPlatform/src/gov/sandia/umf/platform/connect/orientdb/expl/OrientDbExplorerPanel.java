/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.expl;

import gov.sandia.umf.platform.connect.orientdb.expl.images.ImageUtil;
import gov.sandia.umf.platform.connect.orientdb.expl.menus.PopupMenuManager;
import gov.sandia.umf.platform.ui.UIController;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import replete.gui.controls.SimpleTextField;
import replete.gui.controls.mnemonics.MButton;
import replete.gui.controls.simpletree.SimpleTree;
import replete.gui.controls.simpletree.TModel;
import replete.gui.controls.simpletree.TNode;
import replete.gui.windows.Dialogs;
import replete.util.Lay;
import replete.util.StringUtil;

public class OrientDbExplorerPanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    private SimpleTree treOrient;
    private PropertiesPanel pnlProperties;
    private OrientDbExplorerPanelTreeModel model;
    private OrientDbExplorerPanelUIController panelUiController;
    private JButton btnExecute;
    private SimpleTextField txtSearch;
    private UIController platUiController;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public OrientDbExplorerPanel(UIController platUiController) {

        this.platUiController = platUiController;
        model = new OrientDbExplorerPanelTreeModel();
        model.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                treOrient.updateUI();
            }
        }, true);
        panelUiController = new OrientDbExplorerPanelUIController(this, model, platUiController);
        PopupMenuManager.init(panelUiController);

        String initSearch = "select * from gov.sandia.umf.n2a$Part where name like '%BBB%'";
        Lay.BLtg(this,
            "C", Lay.SPL("H",
                Lay.BL(
                    "C", Lay.sp(treOrient = new SimpleTree(new TModel(model.getRoot()))),
                    "S", Lay.BL("hgap=5",
                        "C", txtSearch = new SimpleTextField(initSearch),
                        "E", btnExecute = new MButton("&Execute", ImageUtil.getImage("mag.gif")),
                        "eb=5"
                    )
                ),
                pnlProperties = new PropertiesPanel(),
                "divpixel=500"
            )
        );

        txtSearch.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                searchNowYeah();
            }
        });

        btnExecute.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                searchNowYeah();
            }
        });

        treOrient.addKeyListener(deleteListener);
        treOrient.addMouseListener(contextMenuListener);
        treOrient.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                TNode nSel = treOrient.getSelNode();
                if(nSel != null) {
                    pnlProperties.rebuildTable(nSel);
                }
            }
        });
        treOrient.addDoubleClickListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                attemptExpandClass();
            }
        });
        treOrient.addEnterKeyListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                attemptExpandClass();
            }
        });
        treOrient.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(final KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    attemptExpandClass();
                }
            }
        });
    }

    private void attemptExpandClass() {
        TNode nSel = treOrient.getTSelectionNode();
        if(nSel.getObject() instanceof NodeClass && nSel.getCount() == 0) {
            panelUiController.classLoad(nSel);
            ((NodeClass) nSel.getObject()).setLoaded(true);
        }
    }

    protected void searchNowYeah() {
        try {
            String query = txtSearch.getTText();
            if(query.startsWith("select") || query.startsWith("find")) {
                TNode node = model.search(txtSearch.getText());
                if(node == null) {
                    Dialogs.showWarning("Nothing found...");
                } else {
                    treOrient.select(node);
                }
            } else if(query.startsWith("delete")) {
                Integer count = model.delete(query);
                Dialogs.showWarning(count + " record" + StringUtil.s(count) + " deleted.");
            }
        } catch(Exception ex) {
            Dialogs.showDetails("Error", "Error", ex);
        }
    }

    public void addDefaultSource() {
        panelUiController.databaseAdd(model.getRoot());
//        model.addSource(details);
//        treOrient.expandPath(new TreePath(model.getRoot()));
    }

    private KeyAdapter deleteListener = new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
            if(e.getKeyCode() == KeyEvent.VK_DELETE) {
                TreePath selPath = treOrient.getSelectionPath();
                if(selPath == null) {
                    return;
                }
                TNode nAny = (TNode) selPath.getLastPathComponent();
                /*if(nAny.getUserObject() instanceof NodeCluster) {
                    activeTree.setSelectionPath(selPath);
                    activeTree.requestFocusInWindow();
                    CachedCluster activeCluster = ((NodeCluster) nAny.getUserObject()).getCluster();
                    if(!activeCluster.isUnassigned()) {
                        uiController.actionClusterDelete(activeCluster);
                    }
                } else if(nAny.getUserObject() instanceof NodeNode) {
                    activeTree.setSelectionPath(selPath);
                    activeTree.requestFocusInWindow();
                    CachedNode activeNode = ((NodeNode) nAny.getUserObject()).getNode();
                    uiController.actionNodeDelete(activeNode);
                }*/
            }
        }
    };

    private MouseListener contextMenuListener = new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
            if(SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
                TreePath selPath = treOrient.getPathForLocation(e.getX(), e.getY());
                if(selPath == null) {
                    return;
                }
                TNode nAny = (TNode) selPath.getLastPathComponent();
                treOrient.setSelectionPath(selPath);
                treOrient.requestFocusInWindow();
                if(nAny.getObject() instanceof NodeRoot) {
                    PopupMenuManager.getAllDatabasesMenu().show(treOrient,
                        e.getX(), e.getY(), nAny);
                } else if(nAny.getObject() instanceof NodeDb) {
                    PopupMenuManager.getDatabaseMenu().show(treOrient,
                        e.getX(), e.getY(), nAny);
                } else if(nAny.getObject() instanceof NodeClass) {
                    PopupMenuManager.getClassMenu().show(treOrient,
                        e.getX(), e.getY(), nAny);
                } else if(nAny.getObject() instanceof NodeRecord) {
                    PopupMenuManager.getRecordMenu().show(treOrient,
                        e.getX(), e.getY(), nAny);
                } else if(nAny.type(NodeField.class, NodeList.class, NodeMap.class)) {
                    PopupMenuManager.getFieldMenu().show(treOrient,
                        e.getX(), e.getY(), nAny);
                }
            }
        }
        @Override
        public void mouseClicked(MouseEvent e) {
            if(SwingUtilities.isLeftMouseButton(e) && e.getClickCount() > 1) {
                TreePath selPath = treOrient.getPathForLocation(e.getX(), e.getY());
                if(selPath == null) {
                    return;
                }
                /*TNode nAny = (TNode) selPath.getLastPathComponent();
                if(nAny.isLeaf() && nAny.getUserObject() instanceof NodeJob) {
                    activeTree.setSelectionPath(selPath);
                    activeTree.requestFocusInWindow();
                    CachedJob activeJob = ((NodeJob) nAny.getUserObject()).getJob();
                    uiController.actionJobShowState(activeJob);
                }*/
            }
        }
    };

    public JButton getDefaultButton() {
        return btnExecute;
    }
    public void doFocus() {
        treOrient.requestFocusInWindow();
    }

    public void expand(TNode nActive) {
        treOrient.expand(nActive);
        treOrient.setSelNode(nActive);
    }
    public void expandAll(TNode nActive) {
        treOrient.expandAll(nActive);
        treOrient.setSelNode(nActive);
    }
}

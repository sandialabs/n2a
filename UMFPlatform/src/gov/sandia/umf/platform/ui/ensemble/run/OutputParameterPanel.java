/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.ensemble.run;

import gov.sandia.n2a.parms.Parameter;
import gov.sandia.n2a.parms.ParameterBundle;
import gov.sandia.n2a.parms.ParameterDomain;
import gov.sandia.n2a.parms.ParameterKeyPath;
import gov.sandia.umf.platform.ui.ensemble.TransferableParameterBundles;
import gov.sandia.umf.platform.ui.ensemble.images.ImageUtil;
import gov.sandia.umf.platform.ui.ensemble.tree.FilterableParameterTreePanel;
import gov.sandia.umf.platform.ui.ensemble.tree.NodeParameter;
import gov.sandia.umf.platform.ui.ensemble.tree.NodeSubdomain;
import gov.sandia.umf.platform.ui.ensemble.tree.ParameterTree;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.TreePath;

import replete.gui.controls.IconButton;
import replete.gui.controls.MList;
import replete.gui.controls.SimpleTextField;
import replete.gui.controls.simpletree.NodeBase;
import replete.gui.controls.simpletree.SimpleTree;
import replete.gui.controls.simpletree.TNode;
import replete.gui.controls.simpletree.TPath;
import replete.gui.windows.Dialogs;
import replete.util.Lay;

public class OutputParameterPanel extends JPanel implements DragGestureListener {


    ////////////
    // FIELDS //
    ////////////

//    private ParameterDomain allDomains = new ParameterDomain();
//    private ParameterDomain selectedDomains = new ParameterDomain();
    private TNode nRoot;
    private FilterableParameterTreePanel pnlFilterableTree;
    private JButton btnRemove;
    private JButton btnAdd;
    private SimpleTextField txtAddNew;
    private MList lstSelOutputs;
    private DefaultListModel mdlSelOutputs;
    private Color DRAG_ON_COLOR = Lay.clr("FFF993");


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public OutputParameterPanel() {
        String lblStyle = "fg=white";
        String pnlStyle = "bg=100, eb=3, augb=mb(1b,black), gradient, gradclr1=140, gradclr2=100";

        mdlSelOutputs = new DefaultListModel();

        Lay.BLtg(this,
            "C", Lay.SPL(
                Lay.BL(
                    "N", Lay.p(
                        Lay.lb("Available Parameters", lblStyle),
                        pnlStyle
                    ),
                    "C", pnlFilterableTree = new FilterableParameterTreePanel(null)
                ),
                Lay.BL(
                    "N", Lay.p(
                        Lay.lb("Desired Output Parameters", lblStyle),
                        pnlStyle
                    ),
                    "C", Lay.BL(
                        "N", Lay.BL(
                            "C", txtAddNew = new SimpleTextField(),
                            "E", Lay.FL("hgap=0,vgap=0",
                                Lay.p(btnAdd = new IconButton(ImageUtil.getImage("add.gif")), "Add Selected Output Parameter", "eb=5l"),
                                Lay.p(btnRemove = new IconButton(ImageUtil.getImage("remove.gif"), "Remove Selected Output Parameter"), "eb=5l")
                            ),
                            "eb=5tlr"
                        ),
                        "C", Lay.p(Lay.sp(lstSelOutputs = new MList(mdlSelOutputs)), "eb=5")
                    )
                ),
                "divpixel=300"
            )
        );

        lstSelOutputs.setUseStandardDeleteBehavior(true);
        lstSelOutputs.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                btnRemove.setEnabled(lstSelOutputs.getSelectedIndex() != -1);
            }
        });

        txtAddNew.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addSelOutput();
            }
        });

        btnAdd.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addSelOutput();
            }
        });

        btnRemove.setEnabled(false);
        btnRemove.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                lstSelOutputs.removeSelected();
            }
        });

        final ParameterTree treParams = pnlFilterableTree.getTree();

        treParams.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                TreePath path = treParams.getPathForLocation(e.getX(), e.getY());
                if(path == null || ((TNode) path.getLastPathComponent()).getUserObject() instanceof NodeSubdomain) {
                    treParams.setCursor(Cursor.getDefaultCursor());
                } else {
                    treParams.setCursor(DragCursors.getOpenhandcursor());
                }
            }
        });
        treParams.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount() > 1) {
                    if(treParams.getSelectionCount() != 0) {
                        TPath pSel = treParams.getTSelectionPath();
                        ParameterKeyPath path = new ParameterKeyPath();
                        for(TNode nSel : pSel) {
                            if(nSel.getUserObject() == null) {
                                continue;
                            }
                            if(nSel.getUserObject() instanceof NodeSubdomain) {
//                                path.add(((NodeSubdomain) nSel.getUserObject()).getSubdomain().getName());
                                // TODO Maybe bring this back when
                                continue;
                            } else {
                                path.add(((NodeParameter) nSel.getUserObject()).getParameter().getKey());
                            }
                        }
                        txtAddNew.setText(txtAddNew.getText() + path.toString(false));
                    }
                }
            }
        });

        DragSource ds = new DragSource();
        ds.createDefaultDragGestureRecognizer(treParams, DnDConstants.ACTION_LINK, this);

        new TextFieldDropTargetListener(txtAddNew);
        new ListBoxDropTargetListener(lstSelOutputs);
    }

    protected void addSelOutput() {
        String param = txtAddNew.getText().trim();
        if(!param.equals("")) {
            if(isValid(param)) {
                mdlSelOutputs.addElement(txtAddNew.getText());
                txtAddNew.setText("");
                txtAddNew.requestFocusInWindow();
            } else {
                Dialogs.showError(OutputParameterPanel.this, "Problem!");
            }
        }
    }

    protected boolean isValid(String param) {
        return true;
    }

    // AMap wrap operation
    private void populate(TNode nParent, ParameterDomain domain) {
        for(ParameterDomain subdomain : domain.getSubdomains()) {
            TNode nSubdomain = new TNode(new NodeSubdomain(subdomain));
            populate(nSubdomain, subdomain);
            nParent.add(nSubdomain);
        }
        for(Parameter param : domain.getParameters()) {
            TNode nParam = new TNode(new NodeParameter(param));
            nParent.add(nParam);
        }
    }

    public void setOutputParameters(ParameterDomain domains) {
//        allDomains = domains;
//        selectedDomains.clear();

        nRoot = new TNode();
        if(domains != null) {
            populate(nRoot, domains);
        }
        pnlFilterableTree.getTree().setOriginalModel(nRoot);
    }

    public List<String> getSelectedOutputExpressions() {
        return (List) Arrays.asList(mdlSelOutputs.toArray());
    }


    /////////////////
    // DRAG & DROP //
    /////////////////

    private class TextFieldDropTargetListener extends DropTargetAdapter {


        ////////////
        // FIELDS //
        ////////////

        //private DropTarget dropTarget;  Unneeded as of now
        private SimpleTextField txtAddNew;
        private Color prevColor;


        /////////////////
        // CONSTRUCTOR //
        /////////////////

        public TextFieldDropTargetListener(SimpleTextField txt) {
            txtAddNew = txt;
            prevColor = txtAddNew.getBackground();
            new DropTarget(
                txtAddNew, DnDConstants.ACTION_LINK,
                this, true, null);
        }


        ////////////////
        // OVERRIDDEN //
        ////////////////

        @Override
        public void dragEnter(DropTargetDragEvent dtde) {
            dragOn();
        }
        @Override
        public void dragExit(DropTargetEvent dte) {
            dragOff();
        }
        @Override
        public void drop(DropTargetDropEvent event) {
            try {
                Transferable tr = event.getTransferable();
                List<ParameterBundle> bundles =
                    (List<ParameterBundle>)
                        tr.getTransferData(TransferableParameterBundles.BUNDLE_FLAVOR);
                if(event.isDataFlavorSupported(TransferableParameterBundles.BUNDLE_FLAVOR)) {
                    ParameterBundle bundle = bundles.get(0);
                    String path = "";
                    for(ParameterDomain domain : bundle.getDomains()) {
                        path += domain.getName() + ".";
                    }
                    path += bundle.getParameter().getKey();
                    event.acceptDrop(DnDConstants.ACTION_LINK);
                    int cpos = txtAddNew.getCaretPosition();
                    txtAddNew.insert(cpos, path);
                    txtAddNew.setCaretPosition(cpos + path.length());
                    txtAddNew.requestFocusInWindow();
                    event.dropComplete(true);
                    return;
                }
            } catch(Exception e) {
                e.printStackTrace();
            } finally {
                event.rejectDrop();
                dragOff();
            }
        }


        //////////
        // MISC //
        //////////

        private void dragOn() {
            txtAddNew.setBackground(DRAG_ON_COLOR);
        }
        private void dragOff() {
            txtAddNew.setBackground(prevColor);
        }
    }

    private class ListBoxDropTargetListener extends DropTargetAdapter {


        ////////////
        // FIELDS //
        ////////////

        //private DropTarget dropTarget;  Unneeded as of now
        private JList lstSelOutputs;
        private Color prevColor;


        /////////////////
        // CONSTRUCTOR //
        /////////////////

        public ListBoxDropTargetListener(JList lst) {
            lstSelOutputs = lst;
            prevColor = txtAddNew.getBackground();
            new DropTarget(
                lstSelOutputs, DnDConstants.ACTION_LINK,
                this, true, null);
        }


        ////////////////
        // OVERRIDDEN //
        ////////////////

        @Override
        public void dragEnter(DropTargetDragEvent dtde) {
            dragOn();
        }
        @Override
        public void dragExit(DropTargetEvent dte) {
            dragOff();
        }
        @Override
        public void drop(DropTargetDropEvent event) {
            try {
                Transferable tr = event.getTransferable();
                List<ParameterBundle> bundles =
                    (List<ParameterBundle>)
                        tr.getTransferData(TransferableParameterBundles.BUNDLE_FLAVOR);
                if(event.isDataFlavorSupported(TransferableParameterBundles.BUNDLE_FLAVOR)) {
                    ParameterBundle bundle = bundles.get(0);
                    String path = "";
                    for(ParameterDomain domain : bundle.getDomains()) {
                        path += domain.getName() + ".";
                    }
                    path += bundle.getParameter().getKey();
                    event.acceptDrop(DnDConstants.ACTION_LINK);
                    mdlSelOutputs.addElement(path);
                    event.dropComplete(true);
                    return;
                }

            } catch(Exception e) {
                e.printStackTrace();

            } finally {
                event.rejectDrop();
                dragOff();
            }
        }


        //////////
        // MISC //
        //////////

        private void dragOn() {
            lstSelOutputs.setBackground(DRAG_ON_COLOR);
        }
        private void dragOff() {
            lstSelOutputs.setBackground(prevColor);
        }
    }

    public void dragGestureRecognized(DragGestureEvent event) {
        Cursor cursor = null;
        SimpleTree list = (SimpleTree) event.getComponent();
        TreePath[] paths = list.getSelectionPaths();
        if(paths == null) {
            return;
        }
        List<ParameterBundle> bundles = new ArrayList<ParameterBundle>();
        for(TreePath path : paths) {
            List<ParameterDomain> domains = new ArrayList<ParameterDomain>();
            for(int p = 0; p < path.getPathCount(); p++) {
                Object u = ((TNode)path.getPathComponent(p)).getObject();
                if(u instanceof NodeSubdomain) {
                    domains.add(((NodeSubdomain) u).getSubdomain());
                }
            }
            NodeBase uLeaf = ((TNode) path.getLastPathComponent()).getObject();
            if(uLeaf instanceof NodeParameter) {
                NodeParameter p = (NodeParameter) uLeaf;
                bundles.add(new ParameterBundle(domains, p.getParameter()));
            } else {
                // TODO: add all children?
                return;
            }
        }
        if(event.getDragAction() == DnDConstants.ACTION_LINK) {
            cursor = DragCursors.getGrabhandcursor();
        }
        event.startDrag(cursor, new TransferableParameterBundles(bundles));
    }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.model;

import gov.sandia.n2a.data.Layer;
import gov.sandia.n2a.data.LayerOrient;
import gov.sandia.n2a.data.ModelOrient;
import gov.sandia.n2a.data.Part;
import gov.sandia.n2a.data.PartOrient;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.parse.ASTVarNode;
import gov.sandia.n2a.ui.orientdb.eq.EquationTreeEditContext;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeEquation;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodePart;
import gov.sandia.n2a.ui.orientdb.model.topotree.NodeBridgeLayer;
import gov.sandia.n2a.ui.orientdb.model.topotree.NodeCompEquations;
import gov.sandia.n2a.ui.orientdb.model.topotree.NodeLayer;
import gov.sandia.n2a.ui.orientdb.model.topotree.NodeLayerEquations;
import gov.sandia.n2a.ui.orientdb.model.topotree.NodeRoot;
import gov.sandia.umf.platform.UMF;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ui.CommonWarningMessage;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;
import gov.sandia.umf.platform.ui.search.SearchType;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import replete.gui.controls.IconButton;
import replete.gui.controls.mnemonics.MMenuItem;
import replete.gui.controls.simpletree.NodeBase;
import replete.gui.controls.simpletree.SimpleTree;
import replete.gui.controls.simpletree.TNode;
import replete.gui.windows.Dialogs;
import replete.gui.windows.common.CommonFrame;
import replete.util.Lay;
import replete.util.User;

public class LayerTreePanel extends ModelEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private SimpleTree treLayers;
    private TNode root;
    private JPopupMenu mnuChangePopup;
    private JPopupMenu mnuOverridePopup;
    private JPopupMenu mnuOpenPopup;

    // Misc

    private NDoc editEq;  // Poorly-scalable way to implement the popup menus, ok for now.
    private String editPrefix;
    private Part openPart;

    // Edit Context
    private EquationTreeEditContext eqContext;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public LayerTreePanel(UIController uic, ModelOrient m) {
        super(uic, m);

        root = new TNode(new NodeRoot());
        treLayers = new SimpleTree(root);
        treLayers.setExpandsSelectedPaths(true);
        treLayers.setRootVisible(false);

        treLayers.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    editNode();
                }
            }
        });

        JButton btnAddLayer = new IconButton(ImageUtil.getImage("addlayer.gif"), 2);
        btnAddLayer.setToolTipText("Add Layer...");
        btnAddLayer.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addLayer();
            }
        });

        JButton btnEdit = new IconButton(ImageUtil.getImage("edit.gif"), 2);
        btnEdit.setToolTipText("Edit...");
        btnEdit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                editNode();
            }
        });
        JButton btnDelete = new IconButton(ImageUtil.getImage("remove.gif"), 2);
        btnDelete.setToolTipText("Remove");
        btnDelete.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                removeNode();
            }
        });


        // Side Buttons

        JButton btnAddEqn = new IconButton(ImageUtil.getImage("addexpr.gif"), 2);
        btnAddEqn.setToolTipText("Add Equation...");
        btnAddEqn.addActionListener(addNewListener);

        JButton btnAddAnnot = new IconButton(ImageUtil.getImage("addannot.gif"), 2);
        btnAddAnnot.setToolTipText("Add Annotation...");
        btnAddAnnot.addActionListener(addAnnotListener);

        JButton btnAddRef = new IconButton(ImageUtil.getImage("booknew.gif"), 2);
        btnAddRef.setToolTipText("Add Reference...");
        btnAddRef.addActionListener(addRefListener);

        JButton btnMoveUp = new IconButton(ImageUtil.getImage("up.gif"), 2);
        btnMoveUp.setToolTipText("Move Up");
        btnMoveUp.addActionListener(moveUpListener);

        JButton btnMoveDown = new IconButton(ImageUtil.getImage("down.gif"), 2);
        btnMoveDown.setToolTipText("Move Down");
        btnMoveDown.addActionListener(moveDownListener);

        treLayers.addMouseListener(contextMenuListener);

        JMenuItem mnuChange = new MMenuItem("&Change", ImageUtil.getImage("change.gif"));
        mnuChange.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
//                fireEditEquationNotifier();
            }
        });
        mnuChangePopup = new JPopupMenu();
        mnuChangePopup.add(mnuChange);

        JMenuItem mnuOverride = new MMenuItem("&Override", ImageUtil.getImage("change.gif"));
        mnuOverride.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
//                fireEditEquationNotifier();
            }
        });
        mnuOverridePopup = new JPopupMenu();
        mnuOverridePopup.add(mnuOverride);

/*        JMenuItem mnuOpen = new MMenuItem("&Open", ImageUtil.getImage("open.gif"));
        mnuOpen.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.openExisting(PartX.class, openPart.getId());
            }
        });
        mnuOpenPopup = new JPopupMenu();
        mnuOpenPopup.add(mnuOpen);
*/
        Lay.BLtg(this,
            "C", Lay.p(Lay.sp(treLayers), "eb=5r"),
            "E", Lay.BxL("Y",
                Lay.BL(btnAddLayer, "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL(btnAddEqn, "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL(btnAddAnnot, "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL(btnAddRef, "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL(btnEdit, "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL(btnDelete, "eb=5b,alignx=0.5,maxH=20"),
                Lay.BL(btnMoveUp, "eb=5b,alignx=0.5,maxH=20"),
                Lay.hn(btnMoveDown, "alignx=0.5"),
                Box.createVerticalGlue()
            )
        );

        treLayers.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_DELETE) {
                    removeNode();
                }
            }
        });

        eqContext = new EquationTreeEditContext(uiController, treLayers, NodeLayerEquations.class);
        eqContext.addEqChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                fireContentChangedNotifier();
            }
        });
    }

    ActionListener addNewListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            eqContext.addEquation(null);
        }
    };
    ActionListener addAnnotListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            eqContext.addAnnotationToSelected();
        }
    };
    ActionListener addRefListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            eqContext.addReferenceToSelected();
        }
    };
    ActionListener moveUpListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            eqContext.moveSelectedUp();
//            checkEnabledButtons(); TODO
        }
    };
    ActionListener moveDownListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            eqContext.moveSelectedDown();
//            checkEnabledButtons(); TODO
        }
    };

    private MouseListener contextMenuListener = new MouseAdapter ()
    {
        @Override
        public void mouseReleased (MouseEvent e)
        {
            if (SwingUtilities.isRightMouseButton (e)  &&  e.getClickCount () == 1)
            {
                TreePath selPath = treLayers.getPathForLocation (e.getX (), e.getY ());
                if (selPath == null) return;
                TNode nAny = (TNode) selPath.getLastPathComponent ();
                Object any = nAny.getUserObject ();
                if (any instanceof NodeLayer)
                {
                    treLayers.setSelectionPath (selPath);
                    openPart = ((NodeLayer) any).getLayer ().getDerivedPart ().getParent ();
                    mnuOpenPopup.show (treLayers, e.getX (), e.getY ());
                }
                else if (any instanceof NodeEquation)
                {
                    treLayers.setSelectionPath (selPath);
                    editEq = ((NodeEquation) any).getEq ();
                    NodeBase parent = (NodeBase) ((TNode) nAny.getParent ()).getUserObject ();
                    if (parent instanceof NodeLayerEquations)
                    {
                        editPrefix = null;
                        mnuChangePopup.show (treLayers, e.getX (), e.getY ());
                    }
                    else if (parent instanceof NodePart)
                    {
                        // TODO: fully implement editing of override equations
                        editPrefix = ((NodePart) parent).part.name;
                        mnuOverridePopup.show (treLayers, e.getX(), e.getY());
                    }
                }
            }
        }
    };

    private void editNode() {
        TNode nSel = treLayers.getSelNode();
        if(nSel != null) {
            if(nSel.getUserObject() instanceof NodeLayer) {
                NodeLayer uLay = (NodeLayer) nSel.getUserObject();
                String newLayerName = Dialogs.showInput("Enter a new name for this layer:", "New Layer Name", uLay.getLayer().getName());
                if(newLayerName != null) {
                    uLay.getLayer().setName(newLayerName);
                    treLayers.updateUI();
                    fireContentChangedNotifier();
                }
            } else if(eqContext.isInContext(nSel)) {
                eqContext.editSelectedNode();
            }
        }
    }

    private void removeNode() {
        TNode nSel = treLayers.getSelNode();
        if(nSel != null) {
            if(nSel.getUserObject() instanceof NodeLayer) {
                treLayers.remove(nSel);
                fireContentChangedNotifier();
            } else if(eqContext.isInContext(nSel)) {
                eqContext.removeSelectedNode();
            }
        }
    }
    private void addLayer() {
        CommonFrame parent = (CommonFrame) SwingUtilities.getRoot(this);
        String searchTitle = "Add Layer(s) Using Compartment(s)";
        List<NDoc> chosen = uiController.searchRecordOrient(parent,
            SearchType.COMPARTMENT, searchTitle, null, ImageUtil.getImage("complete.gif"), ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        if(chosen != null) {
            for(NDoc chosenRecord : chosen) {
                NDoc chosenPart = chosenRecord;
                String dfltValue = model.getNextLayerName(chosenPart);
                while(true) {
                    String name = Dialogs.showInput(
                        "Enter a name for this layer for compartment '" + chosenPart.get("name") + "' (must use only characters [A-Za-z0-9_]):",
                        "Layer Name", dfltValue);
                    dfltValue = name;
                    if(name != null) {
                        name = name.trim();
                        if(!ASTVarNode.isValidVariableName(name)) {
                            CommonWarningMessage.showInvalidVariable("Layer name");
                            continue;
                        }
                        if(model.existsLayerName(name)) {
                            Dialogs.showWarning("This name is already used by another layer.");
                            continue;
                        }
                        if(model.existsBridgeName(name)) {
                            Dialogs.showWarning("This name is already used by a bridge.");
                            continue;
                        }
                        try {
                            Part childPart = new PartOrient(
                                "$LayerDerivedPart",
                                User.getName(),
                                "$LayerDerivedPart",
                                "compartment",
                                chosenPart);
                            Layer newLayer = new LayerOrient(
                                name, childPart, model);
                            TNode nLay = addLayer(newLayer);
                            treLayers.select(nLay);
                            fireContentChangedNotifier();
                        } catch(Exception e) {
                            UMF.handleUnexpectedError(null, e, "An error has occurred adding the layer to the tree.");
                        }
                    }
                    break;
                }
            }
        }
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public List<Layer> getLayers() {
        List<Layer> layers = new ArrayList<Layer>();
        for(int i = 0; i < root.getChildCount(); i++) {
            TNode nLay = (TNode) root.getChildAt(i);
            NodeLayer uLay = (NodeLayer) nLay.getUserObject();
            TNode nSpec = (TNode) nLay.getChildAt(0);
            Layer layer = uLay.getLayer();
            layer.getDerivedPart().setEqs(eqContext.getEquations(nSpec));
            layers.add(layer);
        }
        return layers;
    }

    public void setLayers(List<Layer> layers) {
        root.removeAllChildren();
        for(Layer layer : layers) {
            addLayer(layer);
        }
        treLayers.updateUI();
    }

    private TNode addLayer(Layer layer) {

        // Create layer node
        NodeLayer uLay = new NodeLayer(layer);
        TNode nLay = new TNode(uLay);
        treLayers.append(nLay);

        // Create layer equation section.
        TNode nLayerEq = new TNode(new NodeLayerEquations());
        treLayers.append(nLay, nLayerEq);

        Part dPart = layer.getDerivedPart();
        eqContext.setEquations(nLayerEq, dPart.getEqs());

        // Create parent part equation section.
        TNode nComp = new TNode (new NodeCompEquations ());
        treLayers.append (nLay, nComp);
        try
        {
            insertEquationTree (treLayers.getModel (), nComp, new EquationSet (dPart.getParent ().getSource ()));
        }
        catch (Exception error)
        {
            System.out.println ("Exception during equation tree construction: " + error);
        }

        // Expand
        TreePath newPath = new TreePath(new Object[]{root, nLay});
        treLayers.expandPath(newPath);
        // TODO: Select?

        return nLay;
    }

    public static void insertEquationTree (DefaultTreeModel treeModel, TNode targetNode, EquationSet s)
    {
        if (s.connectionBindings != null)
        {
            for (Entry<String,EquationSet> a : s.connectionBindings.entrySet ())
            {
                TNode node = new TNode (new NodeBridgeLayer (a.getValue ().name, a.getKey ()));
                treeModel.insertNodeInto (node, targetNode, targetNode.getChildCount ());
            }
        }
        for (Variable v : s.variables)
        {
            if (v.equations == null) continue;
            for (EquationEntry e : v.equations)
            {
                TNode node = new TNode (new NodeEquation (e));
                treeModel.insertNodeInto (node, targetNode, targetNode.getChildCount ());
                for (Entry<String,String> nv : e.getMetadata ())
                {
                    TNode subnode = new TNode (new NodeAnnotation (nv.getKey (), nv.getValue ()));
                    treeModel.insertNodeInto (subnode, node, node.getChildCount ());
                }
            }
        }
        for (EquationSet p : s.parts)
        {
            TNode node = new TNode (new NodePart (p));
            treeModel.insertNodeInto (node, targetNode, targetNode.getChildCount ());
            insertEquationTree (treeModel, node, p);
        }
    }

    public List<Layer> getSelectedLayers() {
        TreePath[] paths = treLayers.getSelectionPaths();
        List<Layer> layers = new ArrayList<Layer>();
        if(paths != null) {
            for(TreePath path : paths) {
                TNode nSel = (TNode) path.getLastPathComponent();
                if(nSel.getUserObject() instanceof NodeLayer) {
                    layers.add(((NodeLayer) nSel.getUserObject()).getLayer());
                }
            }
        }
        return layers;
    }

    @Override
    public void reload() {}
}

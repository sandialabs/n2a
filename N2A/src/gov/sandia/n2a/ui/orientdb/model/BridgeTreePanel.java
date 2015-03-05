/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.model;

import gov.sandia.n2a.data.Bridge;
import gov.sandia.n2a.data.BridgeOrient;
import gov.sandia.n2a.data.Layer;
import gov.sandia.n2a.data.ModelOrient;
import gov.sandia.n2a.data.Part;
import gov.sandia.n2a.data.PartOrient;
import gov.sandia.n2a.language.ParsedEquation;
import gov.sandia.n2a.language.SpecialVariables;
import gov.sandia.n2a.language.gen.ASTVarNode;
import gov.sandia.n2a.ui.orientdb.eq.EquationSummaryFlatPanel;
import gov.sandia.n2a.ui.orientdb.eq.EquationTreeEditContext;
import gov.sandia.n2a.ui.orientdb.model.topotree.NodeBridge;
import gov.sandia.n2a.ui.orientdb.model.topotree.NodeBridgeEquations;
import gov.sandia.n2a.ui.orientdb.model.topotree.NodeBridgeLayer;
import gov.sandia.n2a.ui.orientdb.model.topotree.NodeBridgeLayers;
import gov.sandia.n2a.ui.orientdb.model.topotree.NodeConnEquations;
import gov.sandia.n2a.ui.orientdb.model.topotree.NodeLayer;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.TreePath;

import replete.gui.controls.IconButton;
import replete.gui.controls.simpletree.SimpleTree;
import replete.gui.controls.simpletree.TNode;
import replete.gui.windows.Dialogs;
import replete.gui.windows.common.CommonFrame;
import replete.util.Lay;
import replete.util.User;

public class BridgeTreePanel extends ModelEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private TopologyContext tContext;
    private SimpleTree treBridges;
    private TNode root;

    // Edit Context
    private EquationTreeEditContext eqContext;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public BridgeTreePanel(UIController uic, ModelOrient m, TopologyContext ctx) {
        super(uic, m);
        tContext = ctx;

        root = new TNode(new NodeRoot());
        treBridges = new SimpleTree(root);
        treBridges.setExpandsSelectedPaths(true);
        treBridges.setRootVisible(false);

        treBridges.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    editNode();
                }
            }
        });

        JButton btnAddBridge = new IconButton(ImageUtil.getImage("bridge.gif"), 2);
        btnAddBridge.setToolTipText("Add Bridge...");
        btnAddBridge.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addBridge();
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

        //treBridges.addMouseListener(contextMenuListener);

        // TODO: Context menu construction here?

        Lay.BLtg(this,
            "C", Lay.p(Lay.sp(treBridges), "eb=5r"),
            "E", Lay.BxL("Y",
                Lay.BL(btnAddBridge, "eb=5b,alignx=0.5,maxH=20"),
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

        treBridges.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.getKeyCode() == KeyEvent.VK_DELETE) {
                    removeNode();
                }
            }
        });

        eqContext = new EquationTreeEditContext(uiController, treBridges, NodeBridgeEquations.class);
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

    /*private MouseListener contextMenuListener = new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
            if(SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
                TreePath selPath = treLayers.getPathForLocation(e.getX(), e.getY());
                if(selPath == null) {
                    return;
                }
                TNode nAny = (TNode) selPath.getLastPathComponent();
                if(nAny.getUserObject() instanceof NodeLayer) {
                    treLayers.setSelectionPath(selPath);
                    openPart = ((NodeLayer) nAny.getUserObject()).getLayer().getDerivedPart().getParent();
                    mnuOpenPopup.show(treLayers, e.getX(), e.getY());
                } else if(nAny.getUserObject() instanceof NodeEquation) {
                    treLayers.setSelectionPath(selPath);
                    if(((TNode)nAny.getParent()).getUserObject() instanceof NodeLayerEquations) {
                        editEq = ((NodeEquation) nAny.getUserObject()).getEq();
                        editPrefix = null;
                        mnuChangePopup.show(treLayers, e.getX(), e.getY());
                    } else {
                        editEq = ((NodeEquation) nAny.getUserObject()).getEq();
                        NodePart parentPart = (NodePart) ((TNode)nAny.getParent()).getUserObject();
                        if(parentPart.getAlias() != null) {
                            editPrefix = parentPart.getAlias();   // Means is included part.
                        }
                        mnuOverridePopup.show(treLayers, e.getX(), e.getY());
                    }
                }
            }
        }
    };*/

    private void editNode() {
        TNode nSel = treBridges.getSelNode();
        if(nSel != null) {
            if(nSel.getUserObject() instanceof NodeLayer) {
                NodeBridge uBridge = (NodeBridge) nSel.getUserObject();
                String newBridgeName = Dialogs.showInput("Enter a new name for this layer:", "New Layer Name", uBridge.getBridge().getName());
                if(newBridgeName != null) {
                    uBridge.getBridge().setName(newBridgeName);
                    treBridges.updateUI();
                    fireContentChangedNotifier();
                }
            } else if(eqContext.isInContext(nSel)) {
                eqContext.editSelectedNode();
            }
        }
    }

    private void removeNode() {
        TNode nSel = treBridges.getSelNode();
        if(nSel != null) {
            if(nSel.getUserObject() instanceof NodeBridge) {
                treBridges.remove(nSel);
                fireContentChangedNotifier();
            } else if(eqContext.isInContext(nSel)) {
                eqContext.removeSelectedNode();
            }
        }
    }

    private void addBridge() {
        List<Layer> layers = tContext.getSelectedLayers();
        if(layers.size() != 0) {
            NDoc chosenConn;

            // Search setup variables.
            CommonFrame parent = (CommonFrame) SwingUtilities.getRoot(this);
            String searchTitle = "Select Connection to connect these layers";
            List<NDoc> chosen = uiController.searchRecordOrient(parent,
                SearchType.CONNECTION, searchTitle, null,
                ImageUtil.getImage("complete.gif"), ListSelectionModel.SINGLE_SELECTION);

            if(chosen != null) {
                chosenConn = chosen.get(0);
            } else {
                return;
            }

            String dfltValue = model.getNextBridgeName(chosenConn);
            while(true) {
                String name = Dialogs.showInput(
                    "Enter a name for this bridge (must use only characters [A-Za-z0-9_]):",
                    "Bridge Name", dfltValue);
                dfltValue = name;
                if(name != null) {
                    name = name.trim();
                    if(!ASTVarNode.isValidVariableName(name)) {
                        CommonWarningMessage.showInvalidVariable("Bridge name");
                        continue;
                    }
                    if(model.existsLayerName(name)) {
                        Dialogs.showWarning("This name is already used by a layer.");
                        continue;
                    }
                    if(model.existsBridgeName(name)) {
                        Dialogs.showWarning("This name is already used by another bridge.");
                        continue;
                    }
                    try {
                        Part childConn = new PartOrient(
                            SpecialVariables.BRIDGE_DP,
                            User.getName(),
                            SpecialVariables.BRIDGE_DP,
                            "connection", chosenConn);
                        Bridge bridge = new BridgeOrient(
                            layers,
                            name, model, childConn);
                        TNode nLay = addBridge(bridge);
                        treBridges.select(nLay);
                        fireContentChangedNotifier();
                    } catch(Exception e) {
                        UMF.handleUnexpectedError(null, e, "An error has occurred adding the bridge to the tree.");
                    }
                }
                break;
            }
        } else {
            Dialogs.showWarning("You must select one or more layers to be connected first.");
        }
    }

    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public List<Bridge> getBridges() {
        List<Bridge> bridges = new ArrayList<Bridge>();
        for(int i = 0; i < root.getChildCount(); i++) {
            TNode nBridge = (TNode) root.getChildAt(i);
            NodeBridge uBridge = (NodeBridge) nBridge.getUserObject();
            TNode nSpec = (TNode) nBridge.getChildAt(1);
            Bridge bridge = uBridge.getBridge();
            bridge.getDerivedPart().setEqs(eqContext.getEquations(nSpec));
            bridges.add(bridge);
        }
        return bridges;
    }

    public void setBridges(List<Bridge> bridges) {
        root.removeAllChildren();
        for(Bridge bridge : bridges) {
            addBridge(bridge);
        }
        treBridges.updateUI();
    }

    private TNode addBridge(Bridge bridge) {

        // Create layer node
        NodeBridge uBridge = new NodeBridge(bridge);
        TNode nBridge = new TNode(uBridge);
        treBridges.append(nBridge);

        // Create layer section.
        TNode nLayers = new TNode(new NodeBridgeLayers());
        treBridges.append(nBridge, nLayers);

        List<Layer> layers = bridge.getLayers();
        Map<String, Layer> aliases = bridge.getAliasLayerMap();
        if(layers.size() == 1) {
            Layer layer = layers.get(0);
            String alias = aliases.keySet().toArray(new String[0])[0];
            TNode nLayer = new TNode(new NodeBridgeLayer(layer, alias));
            treBridges.append(nLayers, nLayer);
            alias = aliases.keySet().toArray(new String[0])[1];
            nLayer = new TNode(new NodeBridgeLayer(layer, alias));
            treBridges.append(nLayers, nLayer);
        } else {
            for(Layer layer : layers) {
                String chosenAlias = "UNKNOWN";
                for(String alias : aliases.keySet()) {
                    Layer aliasedLayer = aliases.get(alias);
                    if(layer.getSource().getId().equals(aliasedLayer.getSource().getId())) {
                        chosenAlias = alias;
                        break;
                    }
                }
                TNode nLayer = new TNode(new NodeBridgeLayer(layer, chosenAlias));
                treBridges.append(nLayers, nLayer);
            }
        }

        // Create layer equation section.
        TNode nBridgeEq = new TNode(new NodeBridgeEquations());
        treBridges.append(nBridge, nBridgeEq);

        Part dPart = bridge.getDerivedPart();
        eqContext.setEquations(nBridgeEq, dPart.getEqs());

        // Create parent part equation section.
        TNode nComp = new TNode(new NodeConnEquations());
        treBridges.append(nBridge, nComp);

        Set<ParsedEquation> eqs = EquationSummaryFlatPanel.createFlatEquationListFromPart(dPart.getParent().getSource());
        EquationSummaryFlatPanel.setFlatEquationListOnNode(treBridges.getModel(), nComp, eqs);

        // Expand
        TreePath newPath = new TreePath(new Object[]{root, nBridge});
        treBridges.expandPath(newPath);
        // TODO: Select?

        return nBridge;
    }

    @Override
    public void reload() {}
}

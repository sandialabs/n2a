/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.eq;

import gov.sandia.n2a.data.Model;
import gov.sandia.n2a.data.PartOrient;
import gov.sandia.n2a.eqset.EquationAssembler;
import gov.sandia.n2a.eqset.PartEquationMap;
import gov.sandia.n2a.language.Annotation;
import gov.sandia.n2a.language.ParsedEquation;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeNone;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeParsedEquation;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeRoot;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ui.UIController;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.JPanel;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultTreeModel;

import replete.event.ChangeNotifier;
import replete.gui.controls.simpletree.SimpleTree;
import replete.gui.controls.simpletree.TNode;
import replete.util.Lay;
import replete.util.User;


public class EquationSummaryFlatPanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    // Core

    private UIController uiController;
    private Set<ParsedEquation> eqs;

    // UI

    private SimpleTree treEqs;
    private TNode root;
//    private TNode nLocal;
//    private TNode nInherited;
//    private TNode nIncluded;
//    private boolean showPopups;
//    private JPopupMenu mnuChangePopup;
//    private JPopupMenu mnuOverridePopup;
//    private JPopupMenu mnuOpenPopup;
//    private EquationX editEq;  // Poorly-scalable way to implement the popup menus, ok for now.
//    private String editPrefix;
//    private PartX openPart;

    protected ChangeNotifier editEquationNotifier = new ChangeNotifier(this);
    public void addEditEquationListener(ChangeListener listener) {
        editEquationNotifier.addListener(listener);
    }
    protected void fireEditEquationNotifier() {
        editEquationNotifier.fireStateChanged();
    }



    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public EquationSummaryFlatPanel(UIController uic, boolean showPop) {
        uiController = uic;
        //showPopups = showPop;

        root = new TNode(new NodeRoot());

        treEqs = new SimpleTree(root);
        treEqs.setExpandsSelectedPaths(true);
        treEqs.setRootVisible(false);
        /*if(showPopups) {
            treEqs.addMouseListener(contextMenuListener);
        }

        JMenuItem mnuChange = new MMenuItem("&Change", ImageUtil.getImage("change.gif"));
        mnuChange.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                fireEditEquationNotifier();
            }
        });
        mnuChangePopup = new JPopupMenu();
        mnuChangePopup.add(mnuChange);

        JMenuItem mnuOverride = new MMenuItem("&Override", ImageUtil.getImage("change.gif"));
        mnuOverride.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                fireEditEquationNotifier();
            }
        });
        mnuOverridePopup = new JPopupMenu();
        mnuOverridePopup.add(mnuOverride);

        JMenuItem mnuOpen = new MMenuItem("&Open", ImageUtil.getImage("open.gif"));
        mnuOpen.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uiController.openExistingPart(openPart.getId());
            }
        });
        mnuOpenPopup = new JPopupMenu();
        mnuOpenPopup.add(mnuOpen);
        */

        Lay.BLtg(this,
            "C", Lay.sp(treEqs)
        );
    }

    public void setEquations(Set<ParsedEquation> eqs) {
        this.eqs = eqs;
        //root.setUserObject(new NodeRoot(part.getType()));
        rebuild();
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public void setStateClear() {
        root.removeAllChildren();
        treEqs.updateUI();
    }

    public void setStateError() {
        root.removeAllChildren();
        treEqs.updateUI();
    }

    public void rebuild() {
        setFlatEquationListOnNode(treEqs.getModel(), root, eqs);
        treEqs.updateUI();
        for (int i = 0; i < treEqs.getRowCount(); i++) {
            treEqs.expandRow(i);
        }
    }

    public static Set<ParsedEquation> createFlatEquationListFromModel(Model model) {
        // cew TODO - should this always be creating a new PartOrient, or should it look for an existing derivedPart?
        PartOrient tempPart = new PartOrient("$TempModelPart", User.getName(), "$TempModelPart", "compartment", (NDoc) null);
        // cew TODO - translate.
        // maybe just add all the derivedParts associated with Layers and Bridges as include associations to tempPart?
        // shouldn't this deal with bridge equations also?  what about outputs?
//        for(Layer layer : model.getLayers()) {
//            tempPart.getPartAssociations().add(new PartAssociation(PartAssociationType.INCLUDE, layer.getName(), tempPart, layer.getDerivedPart()));
//        }
        return null;//createFlatEquationListFromPart(tempPart);

        // TODO? WHICH TO CHOOSE?  Both have design drawbacks... the bottom one fully boxes, and the above obeys boxing rules like with parts.

        // This is an attempt to sort the parsed equations.  This
        // is not an easy task.  The metadata is populated on the
        // PE objects by the EquationAssembler.
        // TODO: Review and clean up.  Right now since the reason
        // is the top level, and that is overridden by the time
        // the target is being evaluated, inherited equations of
        // includes do not necessarily sort above included equa-
        // tions of includes. (Hawk.Egg.baby = 3 (before) Hawk.a
        // which is inherited).
//        Set<ParsedEquation> peqSet = new TreeSet<ParsedEquation>(new Comparator<ParsedEquation>() {
//            public int compare(ParsedEquation o1, ParsedEquation o2) {
//                String layer1 = (String) o1.getMetadata("layer");
//                String layer2 = (String) o2.getMetadata("layer");
//                if(layer1.compareTo(layer2) == 0) {
//                    String reason1 = (String) o1.getMetadata("reason");
//                    String reason2 = (String) o2.getMetadata("reason");
//                    if(reason2.compareTo(reason1) == 0) {
//                        Integer level1 = (Integer) o1.getMetadata("level");
//                        Integer level2 = (Integer) o2.getMetadata("level");
//                        if(level1 == level2) {
//                            Integer order1 = (Integer) o1.getMetadata("order");
//                            if(order1 == null) {
//                                order1 = Integer.MAX_VALUE;
//                            }
//                            Integer order2 = (Integer) o2.getMetadata("order");
//                            if(order2 == null) {
//                                order2 = Integer.MAX_VALUE;
//                            }
//                            if(order1.equals(order2)) {
//                                Integer index1 = (Integer) o1.getMetadata("index");
//                                Integer index2 = (Integer) o2.getMetadata("index");
//                                if(index1.equals(index2)) {
//                                    return 1;
//                                }
//                                return index1 - index2;
//                            }
//                            return order1 - order2;
//                        }
//                        return level1 - level2;
//                    }
//                    return reason2.compareTo(reason1);
//                }
//                return layer1.compareTo(layer2);
//            }
//        });
//
//        for(Layer layer : model.getLayers()) {
//            PartEquationMap map = EquationAssembler.getAssembledPartEquations(layer.getDerivedPart());
//            final String prefix = layer.getName();
//            for(ParsedEquation peq : map.getAsList()) {
//
//                // TOD: Make this more integrated with the EA class.
//                ASTNodeRendererMap nodeRnMap = new ASTNodeRendererMap();
//                nodeRnMap.put(ASTVarNode.class, new ASTNodeRenderer() {
//                    public String render(ASTNodeBase node, ASTRenderingContext context) {
//                        ASTVarNode varNode = (ASTVarNode) node;
////                        if(!paMap.containsKey(varNode.getVariableName()) || paMap.existsPlusEqualsForVar(varNode.getVariableName())) {
////                            return node.toString();
////                        }
//                        return prefix + EquationAssembler.PREFIX_SEP + node.toString();
//                    }
//                });
//                String transformedLine = peq.getTree().toReadable(true, nodeRnMap);
//                for(Annotation anno : peq.getAnnotations().values()) {
//                    transformedLine += "  @" + anno.getTree().toReadable(true, nodeRnMap);
//                }
//                try {
//                    ParsedEquation transformedPeq = EquationParser.parse(transformedLine);
//                    transformedPeq.setMetadata("layer", prefix);
//                    transformedPeq.copyMetadata(peq);
//                    peqSet.add(transformedPeq);
//                } catch(ParseException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//        return peqSet;
    }

    public static Set<ParsedEquation> createFlatEquationListFromPart(NDoc record) {
        PartEquationMap map = EquationAssembler.getAssembledPartEquations(record);

        // This is an attempt to sort the parsed equations.  This
        // is not an easy task.  The metadata is populated on the
        // PE objects by the EquationAssembler.
        // TODO: Review and clean up.  Right now since the reason
        // is the top level, and that is overridden by the time
        // the target is being evaluated, inherited equations of
        // includes do not necessarily sort above included equa-
        // tions of includes. (Hawk.Egg.baby = 3 (before) Hawk.a
        // which is inherited).
        Set<ParsedEquation> peqSet = new TreeSet<ParsedEquation>(new Comparator<ParsedEquation>() {
            public int compare(ParsedEquation o1, ParsedEquation o2) {
                String reason1 = (String) o1.getMetadata("reason");
                String reason2 = (String) o2.getMetadata("reason");
                if(reason2.compareTo(reason1) == 0) {
                    Integer level1 = (Integer) o1.getMetadata("level");
                    Integer level2 = (Integer) o2.getMetadata("level");
                    if(level1 == level2) {
                        Integer order1 = (Integer) o1.getMetadata("order");
                        if(order1 == null) {
                            order1 = Integer.MAX_VALUE;
                        }
                        Integer order2 = (Integer) o2.getMetadata("order");
                        if(order2 == null) {
                            order2 = Integer.MAX_VALUE;
                        }
                        if(order1.equals(order2)) {
                            Integer index1 = (Integer) o1.getMetadata("index");
                            Integer index2 = (Integer) o2.getMetadata("index");
                            if(index1.equals(index2)) {
                                return 1;
                            }
                            return index1 - index2;
                        }
                        return order1 - order2;
                    }
                    return level1 - level2;
                }
                return reason2.compareTo(reason1);
            }
        });

        for(String key : map.keySet()) {
            List<ParsedEquation> peqs = map.get(key);
            peqSet.addAll(peqs);
        }

        return peqSet;
    }

    public static void setFlatEquationListOnNode(DefaultTreeModel treeModel, TNode targetNode, Set<ParsedEquation> peqSet) {
        targetNode.removeAllChildren();

        if(peqSet == null) {
            return;
        }

        for(ParsedEquation peq : peqSet) {
            NodeParsedEquation uEqn = new NodeParsedEquation(peq);
            TNode nEqn = new TNode(uEqn);
            treeModel.insertNodeInto(nEqn, targetNode, targetNode.getChildCount());
            for(Annotation an : peq.getAnnotations().values()) {
                NodeAnnotation uAnnot = new NodeAnnotation(an);
                TNode nAnnot = new TNode(uAnnot);
                treeModel.insertNodeInto(nAnnot, nEqn, nEqn.getChildCount());
            }
            NDoc eq = (NDoc) peq.getMetadata("eq");
            //for(EquationReferenceX eqRef : eq.getRefs()) {
                /*NodeEqReference uER = new NodeEqReference(eqRef);TODO!!!
                TNode nER = new TNode(uER);
                treeModel.insertNodeInto(nER, nEqn, nEqn.getChildCount());*/
            //}
        }

        if(targetNode.getChildCount() == 0) {
            NodeNone uNone = new NodeNone("(no equations)");
            TNode nNone = new TNode(uNone);
            treeModel.insertNodeInto(nNone, targetNode, targetNode.getChildCount());
        }
    }

    /*private MouseListener contextMenuListener = new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
            if(SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
                TreePath selPath = treEqs.getPathForLocation(e.getX(), e.getY());
                if(selPath == null) {
                    return;
                }
                TNode nAny = (TNode) selPath.getLastPathComponent();
                if(nAny.getUserObject() instanceof NodePart) {
                    treEqs.setSelectionPath(selPath);
                    openPart = ((NodePart) nAny.getUserObject()).getPart();
                    mnuOpenPopup.show(EquationSummaryFlatPanel.this, e.getX(), e.getY());
                } else if(nAny.getUserObject() instanceof NodeEquation) {
                    treEqs.setSelectionPath(selPath);
                    if(((TNode)nAny.getParent()).getUserObject() instanceof NodeLocal) {
                        editEq = ((NodeEquation) nAny.getUserObject()).getEq();
                        editPrefix = null;
                        mnuChangePopup.show(EquationSummaryFlatPanel.this, e.getX(), e.getY());
                    } else {
                        editEq = ((NodeEquation) nAny.getUserObject()).getEq();
                        NodePart parentPart = (NodePart) ((TNode)nAny.getParent()).getUserObject();
                        if(parentPart.getAlias() != null) {
                            editPrefix = parentPart.getAlias();   // Means is included part.
                        }
                        mnuOverridePopup.show(EquationSummaryFlatPanel.this, e.getX(), e.getY());
                    }
                }
            }
        }
    };
    */
}

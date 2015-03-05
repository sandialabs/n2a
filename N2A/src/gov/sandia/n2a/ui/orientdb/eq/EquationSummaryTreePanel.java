/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.eq;

import gov.sandia.n2a.eqset.EquationAssembler;
import gov.sandia.n2a.eqset.PartEquationMap;
import gov.sandia.n2a.language.Annotation;
import gov.sandia.n2a.language.ParsedEquation;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeEquation;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeIncluded;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeInherited;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeLocal;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeNone;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodePart;
import gov.sandia.n2a.ui.orientdb.eq.tree.NodeSummaryRoot;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;

import replete.event.ChangeNotifier;
import replete.gui.controls.mnemonics.MMenuItem;
import replete.gui.controls.simpletree.SimpleTree;
import replete.gui.controls.simpletree.TNode;
import replete.util.Lay;

public class EquationSummaryTreePanel extends JPanel {


    ////////////
    // FIELDS //
    ////////////

    // Core

    private UIController uiController;
    private NDoc part;

    // UI

    private SimpleTree treEqs;
    private TNode root;
    private TNode nLocal;
    private TNode nInherited;
    private TNode nIncluded;
    private boolean showPopups;
    private JPopupMenu mnuChangePopup;
    private JPopupMenu mnuOverridePopup;
    private JPopupMenu mnuOpenPopup;
    private NDoc editEq;  // Poorly-scalable way to implement the popup menus, ok for now.
    private String editPrefix;
    private NDoc openPart;

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

    public EquationSummaryTreePanel(UIController uic, NDoc p, boolean showPop) {
        uiController = uic;
        part = p;
        showPopups = showPop;

        root = new TNode(new NodeSummaryRoot((String) part.get("type")));
        root.add(nLocal = new TNode(new NodeLocal()));
        root.add(nInherited = new TNode(new NodeInherited()));
        root.add(nIncluded = new TNode(new NodeIncluded()));

        treEqs = new SimpleTree(root);
        treEqs.setExpandsSelectedPaths(true);
        treEqs.setRootVisible(true);
        if(showPopups) {
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
                uiController.openRecord(openPart);
            }
        });
        mnuOpenPopup = new JPopupMenu();
        mnuOpenPopup.add(mnuOpen);

        Lay.BLtg(this,
            "C", Lay.sp(treEqs)
        );
    }

    public void setPart(NDoc p) {
        part = p;
        root.setUserObject(new NodeSummaryRoot((String) part.get("type")));
        rebuild();
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public void setStateClear() {
        root.removeAllChildren();
        ((NodeSummaryRoot)root.getUserObject()).setString(NodeSummaryRoot.CALC);
        treEqs.updateUI();
    }

    public void setStateError() {
        root.removeAllChildren();
        ((NodeSummaryRoot)root.getUserObject()).setString(NodeSummaryRoot.ERROR);
        treEqs.updateUI();
    }

    public void rebuild() {
        String pName = part.get("name");
        if(pName == null || pName.equals("")) {
            pName = "<No Name Yet>";
        }
        String extra = " For \"" + pName.trim() + "\"";
        ((NodeSummaryRoot)root.getUserObject()).setString(NodeSummaryRoot.ALLEQ + extra);
        root.removeAllChildren();

        List<ParsedEquation> overriding = new ArrayList<ParsedEquation>();
        List<ParsedEquation> overridden = new ArrayList<ParsedEquation>();
        List<NDoc> eqs = part.getAndSetValid("eqs", new ArrayList<NDoc>(), List.class);
        if(eqs.size() != 0) {
            root.add(nLocal);
            nLocal.removeAllChildren();
            PartEquationMap map = EquationAssembler.getAssembledPartEquations(part);
            overriding = map.getOverridingEquations();
            addPartEquationNodes(eqs, nLocal, overriding, overridden);
            overridden = map.getOverriddenEquations();
        }

        // Parent Hierarchy
        NDoc parent = part.get("parent");
        if(parent != null) {
            root.add(nInherited);
            nInherited.removeAllChildren();
            int anIndex = 0;
            // Emergency recursion detection (rebuild should not even be
            // called if there is a loop already).
            Set<String> beans = new HashSet<String>();
            while(parent != null) {
                if(beans.contains(parent.getId())) {
                    setStateError();
                    return;
                }
                String anLabel = constructAncestorLabel(anIndex++);
                NodePart uAnc = new NodePart(parent, anLabel, null);
                TNode nAnc = new TNode(uAnc);
                treEqs.getModel().insertNodeInto(nAnc, nInherited, nInherited.getChildCount());
                List<NDoc> eqs2 = parent.getAndSetValid("eqs", new ArrayList<NDoc>(), List.class);
                addPartEquationNodes(eqs2, nAnc, overriding, overridden);
                beans.add(parent.getId());
                parent = parent.get("parent");
            }
        }

        // Includes
        List<NDoc> associations = part.get("associations");
        List<NDoc> iAssocs = new ArrayList<NDoc>();
        for(NDoc assoc : associations) {
            if(((String) assoc.get("type")).equalsIgnoreCase("include")) {
                iAssocs.add(assoc);
            }
        }
        if(iAssocs.size() != 0) {
            root.add(nIncluded);
            nIncluded.removeAllChildren();
            for(NDoc assoc : iAssocs) {
                NDoc destPart = assoc.get("dest");
                NodePart uIncl = new NodePart(destPart, null,
                    assoc.get("name") == null ? (String) destPart.get("name") : (String) assoc.get("name"));
                TNode nIncl = new TNode(uIncl);
                treEqs.getModel().insertNodeInto(nIncl, nIncluded, nIncluded.getChildCount());
                List<NDoc> eqs2 = destPart.getAndSetValid("eqs", new ArrayList<NDoc>(), List.class);
                addPartEquationNodes(eqs2, nIncl, overriding, overridden);
            }
        }

        if(root.getChildCount() == 0) {
            NodeNone uNone = new NodeNone("(no equations)");
            TNode nNone = new TNode(uNone);
            treEqs.getModel().insertNodeInto(nNone, root, root.getChildCount());
        }

        treEqs.updateUI();
        for (int i = 0; i < treEqs.getRowCount(); i++) {
            treEqs.expandRow(i);
        }
    }

    private void addPartEquationNodes(List<NDoc> eqs, MutableTreeNode parent, List<ParsedEquation> overriding, List<ParsedEquation> overridden) {
        for(NDoc eq : eqs) {
            NodeEquation uEqn = new NodeEquation(eq);
            ParsedEquation peq = uEqn.getParsed();
            for(ParsedEquation ov : overriding) {
                if(eq.getId().equals(ov.getMetadata("eqid"))) {
                    uEqn.setOverriding(true);
                    break;
                }
            }
            for(ParsedEquation ov : overridden) {
                if(eq.getId().equals(ov.getMetadata("eqid"))) {
                    uEqn.setOverridden(true);
                    break;
                }
            }
            TNode nEqn = new TNode(uEqn);
            treEqs.getModel().insertNodeInto(nEqn, parent, parent.getChildCount());
            for(Annotation an : peq.getAnnotations().values()) {
                NodeAnnotation uAnnot = new NodeAnnotation(an);
                TNode nAnnot = new TNode(uAnnot);
                treEqs.getModel().insertNodeInto(nAnnot, nEqn, nEqn.getChildCount());
            }
        }
        if(eqs.size() == 0) {
            NodeNone uNone = new NodeNone("(no equations)");
            TNode nNone = new TNode(uNone);
            treEqs.getModel().insertNodeInto(nNone, parent, parent.getChildCount());
        }
    }

    private String constructAncestorLabel(int anIndex) {
        String anLabel;
        if(anIndex == 0) {
            anLabel = "parent";
        } else if(anIndex == 1) {
            anLabel = "grandparent";
        } else {
            anLabel = "grandparent";
            for(int x = 1; x < anIndex; x++) {
                anLabel = "great-" + anLabel;
            }
        }
        return anLabel.substring(0, 1).toUpperCase() + anLabel.substring(1);
    }

    private MouseListener contextMenuListener = new MouseAdapter() {
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
                    mnuOpenPopup.show(EquationSummaryTreePanel.this, e.getX(), e.getY());
                } else if(nAny.getUserObject() instanceof NodeEquation) {
                    treEqs.setSelectionPath(selPath);
                    if(((TNode)nAny.getParent()).getUserObject() instanceof NodeLocal) {
                        editEq = ((NodeEquation) nAny.getUserObject()).getEq();
                        editPrefix = null;
                        mnuChangePopup.show(EquationSummaryTreePanel.this, e.getX(), e.getY());
                    } else {
                        editEq = ((NodeEquation) nAny.getUserObject()).getEq();
                        NodePart parentPart = (NodePart) ((TNode)nAny.getParent()).getUserObject();
                        if(parentPart.getAlias() != null) {
                            editPrefix = parentPart.getAlias();   // Means is included part.
                        }
                        mnuOverridePopup.show(EquationSummaryTreePanel.this, e.getX(), e.getY());
                    }
                }
            }
        }
    };

    public NDoc getChangeOverrideEquation() {
        return editEq;
    }
    public String getChangeOverridePrefix() {
        return editPrefix;
    }
}

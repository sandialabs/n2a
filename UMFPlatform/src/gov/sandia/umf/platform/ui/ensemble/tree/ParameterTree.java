/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ui.ensemble.tree;

import gov.sandia.umf.platform.ui.ensemble.images.ImageUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;

import replete.gui.controls.simpletree.NodeSimpleLabel;
import replete.gui.controls.simpletree.TModel;
import replete.gui.controls.simpletree.TNode;
import replete.gui.controls.vsstree.VisualStateSavingNoFireTree;

public class ParameterTree extends VisualStateSavingNoFireTree {


    ///////////
    // FIELD //
    ///////////

    private TModel origModel;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ParameterTree(TreeNode root) {
        super(root);
        origModel = (TModel) getModel();  // Save original model
        setRootVisible(false);
        setShowsRootHandles(true);
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    protected TreeCellRenderer createRenderer() {
        return new ParamTreeRenderer();
    }


    //////////////
    // MUTATORS //
    //////////////

    public void setOriginalModel(TNode root) {
        setModel(root);
        origModel = (TModel) getModel();
    }

    public void setShowDefaultValues(boolean show) {
        ((ParamTreeRenderer) getCellRenderer()).setShowDefaultValues(show);
        updateUI();
    }


    ////////////
    // FILTER //
    ////////////

    public void filter(String text) {
        saveState();
        if(text.equals("")) {
            setModel(origModel);

        } else {
            text = text.toUpperCase();
            List<TNode> found = new ArrayList<TNode>();
            search(origModel.getTRoot(), found, text);
            if(found.size() == 0) {
                TNode nRoot = new TNode();
                nRoot.add(new NodeSimpleLabel("<No Results>", ImageUtil.getImage("noresults.gif")));
                setModel(new TModel(nRoot));
            } else {
                TNode nRoot = new TNode();
                Map<TNode, TNode> oldNewMap = new HashMap<TNode, TNode>();
                for(TNode nFound : found) {
                    TNode[] path = nFound.getTPathSegments();
                    TNode nParent = nRoot;

                    for(TNode nSegment : path) {
                        if(nSegment == origModel.getRoot() && nParent == nRoot) {
                            // do nothing

                        } else if(oldNewMap.containsKey(nSegment)) {
                            nParent = oldNewMap.get(nSegment);

                        } else {
                            Object uSegment = nSegment.getUserObject();
                            TNode nNew = new TNode(uSegment);
                            nParent.add(nNew);
                            nParent = nNew;
                            oldNewMap.put(nSegment, nNew);
                        }
                    }
                }
                setModel(new TModel(nRoot));
            }
        }
        restoreStateNoFire();
        updateUI();
    }

    private void search(TNode nParent, List<TNode> found, String text) {
        if(nParent.getUserObject() != null && nParent != getRoot()) {
            if(nParent.getObject().toString().toUpperCase().contains(text)) {
                if(nParent.getUserObject() instanceof NodeParameter) {
                    found.add(nParent);
                }
            }
        }
        for(TNode nChild : nParent.getTChildren()) {
            search(nChild, found, text);
        }
    }
}

/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.awt.FontMetrics;
import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class Move extends Undoable
{
    protected List<String> path;  // to parent of moved node
    protected int          indexBefore;  // all indices are unfiltered
    protected int          indexAfter;
    protected int          indexMetadata;
    protected boolean      orderAbsent;  // before the move

    public Move (NodePart parent, int indexBefore, int indexAfter)
    {
        path        = parent.getKeyPath ();
        orderAbsent = parent.source.child ("$metadata", "gui.order") == null;

        if (orderAbsent)
        {
            NodeBase metadataNode = parent.child ("$metadata");
            if (metadataNode == null)  // We will create a new $metadata node (to hold the gui.order node). This changes meaning of the given indices.
            {
                // Test whether the first child is $inherit, and whether it will remain so after the move. In that case, don't put $metadata in front of it.
                if (((NodeBase) parent.getChildAt (0)).source.key ().equals ("$inherit")  &&  indexBefore > 0  &&  indexAfter > 0) indexMetadata = 1;  // otherwise it is 0
                if (indexAfter >= indexMetadata) indexAfter++;
            }
        }

        this.indexBefore = indexBefore;
        this.indexAfter  = indexAfter;
    }

    public void undo ()
    {
        super.undo ();
        apply (path, indexAfter, indexBefore, indexMetadata, false, orderAbsent);
    }

    public void redo ()
    {
        super.redo ();
        apply (path, indexBefore, indexAfter, indexMetadata, orderAbsent, false);
    }

    public static void apply (List<String> path, int indexBefore, int indexAfter, int indexMetadata, boolean createOrder, boolean destroyOrder)
    {
        NodeBase parent = locateNode (path);
        if (parent == null) throw new CannotUndoException ();

        ModelEditPanel mep = ModelEditPanel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        NodeBase moveNode = (NodeBase) parent.getChildAt (indexBefore);
        model.removeNodeFromParent (moveNode);

        NodeBase metadataNode = parent.child ("$metadata");
        if (createOrder)
        {
            if (metadataNode == null)
            {
                metadataNode = new NodeAnnotations ((MPart) parent.source.set ("", "$metadata"));
                model.insertNodeIntoUnfiltered (metadataNode, parent, indexMetadata);
            }
            NodeBase orderNode = new NodeAnnotation ((MPart) metadataNode.source.set ("", "gui.order"));
            model.insertNodeIntoUnfiltered (orderNode, metadataNode, metadataNode.getChildCount ());
        }
        if (destroyOrder)
        {
            NodeBase orderNode = metadataNode.child ("gui.order");
            FontMetrics fm = orderNode.getFontMetrics (tree);

            metadataNode.source.clear ("gui.order");
            model.removeNodeFromParent (metadataNode.child ("gui.order"));
            if (metadataNode.getChildCount () == 0)
            {
                parent.source.clear ("$metadata");
                model.removeNodeFromParent (metadataNode);
            }
            else
            {
                metadataNode.updateTabStops (fm);
                metadataNode.allNodesChanged (model);
            }
        }

        model.insertNodeIntoUnfiltered (moveNode, parent, indexAfter);

        TreeNode[] movePath = moveNode.getPath ();
        if (! destroyOrder) mep.panelEquations.updateOrder (movePath);
        mep.panelEquations.updateVisibility (movePath);
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (edit instanceof Move)
        {
            Move m = (Move) edit;
            if (m.path.equals (path)  &&  indexAfter == m.indexBefore)
            {
                indexAfter = m.indexAfter;
                return true;
            }
        }
        return false;
    }
}

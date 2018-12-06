/*
Copyright 2017-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelModel;
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
        orderAbsent = parent.source.child ("$metadata", "gui", "order") == null;

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
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotUndoException ();

        PanelModel mep = PanelModel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        NodeBase moveNode = (NodeBase) parent.getChildAt (indexBefore);
        model.removeNodeFromParent (moveNode);

        NodeAnnotations metadataNode = (NodeAnnotations) parent.child ("$metadata");
        boolean needBuild = false;
        if (createOrder)
        {
            if (metadataNode == null)
            {
                metadataNode = new NodeAnnotations ((MPart) parent.source.childOrCreate ("$metadata"));
                model.insertNodeIntoUnfiltered (metadataNode, parent, indexMetadata);
            }
            metadataNode.source.childOrCreate ("gui", "order");
            needBuild = true;
        }
        if (destroyOrder)
        {
            MNode mparent = metadataNode.source;
            mparent.clear ("gui", "order");
            if (mparent.size () == 0)
            {
                parent.source.clear ("$metadata");
                model.removeNodeFromParent (metadataNode);
            }
            else
            {
                needBuild = true;
            }
        }
        if (needBuild)
        {
            List<String> expanded = AddAnnotation.saveExpandedNodes (tree, metadataNode);
            metadataNode.build ();
            metadataNode.filter (model.filterLevel);
            if (metadataNode.visible (model.filterLevel))
            {
                model.nodeStructureChanged (metadataNode);
                AddAnnotation.restoreExpandedNodes (tree, metadataNode, expanded);
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

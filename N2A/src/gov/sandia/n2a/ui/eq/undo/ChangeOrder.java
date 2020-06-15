/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class ChangeOrder extends UndoableView
{
    protected List<String> path;  // to parent of moved node
    protected int          indexBefore;  // all indices are unfiltered
    protected int          indexAfter;
    protected int          indexMetadata;
    protected boolean      orderAbsent;  // before the move

    public ChangeOrder (NodePart parent, int indexBefore, int indexAfter)
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
        apply (indexAfter, indexBefore, indexMetadata, false, orderAbsent);
    }

    public void redo ()
    {
        super.redo ();
        apply (indexBefore, indexAfter, indexMetadata, orderAbsent, false);
    }

    public void apply (int indexBefore, int indexAfter, int indexMetadata, boolean createOrder, boolean destroyOrder)
    {
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotUndoException ();

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();

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
            if (mparent.child ("gui").size () == 0) mparent.clear ("gui");
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
            List<String> expanded = AddAnnotation.saveExpandedNodes (pet.tree, metadataNode);
            metadataNode.build ();
            metadataNode.filter ();
            if (metadataNode.visible ())
            {
                model.nodeStructureChanged (metadataNode);
                AddAnnotation.restoreExpandedNodes (pet.tree, metadataNode, expanded);
            }
        }

        model.insertNodeIntoUnfiltered (moveNode, parent, indexAfter);

        TreeNode[] movePath = moveNode.getPath ();
        if (! destroyOrder) pet.updateOrder (movePath);
        pet.updateVisibility (movePath);
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (edit instanceof ChangeOrder)
        {
            ChangeOrder m = (ChangeOrder) edit;
            if (m.path.equals (path)  &&  indexAfter == m.indexBefore)
            {
                indexAfter = m.indexAfter;
                return true;
            }
        }
        return false;
    }
}

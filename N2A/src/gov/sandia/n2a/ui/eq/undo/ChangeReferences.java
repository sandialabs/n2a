/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.Enumeration;
import java.util.List;

import javax.swing.undo.CannotUndoException;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.StoredPath;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeContainer;
import gov.sandia.n2a.ui.eq.tree.NodeInherit;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeReferences;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

/**
    Applies multiple changes to an existing $reference subtree.
**/
public class ChangeReferences extends UndoableView
{
    protected List<String> path;          // To node that contains the references. $reference itself may be explicit (for parts) or hidden (for variables).
    protected int          index;         // If we create $reference, this is where it goes.
    protected MNode        undoAdd;       // Nodes to apply during undo, starting at $reference. If $reference is absent, then this is null.
    protected MNode        undoRemove;    // Nodes that should be removed during undo, via MNode.uniqueNodes(). Like undoAdd, this is null if $reference is absent.
    protected MNode        doAdd;         // The nodes to be changed, rooted at $reference. There is no corresponding doRemove.
    protected boolean      multi;

    public ChangeReferences (NodeBase parent, MNode reference)
    {
        super (parent);

        path  = parent.getKeyPath ();
        doAdd = reference;

        MNode currentTree = parent.source.child ("$reference");
        if (currentTree == null)
        {
            // We will create a new $reference node. Don't put it in front of either $inherit or $metadata.
            if (parent instanceof NodePart)
            {
                int i = 0;
                Enumeration<?> children = parent.children ();
                while (children.hasMoreElements ())
                {
                    Object o = children.nextElement ();
                    if      (o instanceof NodeInherit)     index = i + 1;
                    else if (o instanceof NodeAnnotations) index = i + 1;
                    i++;
                }
            }
        }
        else
        {
            undoAdd    = new MVolatile ();
            undoRemove = new MVolatile ();
            undoAdd   .merge (reference);
            undoRemove.merge (reference);
            undoAdd   .changes     (currentTree);
            undoRemove.uniqueNodes (currentTree);
        }
    }

    public void setMulti (boolean value)
    {
        multi = value;
    }

    public void undo ()
    {
        super.undo ();
        apply (undoAdd, undoRemove);
    }

    public void redo ()
    {
        super.redo ();
        apply (doAdd, null);
    }

    public void apply (MNode add, MNode remove)
    {
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotUndoException ();

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = null;
        StoredPath sp = null;
        if (pet != null)
        {
            model = (FilteredTreeModel) pet.tree.getModel ();
            sp = new StoredPath (pet.tree);
        }

        NodeContainer referenceNode;          // The immediate container of reference items in the tree.
        MNode         referenceSource = null; // The $reference node under which all changes are made.
        if (parent instanceof NodeVariable)
        {
            referenceNode   = (NodeContainer) parent;
            referenceSource = referenceNode.source.child ("$reference");
        }
        else  // NodePart is the default case
        {
            referenceNode = (NodeContainer) parent.child ("$reference");
            if (referenceNode != null) referenceSource = referenceNode.source;
        }

        boolean needBuild = true;
        if (add == null)  // This is an undo, and $reference did not exist before, so remove it.
        {
            // We can safely assume that referenceSource is non-null, since we only get here during an undo.
            referenceSource.parent ().clear ("$reference");
            if (parent instanceof NodePart)
            {
                if (model == null) FilteredTreeModel.removeNodeFromParentStatic (referenceNode);
                else               model.removeNodeFromParent (referenceNode);
                needBuild = false;
            }
        }
        else  // Update $reference node. Create if it doesn't exist.
        {
            if (referenceSource == null) referenceSource = parent.source.childOrCreate ("$reference");
            if (referenceNode == null)  // only happens when parent is NodePart
            {
                referenceNode = new NodeReferences ((MPart) referenceSource);
                if (model == null) FilteredTreeModel.insertNodeIntoUnfilteredStatic (referenceNode, parent, index);
                else               model.insertNodeIntoUnfiltered (referenceNode, parent, index);
            }
            if (remove != null) referenceSource.uniqueNodes (remove);
            referenceSource.merge (add);
        }

        if (needBuild)
        {
            referenceNode.build ();
            referenceNode.filter ();
            if (model != null  &&  referenceNode.visible ())
            {
                model.nodeStructureChanged (referenceNode);
            }
        }
        PanelEquationTree.updateVisibility (pet, referenceNode.getPath (), -1, false);
        if (! multi  &&  sp != null) sp.restore (pet.tree, true);  // This forces focus back to original location.
        if (pet != null) pet.animate ();
    }
}

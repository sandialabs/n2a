/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class AddVariable extends Undoable
{
    protected List<String> path;  // to part that contains the variable node
    protected int          index; // where to insert among siblings
    protected MNode        createSubtree;
    protected boolean      nameIsGenerated;
    public    NodeBase     createdNode;  ///< Used by caller to initiate editing. Only valid immediately after call to redo().

    public AddVariable (NodePart parent, int index)
    {
        path = parent.getKeyPath ();
        this.index = index;
        createSubtree = new MVolatile (AddPart.uniqueName (parent, "x"), "");
        nameIsGenerated = true;
    }

    public List<String> fullPath ()
    {
        List<String> result = new ArrayList<String> ();
        result.addAll (path);
        result.add (createSubtree.key ());
        return result;
    }

    public void undo ()
    {
        super.undo ();
        destroy (path, false, createSubtree.key ());
    }

    public static void destroy (List<String> path, boolean canceled, String name)
    {
        // Retrieve created node
        NodeBase parent = locateNode (path);
        if (parent == null) throw new CannotUndoException ();
        NodeVariable createdNode = (NodeVariable) parent.child (name);

        ModelEditPanel mep = ModelEditPanel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        FontMetrics fm = createdNode.getFontMetrics (tree);

        TreeNode[] createdPath = createdNode.getPath ();
        int index = parent.getIndexFiltered (createdNode);
        if (canceled) index--;

        MPart mparent = parent.source;
        mparent.clear (name);
        if (mparent.child (name) == null)  // Node is fully deleted
        {
            model.removeNodeFromParent (createdNode);
        }
        else  // Just exposed an overridden node
        {
            createdNode.build ();
            createdNode.findConnections ();
            createdNode.updateColumnWidths (fm);
        }
        parent.updateTabStops (fm);
        parent.allNodesChanged (model);

        mep.panelEquations.updateOrder (createdPath);
        mep.panelEquations.updateVisibility (createdPath, index);  // includes nodeStructureChanged(), if necessary
    }

    public void redo ()
    {
        super.redo ();
        createdNode = create (path, index, createSubtree, nameIsGenerated);
    }

    public static NodeBase create (List<String> path, int index, MNode newPart, boolean nameIsGenerated)
    {
        NodeBase parent = locateNode (path);
        if (parent == null) throw new CannotRedoException ();

        // Update database
        String name = newPart.key ();
        MPart createdPart = (MPart) parent.source.set (name, "");
        createdPart.merge (newPart);

        // Update GUI

        ModelEditPanel mep = ModelEditPanel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        NodeVariable createdNode = (NodeVariable) parent.child (name);  // It's either a NodeVariable or null. Any other case should be blocked by GUI constraints.
        boolean alreadyExists = createdNode != null;
        if (! alreadyExists) createdNode = new NodeVariable (createdPart);
        if (nameIsGenerated) createdNode.setUserObject ("");  // pure create, so about to go into edit mode. This should only happen on first application of the create action, and should only be possible if visibility is already correct.

        FontMetrics fm = createdNode.getFontMetrics (tree);
        createdNode.updateColumnWidths (fm);  // preempt initialization
        if (! alreadyExists) model.insertNodeIntoUnfiltered (createdNode, parent, index);

        TreeNode[] createdPath = createdNode.getPath ();
        if (! nameIsGenerated)
        {
            createdNode.build ();
            createdNode.findConnections ();
            mep.panelEquations.updateOrder (createdPath);

            parent.updateTabStops (fm);
            parent.allNodesChanged (model);
        }
        mep.panelEquations.updateVisibility (createdPath);

        return createdNode;
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (nameIsGenerated  &&  edit instanceof ChangeVariable)
        {
            ChangeVariable change = (ChangeVariable) edit;
            if (path.equals (change.path)  &&  createSubtree.key ().equals (change.nameBefore))
            {
                // There is not direct way to change the key of an MVolatile, so must create a new node and merge into it.
                MNode nextSubtree = new MVolatile (change.nameAfter, "");
                nextSubtree.merge (createSubtree);
                nextSubtree.set (change.valueAfter);
                createSubtree = nextSubtree;
                nameIsGenerated = false;
                return true;
            }
        }
        return false;
    }
}

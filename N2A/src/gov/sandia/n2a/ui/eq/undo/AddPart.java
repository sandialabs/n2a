/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.Do;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class AddPart extends Do
{
    protected List<String> path;  // to containing part
    protected int          index; // where to insert among siblings
    protected MNode        createSubtree;
    protected boolean      nameIsGenerated;
    public    NodeBase     createdNode;  ///< Used by caller to initiate editing. Only valid immediately after call to redo().

    /**
        @param parent Must be the node that contains $metadata, not the $metadata node itself.
        @param index Position in the unfiltered tree where the node should be inserted.
    **/
    public AddPart (NodeBase parent, int index, String inherit)
    {
        path = parent.getKeyPath ();
        this.index = index;
        createSubtree = new MVolatile (uniqueName (parent, "p"), "");
        if (inherit == null)
        {
            nameIsGenerated = true;
        }
        else
        {
            createSubtree.set ("\"" + inherit + "\"", "$inherit");
            nameIsGenerated = false;  // Because we don't go into edit mode on a drag-n-drop. If that changes, then always set nameIsGenerated to true.
        }
    }

    public static String uniqueName (NodeBase parent, String prefix)
    {
        int suffix = 0;
        while (true)
        {
            String result = prefix + suffix;
            if (parent.source.child (result) == null) return result;
            suffix++;
        }
    }

    public void undo ()
    {
        super.undo ();
        destroy (path, createSubtree.key ());
    }

    public static void destroy (List<String> path, String name)
    {
        // Retrieve created node
        NodeBase parent = locateNode (path);
        if (parent == null) throw new CannotUndoException ();
        NodePart createdNode = (NodePart) parent.child (name);

        ModelEditPanel mep = ModelEditPanel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        TreeNode[] createdPath = createdNode.getPath ();
        int filteredIndex = parent.getIndexFiltered (createdNode);

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
            createdNode.filter (model.filterLevel);
        }

        mep.panelEquations.updateAfterDelete (createdPath, filteredIndex);  // includes nodeStructureChanged(), if necessary
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
        MPart createdPart = (MPart) parent.source.set ("", name);
        createdPart.merge (newPart);


        // Update GUI

        ModelEditPanel mep = ModelEditPanel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        NodePart createdNode = (NodePart) parent.child (name);  // It's either a NodePart or null. Any other case should be blocked by GUI constraints.
        if (createdNode == null)
        {
            createdNode = new NodePart (createdPart);
            model.insertNodeIntoUnfiltered (createdNode, parent, index);
        }
        createdNode.build ();
        createdNode.findConnections ();
        createdNode.filter (model.filterLevel);

        TreeNode[] createdPath = createdNode.getPath ();
        if (nameIsGenerated) createdNode.setUserObject ("");  // pure create, so about to go into edit mode. This should only happen on first application of the create action, and should only be possible if visibility is already correct.
        else mep.panelEquations.updateOrder (createdPath);
        mep.panelEquations.updateVisibility (createdPath);

        return createdNode;
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (nameIsGenerated  &&  edit instanceof ChangePart)
        {
            ChangePart change = (ChangePart) edit;
            if (path.equals (change.path)  &&  createSubtree.key ().equals (change.nameBefore))
            {
                // There is not direct way to change the key of an MVolatile, so must create a new node and merge into it.
                MNode nextSubtree = new MVolatile (change.nameAfter, "");
                nextSubtree.merge (createSubtree);
                createSubtree = nextSubtree;
                nameIsGenerated = false;
                return true;
            }
        }
        return false;
    }
}

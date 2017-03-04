/*
Copyright 2016 Sandia Corporation.
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

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeContainer;

public class DeleteAnnotations extends Undoable
{
    protected List<String> path;  ///< to parent of $metadata node
    protected int          index; ///< Position within parent node
    protected MVolatile    saved; ///< subtree under $metadata

    public DeleteAnnotations (NodeBase node)
    {
        NodeBase container = (NodeBase) node.getParent ();
        path  = container.getKeyPath ();
        index = container.getIndex (node);

        saved = new MVolatile ("$metadata", "");
        saved.merge (node.source.getSource ());  // We only save top-document data. $metadata node is guaranteed to be from top doc, due to guard in NodeAnnotations.delete().
    }

    public void undo ()
    {
        super.undo ();
        NodeFactory factory = new NodeFactory ()
        {
            public NodeBase create (MPart part)
            {
                return new NodeAnnotations (part);
            }
        };
        create (path, index, saved, factory);
    }

    public static void create (List<String> path, int index, MNode saved, NodeFactory factory)
    {
        NodeBase parent = locateNode (path);
        if (parent == null) throw new CannotUndoException ();
        String blockName = saved.key ();
        MPart block = (MPart) parent.source.childOrCreate (blockName);
        block.merge (saved);

        ModelEditPanel mep = ModelEditPanel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        NodeContainer node = (NodeContainer) parent.child (blockName);
        if (node == null)
        {
            node = (NodeContainer) factory.create (block);
            model.insertNodeIntoUnfiltered (node, parent, index);
        }
        node.build ();  // Replaces all nodes, so they are set to require tab initialization.
        node.filter (model.filterLevel);
        mep.panelEquations.updateVisibility (node.getPath ());
    }

    public void redo ()
    {
        super.redo ();
        destroy (path, saved);
    }

    public static void destroy (List<String> path, MNode saved)
    {
        NodeBase parent = locateNode (path);
        if (parent == null) throw new CannotRedoException ();

        ModelEditPanel mep = ModelEditPanel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        String blockName = saved.key ();
        NodeContainer node = (NodeContainer) parent.child (blockName);
        TreeNode[] nodePath = node.getPath ();
        int filteredIndex = parent.getIndexFiltered (node);

        MPart mparent = parent.source;
        mparent.clear (blockName);
        if (mparent.child (blockName) == null)
        {
            model.removeNodeFromParent (node);
        }
        else  // Just exposed an overridden node
        {
            node.build ();  // Necessary to remove all overridden nodes
            node.filter (model.filterLevel);
        }
        mep.panelEquations.updateVisibility (nodePath, filteredIndex);
    }
}

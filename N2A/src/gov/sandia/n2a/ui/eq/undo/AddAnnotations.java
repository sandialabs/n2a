/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeContainer;

public class AddAnnotations extends UndoableView implements AddEditable   // The added node is not actually editable, but sometimes there is a use in retrieving it anyway.
{
    protected List<String> path;  // to parent of $metadata node
    protected int          index; // Position within parent node
    protected MVolatile    saved; // subtree under $metadata
    protected boolean      multi;
    protected boolean      multiLast;
    protected boolean      touchesPin;
    protected boolean      touchesCategory;
    protected NodeBase     createdNode;

    public AddAnnotations (NodeBase parent, int index, MNode metadata)
    {
        path = parent.getKeyPath ();
        this.index = index;

        saved = new MVolatile (null, "$metadata");
        saved.merge (metadata);

        touchesPin      =  metadata.containsKey ("pin");
        touchesCategory =  parent.getTrueParent () == null  &&  metadata.containsKey ("category");
    }

    public void setMulti (boolean value)
    {
        multi = value;
    }

    public void setMultiLast (boolean value)
    {
        multiLast = value;
    }

    public void undo ()
    {
        super.undo ();
        destroy (path, saved.key (), ! multi  ||  multiLast, touchesPin, touchesCategory);
    }

    public static void destroy (List<String> path, String blockName, boolean setSelected, boolean touchesPin, boolean touchesCategory)
    {
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotUndoException ();

        PanelEquationTree pet = parent.getTree ();  // The only way to paste a $metadata block is if the tree is visible.
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();

        NodeContainer node = (NodeContainer) parent.child (blockName);
        TreeNode[] nodePath = node.getPath ();
        int index = parent.getIndexFiltered (node);

        MPart mparent = parent.source;
        mparent.clear (blockName);
        if (mparent.child (blockName) == null)
        {
            model.removeNodeFromParent (node);
        }
        else  // Just exposed an overridden node
        {
            node.build ();  // Necessary to remove all overridden nodes
            node.filter ();
        }
        pet.updateVisibility (nodePath, index, setSelected);
        pet.animate ();

        if (blockName.equals ("$metadata")) AddAnnotation.update (parent, touchesPin, touchesCategory);
    }

    public void redo ()
    {
        super.redo ();
        NodeFactory factory = new NodeFactory ()
        {
            public NodeBase create (MPart part)
            {
                return new NodeAnnotations (part);
            }
        };
        createdNode = create (path, index, saved, factory, multi, touchesPin, touchesCategory);
    }

    public NodeBase getCreatedNode ()
    {
        return createdNode;
    }

    public static NodeBase create (List<String> path, int index, MNode saved, NodeFactory factory, boolean multi, boolean touchesPin, boolean touchesCategory)
    {
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        String blockName = saved.key ();
        NodeBase n = parent.child (blockName);
        if (n != null  &&  ! (n instanceof NodeContainer)) throw new CannotRedoException ();
        NodeContainer node = (NodeContainer) n;

        MPart block = (MPart) parent.source.childOrCreate (blockName);
        block.merge (saved);

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();

        if (node == null)
        {
            node = (NodeContainer) factory.create (block);
            model.insertNodeIntoUnfiltered (node, parent, index);
        }
        node.build ();  // Replaces all nodes, so they are set to require tab initialization.
        node.filter ();
        TreeNode[] nodePath = node.getPath ();
        pet.updateVisibility (nodePath, -2, ! multi);
        if (multi) pet.tree.addSelectionPath (new TreePath (nodePath));
        pet.animate ();

        if (blockName.equals ("$metadata")) AddAnnotation.update (parent, touchesPin, touchesCategory);

        return node;
    }
}

/*
Copyright 2016 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.awt.FontMetrics;
import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class AddAnnotation extends Undoable
{
    protected List<String> path;  // to the parent of the block. In the case of a variable, the block is not directly displayed.
    protected int          index; // Where to insert among siblings. Unfiltered.
    protected String       name;
    protected String       value;
    public    NodeBase     createdNode;  ///< Used by caller to initiate editing. Only valid immediately after call to redo().

    /**
        @param parent Must be the node that contains $metadata, not the $metadata node itself.
        @param index Position in the unfiltered tree where the node should be inserted.
    **/
    public AddAnnotation (NodeBase parent, int index, MNode data)
    {
        path = parent.getKeyPath ();
        this.index = index;
        if (data == null)
        {
            name = uniqueName (parent, "$metadata", "a", false);
        }
        else
        {
            name = uniqueName (parent, "$metadata", data.key (), true);
            value = data.get ();
        }
    }

    public AddAnnotation (NodeBase parent, int index, String name, String value)
    {
        path = parent.getKeyPath ();
        this.index = index;
        this.name  = uniqueName (parent, "$metadata", name, true);
        this.value = value;
    }

    public static String uniqueName (NodeBase parent, String blockName, String prefix, boolean allowEmptySuffix)
    {
        MPart block = (MPart) parent.source.child (blockName);
        if (allowEmptySuffix  &&  (block == null  ||  block.child (prefix) == null)) return prefix;
        int suffix = 1;
        if (block != null)
        {
            while (block.child (prefix + suffix) != null) suffix++;
        }
        return prefix + suffix;
    }

    public void undo ()
    {
        super.undo ();
        destroy (path, false, name, "$metadata");
    }

    public static void destroy (List<String> path, boolean canceled, String name, String blockName)
    {
        // Retrieve created node
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotUndoException ();
        NodeBase container = parent;
        if (parent instanceof NodePart) container = parent.child (blockName);
        NodeBase createdNode = container.child (name);

        PanelEquationTree pet = PanelModel.instance.panelEquations;
        JTree tree = pet.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        FontMetrics fm = createdNode.getFontMetrics (tree);

        boolean containerIsVisible = true;
        TreeNode[] createdPath = createdNode.getPath ();
        int index = container.getIndexFiltered (createdNode);
        if (canceled) index--;

        MPart block = (MPart) parent.source.child (blockName);
        block.clear (name);
        if (block.child (name) == null)  // There is no overridden value, so this node goes away completely.
        {
            model.removeNodeFromParent (createdNode);
            if (block.length () == 0)
            {
                parent.source.clear (blockName);  // commit suicide
                if (parent instanceof NodePart)
                {
                    model.removeNodeFromParent (container);
                    // No need to update order, because we just destroyed $metadata, where order is stored.
                    // No need to update tab stops in grandparent, because block nodes don't offer any tab stops.
                    containerIsVisible = false;
                }
            }
        }
        else  // Just exposed an overridden value, so update display.
        {
            if (container.visible (model.filterLevel))  // We are always visible, but our parent could disappear.
            {
                createdNode.updateColumnWidths (fm);
            }
            else
            {
                containerIsVisible = false;
            }
        }

        if (containerIsVisible)
        {
            container.updateTabStops (fm);
            container.allNodesChanged (model);
        }
        pet.updateVisibility (createdPath, index);
        if (path.size () == 1  &&  name.equals ("lock")) pet.updateLock ();
    }

    public void redo ()
    {
        super.redo ();
        NodeFactory factory = new NodeFactory ()
        {
            public NodeBase create (MPart part)
            {
                return new NodeAnnotation (part);
            }
        };
        NodeFactory factoryBlock = new NodeFactory ()
        {
            public NodeBase create (MPart part)
            {
                return new NodeAnnotations (part);
            }
        };
        createdNode = create (path, index, name, value, "$metadata", factory, factoryBlock);
    }

    public static NodeBase create (List<String> path, int index, String name, String value, String blockName, NodeFactory factory, NodeFactory factoryBlock)
    {
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        MPart block = (MPart) parent.source.childOrCreate (blockName);

        PanelEquationTree pet = PanelModel.instance.panelEquations;
        JTree tree = pet.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        NodeBase container = parent;  // If this is a variable, then mix metadata with equations and references
        if (parent instanceof NodePart)  // If this is a part, then display special block
        {
            if (block.length () == 0)  // empty implies the node is absent
            {
                container = factoryBlock.create (block);
                model.insertNodeIntoUnfiltered (container, parent, index);
                index = 0;
            }
            else  // the node is present, so retrieve it
            {
                container = parent.child (blockName);
            }
        }

        NodeBase createdNode = container.child (name);
        boolean alreadyExists = createdNode != null;
        MPart createdPart = (MPart) block.set (name, value);
        if (! alreadyExists) createdNode = factory.create (createdPart);

        FontMetrics fm = createdNode.getFontMetrics (tree);
        if (container.getChildCount () > 0)
        {
            NodeBase firstChild = (NodeBase) container.getChildAt (0);
            if (firstChild.needsInitTabs ()) firstChild.initTabs (fm);
        }

        if (value == null) createdNode.setUserObject ("");  // pure create, so about to go into edit mode. This should only happen on first application of the create action, and should only be possible if visibility is already correct.
        createdNode.updateColumnWidths (fm);  // preempt initialization; uses actual name, not user value
        if (! alreadyExists) model.insertNodeIntoUnfiltered (createdNode, container, index);
        if (value != null)  // create was merged with change name/value
        {
            container.updateTabStops (fm);
            container.allNodesChanged (model);
            TreeNode[] createdPath = createdNode.getPath ();
            pet.updateOrder (createdPath);
            pet.updateVisibility (createdPath);
            if (path.size () == 1  &&  name.equals ("lock")) pet.updateLock ();
        }

        return createdNode;
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (value == null  &&  edit instanceof ChangeAnnotation)
        {
            ChangeAnnotation change = (ChangeAnnotation) edit;
            if (name.equals (change.nameBefore))
            {
                int pathSize   =        path.size ();
                int changeSize = change.path.size ();
                int difference = changeSize - pathSize;
                if (difference == 0  ||  difference == 1)
                {
                    for (int i = 0; i < pathSize; i++) if (! path.get (i).equals (change.path.get (i))) return false;

                    name  = change.nameAfter;
                    value = change.valueAfter;
                    return true;
                }
            }
        }
        return false;
    }
}

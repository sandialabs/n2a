/*
Copyright 2016-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.lang.ref.WeakReference;
import java.util.List;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeReference;
import gov.sandia.n2a.ui.eq.tree.NodeReferences;
import gov.sandia.n2a.ui.ref.PanelReference;

public class AddReference extends UndoableView implements AddEditable
{
    protected List<String>            path;        // to parent of $ref node
    protected int                     index;       // where to insert among siblings
    protected String                  name;
    protected String                  value;
    protected WeakReference<NodeBase> createdNode; // Used by caller to initiate editing. Only valid immediately after call to redo().
    protected boolean                 multi;
    protected boolean                 multiLast;

    /**
        @param parent Must be the node that contains $ref, not the $ref node itself.
        @param index Position in the unfiltered tree where the node should be inserted.
    **/
    public AddReference (NodeBase parent, int index, MNode data)
    {
        path = parent.getKeyPath ();
        this.index = index;

        MPart block = (MPart) parent.source.child ("$ref");
        if (data == null)
        {
            // First attempt to use the currently selected record on the Reference tab.
            name = "";
            MNode ref = PanelReference.instance.panelEntry.model.record;
            if (ref != null)
            {
                name = ref.key ();
                if (block != null  &&  block.child (name) != null) name = "";  // Already used this reference.
                else value = "";  // Try this like a paste rather than pure create.
            }
            if (name.isEmpty ()) name = uniqueName (block, "r", false);
        }
        else
        {
            name = uniqueName (block, data.key (), true);
            value = data.get ();
        }
    }

    public static String uniqueName (MNode block, String prefix, boolean allowEmptySuffix)
    {
        if (allowEmptySuffix  &&  (block == null  ||  block.child (prefix) == null)) return prefix;
        int suffix = 1;
        if (block != null)
        {
            while (block.child (prefix + suffix) != null) suffix++;
        }
        return prefix + suffix;
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
        destroy (path, false, name, ! multi  ||  multiLast);
    }

    public static void destroy (List<String> path, boolean canceled, String name, boolean setSelection)
    {
        // Retrieve created node
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotUndoException ();
        NodeBase container = parent;
        if (parent instanceof NodePart) container = parent.child ("$ref");
        NodeBase createdNode = container.child (name);

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();

        boolean containerIsVisible = true;
        TreeNode[] createdPath = createdNode.getPath ();
        int index = container.getIndexFiltered (createdNode);
        if (canceled) index--;

        MPart block = (MPart) parent.source.child ("$ref");
        block.clear (name);
        if (block.child (name) == null)  // There is no overridden value, so this node goes away completely.
        {
            model.removeNodeFromParent (createdNode);
            if (block.size () == 0)
            {
                parent.source.clear ("$ref");  // commit suicide
                if (parent instanceof NodePart)
                {
                    model.removeNodeFromParent (container);
                    pet.updateOrder (createdPath);
                    // No need to update tab stops in grandparent, because block nodes don't offer any tab stops.
                    containerIsVisible = false;
                }
            }
        }
        else  // Just exposed an overridden value, so update display.
        {
            if (container.visible ())  // We are always visible, but our parent could disappear.
            {
                createdNode.setUserObject ();
            }
            else
            {
                containerIsVisible = false;
            }
        }

        if (containerIsVisible) container.invalidateColumns (null);
        pet.updateVisibility (createdPath, index, setSelection);
        if (containerIsVisible) container.allNodesChanged (model);
        pet.animate ();
    }

    public void redo ()
    {
        super.redo ();
        NodeBase temp = create (path, index, name, value, multi);
        createdNode = new WeakReference<NodeBase> (temp);
    }

    public NodeBase getCreatedNode ()
    {
        if (createdNode == null) return null;
        return createdNode.get ();
    }

    public static NodeBase create (List<String> path, int index, String name, String value, boolean multi)
    {
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        MPart block = (MPart) parent.source.childOrCreate ("$ref");

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();
        NodeBase container = parent;  // If this is a variable, then mix metadata with equations and references
        if (parent instanceof NodePart)  // If this is a part, then display special block
        {
            if (block.size () == 0)  // empty implies the node is absent
            {
                container = new NodeReferences (block);
                model.insertNodeIntoUnfiltered (container, parent, index);
                index = 0;
            }
            else  // the node is present, so retrieve it
            {
                container = parent.child ("$ref");
            }
        }

        NodeBase createdNode = container.child (name);
        boolean alreadyExists = createdNode != null;
        MPart createdPart = (MPart) block.set (value, name);
        if (! alreadyExists) createdNode = new NodeReference (createdPart);

        if (value == null) createdNode.setUserObject ("");  // pure create, so about to go into edit mode. This should only happen on first application of the create action, and should only be possible if visibility is already correct.
        if (! alreadyExists) model.insertNodeIntoUnfiltered (createdNode, container, index);
        if (value != null)  // create was merged with change name/value
        {
            container.invalidateColumns (null);
            TreeNode[] createdPath = createdNode.getPath ();
            pet.updateOrder (createdPath);
            pet.updateVisibility (createdPath, -2, ! multi);
            if (multi) pet.tree.addSelectionPath (new TreePath (createdPath));
            container.allNodesChanged (model);
            pet.animate ();
        }

        return createdNode;
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (value == null  &&  edit instanceof ChangeReference)
        {
            ChangeReference change = (ChangeReference) edit;
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

/*
Copyright 2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.StoredPath;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeContainer;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

/**
    Applies multiple changes to $metadata.gui.
    Coordinates graphic editing with tree editing.
    While this class may seem redundant with ChangeAnnotaton, it serves a slightly different purpose.
    That class supports direct user interaction with the metadata tree, including merging and splitting
    of deep node paths. This class deals with behind-the-scenes modification of GUI data.
**/
public class ChangeGUI extends UndoableView
{
    protected List<String> path;          // to node that contains the gui metadata
    protected int          index;         // If we create metadata node, this is where it goes.
    protected MNode        undoAdd;       // Nodes to apply during undo, starting at $metadata.gui. If "gui" key is absent, then this is null.
    protected MNode        undoRemove;    // Nodes that should be removed during undo, via MNode.uniqueNodes(). Like undoAdd, this is null if "gui" key is absent.
    protected MNode        doAdd;         // The nodes being changed. There is no corresponding doRemove.
    protected boolean      neutralized;   // Indicates that this edit exactly reverses the previous one, so completely remove both.

    public ChangeGUI (NodeBase parent, MNode guiTree)
    {
        super (parent);

        path = parent.getKeyPath ();
        doAdd = guiTree;

        MNode currentTree = parent.source.child ("$metadata", "gui");
        if (currentTree == null)
        {
            NodeBase metadataNode = parent.child ("$metadata");
            if (metadataNode == null  &&  parent.getChildCount () > 0)  // We will create a new $metadata node, so determine whether it should appear first or second in tree.
            {
                // Test whether the first child is $inherit, and whether it will remain so after the move. In that case, don't put $metadata in front of it.
                if (((NodeBase) parent.getChildAt (0)).source.key ().equals ("$inherit")) index = 1;  // otherwise it is 0
            }
        }
        else
        {
            undoAdd    = new MVolatile ();
            undoRemove = new MVolatile ();
            undoAdd   .merge (guiTree);
            undoRemove.merge (guiTree);
            undoAdd   .changes     (currentTree);
            undoRemove.uniqueNodes (currentTree);
        }
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

        boolean needBuild = true;
        NodeContainer metadataNode;          // The immediate container of metadata items in the tree.
        MNode         metadataSource = null; // The $metadata node under which all changes are made.
        if (parent instanceof NodeVariable)
        {
            metadataNode   = (NodeContainer) parent;
            metadataSource = metadataNode.source.child ("$metadata");
        }
        else  // NodePart is the default case
        {
            metadataNode = (NodeContainer) parent.child ("$metadata");
            if (metadataNode != null) metadataSource = metadataNode.source;
        }
        if (add == null)  // Remove the gui node if it exists. Possibly remove $metadata itself.
        {
            // We can safely assume that mparent is non-null, since we only get here during an undo.
            metadataSource.clear ("gui");
            if (metadataSource.size () == 0)  // No siblings of "gui", so get rid of $metadata.
            {
                metadataSource.parent ().clear ("$metadata");
                if (parent instanceof NodePart)
                {
                    if (model == null) FilteredTreeModel.removeNodeFromParentStatic (metadataNode);
                    else               model.removeNodeFromParent (metadataNode);
                    needBuild = false;
                }
            }
        }
        else  // Update gui node, or add if it doesn't exist. Possibly create $metadata
        {
            if (metadataSource == null) metadataSource = parent.source.childOrCreate ("$metadata");
            if (metadataNode == null)  // only happens when parent is NodePart
            {
                metadataNode = new NodeAnnotations ((MPart) metadataSource);
                if (model == null) FilteredTreeModel.insertNodeIntoUnfilteredStatic (metadataNode, parent, index);
                else               model.insertNodeIntoUnfiltered (metadataNode, parent, index);
            }
            MNode gui = metadataSource.childOrCreate ("gui");
            if (remove != null) gui.uniqueNodes (remove);
            gui.merge (add);
        }

        NodeBase updateNode = metadataNode;
        if (needBuild)
        {
            List<String> expanded = null;
            if (model != null) expanded = AddAnnotation.saveExpandedNodes (pet.tree, metadataNode);
            metadataNode.build ();
            if (metadataNode instanceof NodeVariable) ((NodeVariable) metadataNode).findConnections ();
            metadataNode.filter (FilteredTreeModel.filterLevel);
            if (model != null  &&  metadataNode.visible (FilteredTreeModel.filterLevel))
            {
                model.nodeStructureChanged (metadataNode);
                AddAnnotation.restoreExpandedNodes (pet.tree, metadataNode, expanded);
            }

            updateNode = metadataNode.child ("gui");
            if (updateNode == null) updateNode = metadataNode;
        }
        PanelEquationTree.updateVisibility (pet, updateNode.getPath (), -1, false);
        if (sp != null) sp.restore (pet.tree, true);  // This forces focus back to original location.

        // Update graph
        NodePart part;
        if (parent instanceof NodePart) part = (NodePart) parent;
        else                            part = (NodePart) parent.getParent ();  // Presumably this is a NodeVariable, so our immediate parent is a NodePart.
        if (part.graph != null) part.graph.updateGUI ();
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (edit instanceof ChangeGUI)
        {
            ChangeGUI cg = (ChangeGUI) edit;
            if (path.equals (cg.path)  &&  doAdd.structureEquals (cg.doAdd))
            {
                doAdd = cg.doAdd;  // Replace our values with the new values.
                neutralized = undoAdd.equals (doAdd);
                return true;
            }
        }
        return false;
    }

    public boolean anihilate ()
    {
        return neutralized;
    }
}

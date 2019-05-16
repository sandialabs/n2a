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
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.StoredPath;
import gov.sandia.n2a.ui.eq.PanelEquations.StoredView;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

/**
    Applies multiple changes to $metadata.gui.
    Coordinates graphic editing with tree editing.
    While this class may seem redundant with ChangeAnnotaton, it serves a slightly different purpose.
    That class supports direct user interaction with the metadata tree, including merging and splitting
    of deep node paths. This class deals with behind-the-scenes modification of GUI data.
**/
public class ChangeGUI extends Undoable
{
    protected StoredView   view = PanelModel.instance.panelEquations.new StoredView ();
    protected List<String> path;          // to part that contains the gui metadata
    protected int          index;         // If we create metadata node, this is where it goes.
    protected MNode        undoAdd;       // Nodes to apply during undo, starting at $metadata.gui. If "gui" key is absent, then this is null.
    protected MNode        undoRemove;    // Nodes that should be removed during undo, via MNode.uniqueNodes(). Like undoAdd, this is null if "gui" key is absent.
    protected MNode        doAdd;         // The nodes being changed. There is no corresponding doKill.
    protected boolean      neutralized;   // Indicates that this edit exactly reverses the previous one, so completely remove both.

    public ChangeGUI (NodePart parent, MNode guiTree)
    {
        path = parent.getKeyPath ();
        doAdd = guiTree;

        MNode currentTree = parent.source.child ("$metadata", "gui");
        if (currentTree == null)
        {
            NodeBase metadataNode = parent.child ("$metadata");
            if (metadataNode == null)  // We will create a new $metadata node (to hold the gui.order node). This changes meaning of the given indices.
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
        apply (undoAdd, undoRemove, index);
    }

    public void redo ()
    {
        super.redo ();
        apply (doAdd, null, index);
    }

    public void apply (MNode add, MNode remove, int indexMetadata)
    {
        view.restore ();
        NodePart parent = (NodePart) NodeBase.locateNode (path);
        if (parent == null) throw new CannotUndoException ();

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();
        StoredPath sp = new StoredPath (pet.tree);

        boolean needBuild = true;
        NodeAnnotations metadataNode = (NodeAnnotations) parent.child ("$metadata");
        if (add == null)  // Remove the gui node if it exists. Possibly remove $metadata itself.
        {
            // We can safely assume that metadataNode is non-null, since we only get here during an undo.
            MNode mparent = metadataNode.source;
            mparent.clear ("gui");
            if (mparent.size () == 0)  // No siblings of "gui", so get rid of $metadata.
            {
                parent.source.clear ("$metadata");
                model.removeNodeFromParent (metadataNode);
                needBuild = false;
            }
        }
        else  // Update gui node, or add if it doesn't exist. Possibly create $metadata
        {
            if (metadataNode == null)
            {
                metadataNode = new NodeAnnotations ((MPart) parent.source.childOrCreate ("$metadata"));
                model.insertNodeIntoUnfiltered (metadataNode, parent, indexMetadata);
            }
            MNode gui = metadataNode.source.childOrCreate ("gui");
            if (remove != null) gui.uniqueNodes (remove);
            gui.mergeSoft (add);
        }

        NodeBase updateNode = metadataNode;
        if (needBuild)
        {
            List<String> expanded = AddAnnotation.saveExpandedNodes (pet.tree, metadataNode);
            metadataNode.build ();
            metadataNode.filter (FilteredTreeModel.filterLevel);
            if (metadataNode.visible (FilteredTreeModel.filterLevel))
            {
                model.nodeStructureChanged (metadataNode);
                AddAnnotation.restoreExpandedNodes (pet.tree, metadataNode, expanded);
            }

            updateNode = metadataNode.child ("gui");
            if (updateNode == null) updateNode = metadataNode;
        }
        PanelEquationTree.updateVisibility (pet, updateNode.getPath (), -1, false);
        sp.restore (pet.tree);  // This forces focus back to original location.

        // Update graph
        if (parent.graph != null) parent.graph.updateGUI ();
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

/*
Copyright 2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.JTree;
import javax.swing.undo.CannotUndoException;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.StoredPath;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

/**
    Applies multiple changes to $metadata.gui.
    Coordinates graphic editing with tree editing.
**/
public class ChangeGUI extends Undoable
{
    protected List<String> path;          // to part that contains the gui metadata
    protected int          indexMetadata; // If we create metadata node, this is where it goes.
    protected MNode        treeBefore;    // Saved version of original tree, starting at $metadata.gui. If "gui" key is absent, then this is null.
    protected MNode        treeAfter;     // New tree, starting at $metadata.gui. It is merged over the existing tree, if any.

    public ChangeGUI (NodePart parent, MNode guiTree)
    {
        path = parent.getKeyPath ();

        treeAfter = new MVolatile ();
        MNode currentTree = parent.source.child ("$metadata", "gui");
        if (currentTree == null)
        {
            NodeBase metadataNode = parent.child ("$metadata");
            if (metadataNode == null)  // We will create a new $metadata node (to hold the gui.order node). This changes meaning of the given indices.
            {
                // Test whether the first child is $inherit, and whether it will remain so after the move. In that case, don't put $metadata in front of it.
                if (((NodeBase) parent.getChildAt (0)).source.key ().equals ("$inherit")) indexMetadata = 1;  // otherwise it is 0
            }
        }
        else
        {
            treeBefore = new MVolatile ();
            treeBefore.merge (currentTree);
            treeAfter .merge (currentTree);
        }
        treeAfter.merge (guiTree);
    }

    public void undo ()
    {
        super.undo ();
        apply (path, treeBefore, indexMetadata);
    }

    public void redo ()
    {
        super.redo ();
        apply (path, treeAfter, indexMetadata);
    }

    public static void apply (List<String> path, MNode treeAfter, int indexMetadata)
    {
        NodePart parent = (NodePart) NodeBase.locateNode (path);
        if (parent == null) throw new CannotUndoException ();

        PanelModel mep = PanelModel.instance;
        JTree tree = mep.panelEquationTree.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        StoredPath sp = new StoredPath (tree);

        boolean needBuild = true;
        NodeAnnotations metadataNode = (NodeAnnotations) parent.child ("$metadata");
        if (treeAfter == null)  // Remove the gui node if it exists. Possibly remove $metadata itself.
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
            metadataNode.source.set (treeAfter, "gui");
        }

        NodeBase updateNode = metadataNode;
        if (needBuild)
        {
            List<String> expanded = AddAnnotation.saveExpandedNodes (tree, metadataNode);
            metadataNode.build ();
            metadataNode.filter (model.filterLevel);
            if (metadataNode.visible (model.filterLevel))
            {
                model.nodeStructureChanged (metadataNode);
                AddAnnotation.restoreExpandedNodes (tree, metadataNode, expanded);
            }

            updateNode = metadataNode.child ("gui");
            if (updateNode == null) updateNode = metadataNode;
        }
        mep.panelEquationTree.updateVisibility (updateNode.getPath (), -1, false);
        sp.restore (tree);  // This forces focus back to original location.

        // Update graph
        if (parent.graph != null) parent.graph.updateGUI ();
    }
}

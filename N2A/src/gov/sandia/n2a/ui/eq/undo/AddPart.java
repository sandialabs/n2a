/*
Copyright 2017-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.awt.Point;
import java.util.List;

import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationGraph;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.PanelEquations.StoredView;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class AddPart extends Undoable
{
    protected StoredView   view = PanelModel.instance.panelEquations.new StoredView ();
    protected List<String> path;  // to containing part
    protected int          index; // where to insert among siblings
    protected String       name;
    protected MNode        createSubtree;
    protected boolean      nameIsGenerated;
    public    NodeBase     createdNode;  ///< Used by caller to initiate editing. Only valid immediately after call to redo().

    /**
        @param index Position in the unfiltered tree where the node should be inserted.
    **/
    public AddPart (NodeBase parent, int index, MNode data, Point location)
    {
        path = parent.getKeyPath ();
        this.index = index;

        createSubtree = new MVolatile ();
        if (data == null)
        {
            name = uniqueName (parent, "p", 0, false);
            nameIsGenerated = true;
        }
        else
        {
            createSubtree.merge (data);
            name = data.key ();
            if (name.isEmpty ()) name = uniqueName (parent, "p", 0, false);  // Even though this is actually generated, we don't plan to go directly into edit mode, so treat as if not generated.
            else                 name = uniqueName (parent, name, 2, true);
            nameIsGenerated = false;  // Because we don't go into edit mode on a drop or paste. If that changes, then always set nameIsGenerated to true.
        }

        if (location != null)
        {
            MNode bounds = createSubtree.childOrCreate ("$metadata", "gui", "bounds");
            bounds.set (location.x, "x");
            bounds.set (location.y, "y");
        }
    }

    public static String uniqueName (NodeBase parent, String prefix, int suffix, boolean allowEmptySuffix)
    {
        if (allowEmptySuffix  &&  parent.source.child (prefix) == null) return prefix;
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
        view.restore ();
        destroy (path, false, name);
    }

    public static void destroy (List<String> path, boolean canceled, String name)
    {
        // Retrieve created node
        NodePart parent = (NodePart) NodeBase.locateNode (path);
        if (parent == null) throw new CannotUndoException ();
        NodePart createdNode = (NodePart) parent.child (name);

        PanelEquations pe = PanelModel.instance.panelEquations;
        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();
        PanelEquationGraph peg = pe.panelEquationGraph;
        boolean graphParent = parent == pe.part;

        TreeNode[] createdPath = createdNode.getPath ();
        int index = parent.getIndexFiltered (createdNode);
        if (canceled) index--;

        MPart mparent = parent.source;
        mparent.clear (name);
        if (mparent.child (name) == null)  // Node is fully deleted
        {
            model.removeNodeFromParent (createdNode);
            if (graphParent) peg.removePart (createdNode);
        }
        else  // Just exposed an overridden node
        {
            createdNode.build ();
            parent.findConnections ();
            createdNode.filter (FilteredTreeModel.filterLevel);
        }

        pet.updateOrder (createdPath);
        pet.updateVisibility (createdPath, index);  // includes nodeStructureChanged(), if necessary
        if (graphParent)
        {
            peg.reconnect ();
            peg.paintImmediately ();
        }
    }

    public void redo ()
    {
        super.redo ();
        view.restore ();
        createdNode = create (path, index, name, createSubtree, nameIsGenerated);
    }

    public static NodeBase create (List<String> path, int index, String name, MNode newPart, boolean nameIsGenerated)
    {
        NodePart parent = (NodePart) NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodeBase n = parent.child (name);
        if (n != null  &&  ! (n instanceof NodePart)) throw new CannotUndoException ();  // Should be blocked by GUI constraints, but this defends against ill-formed model on clipboard.
        NodePart createdNode = (NodePart) n;

        // Update database
        MPart createdPart = (MPart) parent.source.set ("", name);
        createdPart.merge (newPart);

        // Update GUI

        PanelEquations pe = PanelModel.instance.panelEquations;
        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();
        PanelEquationGraph peg = pe.panelEquationGraph;
        boolean graphParent = parent == pe.part;

        if (createdNode == null)
        {
            createdNode = new NodePart (createdPart);
            model.insertNodeIntoUnfiltered (createdNode, parent, index);
            if (graphParent) peg.addPart (createdNode);
        }
        createdNode.build ();
        parent.findConnections ();  // Other nodes besides immediate siblings can also refer to us, so to be strictly correct, should run findConnectins() on root of tree.
        createdNode.filter (FilteredTreeModel.filterLevel);

        TreeNode[] createdPath = createdNode.getPath ();
        if (nameIsGenerated) createdNode.setUserObject ("");  // pure create, so about to go into edit mode. This should only happen on first application of the create action, and should only be possible if visibility is already correct.
        else pet.updateOrder (createdPath);
        pet.updateVisibility (createdPath);

        if (graphParent)
        {
            peg.reconnect ();
            peg.paintImmediately ();
        }

        return createdNode;
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (nameIsGenerated  &&  edit instanceof ChangePart)
        {
            ChangePart change = (ChangePart) edit;
            if (path.equals (change.path)  &&  name.equals (change.nameBefore))
            {
                name = change.nameAfter;
                nameIsGenerated = false;
                return true;
            }
        }
        return false;
    }
}

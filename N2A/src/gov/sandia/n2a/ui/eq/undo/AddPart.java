/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationGraph;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class AddPart extends UndoableView
{
    protected List<String> path;           // to containing part
    protected int          index;          // Position in the unfiltered tree where the node should be inserted. -1 means add to end.
    protected String       name;
    protected MNode        createSubtree;
    protected boolean      nameIsGenerated;
    public    NodeBase     createdNode;    // Used by caller to initiate editing. Only valid immediately after call to redo().
    protected boolean      multi;          // Indicates that this is one of several parts being added at the same time, so set selected.
    protected boolean      multiLead;      // Indicates this is the lead (focused) item in the selection.

    public AddPart (NodeBase parent, int index, MNode data, Point location)
    {
        this (parent, index, data, location, false, false);
    }

    public AddPart (NodeBase parent, int index, MNode data, Point location, boolean multi, boolean multiLead)
    {
        path           = parent.getKeyPath ();
        this.index     = index;
        this.multi     = multi;
        this.multiLead = multiLead;

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

        PanelEquations pe = PanelModel.instance.panelEquations;
        if (location == null) location = pe.panelEquationGraph.getCenter ();
        MNode bounds = createSubtree.childOrCreate ("$metadata", "gui", "bounds");
        bounds.set (location.x, "x");
        bounds.set (location.y, "y");
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
        destroy (path, false, name);
    }

    public static void destroy (List<String> path, boolean canceled, String name)
    {
        // Retrieve created node
        NodePart parent = (NodePart) NodeBase.locateNode (path);
        if (parent == null) throw new CannotUndoException ();
        NodePart createdNode = (NodePart) parent.child (name);

        PanelEquations pe = PanelModel.instance.panelEquations;
        boolean graphParent =  parent == pe.part;
        PanelEquationTree pet = graphParent ? null : parent.getTree ();  // Only use tree if it is not the graph parent, since graph parent hides its sub-parts.
        FilteredTreeModel model = null;
        if (pet != null) model = (FilteredTreeModel) pet.tree.getModel ();
        PanelEquationGraph peg = pe.panelEquationGraph;  // only used if graphParent is true

        TreeNode[] createdPath = createdNode.getPath ();
        int index = parent.getIndexFiltered (createdNode);  // returns -1 if createdNode is filtered out of parent
        if (canceled) index--;

        MPart mparent = parent.source;
        mparent.clear (name);
        if (mparent.child (name) == null)  // Node is fully deleted
        {
            if (model == null) FilteredTreeModel.removeNodeFromParentStatic (createdNode);
            else               model.removeNodeFromParent (createdNode);
            if (graphParent) peg.removePart (createdNode);
        }
        else  // Just exposed an overridden node
        {
            createdNode.build ();  // Does not change the fake-root status of this node.
            parent.findConnections ();
            createdNode.filter (FilteredTreeModel.filterLevel);
            if (graphParent)  // Need to update entire model under fake root.
            {
                PanelEquationTree subpet = createdNode.getTree ();
                if (subpet != null)
                {
                    FilteredTreeModel submodel = (FilteredTreeModel) subpet.tree.getModel ();
                    submodel.nodeStructureChanged (createdNode);
                    subpet.animate ();
                }
                // Implicitly, the title of the node was focused when the part was deleted, so ensure it gets the focus back.
                createdNode.graph.takeFocusOnTitle ();
            }
        }

        pe.resetBreadcrumbs ();
        if (pet == null)
        {
            PanelEquationTree.updateOrder (null, createdPath);
            PanelEquationTree.updateVisibility (null, createdPath, index, false);
        }
        else
        {
            pet.updateOrder (createdPath);
            pet.updateVisibility (createdPath, index);  // includes nodeStructureChanged(), if necessary
            pet.animate ();
        }
        if (graphParent)
        {
            peg.reconnect ();
            peg.repaint ();
            if (pe.view == PanelEquations.NODE  &&  peg.isEmpty ()) pe.panelParent.setOpen (true);
        }
    }

    public void redo ()
    {
        super.redo ();
        createdNode = create (path, index, name, createSubtree, nameIsGenerated, multi, multiLead);
    }

    public static NodeBase create (List<String> path, int index, String name, MNode newPart, boolean nameIsGenerated, boolean multi, boolean multiLead)
    {
        NodePart parent = (NodePart) NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodeBase n = parent.child (name);
        if (n != null  &&  ! (n instanceof NodePart)) throw new CannotUndoException ();  // Should be blocked by GUI constraints, but this defends against ill-formed model on clipboard.
        NodePart createdNode = (NodePart) n;

        // Update database
        MPart createdPart = (MPart) parent.source.childOrCreate (name);
        createdPart.merge (newPart);

        // Update GUI

        PanelEquations pe = PanelModel.instance.panelEquations;
        boolean graphParent =  parent == pe.part;
        PanelEquationTree pet = graphParent ? null : parent.getTree ();
        FilteredTreeModel model = null;
        if (pet != null) model = (FilteredTreeModel) pet.tree.getModel ();
        PanelEquationGraph peg = pe.panelEquationGraph;

        boolean addGraphNode = false;
        if (createdNode == null)
        {
            addGraphNode = true;
            createdNode = new NodePart (createdPart);
            createdNode.hide = graphParent;
            if (index < 0) index = parent.getChildCount ();
            if (model == null) FilteredTreeModel.insertNodeIntoUnfilteredStatic (createdNode, parent, index);
            else               model.insertNodeIntoUnfiltered (createdNode, parent, index);
        }
        createdNode.build ();
        parent.findConnections ();  // Other nodes besides immediate siblings can also refer to us, so to be strictly correct, should run findConnectins() on root of tree.
        createdNode.filter (FilteredTreeModel.filterLevel);

        if (nameIsGenerated) createdNode.setUserObject ("");  // pure create, so about to go into edit mode. This should only happen on first application of the create action, and should only be possible if visibility is already correct.
        TreeNode[] createdPath = createdNode.getPath ();
        if (pet == null)
        {
            if (! nameIsGenerated) PanelEquationTree.updateOrder (null, createdPath);
            PanelEquationTree.updateVisibility (null, createdPath, -2, false);
        }
        else
        {
            if (! nameIsGenerated) pet.updateOrder (createdPath);
            pet.updateVisibility (createdPath);
            pet.animate ();
        }

        if (graphParent)
        {
            if (addGraphNode)
            {
                peg.addPart (createdNode);
            }
            else  // Existing graph node; content needs to be restructured.
            {
                PanelEquationTree subpet = createdNode.getTree ();
                if (subpet != null)
                {
                    FilteredTreeModel submodel = (FilteredTreeModel) subpet.tree.getModel ();
                    submodel.nodeStructureChanged (createdNode);
                    subpet.animate ();
                }
            }
            createdNode.hide = false;
            if (multi) createdNode.graph.setSelected (true);
            else       peg.clearSelection ();
            if (! multi  ||  multiLead)
            {
                createdNode.graph.takeFocusOnTitle ();
                peg.reconnect ();
                peg.repaint ();
            }
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

/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.ArrayList;
import java.util.List;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class AddVariable extends UndoableView implements AddEditable
{
    protected List<String> path;         // to part that contains the variable node
    protected int          index;        // where to insert among siblings
    protected String       name;
    protected MNode        createSubtree;
    protected boolean      nameIsGenerated;
    protected NodeBase     createdNode;  // Used by caller to initiate editing. Only valid immediately after call to redo().
    protected boolean      multi;
    protected boolean      multiLast;

    public AddVariable (NodePart parent, int index, MNode data)
    {
        path = parent.getKeyPath ();
        this.index = index;

        createSubtree = new MVolatile ();
        if (data == null)
        {
            name = AddPart.uniqueName (parent, "x", 0, false);
            nameIsGenerated = true;
        }
        else
        {
            createSubtree.merge (data);
            name = data.key ();
            if (name.isEmpty ()) name = AddPart.uniqueName (parent, "x", 0, false);  // Even though this is actually generated, we don't plan to go directly into edit mode, so treat as if not generated.
            else                 name = AddPart.uniqueName (parent, name, 2, true);
            nameIsGenerated = false;  // Because we don't go into edit mode on a drop or paste. If that changes, then always set nameIsGenerated to true.
        }

        if (FilteredTreeModel.filterLevel == FilteredTreeModel.PARAM)
        {
            // Force variable to be a parameter, so it will be visible when going into edit mode.
            if (! createSubtree.getFlag ("$metadata", "param")) createSubtree.set ("", "$metadata", "param");
        }
    }

    public List<String> fullPath ()
    {
        List<String> result = new ArrayList<String> ();
        result.addAll (path);
        result.add (name);
        return result;
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
        NodePart parent = (NodePart) NodeBase.locateNode (path);
        if (parent == null) throw new CannotUndoException ();
        NodeVariable createdNode = (NodeVariable) parent.child (name);

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();

        TreeNode[] createdPath = createdNode.getPath ();
        int index = parent.getIndexFiltered (createdNode);
        if (canceled) index--;

        MPart mparent = parent.source;
        mparent.clear (name);
        if (mparent.child (name) == null)  // Node is fully deleted
        {
            if (createdNode.isBinding)
            {
                if (parent.graph != null) parent.graph.killEdge (name);
                if (mparent.root () == mparent) PanelModel.instance.panelSearch.updateConnectors (mparent);
            }
            model.removeNodeFromParent (createdNode);
            parent.findConnections ();
        }
        else  // Just exposed an overridden node
        {
            boolean wasBinding = createdNode.isBinding;
            createdNode.build ();
            createdNode.findConnections ();
            createdNode.filter (FilteredTreeModel.filterLevel);

            if (createdNode.isBinding  ||  wasBinding)
            {
                parent.updateConnections ();
                if (parent.graph != null)
                {
                    if (createdNode.isBinding) parent.graph.updateEdge (name, parent.connectionBindings.get (name));
                    else if (wasBinding)       parent.graph.killEdge   (name);
                }
                if (mparent.root () == mparent) PanelModel.instance.panelSearch.updateConnectors (mparent);
            }
        }
        parent.invalidateColumns (model);

        pet.updateOrder (createdPath);
        pet.updateVisibility (createdPath, index, setSelection);  // includes nodeStructureChanged(), if necessary
        pet.animate ();
    }

    public void redo ()
    {
        super.redo ();
        createdNode = create (path, index, name, createSubtree, nameIsGenerated, multi);
    }

    public NodeBase getCreatedNode ()
    {
        return createdNode;
    }

    public static NodeBase create (List<String> path, int index, String name, MNode newPart, boolean nameIsGenerated, boolean multi)
    {
        NodePart parent = (NodePart) NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodeBase n = parent.child (name);
        if (n != null  &&  ! (n instanceof NodeVariable)) throw new CannotRedoException ();  // Should be blocked by GUI constraints, but this defends against ill-formed model on clipboard.
        NodeVariable createdNode = (NodeVariable) n;

        // Update database
        MPart createdPart = (MPart) parent.source.childOrCreate (name);
        createdPart.merge (newPart);

        // Update GUI

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();

        boolean alreadyExists = createdNode != null;
        boolean wasBinding =  alreadyExists  &&  createdNode.isBinding;
        if (! alreadyExists) createdNode = new NodeVariable (createdPart);
        if (nameIsGenerated) createdNode.setUserObject ("");  // pure create, so about to go into edit mode. This should only happen on first application of the create action, and should only be possible if visibility is already correct.
        if (! alreadyExists) model.insertNodeIntoUnfiltered (createdNode, parent, index);

        TreeNode[] createdPath = createdNode.getPath ();
        if (! nameIsGenerated)
        {
            createdNode.build ();
            createdNode.findConnections ();
            createdNode.filter (FilteredTreeModel.filterLevel);
            pet.updateOrder (createdPath);
            parent.invalidateColumns (model);

            if (createdNode.isBinding  ||  wasBinding)
            {
                parent.updateConnections ();
                if (parent.graph != null)
                {
                    if (createdNode.isBinding) parent.graph.updateEdge (name, parent.connectionBindings.get (name));
                    else if (wasBinding)       parent.graph.killEdge   (name);
                }
                MPart mparent = parent.source;
                if (mparent.root () == mparent) PanelModel.instance.panelSearch.updateConnectors (mparent);
            }
        }
        pet.updateVisibility (createdPath, -2, ! multi);
        if (multi) pet.tree.addSelectionPath (new TreePath (createdPath));
        pet.animate ();

        return createdNode;
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (nameIsGenerated  &&  edit instanceof ChangeVariable)
        {
            ChangeVariable change = (ChangeVariable) edit;
            if (path.equals (change.path)  &&  name.equals (change.nameBefore))
            {
                name = change.nameAfter;
                nameIsGenerated = false;
                createSubtree.merge (change.savedTree);  // Generally, there should be nothing in savedTree. Just being thorough.
                createSubtree.set (change.valueAfter);
                return true;
            }
        }
        return false;
    }
}

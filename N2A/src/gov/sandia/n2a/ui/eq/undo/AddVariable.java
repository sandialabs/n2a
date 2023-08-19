/*
Copyright 2017-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.lang.ref.WeakReference;
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
    protected List<String>            path;         // to part that contains the variable node
    protected int                     index;        // where to insert among siblings
    protected String                  name;
    protected MNode                   createSubtree;
    protected boolean                 nameIsGenerated;
    protected WeakReference<NodeBase> createdNode;  // Used by caller to initiate editing. Only valid immediately after call to redo().
    protected boolean                 multi;
    protected boolean                 multiLast;

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

        if (FilteredTreeModel.showParam  &&  ! FilteredTreeModel.showLocal)
        {
            // Force variable to be a parameter, so it will be visible when going into edit mode.
            if (! createSubtree.getFlag ("$meta", "param")) createSubtree.set ("", "$meta", "param");
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
        FilteredTreeModel model = null;
        if (pet != null) model = (FilteredTreeModel) pet.tree.getModel ();

        TreeNode[] createdPath = createdNode.getPath ();
        int index = parent.getIndexFiltered (createdNode);
        if (canceled) index--;

        // Update database
        MPart mparent = parent.source;
        deleteOrKill (mparent, name);

        // Update GUI
        if (mparent.child (name) == null)  // Node is fully deleted
        {
            if (createdNode.isBinding)
            {
                if (parent.graph != null) parent.graph.killEdge (name);
                if (mparent.root () == mparent) PanelModel.instance.panelSearch.updateConnectors (mparent);
            }
            if (model == null) FilteredTreeModel.removeNodeFromParentStatic (createdNode);
            else               model.removeNodeFromParent (createdNode);
            parent.findConnections ();
        }
        else  // Just exposed an overridden node
        {
            boolean wasBinding = createdNode.isBinding;
            createdNode.build ();
            createdNode.findConnections ();
            createdNode.filter ();

            if (createdNode.isBinding != wasBinding)
            {
                parent.updateSubpartConnections ();
                if (parent.graph != null)
                {
                    if (createdNode.isBinding) parent.graph.updateEdge (name, parent.connectionBindings.get (name));
                    else                       parent.graph.killEdge   (name);
                }
            }
            if (createdNode.isBinding != wasBinding  ||  createdNode.isBinding)
            {
                if (mparent.root () == mparent) PanelModel.instance.panelSearch.updateConnectors (mparent);
            }
        }
        parent.updatePins ();  // Actually, this could start at grandparent, because parent's pin structure hasn't changed. However, this is convenient and simple.

        if (pet != null)
        {
            parent.invalidateColumns (model);
            pet.updateOrder (createdPath);
            pet.updateVisibility (createdPath, index, setSelection);  // includes nodeStructureChanged(), if necessary
            pet.animate ();
        }
    }

    /**
        Toggles between local override, inherited, killed and unkilled.
        The state transitions are (roughly):
        <dl>
        <di>Local only (no parent value)</di>
            <dd> -> Nonexistent</dd>
        <di>Overridden (has a parent value and local value)</di>
            <dd> -> Inherited</dd>
        <di>Inherited (has a parent value only, no local value)</di>
            <dd> -> Overridden Killed</dd>
        <di>Overridden Killed (local override sets kill true)</di>
            <dd> -> Inherited</dd>
        <di>Inherited Killed (kill flag is set true in parent)</di>
            <dd> -> Overridden Unkilled</dd>
        <di>Overridden Unkilled (local override sets kill false)</di>
            <dd> -> Inherited Killed</dd>
        </dl>
    **/
    public static void deleteOrKill (MPart mparent, String name)
    {
        MPart mchild = (MPart) mparent.child (name);
        if (mchild.isFromTopDocument ())  // Local. Includes newly-created, locally-revoked, and locally un-revoked.
        {
            mparent.clear (name);
        }
        else  // inherited
        {
            if (mchild.getFlag ("$kill"))  // currently revoked, so restore
            {
                mchild.set ("0", "$kill");  // un-revoke
            }
            else  // currently active, so revoke
            {
                MNode mflag = mchild.child ("$kill");
                mchild.set (mflag == null ? "" : "1",  "$kill");  // revoke or re-revoke
            }
        }
    }

    /**
        Ensure that added node is not currently revoked.
        A new node could be revoked if it overwrites a node that was revoked,
        either locally or inherited.
    **/
    public static void unkill (MPart createdPart)
    {
        if (! createdPart.getFlag ("$kill")) return;
        MPart mflag = (MPart) createdPart.child ("$kill");
        if (mflag.isInherited ()) mflag.set ("0");
        else                      createdPart.clear ("$kill");
    }

    public void redo ()
    {
        super.redo ();
        NodeBase temp = create (path, index, name, createSubtree, nameIsGenerated, multi);
        createdNode = new WeakReference<NodeBase> (temp);
    }

    public NodeBase getCreatedNode ()
    {
        if (createdNode == null) return null;
        return createdNode.get ();
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
        unkill (createdPart);

        // Update GUI

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = null;
        if (pet != null) model = (FilteredTreeModel) pet.tree.getModel ();

        boolean alreadyExists = createdNode != null;
        boolean wasBinding =  alreadyExists  &&  createdNode.isBinding;
        if (! alreadyExists) createdNode = new NodeVariable (createdPart);
        if (nameIsGenerated) createdNode.setUserObject ("");  // pure create, so about to go into edit mode. This should only happen on first application of the create action, and should only be possible if visibility is already correct.
        if (! alreadyExists)
        {
            if (model == null) FilteredTreeModel.insertNodeIntoUnfilteredStatic (createdNode, parent, index);
            else               model.insertNodeIntoUnfiltered (createdNode, parent, index);
        }

        TreeNode[] createdPath = createdNode.getPath ();
        if (! nameIsGenerated)
        {
            createdNode.build ();
            createdNode.filter ();
            if (pet != null)
            {
                pet.updateOrder (createdPath);
                parent.invalidateColumns (model);
            }

            if (parent.updateVariableConnections ()) parent.updateSubpartConnections ();
            if (createdNode.isBinding != wasBinding)
            {
                if (parent.graph != null)
                {
                    if (createdNode.isBinding) parent.graph.updateEdge (name, parent.connectionBindings.get (name));
                    else                       parent.graph.killEdge   (name);
                }
            }
            if (createdNode.isBinding != wasBinding  ||  createdNode.isBinding)  // The second case allows for change in binding value.
            {
                MPart mparent = parent.source;
                if (mparent.root () == mparent) PanelModel.instance.panelSearch.updateConnectors (mparent);
            }
            parent.updatePins ();
        }
        if (pet != null)
        {
            pet.updateVisibility (createdPath, -2, ! multi);
            if (multi) pet.tree.addSelectionPath (new TreePath (createdPath));
            pet.animate ();
        }

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

    public String getPresentationName ()
    {
        // Hack to allow NodeVariable.applyEdit() to distinguish between new blank variable
        // and one that already has its name/value set.
        return "AddVariable" + (nameIsGenerated ? " noname" : "");
    }
}

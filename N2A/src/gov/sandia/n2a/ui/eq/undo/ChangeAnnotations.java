/*
Copyright 2019-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.StoredPath;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeContainer;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

/**
    Applies multiple changes to an existing $metadata subtree.
**/
public class ChangeAnnotations extends UndoableView
{
    protected List<String> path;          // To node that contains the metadata. $metadata itself may be explicit (for parts) or hidden (for variables).
    protected int          index;         // If we create $metadata, this is where it goes.
    protected MNode        undoAdd;       // Nodes to apply during undo, starting at $metadata. If $metadata key is absent, then this is null.
    protected MNode        undoRemove;    // Nodes that should be removed during undo, via MNode.uniqueNodes(). Like undoAdd, this is null if $metadata key is absent.
    protected MNode        doAdd;         // The nodes to be changed, rooted at $metadata. There is no corresponding doRemove.
    protected boolean      neutralized;   // Indicates that this edit exactly reverses the previous one, so completely remove both.
    protected boolean      multi;

    public ChangeAnnotations (NodeBase parent, MNode metadata)
    {
        super (parent);

        path  = parent.getKeyPath ();
        doAdd = metadata;

        MNode currentTree = parent.source.child ("$metadata");
        if (currentTree == null)
        {
            // We will create a new $metadata node, so determine whether it should appear first or second in tree.
            if (parent.getChildCount () > 0)
            {
                // Test whether the first child is $inherit. In that case, don't put $metadata in front of it.
                if (((NodeBase) parent.getChildAt (0)).source.key ().equals ("$inherit")) index = 1;  // otherwise it is 0
            }
        }
        else
        {
            undoAdd    = new MVolatile ();
            undoRemove = new MVolatile ();
            undoAdd   .merge (metadata);
            undoRemove.merge (metadata);
            undoAdd   .changes     (currentTree);
            undoRemove.uniqueNodes (currentTree);
        }
    }

    public void setMulti (boolean value)
    {
        multi = value;
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

        boolean needBuild = true;
        if (add == null)  // This is an undo, and $metadata did not exist before, so remove it.
        {
            // We can safely assume that metadataSource is non-null, since we only get here during an undo.
            metadataSource.parent ().clear ("$metadata");
            if (parent instanceof NodePart)
            {
                if (model == null) FilteredTreeModel.removeNodeFromParentStatic (metadataNode);
                else               model.removeNodeFromParent (metadataNode);
                needBuild = false;
            }
        }
        else  // Update $metadata node. Create if it doesn't exist.
        {
            if (metadataSource == null) metadataSource = parent.source.childOrCreate ("$metadata");
            if (metadataNode == null)  // only happens when parent is NodePart
            {
                metadataNode = new NodeAnnotations ((MPart) metadataSource);
                if (model == null) FilteredTreeModel.insertNodeIntoUnfilteredStatic (metadataNode, parent, index);
                else               model.insertNodeIntoUnfiltered (metadataNode, parent, index);
            }
            if (remove != null) metadataSource.uniqueNodes (remove);
            metadataSource.merge (add);
        }

        if (needBuild)
        {
            List<String> expanded = null;
            if (model != null) expanded = AddAnnotation.saveExpandedNodes (pet.tree, metadataNode);
            metadataNode.build ();
            metadataNode.filter ();
            if (model != null  &&  metadataNode.visible ())
            {
                model.nodeStructureChanged (metadataNode);
                AddAnnotation.restoreExpandedNodes (pet.tree, metadataNode, expanded);
            }
        }
        PanelEquationTree.updateVisibility (pet, metadataNode.getPath (), -1, false);
        if (! multi  &&  sp != null) sp.restore (pet.tree, true);  // This forces focus back to original location.

        // Update graph
        NodePart part;
        NodeVariable binding = null;
        if (parent instanceof NodePart)
        {
            part = (NodePart) parent;
        }
        else  // Presumably this is a NodeVariable, so our immediate parent is a NodePart.
        {
            binding = (NodeVariable) parent;  // If parent is not a NodeVariable, it is a bug in the code that made this ChangeAnnotations, and this line will throw a class cast exception.
            part = (NodePart) parent.getParent ();
        }
        if (part.graph != null)
        {
            if (binding != null)
            {
                String alias = binding.source.key ();
                part.graph.updateEdge (alias, part.connectionBindings.get (alias));
            }
            part.graph.updateGUI ();
            if (multi) part.graph.setSelected (true);
        }
        else
        {
            PanelEquations pe = PanelModel.instance.panelEquations;
            if (part == pe.part)
            {
                pe.panelParent.animate ();  // Reads latest metadata in getPreferredSize().
                pe.panelEquationGraph.updateGUI ();
            }
        }
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (edit instanceof ChangeAnnotations)
        {
            ChangeAnnotations ca = (ChangeAnnotations) edit;
            if (path.equals (ca.path)  &&  doAdd.structureEquals (ca.doAdd))
            {
                doAdd = ca.doAdd;  // Replace our values with the new values.
                neutralized =  undoAdd != null  &&  undoAdd.equals (doAdd);
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

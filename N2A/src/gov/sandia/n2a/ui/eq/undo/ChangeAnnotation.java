/*
Copyright 2017-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeContainer;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class ChangeAnnotation extends UndoableView
{
    protected List<String> path;           // to the direct parent of the node to be changed
    protected String       parentKeys;     // Keys relative to DB parent node which are folded into the GUI parent node at the time this transaction is created.
    protected String       nameBefore;
    protected String       nameAfter;
    protected String       prefixBefore;   // DB name path of node which gets removed during rename. Path is relative to closest container that won't change form.
    protected String       prefixAfter;    // DB name path of first node that does not already exist at destination. If everything in nameAfter already exists, then this string is empty and tree structure will not be cleared by undo().
    protected String       valueBefore;
    protected String       valueBeforeRebase;  // For use by rebase, in the rare case that there is a non-empty value at the target of a value injection.
    protected String       valueAfter;
    protected MNode        savedTree;      // The entire subtree from the top document. If not from top document, then at least a single node for the node itself.
    protected boolean      multi;          // Add to existing selection rather than blowing it away.
    public    boolean      selectVariable; // Select containing variable rather than specific metadata node. Implies the relevant node is directly under a variable.
    protected boolean      touchesPin;
    protected boolean      touchesCategory;

    public ChangeAnnotation (NodeAnnotation node, String nameAfter, String valueAfter)
    {
        NodeBase parent = (NodeBase) node.getParent ();
        path = parent.getKeyPath ();

        // Gather keys relative to DB parent node which are folded into the GUI parent node.
        // This only happens with NodeAnnotation. Everything else (including NodeAnnotations)
        // does not do folding.
        parentKeys = "";
        if (parent instanceof NodeAnnotation)
        {
            List<String> keys = ((NodeAnnotation) parent).keyList ();
            for (int i = 1; i < keys.size (); i++) parentKeys += keys.get (i) + ".";
        }

        nameBefore      = parentKeys + node.key ();
        valueBefore     = node.folded.get ();
        this.nameAfter  = parentKeys + nameAfter;
        this.valueAfter = valueAfter;

        savedTree = new MVolatile ();
        if (node.folded.isFromTopDocument ()) savedTree.merge (node.folded.getSource ());

        if (nameBefore.equals (nameAfter)) prefixBefore = "";  // In this case, prefixBefore is ignored.
        else                               prefixBefore = parentKeys + nameBefore.split ("\\.")[0];

        prefixAfter = "";
        String[] nameAfters = nameAfter.split ("\\.");
        NodeBase prefixNode = AddAnnotation.findClosest (parent, nameAfters);
        while (prefixNode != parent)
        {
            prefixAfter = ((NodeAnnotation) prefixNode).key () + "." + prefixAfter;
            prefixNode = (NodeBase) prefixNode.getParent ();
        }
        if (! prefixAfter.isEmpty ()) prefixAfter = prefixAfter.substring (0, prefixAfter.length () - 1);
        String[] prefixAfters = prefixAfter.split ("\\.");
        prefixAfter = "";
        int length = Math.min (nameAfters.length, prefixAfters.length);
        int i = 0;
        for (; i < length; i++)
        {
            if (! nameAfters[i].equals (prefixAfters[i])) break;
            prefixAfter += "." + nameAfters[i];
        }
        if (i == nameAfters.length)  // The entire destination path already exists.
        {
            prefixAfter = "";  // Indicates that undo() should not clear any nodes.
        }
        else  // Only part of the destination path exists, so note the first path element that does not already exist.
        {
            prefixAfter = parentKeys + (prefixAfter + "." + nameAfters[i]).substring (1);
        }

        touchesPin =  path.contains ("pin")  ||  nameBefore.contains ("pin")  ||  nameAfter.contains ("pin");  // Crude heuristic to see if this changes pin metadata.

        NodeBase p = parent;
        while (! (p instanceof NodePart)) p = (NodeBase) p.getParent ();
        touchesCategory =  p.getTrueParent () == null  &&  (nameBefore.contains ("category")  ||  nameAfter.contains ("category"));
    }

    public void setMulti (boolean value)
    {
        multi = value;
    }

    public void undo ()
    {
        super.undo ();
        savedTree.set (valueBefore);
        apply (nameAfter, nameBefore, prefixAfter);
    }

    public void redo ()
    {
        super.redo ();
        savedTree.set (valueAfter);
        apply (nameBefore, nameAfter, prefixBefore);
    }

    public void apply (String nameBefore, String nameAfter, String prefixBefore)
    {
        NodeContainer parent = (NodeContainer) NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        MPart mparent = parent.source;
        if (parent instanceof NodeVariable) mparent = (MPart) mparent.child ("$metadata");

        // Update database
        String[] names = nameAfter.split ("\\.");
        if (nameAfter.equals (nameBefore))  // Same name
        {
            // We only need to change the direct value, not the subtree.
            mparent.set (savedTree.get (), names);
        }
        else  // Name changed
        {
            if (! prefixBefore.isEmpty ())
            {
                String[] prefixes = prefixBefore.split ("\\.");
                mparent.clear (prefixes);
            }
            MPart partAfter = (MPart) mparent.childOrCreate (names);
            if (valueBeforeRebase == null) valueBeforeRebase = partAfter.get ();
            partAfter.merge (savedTree);  // saveTree may have children, or it might be a simple value with no children.
        }

        // Update GUI

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = null;
        if (pet != null) model = (FilteredTreeModel) pet.tree.getModel ();

        List<String> expanded = null;
        if (model != null) expanded = AddAnnotation.saveExpandedNodes (pet.tree, parent);
        parent.build ();
        parent.filter ();
        if (model != null  &&  parent.visible ())
        {
            model.nodeStructureChanged (parent);
            AddAnnotation.restoreExpandedNodes (pet.tree, parent, expanded);
        }

        NodeBase nodeAfter = AddAnnotation.findClosest (parent, names);
        if (pet != null)
        {
            TreeNode[] afterPath  = nodeAfter.getPath ();
            TreeNode[] parentPath = parent   .getPath ();
            if (selectVariable) pet.updateVisibility (parentPath, -2, ! multi);
            else                pet.updateVisibility (afterPath,  -2, ! multi);
            if (multi)
            {
                if (selectVariable) pet.tree.addSelectionPath (new TreePath (parentPath));  // Assumes nodeAfter is directly under a NodeVariable.
                else                pet.tree.addSelectionPath (new TreePath (afterPath));
            }
            pet.animate ();
        }

        AddAnnotation.update (parent, touchesPin, touchesCategory);
    }

    /**
        Shift path and name so they more closely encompass the node that gets changed.
        This reduces the amount of rebuilding and GUI updating needed to implement a change.
        Assumes this is a pure change with no rename, at least at the database level.
    **/
    public void rebase ()
    {
        NodeBase parent = NodeBase.locateNode (path);

        // Adjust names for any keys currently folded into parent.
        String tempNameAfter   = nameAfter;
        String tempPrefixAfter = prefixAfter;
        if (parent instanceof NodeAnnotation)
        {
            int prefix = 0;
            List<String> keys = ((NodeAnnotation) parent).keyList ();
            for (int i = 1; i < keys.size (); i++) i += keys.get (i).length () + 1;
            tempNameAfter = nameAfter.substring (prefix);
            if (! prefixAfter.isEmpty ()) tempPrefixAfter = prefixAfter.substring (prefix);
        }

        NodeBase node      = AddAnnotation.findClosest (parent, tempNameAfter.split ("\\."));
        NodeBase container = (NodeBase) node.getParent ();
        if (   container != parent  &&  container.getChildCount () == 2  // node has exactly one sibling
            && ! prefixAfter.isEmpty ()  // node will be deleted during undo
            && container instanceof NodeAnnotation  &&  ((NodeAnnotation) container).folded.get ().isEmpty ())  // sibling can be folded back into container
        {
            // Path to container can change, so we need to select a higher node where the path will remain constant.
            container = (NodeBase) container.getParent ();
        }

        // Gather keys folded under new parent.
        parentKeys = "";
        if (container instanceof NodeAnnotation)
        {
            List<String> keys = ((NodeAnnotation) container).keyList ();
            for (int i = 1; i < keys.size (); i++) parentKeys += keys.get (i) + ".";
        }

        // To rebase to the new container, we must remove all the path elements from the old to the new location.
        List<String> newPath = container.getKeyPath ();
        int killPrefix = 0;
        for (int i = path.size (); i < newPath.size (); i++) killPrefix += newPath.get (i).length () + 1;
        nameBefore = nameAfter = parentKeys + tempNameAfter.substring (killPrefix);
        path = newPath;

        valueBefore = valueBeforeRebase;  // This is safe to do in all cases. If we are replacing an AddAnnotation, then name was changed, so valueBeforeRebase was set. If we merge into an AddAnnotation, then this will be ignored.

        // When names are equal, prefixes are ignored. However, we update prefixAfter
        // so AddAnnotation can abuse this function to merge add followed by change.
        if (tempPrefixAfter.length () > killPrefix) tempPrefixAfter = parentKeys + tempPrefixAfter.substring (killPrefix);
    }

    public boolean shouldReplaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddAnnotation)
        {
            AddAnnotation aa = (AddAnnotation) edit;
            if (aa.nameIsGenerated  &&  path.equals (aa.path)  &&  nameBefore.equals (parentKeys + aa.name))
            {
                // Must be pure change, no new db node created.
                // Name must change, not just value. If name remains the same (rare case), then it is better
                // for the AddAnnotation to absorb this edit.
                return prefixAfter.isEmpty ()  &&  ! nameBefore.equals (nameAfter);
            }
        }
        return false;
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (! shouldReplaceEdit (edit)) return false;
        rebase ();
        return true;
    }
}

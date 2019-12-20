/*
Copyright 2017-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.PanelEquations.StoredView;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeContainer;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class ChangeAnnotation extends Undoable
{
    protected StoredView   view = PanelModel.instance.panelEquations.new StoredView ();
    protected List<String> path;         // to the direct parent of the node that was changed
    protected String       nameBefore;
    protected String       nameAfter;
    protected String       prefixBefore; // Base of tree which gets removed during rename. Single path element.
    protected String       prefixAfter;  // Name path of first node that does not already exist at destination. If everything in nameAfter already exists, then this string is empty and tree structure will not be cleared by undo().
    protected String       valueBefore;
    protected String       valueAfter;
    protected MNode        savedTree;    // The entire subtree from the top document. If not from top document, then at least a single node for the variable itself.
    public    boolean      setSelection = true;

    public ChangeAnnotation (NodeAnnotation node, String nameAfter, String valueAfter)
    {
        NodeBase parent = (NodeBase) node.getParent ();
        path = parent.getKeyPath ();

        nameBefore  = node.key ();
        valueBefore = node.folded.get ();
        this.nameAfter  = nameAfter;
        this.valueAfter = valueAfter;

        savedTree = new MVolatile ();
        if (node.folded.isFromTopDocument ()) savedTree.merge (node.folded.getSource ());

        if (nameBefore.equals (nameAfter)) prefixBefore = "";  // In this case, prefixBefore is ignored.
        else                               prefixBefore = nameBefore.split ("\\.")[0];

        prefixAfter = "";
        NodeBase prefixNode = AddAnnotation.resolve (parent, nameAfter);
        while (prefixNode != parent)
        {
            prefixAfter = ((NodeAnnotation) prefixNode).key () + "." + prefixAfter;
            prefixNode = (NodeBase) prefixNode.getParent ();
        }
        if (! prefixAfter.isEmpty ()) prefixAfter = prefixAfter.substring (0, prefixAfter.length () - 1);
        String[] nameAfters   = nameAfter.  split ("\\.");
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
            prefixAfter = "";  // Indicate that undo() should not to clear any nodes.
        }
        else  // Only part of the destination path exists, so note the first path element that does not already exist.
        {
            prefixAfter = (prefixAfter + "." + nameAfters[i]).substring (1);
        }
    }

    public void undo ()
    {
        super.undo ();
        savedTree.set (valueBefore);
        apply (nameAfter, nameBefore, prefixAfter, setSelection);
    }

    public void redo ()
    {
        super.redo ();
        savedTree.set (valueAfter);
        apply (nameBefore, nameAfter, prefixBefore, setSelection);
    }

    public void apply (String nameBefore, String nameAfter, String prefixBefore, boolean setSelection)
    {
        view.restore ();
        NodeContainer parent = (NodeContainer) NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        MPart mparent = parent.source;
        if (parent instanceof NodeVariable) mparent = (MPart) mparent.child ("$metadata");
        else if (parent instanceof NodeAnnotation) mparent = ((NodeAnnotation) parent).folded;

        // Update database
        String[] names = nameAfter.split ("\\.");
        if (nameAfter.equals (nameBefore))
        {
            // If name is unchanged, then node should already exist,
            // and we only need to change the direct value, not the subtree.
            mparent.set (savedTree.get (), names);
        }
        else
        {
            if (! prefixBefore.isEmpty ())
            {
                String[] prefixes = prefixBefore.split ("\\.");
                mparent.clear (prefixes);
            }
            MPart partAfter = (MPart) mparent.childOrCreate (names);
            partAfter.merge (savedTree);  // saveTree may have children, or it might be a simple value with no children.
        }

        // Update GUI

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();

        List<String> expanded = AddAnnotation.saveExpandedNodes (pet.tree, parent);
        parent.build ();
        parent.filter (FilteredTreeModel.filterLevel);
        if (parent.visible (FilteredTreeModel.filterLevel))
        {
            model.nodeStructureChanged (parent);
            AddAnnotation.restoreExpandedNodes (pet.tree, parent, expanded);
        }
        NodeBase nodeAfter = AddAnnotation.resolve (parent, nameAfter);
        pet.updateVisibility (nodeAfter.getPath (), -2, setSelection);

        while (parent instanceof NodeAnnotation  ||  parent instanceof NodeAnnotations) parent = (NodeContainer) parent.getParent ();
        if (parent instanceof NodePart)
        {
            NodePart p = (NodePart) parent;
            if (p.graph != null) p.graph.updateGUI ();
        }
    }

    /**
        Shift path and name so they more closely encompass the node that gets changed.
        This reduces the amount of rebuilding and GUI updating needed to implement a change.
        Assumes this is a pure change with no rename, at least at the database level.
    **/
    public void rebase ()
    {
        NodeBase parent    = NodeBase.locateNode (path);
        NodeBase node      = AddAnnotation.resolve (parent, nameAfter);
        NodeBase container = (NodeBase) node.getParent ();
        if (   container != parent  &&  container.getChildCount () == 2  // node has exactly one sibling
            && ! prefixAfter.isEmpty ()  // node will be deleted during undo
            && container instanceof NodeAnnotation  &&  ((NodeAnnotation) container).folded.get ().isEmpty ())  // sibling can be folded back into container
        {
            // Path to container can change, so we need to select a higher node where the path will remain constant.
            container = (NodeBase) container.getParent ();
        }

        // To rebase to the new container, we must remove all the path elements from the old to the new location.
        List<String> newPath = container.getKeyPath ();
        int killPrefix = 0;
        for (int i = path.size (); i < newPath.size (); i++) killPrefix += newPath.get (i).length () + 1;
        nameBefore = nameAfter = nameAfter.substring (killPrefix);
        path = newPath;

        // When names are equal, prefixes are ignored. However, we update prefixAfter
        // so AddAnnotation can abuse this function to merge add followed by change.
        if (prefixAfter.length () > killPrefix) prefixAfter = prefixAfter.substring (killPrefix);
    }

    public boolean shouldReplaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddAnnotation)
        {
            AddAnnotation aa = (AddAnnotation) edit;
            if (aa.nameIsGenerated  &&  path.equals (aa.path)  &&  nameBefore.equals (aa.name))
            {
                return prefixAfter.isEmpty ();  // Must be pure change, no new db node created.
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

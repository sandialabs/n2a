/*
Copyright 2017-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeContainer;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class ChangeAnnotation extends Undoable
{
    protected List<String> path;  // to the direct parent, whether a $metadata block or a variable
    protected String       nameBefore;
    protected String       nameAfter;
    protected String       prefixBefore; // Base of tree which gets removed during rename. Single path element.
    protected String       prefixAfter;  // Name path of first node that does not already exist at destination. If everything in nameAfter already exists, then this string is empty and tree structure will not be cleared by undo().
    protected String       valueBefore;
    protected String       valueAfter;
    protected MNode        savedTree;  // The entire subtree from the top document. If not from top document, then at least a single node for the variable itself.

    public ChangeAnnotation (NodeAnnotation node, String nameAfter, String valueAfter)
    {
        NodeBase parent = (NodeBase) node.getParent ();
        path = parent.getKeyPath ();

        nameBefore  = node.key ();
        valueBefore = node.source.get ();
        this.nameAfter  = nameAfter;
        this.valueAfter = valueAfter;

        savedTree = new MVolatile ();
        if (node.folded.isFromTopDocument ()) savedTree.merge (node.folded.getSource ());

        if (nameBefore.equals (nameAfter)) prefixBefore = "";  // In this, prefixBefore is actually ignored.
        else                               prefixBefore = nameBefore.split ("\\.")[0];

        prefixAfter = "";
        NodeBase prefixNode = AddAnnotation.resolve (parent, nameAfter);
        while (prefixNode != parent)
        {
            prefixAfter = ((NodeAnnotation) prefixNode).key () + "." + prefixAfter;
            prefixNode = (NodeBase) prefixNode.getParent ();
        }
        if (prefixAfter.endsWith (".")) prefixAfter = prefixAfter.substring (0, prefixAfter.length () - 1);
        if (prefixAfter.length () >= nameAfter.length ())  // The entire destination path already exists.
        {
            prefixAfter = "";  // Indicate that undo() should not to clear any nodes.
        }
        else
        {
            if (! prefixAfter.isEmpty ()) prefixAfter += ".";
            String suffix = nameAfter.substring (prefixAfter.length ());
            prefixAfter += suffix.split ("\\.")[0];
        }
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
        if (nameAfter.equals (nameBefore))
        {
            // If name is unchanged, then node should already exist,
            // and we only need to change the direct value, not the subtree.
            mparent.child (names).set (savedTree.get ());
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

        PanelEquationTree pet = PanelModel.instance.panelEquations;
        JTree tree = pet.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        List<String> expanded = AddAnnotation.saveExpandedNodes (tree, parent);
        parent.build ();
        parent.filter (model.filterLevel);
        if (parent.visible (model.filterLevel))
        {
            model.nodeStructureChanged (parent);
            AddAnnotation.restoreExpandedNodes (tree, parent, expanded);
        }
        NodeBase nodeAfter = AddAnnotation.resolve (parent, nameAfter);
        tree.expandPath (new TreePath (nodeAfter.getPath ()));
        pet.updateVisibility (nodeAfter.getPath ());
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
        if (   container != parent  &&  container.getChildCount () == 2  // node has a sibling
            && ! prefixAfter.isEmpty ()  // node will be deleted
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

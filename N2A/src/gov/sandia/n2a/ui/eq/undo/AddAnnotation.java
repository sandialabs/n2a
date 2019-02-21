/*
Copyright 2016-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeContainer;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class AddAnnotation extends Undoable
{
    protected List<String> path;    // to the container of the new node. Can be a part, variable, $metadata, or annotation.
    protected int          index;   // Where to insert among siblings. Unfiltered.
    protected String       name;
    protected String       prefix;  // Name path to first node that does not already exist at add location. If all nodes already exist (and this is merely a value set), then string is empty and undo() will not clear anything.
    protected MNode        createSubtree;
    protected boolean      nameIsGenerated;
    public    NodeBase     createdNode;  ///< Used by caller to initiate editing. Only valid immediately after call to redo().

    /**
        @param parent Direct container of the new node, even if not a $metadata node.
        @param index Position in the unfiltered tree where the node should be inserted.
        @param data The key, value, and perhaps subtree to be installed. Could be null, in which
        case we create generic name with no value.
    **/
    public AddAnnotation (NodeBase parent, int index, MNode data)
    {
        path = parent.getKeyPath ();
        this.index = index;

        MNode mparent = parent.source;
        if (parent instanceof NodePart  ||  parent instanceof NodeVariable) mparent = mparent.child ("$metadata");

        createSubtree = new MVolatile ();
        if (data == null)
        {
            name = uniqueName (mparent, "a", false);
            prefix = name;
            nameIsGenerated = true;
        }
        else  // Paste or import
        {
            // Assemble deep path
            name = "";
            while (true)
            {
                String key = data.key ();
                name += "." + key;
                if (data.size () != 1  ||  ! data.get ().isEmpty ()) break;
                data = data.iterator ().next ();  // Retrieve the only child.
            }
            name = name.substring (1);
            createSubtree.merge (data);

            // Determine prefix
            prefix = "";
            String[] names = name.split ("\\.");
            if (mparent == null)
            {
                prefix = names[0];
            }
            else
            {
                for (String n : names)
                {
                    prefix += "." + n;
                    mparent = mparent.child (n);
                    if (mparent == null) break;
                }
                prefix = prefix.substring (1);

                // Ensure that last path element is unique
                if (mparent != null)
                {
                    int last = names.length - 1;
                    names[last] = uniqueName (mparent, names[last], true);
                    name = "";
                    for (String n : names) name += "." + n;
                    name = name.substring (1);
                    prefix = name;
                }
            }

            nameIsGenerated = false;
        }
    }

    public String uniqueName (MNode mparent, String prefix, boolean allowEmptySuffix)
    {
        if (allowEmptySuffix  &&  (mparent == null  ||  mparent.child (prefix) == null)) return prefix;
        int suffix = 1;
        if (mparent != null)
        {
            while (mparent.child (prefix + suffix) != null) suffix++;
        }
        return prefix + suffix;
    }

    public void undo ()
    {
        super.undo ();
        destroy (path, false, name, prefix, createSubtree);
    }

    public static void destroy (List<String> path, boolean canceled, String name, String prefix, MNode createSubtree)
    {
        // Retrieve created node
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotUndoException ();
        NodeContainer container = (NodeContainer) parent;
        if (parent instanceof NodePart) container = (NodeContainer) parent.child ("$metadata");
        NodeBase createdNode = resolve (container, name);
        if (createdNode == container) throw new CannotUndoException ();

        // Update database

        MPart mparent = parent.source;
        if (parent instanceof NodePart  ||  parent instanceof NodeVariable) mparent = (MPart) mparent.child ("$metadata");
        else if (parent instanceof NodeAnnotation) mparent = ((NodeAnnotation) parent).folded;
        // else parent is a NodeAnnotations, which can be used directly.

        boolean killBlock = false;
        if (! prefix.isEmpty ())
        {
            String[] names = prefix.split ("\\.");
            mparent.clear (names);
            if (mparent.key ().equals ("$metadata")  &&  mparent.size () == 0)
            {
                mparent.parent ().clear ("$metadata");
                killBlock = true;
            }
        }

        // Update GUI

        PanelEquationTree pet = PanelModel.instance.panelEquations;
        JTree tree = pet.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        TreeNode[] createdPath = createdNode.getPath ();
        int index = container.getIndexFiltered (createdNode);
        if (canceled) index--;

        if (killBlock  &&  parent instanceof NodePart)  // We just emptied $metadata, so remove the node.
        {
            model.removeNodeFromParent (container);
            // No need to update order, because we just destroyed $metadata, where order is stored.
            // No need to update tab stops in grandparent, because block nodes don't offer any tab stops.
        }
        else  // Rebuild container (variable, metadata block, or annotation)
        {
            List<String> expanded = saveExpandedNodes (tree, container);
            container.build ();
            container.filter (model.filterLevel);
            if (container.visible (model.filterLevel))
            {
                model.nodeStructureChanged (container);
                restoreExpandedNodes (tree, container, expanded);
            }
        }
        pet.updateVisibility (createdPath, index);
    }

    public void redo ()
    {
        super.redo ();
        createdNode = create (path, index, name, createSubtree, nameIsGenerated);
    }

    public static NodeBase create (List<String> path, int index, String name, MNode createSubtree, boolean nameIsGenerated)
    {
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();

        // Update database

        MPart mparent = parent.source;
        if (parent instanceof NodePart  ||  parent instanceof NodeVariable) mparent = (MPart) mparent.childOrCreate ("$metadata");
        else if (parent instanceof NodeAnnotation) mparent = ((NodeAnnotation) parent).folded;
        // else parent is a NodeAnnotations, which can be used directly.

        // For a simple add, name has only one path element. However, if a ChangeAnnotation was
        // merged into this, then the name may have several path elements.
        String[] names = name.split ("\\.");
        MPart createdPart = (MPart) mparent.childOrCreate (names);
        createdPart.merge (createSubtree);

        // Update GUI

        PanelEquationTree pet = PanelModel.instance.panelEquations;
        JTree tree = pet.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        NodeContainer container = (NodeContainer) parent;
        if (parent instanceof NodePart)  // If this is a part, then display special block.
        {
            if (mparent.size () == 0)  // empty implies the node is absent
            {
                container = new NodeAnnotations (mparent);
                model.insertNodeIntoUnfiltered (container, parent, index);
                // TODO: update order?
                index = 0;
            }
            else  // the node is present, so retrieve it
            {
                container = (NodeContainer) parent.child ("$metadata");
            }
        }

        NodeBase createdNode;
        if (nameIsGenerated)  // pure create, going into edit mode
        {
            // The given name should be unique, so don't bother checking for an existing node.
            createdNode = new NodeAnnotation (createdPart);

            FontMetrics fm = createdNode.getFontMetrics (tree);
            if (container.getChildCount () > 0)
            {
                NodeBase firstChild = (NodeBase) container.getChildAt (0);
                if (firstChild.needsInitTabs ()) firstChild.initTabs (fm);
            }

            createdNode.setUserObject ("");  // For edit mode. This should only happen on first application of the create action, and should only be possible if visibility is already correct.
            createdNode.updateColumnWidths (fm);  // preempt initialization; uses actual name, not user value
            model.insertNodeIntoUnfiltered (createdNode, container, index);
        }
        else  // create was merged with change name/value
        {
            List<String> expanded = saveExpandedNodes (tree, container);
            container.build ();
            container.filter (model.filterLevel);
            if (container.visible (model.filterLevel))
            {
                model.nodeStructureChanged (container);
                restoreExpandedNodes (tree, container, expanded);
            }
            createdNode = resolve (container, name);
            tree.expandPath (new TreePath (createdNode.getPath ()));
            pet.updateVisibility (createdNode.getPath ());
        }
        return createdNode;
    }

    public static NodeBase resolve (NodeBase container, String name)
    {
        int count = container.getChildCount ();
        if (count > 0)
        {
            for (int i = 0; i < count; i++)
            {
                NodeBase a = (NodeBase) container.getChildAt (i);  // unfiltered
                if (! (a instanceof NodeAnnotation)) continue;  // For example, an equation at the same level as annotation under a variable.
                String key = ((NodeAnnotation) a).key ();
                if (key.startsWith (name)) return a;
                if (name.startsWith (key))
                {
                    String suffix = name.substring (key.length ());
                    if (suffix.startsWith (".")) return resolve (a, suffix.substring (1));
                }
            }
        }
        return container;
    }

    public static List<String> saveExpandedNodes (JTree tree, NodeBase parent)
    {
        List<String> result = new ArrayList<String> ();
        Enumeration<TreePath> expandedNodes = tree.getExpandedDescendants (new TreePath (parent.getPath ()));
        if (expandedNodes != null)
        {
            while (expandedNodes.hasMoreElements ())
            {
                TreePath path = expandedNodes.nextElement ();
                NodeBase node = (NodeBase) path.getLastPathComponent ();
                String name = "";
                while (node != parent)
                {
                    String key;
                    if (node instanceof NodeAnnotation) key = ((NodeAnnotation) node).key ();
                    else                                key = node.source.key ();
                    name = key + "." + name;
                    node = (NodeBase) node.getParent ();
                }
                if (! name.isEmpty ())
                {
                    name = name.substring (0, name.length () - 1);
                    result.add (name);
                }
            }
        }
        return result;
    }

    public static void restoreExpandedNodes (JTree tree, NodeBase parent, List<String> names)
    {
        for (String n : names)
        {
            NodeBase node = resolve (parent, n);
            if (node != parent) tree.expandPath (new TreePath (node.getPath ()));
        }
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (nameIsGenerated  &&  edit instanceof ChangeAnnotation)
        {
            ChangeAnnotation change = (ChangeAnnotation) edit;
            if (change.shouldReplaceEdit (this)) return false;
            if (path.equals (change.path)  &&  name.equals (change.nameBefore))
            {
                change.rebase ();
                path            = change.path;
                name            = change.nameAfter;
                prefix          = change.prefixAfter;
                nameIsGenerated = false;
                createSubtree.merge (change.savedTree);  // Generally, there should be nothing in savedTree. Just being thorough.
                createSubtree.set (change.valueAfter);
                return true;
            }
        }
        return false;
    }
}

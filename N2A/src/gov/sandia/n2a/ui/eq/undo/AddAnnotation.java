/*
Copyright 2016-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

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
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeContainer;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class AddAnnotation extends UndoableView implements AddEditable
{
    protected List<String> path;            // to the container of the new node. Can be a variable, $metadata (under a part), or annotation.
    protected int          index;           // Where to insert among siblings. Unfiltered.
    protected String       name;
    protected String       prefix;          // Name path to first node that does not already exist at add location. If all nodes already exist (and this is merely a value set), then string is empty and undo() will not clear anything.
    protected MNode        createSubtree;
    protected boolean      nameIsGenerated;
    protected NodeBase     createdNode;     // Used by caller to initiate editing. Only valid immediately after call to redo().
    protected boolean      multi;           // Add to existing selection rather than blowing it away.
    protected boolean      multiLast;       // Set selection during delete, but add to selection during create.
    public    boolean      selectVariable;  // Select containing variable rather than specific metadata node. Implies the relevant node is directly under a variable.

    /**
        @param parent Direct container of the new node, even if not a $metadata node.
        Sometimes this can be a part which doesn't yet contain a $metadata node. In that case, the part node is
        treated as if it contained a metadata node, and the non-existent metadata node becomes the parent.
        @param index Position in the unfiltered tree where the node should be inserted.
        @param data The key, value, and perhaps subtree to be installed. Could be null, in which
        case we create generic name with no value.
    **/
    public AddAnnotation (NodeBase parent, int index, MNode data)
    {
        path = parent.getKeyPath ();
        this.index = index;

        if (parent instanceof NodePart) path.add ("$metadata");  // Fake the metadata block.

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
        destroy (path, false, name, prefix, multi, multiLast, selectVariable);
    }

    public static void destroy (List<String> path, boolean canceled, String name, String prefix, boolean multi, boolean multiLast, boolean selectVariable)
    {
        // Retrieve created node
        NodeContainer parent = (NodeContainer) NodeBase.locateNode (path);
        if (parent == null) throw new CannotUndoException ();
        NodeBase createdNode = resolve (parent, name);
        if (createdNode == parent) throw new CannotUndoException ();

        // Update database

        MPart mparent = parent.source;
        if (parent instanceof NodeVariable) mparent = (MPart) mparent.child ("$metadata");
        else if (parent instanceof NodeAnnotation) mparent = ((NodeAnnotation) parent).folded;
        // else parent is a NodeAnnotations, so mparent is $metadata, which should be used directly.

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

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = null;
        if (pet != null) model = (FilteredTreeModel) pet.tree.getModel ();

        TreeNode[] createdPath = createdNode.getPath ();
        int index = parent.getIndexFiltered (createdNode);
        if (canceled) index--;

        if (killBlock  &&  parent instanceof NodeAnnotations)  // We just emptied $metadata, so remove the node.
        {
            if (model == null) FilteredTreeModel.removeNodeFromParentStatic (parent);
            else               model.removeNodeFromParent (parent);
            // No need to update order, because we just destroyed $metadata, where order is stored.
            // No need to update tab stops in grandparent, because block nodes don't offer any tab stops.
        }
        else  // Rebuild container (variable, metadata block, or annotation)
        {
            List<String> expanded = null;
            if (model != null) expanded = saveExpandedNodes (pet.tree, parent);
            parent.build ();
            parent.filter ();
            if (model != null  &&  parent.visible ())
            {
                model.nodeStructureChanged (parent);
                restoreExpandedNodes (pet.tree, parent, expanded);
            }
        }
        if (pet != null)
        {
            TreeNode[] parentPath = parent.getPath ();
            if (selectVariable) pet.updateVisibility (parentPath,  index, ! multi);
            else                pet.updateVisibility (createdPath, index, ! multi  ||  multiLast);
            if (multi  &&  selectVariable) pet.tree.addSelectionPath (new TreePath (parentPath));  // Assumes nodeAfter is directly under a NodeVariable. Note that effect will be redundant with above when multiLast is true.
            pet.animate ();
        }

        while (parent instanceof NodeAnnotation  ||  parent instanceof NodeAnnotations) parent = (NodeContainer) parent.getParent ();
        NodeVariable binding = null;
        if (parent instanceof NodeVariable  &&  ((NodeVariable) parent).isBinding)
        {
            binding = (NodeVariable) parent;
            parent = (NodeContainer) parent.getParent ();  // So arrowhead can update.
        }
        if (parent instanceof NodePart)
        {
            NodePart p = (NodePart) parent;
            if (p.graph != null)
            {
                if (binding != null)
                {
                    String alias = binding.source.key ();
                    p.graph.updateEdge (alias, p.connectionBindings.get (alias));
                }
                p.graph.updateGUI ();
            }
            else
            {
                PanelEquations pe = PanelModel.instance.panelEquations;
                if (p == pe.part)
                {
                    pe.panelParent.animate ();  // Reads latest metadata in getPreferredSize().
                    pe.panelEquationGraph.updateGUI ();
                }
            }
        }
        if (parent.getTrueParent () == null  &&  name.endsWith ("category"))  // root node, so update categories in search list
        {
            PanelModel.instance.panelSearch.search ();
        }
    }

    public void redo ()
    {
        super.redo ();
        createdNode = create (path, index, name, createSubtree, nameIsGenerated, multi, selectVariable);
    }

    public NodeBase getCreatedNode ()
    {
        return createdNode;
    }

    public static NodeBase create (List<String> path, int index, String name, MNode createSubtree, boolean nameIsGenerated, boolean multi, boolean selectVariable)
    {
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null)
        {
            int last = path.size () - 1;
            if (path.get (last).equals ("$metadata")) parent = NodeBase.locateNode (path.subList (0, last));
        }
        if (parent == null) throw new CannotRedoException ();

        // Update database

        MPart mparent = parent.source;
        if (parent instanceof NodePart  ||  parent instanceof NodeVariable) mparent = (MPart) mparent.childOrCreate ("$metadata");
        else if (parent instanceof NodeAnnotation) mparent = ((NodeAnnotation) parent).folded;
        // else parent is a NodeAnnotations, so mparent is $metadata, which can be used directly.

        // For a simple add, name has only one path element. However, if a ChangeAnnotation was
        // merged into this, then the name may have several path elements.
        String[] names = name.split ("\\.");
        MPart createdPart = (MPart) mparent.childOrCreate (names);
        createdPart.merge (createSubtree);

        // Update GUI

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = null;
        if (pet != null) model = (FilteredTreeModel) pet.tree.getModel ();

        NodeContainer container = (NodeContainer) parent;
        if (parent instanceof NodePart)  // If this is a part, then display special block.
        {
            container = (NodeContainer) parent.child ("$metadata");
            if (container == null)
            {
                container = new NodeAnnotations (mparent);
                if (model == null) FilteredTreeModel.insertNodeIntoUnfilteredStatic (container, parent, index);
                else               model.insertNodeIntoUnfiltered (container, parent, index);
                // TODO: update order?
                index = 0;
            }
        }

        NodeBase createdNode;
        if (nameIsGenerated)  // pure create, going into edit mode
        {
            // The given name should be unique, so don't bother checking for an existing node.
            createdNode = new NodeAnnotation (createdPart);
            createdNode.setUserObject ("");  // For edit mode. This should only happen on first application of the create action, and should only be possible if visibility is already correct.
            if (model == null) FilteredTreeModel.insertNodeIntoUnfilteredStatic (createdNode, container, index);
            else               model.insertNodeIntoUnfiltered (createdNode, container, index);
        }
        else  // create was merged with change name/value
        {
            List<String> expanded = null;
            if (model != null) expanded = saveExpandedNodes (pet.tree, container);
            container.build ();
            container.filter ();
            if (model != null  &&  container.visible ())
            {
                model.nodeStructureChanged (container);
                restoreExpandedNodes (pet.tree, container, expanded);
            }

            createdNode = resolve (container, name);
            if (pet != null)
            {
                TreeNode[] parentPath      = parent     .getPath ();
                TreeNode[] createdPath     = createdNode.getPath ();
                TreePath   createdTreePath = new TreePath (createdPath);
                pet.tree.expandPath (createdTreePath);
                if (selectVariable) pet.updateVisibility (parentPath,  -2, ! multi);
                else                pet.updateVisibility (createdPath, -2, ! multi);
                if (multi)
                {
                    if (selectVariable) pet.tree.addSelectionPath (new TreePath (parentPath));
                    else                pet.tree.addSelectionPath (createdTreePath);
                }
                pet.animate ();
            }
        }

        // Do related record-keeping. For the most part, this only applies when nameIsGenerated==false.
        // However, it also applies when creating a new pin.
        while (parent instanceof NodeAnnotation  ||  parent instanceof NodeAnnotations) parent = (NodeBase) parent.getParent ();
        NodeVariable binding = null;
        if (parent instanceof NodeVariable  &&  ((NodeVariable) parent).isBinding)
        {
            binding = (NodeVariable) parent;
            parent = (NodeBase) parent.getParent ();
        }
        if (parent instanceof NodePart)
        {
            NodePart p = (NodePart) parent;
            if (p.graph != null)
            {
                if (binding != null)
                {
                    String alias = binding.source.key ();
                    p.graph.updateEdge (alias, p.connectionBindings.get (alias));
                }
                p.graph.updateGUI ();
            }
            else
            {
                PanelEquations pe = PanelModel.instance.panelEquations;
                if (p == pe.part)
                {
                    pe.panelParent.animate ();  // Reads latest metadata in getPreferredSize().
                    pe.panelEquationGraph.updateGUI ();
                }
            }
        }
        if (parent.getTrueParent () == null  &&  name.endsWith ("category"))
        {
            PanelModel.instance.panelSearch.search ();
        }

        return createdNode;
    }

    /**
        Returns the closest node that contains the given name.
    **/
    public static NodeBase resolve (NodeBase container, String name)
    {
        int count = container.getChildCount ();
        if (count > 0)
        {
            String[] names = name.split ("\\.");
            for (int i = 0; i < count; i++)
            {
                NodeBase a = (NodeBase) container.getChildAt (i);  // unfiltered
                if (! (a instanceof NodeAnnotation)) continue;  // For example, an equation at the same level as annotation under a variable.
                String key = ((NodeAnnotation) a).key ();
                String[] keys = key.split ("\\.");
                int length = Math.min (names.length, keys.length);
                int j = 0;
                for (; j < length; j++) if (! names[j].equals (keys[j])) break;
                if (j > 0)  // At least one element of path matched
                {
                    if (j < length  ||  length == names.length) return a;  // Partial match, or name is fully matched.
                    // key is fully matched, but some suffix of name remains, so keep searching in sub-node.
                    return resolve (a, name.substring (key.length () + 1));  // The +1 removes the next dot in the path
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

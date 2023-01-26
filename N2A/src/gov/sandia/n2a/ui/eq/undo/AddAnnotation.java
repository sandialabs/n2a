/*
Copyright 2016-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import gov.sandia.n2a.ui.UndoManager;
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
    protected List<String> path;            // to the container of the new node. Can be a variable, $meta (under a part), or annotation.
    protected int          index;           // Where to insert among siblings. Unfiltered.
    protected String       name;
    protected String       prefix;          // Name path to first node that does not already exist at add location. If all nodes already exist (and this is merely a value set), then string is empty and undo() will not clear anything.
    protected MNode        createSubtree;
    protected boolean      nameIsGenerated;
    protected NodeBase     createdNode;     // Used by caller to initiate editing. Only valid immediately after call to redo().
    protected boolean      multi;           // Add to existing selection rather than blowing it away.
    protected boolean      multiLast;       // Set selection during delete, but add to selection during create.
    public    boolean      selectVariable;  // Select containing variable rather than specific metadata node. Implies the relevant node is directly under a variable.
    protected boolean      touchesPin;
    protected boolean      touchesCategory;

    /**
        @param parent Direct container of the new node, even if not a $meta node.
        Sometimes this can be a part which doesn't yet contain a $meta node. In that case, the part node is
        treated as if it contained a metadata node, and the non-existent metadata node becomes the parent.
        @param index Position in the unfiltered tree where the node should be inserted.
        @param data The key, value, and perhaps subtree to be installed. Could be null, in which
        case we create generic name with no value.
    **/
    public AddAnnotation (NodeBase parent, int index, MNode data)
    {
        path = parent.getKeyPath ();
        this.index = index;

        if (parent instanceof NodePart) path.add ("$meta");  // Fake the metadata block.

        MNode mparent = parent.source;
        if (parent instanceof NodePart  ||  parent instanceof NodeVariable) mparent = mparent.child ("$meta");

        createSubtree = new MVolatile ();
        if (data == null)
        {
            name = uniqueName (mparent, "a", 0, false);
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
                MNode mchild = mparent;
                for (String n : names)
                {
                    prefix += "." + n;
                    mparent = mchild;
                    mchild = mparent.child (n);
                    if (mchild == null) break;
                }
                prefix = prefix.substring (1);

                if (mchild != null)  // The target name already exists, so pick a unique one.
                {
                    int last = names.length - 1;
                    names[last] = uniqueName (mparent, names[last], 2, true);
                    name = names[0];
                    for (int i = 1; i <= last; i++) name += "." + names[i];
                    prefix = name;
                }
            }

            nameIsGenerated = false;
        }

        touchesPin =  path.contains ("pin")  ||  name.contains ("pin")  ||  createSubtree.containsKey ("pin");

        NodeBase p = parent;
        while (! (p instanceof NodePart)) p = (NodeBase) p.getParent ();
        touchesCategory =  p.getTrueParent () == null  &&  (name.contains ("category")  ||  createSubtree.containsKey ("category"));
    }

    public String uniqueName (MNode mparent, String prefix, int suffix, boolean allowEmptySuffix)
    {
        if (allowEmptySuffix  &&  (mparent == null  ||  mparent.child (prefix) == null)) return prefix;
        if (mparent == null) return prefix + suffix;
        while (true)
        {
            String result = prefix + suffix;
            if (mparent.child (result) == null) return result;
            suffix++;
        }
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
        destroy (path, false, name, prefix, multi, multiLast, selectVariable, touchesPin, touchesCategory);
    }

    public static void destroy (List<String> path, boolean canceled, String name, String prefix, boolean multi, boolean multiLast, boolean selectVariable, boolean touchesPin, boolean touchesCategory)
    {
        // Retrieve created node
        NodeContainer parent = (NodeContainer) NodeBase.locateNode (path);
        if (parent == null) throw new CannotUndoException ();
        NodeBase createdNode = findClosest (parent, name.split ("\\."));
        if (createdNode == parent) throw new CannotUndoException ();

        // Update database

        MPart mparent = parent.source;
        if (parent instanceof NodeVariable) mparent = (MPart) mparent.child ("$meta");
        else if (parent instanceof NodeAnnotation) mparent = ((NodeAnnotation) parent).folded;
        // else parent is a NodeAnnotations, so mparent is $meta, which should be used directly.

        boolean killBlock = false;
        if (! prefix.isEmpty ())
        {
            String[] names = prefix.split ("\\.");
            mparent.clear (names);
            if (mparent.key ().equals ("$meta")  &&  mparent.size () == 0)
            {
                mparent.parent ().clear ("$meta");
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

        if (killBlock  &&  parent instanceof NodeAnnotations)  // We just emptied $meta, so remove the node.
        {
            if (model == null) FilteredTreeModel.removeNodeFromParentStatic (parent);
            else               model.removeNodeFromParent (parent);
            // No need to update order, because we just destroyed $meta, where order is stored.
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

        update (parent, touchesPin, touchesCategory);
    }

    /**
        Do related record-keeping.
        This function is shared by all undo classes that modify $meta.
    **/
    public static void update (NodeBase parent, boolean touchesPin, boolean touchesCategory)
    {
        while (parent instanceof NodeAnnotation  ||  parent instanceof NodeAnnotations) parent = (NodeBase) parent.getParent ();
        NodeVariable binding = null;
        if (parent instanceof NodeVariable  &&  ((NodeVariable) parent).isBinding)
        {
            binding = (NodeVariable) parent;
            parent = (NodeBase) parent.getParent ();  // So arrowhead can update.
        }

        if (! (parent instanceof NodePart)) return;
        NodePart p = (NodePart) parent;

        boolean touchesImage =  p.iconCustom != null  ||  p.source.child ("$meta", "gui", "icon") != null;
        if (touchesImage) p.setIcon ();

        PanelEquations pe = PanelModel.instance.panelEquations;
        if (touchesPin)
        {
            p.updatePins ();
            // A change in pin structure can affect any level of graph above the current node,
            // so always refresh display.
            pe.panelEquationGraph.updatePins ();
            pe.panelEquationGraph.reconnect ();
            pe.panelEquationGraph.repaint ();
        }
        if (p.graph == null)  // It's either the parent node, or a node below the current level of graph.
        {
            if (p == pe.part) pe.updateGUI ();
        }
        else
        {
            if (binding == null)  // Target is parent itself.
            {
                // PanelEquationGraph.reconnect() must come before updateGUI(). Otherwise, graph node might
                // operate on edges that no longer have pin metadata.
                p.graph.updateGUI ();
            }
            else  // Target is variable under parent, likely a connection binding.
            {
                if (! touchesPin)
                {
                    String alias = binding.source.key ();
                    p.graph.updateEdge (alias, p.connectionBindings.get (alias));
                }
                // otherwise all edges in the graph have been updated above, so no need to do incremental update here.
            }
        }

        // Update categories in search list.
        if (touchesCategory) PanelModel.instance.panelSearch.search ();
    }

    public void redo ()
    {
        super.redo ();
        createdNode = create (path, index, name, createSubtree, nameIsGenerated, multi, selectVariable, touchesPin, touchesCategory);
    }

    public NodeBase getCreatedNode ()
    {
        return createdNode;
    }

    public static NodeBase create (List<String> path, int index, String name, MNode createSubtree, boolean nameIsGenerated, boolean multi, boolean selectVariable, boolean touchesPin, boolean touchesCategory)
    {
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null)
        {
            int last = path.size () - 1;
            if (path.get (last).equals ("$meta")) parent = NodeBase.locateNode (path.subList (0, last));
        }
        if (parent == null) throw new CannotRedoException ();

        // Update database

        MPart mparent = parent.source;
        if (parent instanceof NodePart  ||  parent instanceof NodeVariable) mparent = (MPart) mparent.childOrCreate ("$meta");
        else if (parent instanceof NodeAnnotation) mparent = ((NodeAnnotation) parent).folded;
        // else parent is a NodeAnnotations, so mparent is $meta, which can be used directly.

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
            container = (NodeContainer) parent.child ("$meta");
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

            createdNode = findClosest (container, names);
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

        update (parent, touchesPin, touchesCategory);

        return createdNode;
    }

    /**
        Returns the node with the longest path prefix in common with the given path.
        The target may be folded into a child node, in which case the child node is returned.
        If the target does not exist, then the result will be a sibling path (one that
        has the closest common ancestor with the target). If no sibling exists, then it will
        be the closest direct ancestor.
        The sibling case is required by the ChangeAnnotations constructor to determine prefix.
        Most other places it is not an issue because the target node is known to exist.
    **/
    public static NodeBase findClosest (NodeBase container, String... names)
    {
        return findClosest (container, 0, names);
    }

    public static NodeBase findClosest (NodeBase container, int offset, String... names)
    {
        String name = names[offset];
        NodeBase a = container.child (name);
        if (! (a instanceof NodeAnnotation)) return container;  // Not found. Result is parent of target.
        String[] keys = ((NodeAnnotation) a).keyArray ();

        int namesLength = names.length - offset;
        int length = Math.min (namesLength, keys.length);
        // The first name in the path was already matched above.
        for (int i = 1; i < length; i++) if (! names[offset+i].equals (keys[i])) return a;  // partial match (sibling)
        if (length == namesLength) return a;  // target is fully matched (exact match or child)

        // key is fully matched, but the target path is longer, so keep searching in sub-node.
        return findClosest (a, offset + keys.length, names);
    }

    /**
        Returns the NodeAnnotation whose folded field points to the exact node specified by the path.
        Otherwise, returns null. Neither folded children nor parents nor siblings are allowed.
        @param allowFolded Indicates the a node with folded children is OK. Parents are siblings
        are still forbidden.
    **/
    public static NodeBase findExact (NodeBase container, boolean allowFolded, String... names)
    {
        return findExact (container, allowFolded, 0, names);
    }

    public static NodeBase findExact (NodeBase container, boolean allowFolded, int offset, String... names)
    {
        String name = names[offset];
        NodeBase a = container.child (name);
        if (! (a instanceof NodeAnnotation)) return null;  // Not found.
        String[] keys = ((NodeAnnotation) a).keyArray ();

        int namesLength = names.length - offset;
        int length = Math.min (namesLength, keys.length);
        for (int i = 1; i < length; i++) if (! names[offset+i].equals (keys[i])) return null;  // Partial mismatch
        if (keys.length < namesLength) return findExact (a, allowFolded, offset + keys.length, names);  // This level matches, but the target path is longer, so descend a level.
        if (keys.length > namesLength  &&  ! allowFolded) return null;  // forbid folded child
        return a;  // exact match; may also be folded child, if allowed
    }

    /**
        Returns a node that is guaranteed to edit the exact metadata key specified.
        Creates $meta if it does not already exist. Searches the specified path.
        If the path ends short, then adds the necessary node to reach the key.
        If the path runs long (because folding goes past the exact key), then
        adds a new node that unfolds the tree at the required point.
        Will create at most one edit action.
        TODO: This could be generalized to handle NodeVariable containers as well.
    **/
    public static NodeAnnotation findOrCreate (UndoManager um, NodePart part, String... names)
    {
        // Get or create the $meta node.
        NodeBase metadata = part.child ("$meta");
        if (metadata == null)
        {
            int index = 0;
            if (part.getChildCount () > 0)
            {
                NodeBase sibling = (NodeBase) part.getChildAt (0);
                if (sibling.toString ().startsWith ("$inherit")) index = 1;
            }
            MNode mdata = new MVolatile ();
            mdata.set ("", names);
            AddAnnotations aa = new AddAnnotations (part, index, mdata);
            um.apply (aa);
            metadata = aa.getCreatedNode ();
        }

        // Find the GUI node, or determine that it needs to be created.
        // It is possible that the DB node exists, but is folded into a GUI node.
        // That case requires that a new GUI node be created to inject a value.
        NodeBase parent = metadata;  // If we create a new node, this will be its parent.
        int      offset = 0;         // The name of the created node will start at this position in the specified path.
        while (offset < names.length)
        {
            // Find the node that starts with the current name within the current parent.
            String name = names[offset];
            NodeAnnotation na = (NodeAnnotation) parent.child (name);
            if (na == null) break;  // Can't go any deeper.

            String[] keys = na.keyArray ();
            if (keys.length > names.length - offset) break;  // folded in a child, or na is a sibling
            boolean allMatch = true;
            for (int i = 1; i < keys.length; i++)  // First key was already matched by child(String) call above.
            {
                if (! keys[i].equals (names[offset+i]))
                {
                    allMatch = false;
                    break;
                }
            }
            if (! allMatch) break;

            offset += keys.length;
            if (offset == names.length) return na;  // Exact match
            parent = na;
        }

        int length = names.length - offset;
        String[] path = new String[length];
        for (int i = 0; i < length; i++) path[i] = names[offset+i];

        MNode data = new MVolatile ();
        data.set (" ", path);  // The space is to keep the path from re-folding, at least for this session.
        AddAnnotation aa = new AddAnnotation (parent, 0, data.child (path[0]));  // It is necessary that the top-level key be the actual beginning of the path, rather than the blank key created by the MVolatile() constructor.
        um.apply (aa);
        return (NodeAnnotation) aa.getCreatedNode ();
    }

    public static List<String> saveExpandedNodes (JTree tree, NodeBase parent)
    {
        List<String> result = new ArrayList<String> ();
        Enumeration<TreePath> expandedNodes = tree.getExpandedDescendants (new TreePath (parent.getPath ()));
        if (expandedNodes == null) return result;

        String prefix = "";
        if (parent instanceof NodeAnnotation) prefix = ((NodeAnnotation) parent).key () + ".";
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
                result.add (prefix + name);
            }
        }
        return result;
    }

    public static void restoreExpandedNodes (JTree tree, NodeBase parent, List<String> names)
    {
        int prefix = 0;
        if (parent instanceof NodeAnnotation) prefix = ((NodeAnnotation) parent).key ().length () + 1;
        for (String n : names)
        {
            if (n.length () <= prefix) continue;
            n = n.substring (prefix);
            NodeBase node = findClosest (parent, n.split ("\\."));
            if (node != parent) tree.expandPath (new TreePath (node.getPath ()));
        }
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (nameIsGenerated  &&  edit instanceof ChangeAnnotation)
        {
            ChangeAnnotation change = (ChangeAnnotation) edit;
            if (change.shouldReplaceEdit (this)) return false;
            if (path.equals (change.path)  &&  (change.parentKeys + name).equals (change.nameBefore))
            {
                boolean valueOnly = change.nameAfter.equals (change.nameBefore);
                change.rebase ();
                int kill = change.parentKeys.length ();

                path            = change.path;
                name            = change.nameAfter.substring (kill);
                nameIsGenerated = false;
                touchesPin      = change.touchesPin;
                touchesCategory = change.touchesCategory;
                createSubtree.merge (change.savedTree);  // Generally, there should be nothing in savedTree. Just being thorough.
                createSubtree.set (change.valueAfter);
                if (change.prefixAfter.isEmpty ())
                {
                    if (valueOnly) prefix = name;  // Rejected name change, or user actually entered same name as auto-generated, so we should be prepared to delete new node on undo.
                    else           prefix = "";
                }
                else
                {
                    prefix = change.prefixAfter.substring (kill);
                }

                return true;
            }
        }
        return false;
    }
}

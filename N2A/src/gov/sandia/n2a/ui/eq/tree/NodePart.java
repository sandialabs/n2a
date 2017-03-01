/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPersistent;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.NodeBase;
import gov.sandia.n2a.ui.eq.NodeContainer;
import gov.sandia.n2a.ui.eq.undo.AddAnnotation;
import gov.sandia.n2a.ui.eq.undo.AddReference;
import gov.sandia.n2a.ui.eq.undo.DeleteDoc;
import gov.sandia.n2a.ui.eq.undo.ChangeDoc;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

public class NodePart extends NodeContainer
{
    protected static ImageIcon iconCompartment = ImageUtil.getImage ("comp.gif");
    protected static ImageIcon iconConnection  = ImageUtil.getImage ("connection.png");

    protected boolean isConnection;
    protected String parentName = "";
    protected List<Integer> filtered;

    public NodePart ()
    {
    }

    public NodePart (MPart source)
    {
        this.source = source;
        setUserObject ();
    }

    public void setUserObject ()
    {
        setUserObject (source.key ());  // This won't actually be used in editing, but it does prevent editingCancelled() from getting a null object.

        parentName = "";
        if (! isRoot ())
        {
            MNode inherit = source.child ("$inherit");
            if (inherit != null) parentName = inherit.get ().split (",", 2)[0].replace ("\"", "");
        }
    }

    @Override
    public void build ()
    {
        setUserObject ();
        removeAllChildren ();

        String order = source.get ("$metadata", "gui.order");
        Set<String> sorted = new HashSet<String> ();
        String[] subkeys = order.split (",");  // comma-separated list
        for (String k : subkeys)
        {
            MNode c = source.child (k);
            if (c != null)
            {
                buildTriage ((MPart) c);
                sorted.add (k);
            }
        }
        // Build everything else. Sort all subparts to the end.
        ArrayList<MNode> subparts = new ArrayList<MNode> ();
        for (MNode c : source)
        {
            if (sorted.contains (c.key ())) continue;
            if (MPart.isPart (c)) subparts.add (c);
            else                  buildTriage ((MPart) c);
        }
        for (MNode c : subparts) buildTriage ((MPart) c);
    }

    public void buildTriage (MPart line)
    {
        String key = line.key ();
        if (key.equals ("$inherit"))
        {
            NodeInherit i = new NodeInherit (line);
            add (i);
            return;
        }
        if (key.equals ("$metadata"))
        {
            NodeAnnotations a = new NodeAnnotations (line);
            add (a);
            a.build ();
            return;
        }
        if (key.equals ("$reference"))
        {
            NodeReferences r = new NodeReferences (line);
            add (r);
            r.build ();
            return;
        }

        if (line.isPart ())
        {
            NodePart p = new NodePart (line);
            add (p);
            p.build ();
            return;
        }

        NodeVariable v = new NodeVariable (line);
        add (v);
        v.build ();
        // Note: connection bindings will be marked later, after full tree is assembled.
        // This allows us to take advantage of the work done to identify sub-parts.
    }

    @Override
    public boolean visible (int filterLevel)
    {
        if (filterLevel == FilteredTreeModel.ALL) return true;
        if (source.isFromTopDocument ()) return true;
        if (filterLevel >= FilteredTreeModel.LOCAL) return false;  // Since we already fail the "local" requirement
        // FilteredTreeModel.PUBLIC ...
        if (children != null  &&  children.size () > 0  &&  (filtered == null  ||  filtered.size () > 0)) return true;  // We have subnodes, and at least some of them are visible (which can only happen at this point if they are public).
        return source.child ("$metadata", "public") != null;
    }

    @Override
    public void filter (int filterLevel)
    {
        if (children == null)
        {
            filtered = null;
            return;
        }

        int count = children.size ();
        filtered = new Vector<Integer> (count);
        int childIndex = 0;
        for (Object o : children)
        {
            NodeBase c = (NodeBase) o;
            c.filter (filterLevel);
            c.invalidateTabs ();  // force columns to be updated for new subset of children
            if (c.visible (filterLevel)) filtered.add (childIndex);
            childIndex++;  // always increment
        }
        if (filtered.size () == count) filtered = null;  // all children are visible, so don't bother
    }

    @Override
    public List<Integer> getFiltered ()
    {
        return filtered;
    }

    @Override
    public void insertFiltered (int filteredIndex, int childrenIndex, boolean shift)
    {
        if (filtered == null)
        {
            if (filteredIndex == childrenIndex) return;  // the new entry does does not require instantiating "filtered", because the list continues to be exactly 1-to-1
            int count = children.size () - 1;
            filtered = new ArrayList<Integer> (count);
            for (int i = 0; i < count; i++) filtered.add (i);
        }

        if (filteredIndex >= 0)
        {
            filtered.add (filteredIndex, childrenIndex);  // effectively duplicates the entry at filteredIndex
            if (shift)
            {
                int count = filtered.size ();
                for (int i = filteredIndex + 1; i < count; i++) filtered.set (i, filtered.get (i).intValue () + 1);  // Shift child indices up by one, to account for the new entry added ahead of them.
            }
        }
        else // filteredIndex == -1
        {
            // Don't add element to filtered, since it is invisible, but still ripple up the child indices.
            if (shift)
            {
                int count = filtered.size ();
                for (int i = 0; i < count; i++)
                {
                    int index = filtered.get (i).intValue ();
                    if (index >= childrenIndex) filtered.set (i, index + 1);
                }
            }
        }
     }

    @Override
    public void removeFiltered (int filteredIndex, boolean shift)
    {
        if (filtered == null)
        {
            int count = children.size ();
            filtered = new ArrayList<Integer> (count);
            for (int i = 0; i < count; i++) filtered.add (i);
        }
        filtered.remove (filteredIndex);
        if (shift)  // Shift child indices down by 1 to account for entry removed ahead of them.
        {
            int count = filtered.size ();
            for (int i = filteredIndex; i < count; i++)  filtered.set (i, filtered.get (i).intValue () - 1);
        }
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        if (isConnection) return iconConnection;
        else              return iconCompartment;
    }

    @Override
    public String getText (boolean expanded, boolean editing)
    {
        String key = toString ();  // This allows us to set editing text to "" for new objects, while showing key for old objects.
        if (expanded  ||  editing  ||  parentName.isEmpty ()) return key;
        return key + "  (" + parentName + ")";
    }

    @Override
    public float getFontScale ()
    {
        if (isRoot ()) return 2f;
        return 1;
    }

    @Override
    public int getFontStyle ()
    {
        return Font.BOLD;
    }

    /**
        Examines a fully-built tree to determine the value of the isConnection member.
    **/
    public void findConnections ()
    {
        isConnection = false;
        Enumeration i = children ();
        while (i.hasMoreElements ())
        {
            Object o = i.nextElement ();
            if      (o instanceof NodePart)     ((NodePart)     o).findConnections ();  // Recurses down to sub-parts, so everything gets examined.
            else if (o instanceof NodeVariable) ((NodeVariable) o).findConnections ();  // Checks if variable is a connection binding. If so, sets isBinding on the variable and also sets our isConnection member.
        }
    }

    public NodeBase resolveName (String name)
    {
        if (name.isEmpty ()) return this;
        String[] pieces = name.split ("\\.", 2);
        String ns = pieces[0];
        String nextName;
        if (pieces.length > 1) nextName = pieces[1];
        else                   nextName = "";

        NodePart parent = (NodePart) getParent ();
        if (ns.equals ("$up"))  // Don't bother with local checks if we know we are going up
        {
            if (parent == null) return null;
            return parent.resolveName (nextName);
        }

        Enumeration i = children ();
        while (i.hasMoreElements ())
        {
            Object o = i.nextElement ();
            if (o instanceof NodeVariable)
            {
                if (((NodeVariable) o).source.key ().equals (ns)) return null;  // could also return the actual NodeVariable, if it proved useful
            }
            else if (o instanceof NodePart)
            {
                NodePart p = (NodePart) o;
                if (p.source.key ().equals (ns)) return p.resolveName (nextName);
            }
        }

        if (parent == null) return null;
        return parent.resolveName (name);
    }

    @Override
    public NodeBase add (String type, JTree tree)
    {
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        if (tree.isCollapsed (new TreePath (getPath ()))  &&  model.getChildCount (this) > 0  &&  ! isRoot ())  // The node is deliberately closed to indicate user intent.
        {
            if (type.isEmpty ()) return ((NodeBase) getParent ()).add ("Part", tree);
            return ((NodeBase) getParent ()).add (type, tree);
        }

        NodeAnnotations a = null;
        NodeReferences  r = null;
        int variableIndex = -1;
        int subpartIndex  = -1;
        boolean found = false;
        int count = getChildCount ();  // unfiltered, so we can insert at the correct place in the underlying collection
        for (int i = 0; i < count; i++)
        {
            TreeNode t = getChildAt (i);
            if      (t instanceof NodeReferences)  r = (NodeReferences)  t;
            else if (t instanceof NodeAnnotations) a = (NodeAnnotations) t;
            else if (t instanceof NodePart)
            {
                if (! found) variableIndex = i;
                found = true;
                subpartIndex = i + 1;
            }
        }
        if (variableIndex < 0) variableIndex = count;
        if (subpartIndex  < 0) subpartIndex  = count;

        TreePath path = tree.getSelectionPath ();
        if (path != null)
        {
            NodeBase selected = (NodeBase) path.getLastPathComponent ();
            if (selected.getParent () == this)
            {
                // When we have a specific item selected, the user expects the new item to appear directly below it.
                int selectedIndex = getIndex (selected);  // unfiltered
                variableIndex = selectedIndex + 1;
                subpartIndex  = selectedIndex + 1;
            }
        }

        NodeBase result;
        if (type.equals ("Annotation"))
        {
            AddAnnotation aa = new AddAnnotation (this, 0);
            ModelEditPanel.instance.doManager.add (aa);  // aa will automagically insert a $metadata block if needed
            return aa.createdNode;
        }
        else if (type.equals ("Reference"))
        {
            AddReference ar = new AddReference (this, 0);
            ModelEditPanel.instance.doManager.add (ar);
            return ar.createdNode;
        }
        else if (type.equals ("Part"))
        {
            int suffix = 0;
            while (source.child ("p" + suffix) != null) suffix++;
            result = new NodePart ((MPart) source.set ("", "p" + suffix));
            result.setUserObject ("");
            model.insertNodeIntoUnfiltered (result, this, subpartIndex);
        }
        else  // treat all other requests as "Variable"
        {
            int suffix = 0;
            while (source.child ("x" + suffix) != null) suffix++;
            result = new NodeVariable ((MPart) source.set ("0", "x" + suffix));
            result.setUserObject ("");
            result.updateColumnWidths (getFontMetrics (tree));  // preempt initialization
            model.insertNodeIntoUnfiltered (result, this, variableIndex);
        }

        return result;
    }

    @Override
    public NodeBase addDnD (String key, JTree tree)
    {
        NodePart result = (NodePart) add ("Part", tree);
        result.source.set ("\"" + key + "\"", "$inherit");  // This brings in all the equations for the new sub-part.
        result.build ();
        result.findConnections ();
        ((FilteredTreeModel) tree.getModel ()).nodeStructureChanged (result);

        return result;
    }

    @Override
    public void applyEdit (JTree tree)
    {
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        String input = (String) getUserObject ();
        String[] pieces = input.split ("=", 2);
        String name = pieces[0].trim ();
        String oldKey = source.key ();
        if (name.equals (oldKey))
        {
            setUserObject ();
            model.nodeChanged (this);
            return;
        }

        if (isRoot ())  // Edits to root cause a rename of the document on disk
        {
            if (name.isEmpty ())
            {
                setUserObject ();
                model.nodeChanged (this);
                return;
            }

            String stem = name;
            int suffix = 0;
            MNode models = AppData.models;
            MNode existingDocument = models.child (name);
            while (existingDocument != null)
            {
                suffix++;
                name = stem + " " + suffix;
                existingDocument = models.child (name);
            }

            ModelEditPanel.instance.doManager.add (new ChangeDoc (oldKey, name));
            // MDir promises to maintain object identity during the move, so "source" is still valid.
            return;
        }

        if (input.isEmpty ())
        {
            delete (tree);
            return;
        }

        NodeBase parent = (NodeBase) getParent ();
        if (parent.child (name) != null)  // the name already exists, so reject rename
        {
            setUserObject ();
            model.nodeChanged (this);
            return;
        }

        // Move the subtree
        MPart       mparent   = source.getParent ();
        MPersistent docParent = mparent.getSource ();
        mparent.clear (oldKey, false);  // Undoes the override in the collated tree, but data remains in the top-level document.
        docParent.move (oldKey, name);

        // Update the displayed tree

        //   Handle any overridden structure
        MPart oldPart = (MPart) mparent.child (oldKey);
        if (oldPart != null)  // This node overrode some inherited values.
        {
            NodePart p = new NodePart (oldPart);
            model.insertNodeIntoUnfiltered (p, parent, parent.getIndex (this));
            p.build ();
            p.findConnections ();
            model.nodeStructureChanged (p);
        }

        //   Update the current node
        MPersistent newDocNode = (MPersistent) docParent.child (name);
        if (newDocNode == null)  // It could be null if we try to rename a purely inherited node (no overrides anywhere). TODO: better support for renames. Should revoke old subtree and make full copy at new location.
        {
            model.removeNodeFromParent (this);  // Because we just created a new node above for the supposedly exposed overridden part.
        }
        else
        {
            source = mparent.update (newDocNode);  // re-collate this node to weave in any included part
            build ();
            findConnections ();
            model.nodeStructureChanged (this);
        }
    }

    @Override
    public void delete (JTree tree)
    {
        if (! source.isFromTopDocument ()) return;  // This should be true of root, as well as any other node we might try to delete.
        ModelEditPanel mep = ModelEditPanel.instance;
        if (isRoot ())
        {
            mep.doManager.add (new DeleteDoc ((MDoc) source.getSource ()));
        }
        else
        {
            String key = source.key ();
            FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
            MPart mparent = source.getParent ();
            mparent.clear (key);
            if (mparent.child (key) == null)  // Node is fully deleted
            {
                model.removeNodeFromParent (this);
            }
            else  // Just exposed an overridden node
            {
                build ();
                findConnections ();
                filter (model.filterLevel);
                if (visible (model.filterLevel)) model.nodeStructureChanged (this);
                else                             ((NodePart) getParent ()).hide (this, model, true);
            }
        }
    }
}

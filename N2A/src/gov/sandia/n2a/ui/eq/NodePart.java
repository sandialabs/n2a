/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq;

import java.awt.Font;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.umf.platform.db.AppData;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.db.MPersistent;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

public class NodePart extends NodeBase
{
    protected static ImageIcon iconCompartment = ImageUtil.getImage ("comp.gif");
    protected static ImageIcon iconConnection  = ImageUtil.getImage ("connection.png");

    protected boolean isConnection;
    protected String parentName = "";

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
        for (MNode c : source)  // output everything else
        {
            if (sorted.contains (c.key ())) continue;
            buildTriage ((MPart) c);
        }
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
    public Icon getIcon (boolean expanded)
    {
        if (isConnection) return iconConnection;
        else              return iconCompartment;
    }

    @Override
    public String getText (boolean expanded)
    {
        String key = toString ();  // This allows us to set editing text to "" for new objects, while showing key for old objects.
        if (expanded  ||  parentName.isEmpty ()) return key;
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
        if (tree.isCollapsed (new TreePath (getPath ()))  &&  getChildCount () > 0  &&  ! isRoot ())  // The node is deliberately closed to indicate user intent.
        {
            if (type.isEmpty ()) return ((NodeBase) getParent ()).add ("Part", tree);
            return ((NodeBase) getParent ()).add (type, tree);
        }

        NodeAnnotations a = null;
        NodeReferences  r = null;
        int lastSubpart  = -1;
        int lastVariable = -1;
        for (int i = 0; i < getChildCount (); i++)
        {
            TreeNode t = getChildAt (i);
            if      (t instanceof NodeReferences)  r = (NodeReferences)  t;
            else if (t instanceof NodeAnnotations) a = (NodeAnnotations) t;
            else if (t instanceof NodePart)        lastSubpart  = i;
            else                                   lastVariable = i;
        }
        if (lastSubpart  < 0) lastSubpart  = getChildCount () - 1;
        if (lastVariable < 0) lastVariable = getChildCount () - 1;

        TreePath path = tree.getSelectionPath ();
        if (path != null)
        {
            NodeBase selected = (NodeBase) path.getLastPathComponent ();
            if (isNodeChild (selected))
            {
                // When we have a specific item selected, the user expects the new item to appear directly below it.
                int selectedIndex = getIndex (selected);
                lastSubpart  = selectedIndex;
                lastVariable = selectedIndex;
            }
        }

        NodeBase result;
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();
        if (type.equals ("Annotation"))
        {
            if (a == null)
            {
                a = new NodeAnnotations ((MPart) source.set ("", "$metadata"));
                model.insertNodeInto (a, this, 0);
            }
            return a.add (type, tree);
        }
        else if (type.equals ("Reference"))
        {
            if (r == null)
            {
                r = new NodeReferences ((MPart) source.set ("", "$reference"));
                model.insertNodeInto (r, this, 0);
            }
            return r.add (type, tree);
        }
        else if (type.equals ("Part"))
        {
            int suffix = 0;
            while (source.child ("p" + suffix) != null) suffix++;
            result = new NodePart ((MPart) source.set ("", "p" + suffix));
            result.setUserObject ("");
            model.insertNodeInto (result, this, lastSubpart + 1);
        }
        else  // treat all other requests as "Variable"
        {
            int suffix = 0;
            while (source.child ("x" + suffix) != null) suffix++;
            result = new NodeVariable ((MPart) source.set ("0", "x" + suffix));
            result.setUserObject ("");
            result.updateColumnWidths (getFontMetrics (tree));  // preempt initialization
            model.insertNodeInto (result, this, lastVariable + 1);
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
        ((DefaultTreeModel) tree.getModel ()).nodeStructureChanged (result);

        return result;
    }

    @Override
    public void applyEdit (JTree tree)
    {
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();

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

            MNode dir = source.getSource ().getParent ();
            dir.move (oldKey, name);  // MDir promises to maintain object identity during the move, so our source reference is still valid.
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
            oldPart.expand ();
            NodePart p = new NodePart (oldPart);
            model.insertNodeInto (p, parent, parent.getIndex (this));
            p.build ();
            p.findConnections ();
            model.nodeStructureChanged (p);
        }

        //   Update the current node
        MPersistent newDocNode = (MPersistent) docParent.child (name);
        if (newDocNode == null)  // It could be null if we try to rename a non-overridden node. TODO: better support for renames. Should revoke old subtree and make full copy at new location.
        {
            model.removeNodeFromParent (this);
        }
        else
        {
            MPart newPart = mparent.update (newDocNode);  // re-collate this node to weave in any included part
            source = newPart;
            build ();
            findConnections ();
            model.nodeStructureChanged (this);
        }
    }

    @Override
    public void delete (JTree tree)
    {
        if (! source.isFromTopDocument ()) return;  // This should be true of root, as well as any other node we might try to delete.
        if (isRoot ()) return;

        String key = source.key ();
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();
        MPart mparent = source.getParent ();
        mparent.clear (key);
        if (mparent.child (key) == null) model.removeNodeFromParent (this);
        else reloadTree (tree);  // See comments about clearing an include in applyEdit()
    }
}

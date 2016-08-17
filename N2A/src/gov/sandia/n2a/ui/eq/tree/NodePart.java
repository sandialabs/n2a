/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

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
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

public class NodePart extends NodeBase
{
    protected static ImageIcon iconCompartment = ImageUtil.getImage ("comp.gif");
    protected static ImageIcon iconConnection  = ImageUtil.getImage ("connection.png");

    protected boolean isConnection;

    public NodePart ()
    {
    }

    public NodePart (MPart source)
    {
        this.source = source;
    }

    public void setUserObject ()
    {
        String key = source.key ();
        if (isRoot ())            setUserObject (key);
        else
        {
            String value = source.get ();
            if (value.isEmpty ()) setUserObject (key);
            else                  setUserObject (key + "=" + value);
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
        if (key.equals ("$metadata"))
        {
            NodeAnnotations a = new NodeAnnotations (line);
            add (a);
            a.build ();
            return;
        }
        else if (key.equals ("$reference"))
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

        if (line.key ().equals ("$inherit"))
        {
            NodeInherit i = new NodeInherit (line);
            add (i);
        }
        else
        {
            NodeVariable v = new NodeVariable (line);
            add (v);
            v.build ();
        }
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
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();

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
            if (! isRoot ()) return null;  // TODO: temporary hack until we fully handle adding parts at lower levels of tree

            int suffix = 0;
            while (source.child ("p" + suffix) != null) suffix++;
            NodePart child = new NodePart ((MPart) source.set ("$include(\"\")", "p" + suffix));
            child.setUserObject ();
            model.insertNodeInto (child, this, lastSubpart + 1);
            return child;
        }
        else  // treat all other requests as "Variable"
        {
            int suffix = 0;
            while (source.child ("x" + suffix) != null) suffix++;
            NodeBase child = new NodeVariable ((MPart) source.set ("0", "x" + suffix));
            model.insertNodeInto (child, this, lastVariable + 1);
            return child;
        }
    }

    @Override
    public NodeBase addDnD (String key, JTree tree)
    {
        if (! isRoot ()) return ((NodeBase) getParent ()).addDnD (key, tree);

        NodePart result = (NodePart) add ("Part", tree);
        result.source.set ("$include(\"" + key + "\")");
        result.source.update ();  // re-collate this node to weave in any included part
        result.build ();
        ((DefaultTreeModel) tree.getModel ()).nodeStructureChanged (result);

        return result;
    }

    @Override
    public boolean allowEdit ()
    {
        if (isRoot ()) return true;
        if (((DefaultMutableTreeNode) getParent ()).isRoot ()) return true;
        return false;
    }

    @Override
    public void applyEdit (JTree tree)
    {
        String input = (String) getUserObject ();
        String oldKey = source.key ();

        if (isRoot ())  // Edits to root cause a rename of the document on disk
        {
            if (input.equals (oldKey)) return;
            if (input.contains ("=")  ||  input.isEmpty ())
            {
                setUserObject (oldKey);
                return;
            }

            String stem = input;
            int suffix = 0;
            MNode models = AppData.getInstance ().models;
            MNode existingDocument = models.child (input);
            while (existingDocument != null)
            {
                suffix++;
                input = stem + " " + suffix;
                existingDocument = models.child (input);
            }

            MNode dir = source.getSource ().getParent ();
            dir.move (oldKey, input);  // MDir promises to maintain object identity during the move, so our source reference is still valid.
            return;
        }

        if (input.isEmpty ())
        {
            delete (tree);
            return;
        }

        String[] parts = input.split ("=", 2);
        String name = parts[0];
        String value;
        if (parts.length > 1) value = parts[1];
        else                  value = "";

        DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();
        String oldValue = source.get ();
        NodeBase parent = (NodeBase) getParent ();
        boolean renamed = ! name.equals (oldKey)  &&  ! name.isEmpty ()  &&  parent.child (name) == null;
        if (! renamed)
        {
            if (value.equals (oldValue))  // nothing to do
            {
                if (! name.equals (oldKey))  // except maybe undo a rejected name change
                {
                    setUserObject ();
                    model.nodeChanged (this);
                }
                return;
            }
            name = oldKey;  // for use below
        }

        // Save changes and move the subtree
        source.set (value);  // Save the new value, which could be the same as the old value, or even a reset to the non-overridden state.
        MPart       mparent   = source.getParent ();
        MPersistent docParent = mparent.getSource ();
        mparent.clear (oldKey, false);  // Undoes the override in the collated tree, but data remains in the top-level document.
        if (renamed) docParent.move (oldKey, name);

        // Update the displayed tree

        //   Handle any overridden structure
        MPart oldPart = (MPart) mparent.child (oldKey);
        if (oldPart != null)  // This node overrode some inherited values.
        {
            if (oldValue.contains ("$include"))  // the usual case
            {
                // We need to rebuild the entire tree, because the subtree built from the $include
                // is indistinguishable from inherited equations. Thus, there is no simple way
                // to back them out.
                reloadTree (tree);
                return;
            }
            if (renamed)  // Make a new tree node to hold the variable left behind.
            {
                if (oldPart.isPart ())
                {
                    NodePart p = new NodePart (oldPart);
                    model.insertNodeInto (p, parent, parent.getIndex (this));
                    p.build ();
                    model.nodeStructureChanged (p);
                }
                else
                {
                    NodeVariable v = new NodeVariable (oldPart);
                    model.insertNodeInto (v, parent, parent.getIndex (this));
                    v.build ();
                    model.nodeStructureChanged (v);
                }
            }
        }

        //   Update the current node
        MPersistent newDocNode = (MPersistent) docParent.child (name);
        if (newDocNode == null)  // It could be null if the change the user made reverted the old node back to a non-overridden state.
        {
            model.removeNodeFromParent (this);
        }
        else
        {
            MPart newPart = mparent.update (newDocNode);  // re-collate this node to weave in any included part
            if (newPart.isPart ())
            {
                source = newPart;
                build ();
                model.nodeStructureChanged (this);
            }
            else  // replace ourselves with a variable
            {
                int position = parent.getIndex (this);
                model.removeNodeFromParent (this);
                NodeVariable v = new NodeVariable (newPart);
                model.insertNodeInto (v, parent, position);
                v.build ();
                model.nodeStructureChanged (v);
            }
        }
    }

    @Override
    public void delete (JTree tree)
    {
        if (! source.isFromTopDocument ()) return;  // This should be true of root, as well as any other node we might try to delete.

        String key = source.key ();
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();
        if (isRoot ())
        {
            MNode dir = source.getSource ().getParent ();
            dir.clear (key);
            model.setRoot (null);
            return;
        }

        MPart mparent = source.getParent ();
        mparent.clear (key);
        if (mparent.child (key) == null) model.removeNodeFromParent (this);
        else reloadTree (tree);  // See comments about clearing an include in applyEdit()
    }
}

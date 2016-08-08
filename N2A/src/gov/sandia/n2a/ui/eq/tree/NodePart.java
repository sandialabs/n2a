/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.ui.eq.EquationTreePanel;
import gov.sandia.umf.platform.db.AppData;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.db.MPersistent;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.ImageIcon;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

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

    public void build ()
    {
        String key  = source.key ();
        String name = source.getOrDefault (key, "$metadata", "name");
        if (isRoot ())            setUserObject (name);
        else
        {
            String value = source.get ();
            if (value.isEmpty ()) setUserObject (key);
            else                  setUserObject (key + "=" + value);
        }
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
    public void prepareRenderer (DefaultTreeCellRenderer renderer, boolean selected, boolean expanded, boolean hasFocus)
    {
        if (isConnection) renderer.setIcon (iconConnection);
        else              renderer.setIcon (iconCompartment);
        setFont (renderer, isRoot (), false);
    }

    @Override
    public NodeBase add (String type, EquationTreePanel panel)
    {
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
        if (lastSubpart  < 0) lastSubpart  = getChildCount ();
        if (lastVariable < 0) lastVariable = getChildCount ();

        if (type.equals ("Annotation"))
        {
            if (a == null)
            {
                a = new NodeAnnotations ((MPart) source.set ("", "$metadata"));
                panel.model.insertNodeInto (a, this, 0);
            }
            return a.add (type, panel);
        }
        else if (type.equals ("Reference"))
        {
            if (r == null)
            {
                r = new NodeReferences ((MPart) source.set ("", "$reference"));
                panel.model.insertNodeInto (r, this, 0);
            }
            return r.add (type, panel);
        }
        else if (type.equals ("Part"))
        {
            int suffix = 0;
            while (source.child ("p" + suffix) != null) suffix++;
            NodeBase child = new NodePart ((MPart) source.set ("$include(\"\")", "p" + suffix));
            panel.model.insertNodeInto (child, this, lastSubpart);
            return child;
        }
        else  // treat all other requests as "Variable"
        {
            int suffix = 0;
            while (source.child ("x" + suffix) != null) suffix++;
            NodeBase child = new NodeVariable ((MPart) source.set ("0", "x" + suffix));
            panel.model.insertNodeInto (child, this, lastVariable);
            return child;
        }
    }

    @Override
    public boolean allowEdit ()
    {
        if (isRoot ()) return true;
        if (((DefaultMutableTreeNode) getParent ()).isRoot ()) return true;
        return false;
    }

    @Override
    public void applyEdit (DefaultTreeModel model)
    {
        String input = (String) getUserObject ();
        String oldKey = source.key ();

        if (isRoot ())  // Edits root to rename the document on disk
        {
            if (input.equals (oldKey)) return;
            if (input.contains ("="))
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

            MNode dir = source.getParent ();
            dir.move (oldKey, input);  // MDir promises to maintain object identity during the move, so our source reference is still valid.
            return;
        }

        String[] parts = input.split ("=", 2);
        String name = parts[0];
        String value;
        if (parts.length > 1) value = parts[1];
        else                  value = "";

        NodeBase existingPart = null;
        NodeBase parent = (NodeBase) getParent ();
        if (! name.equals (oldKey)) existingPart = parent.child (name);

        if (name.equals (oldKey)  ||  existingPart != null)  // No name change, or name change not permitted.
        {
            String oldValue = source.get ();
            if (! value.equals (oldValue))
            {
                source.set (value);
                source.updateInclude ();
                build ();
                model.nodeStructureChanged (this);
            }

            if (existingPart != null)  // Necessary to change displayed value
            {
                setUserObject (oldKey + "=" + value);
                model.nodeChanged (this);
            }
        }
        else  // The name was changed. Move the whole tree under us to a new location. This may also expose an overridden variable.
        {
            // Inject the changed equation into the underlying data first, then move and rebuild the displayed nodes as necessary.
            TreeMap<String,MNode> equations = new TreeMap<String,MNode> ();
            for (MNode n : source)
            {
                String key = n.key ();
                if (key.startsWith ("@")) equations.put (key.substring (1), n);
            }
            if (equations.size () == 0)
            {
                source.set (value);
            }
            else
            {
                Variable.ParsedValue pieces = new Variable.ParsedValue (value);
                source.set (pieces.combiner);

                MNode e = equations.get (pieces.conditional);
                if (e == null) source.set (pieces.expression, "@" + pieces.conditional);
                else                e.set (pieces.expression);
            }

            // Change ourselves into the new key=value pair
            MPart p = source.getParent ();
            p.move (oldKey, name);
            MPart newPart = (MPart) p.child (name);
            if (p.child (oldKey) == null)
            {
                // We were not associated with an override, so we can re-use this tree node.
                source = newPart;
            }
            else
            {
                // Make a new tree node, and leave this one to present the non-overridden value.
                // Note that our source is still set to the old part.
                NodeVariable v = new NodeVariable (newPart);
                model.insertNodeInto (v, parent, parent.getIndex (this));
                v.build ();
                model.nodeStructureChanged (v);
            }
            build ();
            model.nodeStructureChanged (this);
        }
    }
}

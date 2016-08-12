/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

import java.util.Enumeration;
import java.util.TreeMap;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;

public class NodeEquation extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("equation.png");

    public NodeEquation (MPart source)
    {
        this.source = source;
        setUserObject ();
    }

    public void setUserObject ()
    {
        String key = source.key ();
        if (key.equals ("@")) setUserObject (source.get ());
        else                  setUserObject (source.get () + key);  // key should start with "@"
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        return icon;
    }

    @Override
    public NodeBase add (String type, JTree tree)
    {
        if (type.isEmpty ()) type = "Equation";
        return ((NodeBase) getParent ()).add (type, tree);
    }

    @Override
    public void applyEdit (JTree tree)
    {
        String input = (String) getUserObject ();
        if (input.isEmpty ())
        {
            delete (tree);
            return;
        }

        Variable.ParsedValue pieces = new Variable.ParsedValue (input);
        String conditional = "@" + pieces.conditional;  // ParsedValue removes the @

        NodeBase existingEquation = null;
        String oldKey = source.key ();
        NodeVariable parent = (NodeVariable) getParent ();
        if (! conditional.equals (oldKey)) existingEquation = parent.child (conditional);

        DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();
        if (conditional.equals (oldKey))  // Condition is the same
        {
            source.set (pieces.expression);
        }
        else if (existingEquation != null)  // Condition already exists, so no change allowed
        {
            source.set (pieces.expression);
            setUserObject ();
            model.nodeChanged (this);
        }
        else  // The name was changed.
        {
            MPart p = source.getParent ();
            MPart newPart = (MPart) p.set (pieces.expression, conditional);
            p.clear (oldKey);
            if (p.child (oldKey) == null) source = newPart;  // We were not associated with an override, so we can re-use this tree node.
            else model.insertNodeInto (new NodeEquation (newPart), parent, parent.getChildCount ());  // Make a new tree node, and leave this one to present the non-overridden value.
        }

        // The fact that we are modifying an equation indicates that the main variable will display only a combiner.
        // We always override it.
        if (! pieces.combiner.isEmpty ())
        {
            if (! parent.source.get ().equals (pieces.combiner))
            {
                parent.source.set (pieces.combiner);
                parent.setUserObject (parent.source.key () + "=" + parent.source.get ());
                model.nodeChanged (parent);
            }
            setUserObject ();
            model.nodeChanged (this);
        }
    }

    @Override
    public void delete (JTree tree)
    {
        if (! source.isFromTopDocument ()) return;

        DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();
        MPart mparent = source.getParent ();
        String key = source.key ();
        mparent.clear (key);
        if (mparent.child (key) == null)
        {
            NodeVariable variable = (NodeVariable) getParent ();
            model.removeNodeFromParent (this);

            // If we are down to only 1 equation, then fold it back into a single-line variable.
            TreeMap<String,NodeEquation> equations = new TreeMap<String,NodeEquation> ();
            Enumeration i = variable.children ();
            while (i.hasMoreElements ())
            {
                Object o = i.nextElement ();
                if (o instanceof NodeEquation)
                {
                    NodeEquation e = (NodeEquation) o;
                    equations.put (e.source.key ().substring (1), e);
                }
            }
            if (equations.size () == 1)
            {
                NodeBase e = equations.firstEntry ().getValue ();
                String ekey = e.source.key ();
                variable.source.clear (ekey);
                if (ekey.equals ("@")) variable.source.set (variable.source.get () + e.source.get ());
                else                   variable.source.set (variable.source.get () + e.source.get () + ekey);
                variable.setUserObject (variable.source.key () + "=" + variable.source.get ());
                model.removeNodeFromParent (e);
                model.nodeChanged (variable);
            }
        }
        else
        {
            setUserObject ();
            model.nodeChanged (this);
        }
    }
}

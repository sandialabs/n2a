/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.ui.eq.EquationTreePanel;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.ImageIcon;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

public class NodePart extends NodeBase
{
    protected static ImageIcon iconCompartment = ImageUtil.getImage ("comp.gif");
    protected static ImageIcon iconConnection  = ImageUtil.getImage ("connection.png");

    public EquationSet part;

    public NodePart ()
    {
    }

    public NodePart (EquationSet part)
    {
        this.part = part;
    }

    @Override
    public void prepareRenderer (DefaultTreeCellRenderer renderer, boolean selected, boolean expanded, boolean hasFocus)
    {
        if (part != null  &&  part.connectionBindings != null) renderer.setIcon (iconConnection);
        else                                                   renderer.setIcon (iconCompartment);
        setFont (renderer, isRoot (), false);
    }

    @Override
    public void add (String type, EquationTreePanel panel)
    {
        NodeAnnotations a = null;
        NodeReferences  r = null;
        int indexOfFirstSubpart = 0;
        Enumeration e = children ();
        boolean partFound = false;
        while (e.hasMoreElements ())
        {
            Object o = e.nextElement ();
            if      (o instanceof NodeReferences)  r = (NodeReferences)  o;
            else if (o instanceof NodeAnnotations) a = (NodeAnnotations) o;
            else if (o instanceof NodePart)        partFound = true;
            if (! partFound) indexOfFirstSubpart++;
        }

        NodeBase editMe;
        if (type.equals ("Annotation"))
        {
            if (a == null)
            {
                a = new NodeAnnotations ();
                panel.model.insertNodeInto (a, this, 0);
            }
            editMe = new NodeAnnotation ("", "");
            panel.model.insertNodeInto (editMe, a, a.getChildCount ());
        }
        else if (type.equals ("Reference"))
        {
            if (r == null)
            {
                r = new NodeReferences ();
                panel.model.insertNodeInto (r, this, 0);
            }
            editMe = new NodeReference ("", "");
            panel.model.insertNodeInto (editMe, r, r.getChildCount ());
        }
        else  // treat all other requests as "Variable"
        {
            int suffix = 0;
            Variable v = new Variable ("", 0);
            while (true)
            {
                v.name = "x" + suffix;
                if (part.find (v) == null) break;
                suffix++;
            }
            EquationEntry ee = new EquationEntry (v, "");
            v.add (ee);
            ee.expression = new Constant (new Scalar (0));
            editMe = new NodeVariable (v);
            // Note: If we change how single-line variables behave, we may also need to call NodeVariable.build() here.
            editMe.setUserObject ("");
            panel.model.insertNodeInto (editMe, this, indexOfFirstSubpart);
        }
        // TODO: How to add sub-parts, either as $includes or direct sub-namespace?

        TreePath path = new TreePath (editMe.getPath ());
        panel.tree.scrollPathToVisible (path);
        panel.tree.startEditingAtPath (path);
    }

    public void build (DefaultTreeModel model)
    {
        if (isRoot ()) setUserObject (part.source.getOrDefault (part.name, "$metadata", "name"));
        else           setUserObject (part.name + " = $include(\"" + part.source.getOrDefault (part.name, "$metadata", "name") + "\")");
        removeAllChildren ();

        // TODO: add $inherit() lines from original MNode, because they are dropped (processed away) by EquationSet

        Set<Entry<String,String>> metadata = part.getMetadata ();
        if (metadata.size () > 0)
        {
            DefaultMutableTreeNode dollarnode = new NodeAnnotations ();
            model.insertNodeInto (dollarnode, this, getChildCount ());
            for (Entry<String,String> m : metadata)
            {
                model.insertNodeInto (new NodeAnnotation (m.getKey (), m.getValue ()), dollarnode, dollarnode.getChildCount ());
            }
        }

        // Note that references are not collated like metadata or equations. Only local references appear.
        // TODO: collate references?
        MNode references = part.source.child ("$reference");
        if (references != null  &&  references.length () > 0)
        {
            DefaultMutableTreeNode dollarnode = new NodeReferences ();
            model.insertNodeInto (dollarnode, this, getChildCount ());
            for (MNode r : references)
            {
                dollarnode.add (new NodeReference (r.key (), r.get ()));
            }
        }

        if (part.connectionBindings != null)
        {
            for (Entry<String,EquationSet> a : part.connectionBindings.entrySet ())
            {
                model.insertNodeInto (new NodeBinding (a.getKey (), a.getValue ().name), this, getChildCount ());
            }
        }

        Set<Variable> unsorted = new TreeSet<Variable> (part.variables);
        String[] keys = part.getNamedValue ("gui.order").split (",");  // comma-separated list
        for (int i = 0; i < keys.length; i++)
        {
            Variable query = new Variable (keys[i]);
            Variable result = part.find (query);
            if (result != null)
            {
                unsorted.remove (result);
                NodeVariable vnode = new NodeVariable (result);
                model.insertNodeInto (vnode, this, getChildCount ());
                vnode.build (model);
            }
        }
        for (Variable v : unsorted)  // output everything else
        {
            NodeVariable vnode = new NodeVariable (v);
            model.insertNodeInto (vnode, this, getChildCount ());
            vnode.build (model);
        }

        for (EquationSet p : part.parts)
        {
            NodePart pnode = new NodePart (p);
            model.insertNodeInto (pnode, this, getChildCount ());
            pnode.build (model);
        }
    }
}

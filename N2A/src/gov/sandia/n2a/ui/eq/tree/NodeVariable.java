/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

import java.util.Map.Entry;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.ui.eq.EquationTreePanel;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.ImageIcon;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

public class NodeVariable extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("delta.png");

    public Variable variable;

    public NodeVariable (Variable variable)
    {
        this.variable = variable;
    }

    @Override
    public void add (String type, EquationTreePanel panel)
    {
        NodeBase editMe;
        if (type.isEmpty ()  ||  type.equals ("Equation"))
        {
            int count = variable.equations.size ();
            if (count == 0)
            {
                editMe = this;
            }
            else
            {
                if (count == 1)  // We are about to switch from single-line form to multi-conditional, so make a tree node for the existing equation.
                {
                    setUserObject (variable.nameString () + "=" + variable.combinerString ());
                    panel.model.insertNodeInto (new NodeEquation (variable.equations.first ()), this, 0);
                }
                EquationEntry ee = new EquationEntry (variable, "");
                while (true)
                {
                    ee.expression = new Constant (new Scalar (count));
                    ee.conditional = new Constant (new Scalar (count));
                    ee.ifString = ee.conditional.render ();
                    if (variable.find (ee) == null) break;
                    count++;
                }
                ee.ifString = ee.conditional.render ();
                variable.add (ee);
                editMe = new NodeEquation (ee);
                editMe.setUserObject ("");
                panel.model.insertNodeInto (editMe, this, 0);
            }
        }
        else if (type.equals ("Annotation"))
        {
            editMe = new NodeAnnotation ("", "");
            int index = variable.equations.size ();
            if (index < 2) index = 0;
            index += variable.metadata.size ();
            panel.model.insertNodeInto (editMe, this, index);
        }
        else if (type.equals ("Reference"))
        {
            editMe = new NodeReference ("", "");
            panel.model.insertNodeInto (editMe, this, getChildCount ());
        }
        else
        {
            ((NodeBase) getParent ()).add (type, panel);  // refer all other requests up the tree
            return;
        }

        TreePath path = new TreePath (editMe.getPath ());
        panel.tree.scrollPathToVisible (path);
        panel.tree.startEditingAtPath (path);
    }

    @Override
    public void prepareRenderer (DefaultTreeCellRenderer renderer, boolean selected, boolean expanded, boolean hasFocus)
    {
        renderer.setIcon (icon);
        setFont (renderer, false, false);
        // TODO: set color based on override status
    }

    public void parseEditedString (String input)
    {
    }

    public void build (DefaultTreeModel model)
    {
        String label = variable.nameString ();
        if (variable.equations.size () > 0)
        {
            label = label + "=" + variable.combinerString ();
            if (variable.equations.size () == 1) label = label + variable.equations.first ().toString ();  // Otherwise, we use child nodes to display the equations.
        }
        setUserObject (label);

        removeAllChildren ();

        if (variable.equations.size () > 1)  // for a single equation, the variable line itself suffices
        {
            for (EquationEntry e : variable.equations)
            {
                model.insertNodeInto (new NodeEquation (e), this, getChildCount ());
            }
        }
        for (Entry<String,String> m : variable.getMetadata ())
        {
            model.insertNodeInto (new NodeAnnotation (m.getKey (), m.getValue ()), this, getChildCount ());
        }
        MNode references = variable.source.child ("$reference");  // TODO: should have collated references in the Variable object
        if (references != null  &&  references.length () > 0)
        {
            for (MNode r : references)
            {
                model.insertNodeInto (new NodeReference (r.key (), r.get ()), this, getChildCount ());
            }
        }
    }
}

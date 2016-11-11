/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
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
    protected List<Integer> columnWidths;

    public NodeEquation (MPart source)
    {
        this.source = source;
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        return icon;
    }

    @Override
    public boolean needsInitTabs ()
    {
        return columnWidths == null;
    }

    @Override
    public void updateColumnWidths (FontMetrics fm)
    {
        if (columnWidths == null)
        {
            columnWidths = new ArrayList<Integer> (1);
            columnWidths.add (0);
        }
        columnWidths.set (0, fm.stringWidth (source.get () + " "));
    }

    @Override
    public List<Integer> getColumnWidths ()
    {
        return columnWidths;
    }

    @Override
    public void applyTabStops (List<Integer> tabs, FontMetrics fm)
    {
        String key    = source.key ();
        String result = source.get ();
        if (! key.equals ("@"))  // Means that there is more than blank for a condition. In all cases, condition starts with "@".
        {
            int offset = tabs.get (0).intValue () - fm.stringWidth (result);
            result = result + pad (offset, fm) + "@ " + key.substring (1);
        }
        setUserObject (result);
    }

    @Override
    public NodeBase add (String type, JTree tree)
    {
        if (type.isEmpty ()) type = "Equation";
        return ((NodeBase) getParent ()).add (type, tree);
    }

    @Override
    public boolean allowEdit ()
    {
        if (! getUserObject ().toString ().isEmpty ())  // An empty user object indicates a newly created node, which we want to edit as a blank.
        {
            String condition  = source.key ();
            String expression = source.get ();
            if (condition.equals ("@")) setUserObject (expression);
            else                        setUserObject (expression + condition);
        }
        return true;
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
        FontMetrics fm = getFontMetrics (tree);
        if (conditional.equals (oldKey)  ||  existingEquation != null)  // Condition is the same, or not allowed to change
        {
            source.set (pieces.expression);
        }
        else  // The condition has changed.
        {
            MPart p = source.getParent ();
            MPart newPart = (MPart) p.set (pieces.expression, conditional);
            p.clear (oldKey);
            if (p.child (oldKey) == null)  // We were not associated with an override, so we can re-use this tree node.
            {
                source = newPart;
            }
            else  // Make a new tree node, and leave this one to present the newly-exposed non-overridden value.
            {
                NodeEquation newEquation = new NodeEquation (newPart);
                model.insertNodeInto (newEquation, parent, parent.getChildCount ());
                newEquation.updateColumnWidths (fm);
            }
        }

        // The fact that we are modifying an equation indicates that the main variable will display only a combiner.
        // We always override it.
        if (! pieces.combiner.isEmpty ())
        {
            if (! parent.source.get ().equals (pieces.combiner))
            {
                parent.source.set (pieces.combiner);
                parent.updateColumnWidths (fm);
                NodeBase grandparent = (NodeBase) parent.getParent ();
                grandparent.updateTabStops (fm);
                grandparent.nodesChanged (model);
            }
        }

        updateColumnWidths (fm);
        parent.updateTabStops (fm);
        parent.nodesChanged (model);
    }

    @Override
    public void delete (JTree tree)
    {
        if (! source.isFromTopDocument ()) return;

        DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();
        FontMetrics fm = getFontMetrics (tree);

        NodeVariable parent = (NodeVariable) getParent ();
        MPart mparent = source.getParent ();
        String key = source.key ();
        mparent.clear (key);
        if (mparent.child (key) == null)  // There is no overridden value, so this node goes away completely.
        {
            model.removeNodeFromParent (this);

            // If we are down to only 1 equation, then fold it back into a single-line variable.
            NodeEquation lastEquation = null;
            int equationCount = 0;
            Enumeration i = parent.children ();
            while (i.hasMoreElements ())
            {
                Object o = i.nextElement ();
                if (o instanceof NodeEquation)
                {
                    equationCount++;
                    lastEquation = (NodeEquation) o;
                }
            }
            if (equationCount == 1)
            {
                String lastCondition  = lastEquation.source.key ();
                String lastExpression = lastEquation.source.get ();
                parent.source.clear (lastCondition);
                if (lastCondition.equals ("@")) parent.source.set (parent.source.get () + lastExpression);
                else                            parent.source.set (parent.source.get () + lastExpression + lastCondition);
                model.removeNodeFromParent (lastEquation);
                parent.updateColumnWidths (fm);
                NodeBase grandparent = (NodeBase) parent.getParent ();
                grandparent.updateTabStops (fm);
                grandparent.nodesChanged (model);
            }
            else if (equationCount == 0)
            {
                return;  // avoid falling through to parent update below
            }
        }
        else  // Just exposed an overridden value, so update display.
        {
            updateColumnWidths (fm);
        }

        parent.updateTabStops (fm);
        parent.nodesChanged (model);
    }
}

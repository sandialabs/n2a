/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.awt.FontMetrics;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;

import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeEquation;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class AddEquation extends Undoable
{
    protected List<String>      path;  // to variable node
    protected int               equationCount;  // before adding this equation
    protected int               index; // where to insert among siblings
    protected String            name;  // includes the leading @
    protected String            value;
    public    NodeBase          createdNode;  ///< Used by caller to initiate editing. Only valid immediately after call to redo().

    public AddEquation (NodeVariable parent, int index)
    {
        path = parent.getKeyPath ();
        this.index = index;

        // Select a unique name

        TreeSet<String> equations = new TreeSet<String> ();
        for (MNode n : parent.source)
        {
            String key = n.key ();
            if (key.startsWith ("@")) equations.add (key.substring (1));
        }
        equationCount = equations.size ();
        if (equationCount == 0)
        {
            Variable.ParsedValue pieces = new Variable.ParsedValue (parent.source.get ());
            equations.add (pieces.conditional);
        }

        int suffix = equations.size ();
        while (true)
        {
            name = String.valueOf (suffix);
            if (! equations.contains (name)) break;
            suffix++;
        }
        name = "@" + name;
    }

    public void undo ()
    {
        super.undo ();
        destroy (path, name);
    }

    public static void destroy (List<String> path, String name)
    {
        // Retrieve created node
        NodeBase parent = locateNode (path);
        if (parent == null) throw new CannotUndoException ();
        NodeBase createdNode = parent.child (name);

        ModelEditPanel mep = ModelEditPanel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        FontMetrics fm = createdNode.getFontMetrics (tree);

        TreeNode[] createdPath = createdNode.getPath ();
        int filteredIndex = parent.getIndexFiltered (createdNode);

        MPart mparent = parent.source;
        mparent.clear (name);
        if (mparent.child (name) == null)  // There is no overridden value, so this node goes away completely.
        {
            model.removeNodeFromParent (createdNode);

            // If we are down to only 1 equation, then fold it back into a single-line variable.
            NodeEquation lastEquation = null;
            int equationCount = 0;
            Enumeration i = parent.children ();  // unfiltered
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

                // Update tabs among this variable's siblings
                parent.updateColumnWidths (fm);
                NodeBase grandparent = (NodeBase) parent.getParent ();
                grandparent.updateTabStops (fm);
                grandparent.allNodesChanged (model);
            }
        }
        else  // Just exposed an overridden value, so update display.
        {
            createdNode.updateColumnWidths (fm);
        }

        parent.updateTabStops (fm);
        parent.allNodesChanged (model);
        mep.panelEquations.updateAfterDelete (createdPath, filteredIndex);
    }

    public void redo ()
    {
        super.redo ();
        createdNode = create (path, equationCount, index, name, value);
    }

    public static NodeBase create (List<String> path, int equationCount, int index, String name, String value)
    {
        NodeBase parent = locateNode (path);
        if (parent == null) throw new CannotRedoException ();

        ModelEditPanel mep = ModelEditPanel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        // Update the database

        // The minimum number of equations is 2. There should never be exactly 1 equation, because that is single-line form, which should have no child equations at all.
        if (equationCount == 0)  // We are about to switch from single-line form to multi-conditional, so make a tree node for the existing equation.
        {
            Variable.ParsedValue pieces = new Variable.ParsedValue (parent.source.get ());
            parent.source.set (pieces.combiner);
            parent.setUserObject (parent.source.key () + "=" + pieces.combiner);
            MPart equation = (MPart) parent.source.set (pieces.expression, "@" + pieces.conditional);
            model.insertNodeIntoUnfiltered (new NodeEquation (equation), parent, 0);
        }
        MPart createdPart = (MPart) parent.source.set (value == null ? name.substring (1) : value, name);

        // Update the GUI

        NodeBase createdNode = parent.child (name);
        boolean alreadyExists = createdNode != null;
        if (! alreadyExists) createdNode = new NodeEquation (createdPart);

        FontMetrics fm = createdNode.getFontMetrics (tree);
        if (parent.getChildCount () > 0)
        {
            NodeBase firstChild = (NodeBase) parent.getChildAt (0);
            if (firstChild.needsInitTabs ()) firstChild.initTabs (fm);
        }

        if (value == null) createdNode.setUserObject ("");
        createdNode.updateColumnWidths (fm);  // preempt initialization
        if (! alreadyExists) model.insertNodeIntoUnfiltered (createdNode, parent, index);
        if (value != null)  // create was merged with change name/value
        {
            parent.updateTabStops (fm);
            parent.allNodesChanged (model);
            mep.panelEquations.updateVisibility (createdNode.getPath ());
        }

        return createdNode;
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (value == null  &&  edit instanceof ChangeEquation)
        {
            ChangeEquation change = (ChangeEquation) edit;
            if (name.equals (change.nameBefore))
            {
                name  = change.nameAfter;
                value = change.valueAfter;
                return true;
            }
        }
        return false;
    }
}

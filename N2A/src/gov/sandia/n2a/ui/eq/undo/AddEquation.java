/*
Copyright 2017-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.lang.ref.WeakReference;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPart;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeEquation;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class AddEquation extends UndoableView implements AddEditable
{
    protected List<String>            path;  // to variable node
    protected int                     equationCount;  // of subsidiary nodes only; before adding this equation
    protected int                     index; // where to insert among siblings
    protected String                  name;  // includes the leading @
    protected String                  combinerBefore;
    protected String                  combinerAfter;
    protected String                  value;
    protected WeakReference<NodeBase> createdNode;  ///< Used by caller to initiate editing. Only valid immediately after call to redo().
    protected boolean                 killedVariable;
    protected boolean                 multi;
    protected boolean                 multiLast;

    public AddEquation (NodeVariable parent, int index, MNode data)
    {
        path           = parent.getKeyPath ();
        this.index     = index;
        Variable.ParsedValue pieces = new Variable.ParsedValue (parent.source.get ());
        combinerBefore = pieces.combiner;
        combinerAfter  = combinerBefore;
        killedVariable = parent.source.getFlag ("$kill");

        TreeSet<String> equations = new TreeSet<String> ();
        for (MNode n : parent.source)
        {
            String key = n.key ();
            if (key.startsWith ("@")) equations.add (key);
        }
        equationCount = equations.size ();  // Count of subsidiary nodes only.
        if (equationCount == 0) equations.add ("@" + pieces.condition);  // single-line variable

        String prefix = "@";
        if (data != null)
        {
            prefix = data.key ();
            value  = data.get ();
            // When data is provided (from a paste operation), it will not carry its own combiner.
        }

        // Select a unique name
        int suffix = equations.size ();
        name = prefix;
        while (equations.contains (name)) name = prefix + suffix++;
    }

    public AddEquation (NodeVariable parent, String name, String combiner, String value)
    {
        path           = parent.getKeyPath ();
        index          = 0;
        this.name      = "@" + name;
        combinerBefore = new Variable.ParsedValue (parent.source.get ()).combiner;
        combinerAfter  = combiner;
        this.value     = value;
        killedVariable = parent.source.getFlag ("$kill");

        for (MNode n : parent.source) if (n.key ().startsWith ("@")) equationCount++;
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
        destroy (path, equationCount, false, name, combinerBefore, killedVariable, ! multi  ||  multiLast);
    }

    public static void destroy (List<String> path, int equationCount, boolean canceled, String name, String combinerBefore, boolean killedVariable, boolean setSelection)
    {
        // Retrieve created node
        NodeVariable parent = (NodeVariable) NodeBase.locateNode (path);
        if (parent == null) throw new CannotUndoException ();
        NodeBase createdNode = parent.child (name);

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();

        TreeNode[] createdPath = createdNode.getPath ();
        int index = parent.getIndexFiltered (createdNode);
        if (canceled) index--;

        // Update database
        MPart mparent = parent.source;
        AddVariable.deleteOrKill (mparent, name);
        ChangeVariable.updateRevokation (mparent, killedVariable);
        boolean parentChanged = false;
        if (! mparent.get ().equals (combinerBefore))
        {
            mparent.set (combinerBefore);  // This value may be replaced below if we switch back to single-line.
            parentChanged = true;
        }

        // Update GUI

        if (mparent.child (name) == null)  // There is no overridden value, so this node goes away completely.
        {
            model.removeNodeFromParent (createdNode);

            if (equationCount == 0)  // The node used to be single-line, so fold the last equation back into it.
            {
                NodeEquation lastEquation = null;  // The one remaining equation.
                Enumeration<?> i = parent.children ();  // unfiltered
                while (i.hasMoreElements ())
                {
                    Object o = i.nextElement ();
                    if (o instanceof NodeEquation)
                    {
                        lastEquation = (NodeEquation) o;
                        break;
                    }
                }

                String lastCondition  = lastEquation.source.key ();
                String lastExpression = lastEquation.source.get ();
                mparent.clear (lastCondition);
                if (lastCondition.equals ("@")) mparent.set (combinerBefore + lastExpression);
                else                            mparent.set (combinerBefore + lastExpression + lastCondition);
                parentChanged = true;
                model.removeNodeFromParent (lastEquation);
            }
        }
        else  // Just exposed an overridden value, so update.
        {
            createdNode.setUserObject ();
        }

        if (parentChanged)  // Update tabs among this variable's siblings
        {
            parent.setUserObject ();
            parent.findHighlights (null, null);  // Clear highlights, and hope a change of selection will regenerate them.
            NodeBase grandparent = (NodeBase) parent.getParent ();
            grandparent.invalidateColumns (model);
        }
        parent.invalidateColumns (null);
        pet.updateOrder (createdPath);
        pet.updateVisibility (createdPath, index, setSelection);
        parent.allNodesChanged (model);
        pet.animate ();
    }

    public void redo ()
    {
        super.redo ();
        NodeBase temp = create (path, equationCount, index, name, combinerAfter, value, multi);
        createdNode = new WeakReference<NodeBase> (temp);
    }

    public NodeBase getCreatedNode ()
    {
        if (createdNode == null) return null;
        return createdNode.get ();
    }

    public static NodeBase create (List<String> path, int equationCount, int index, String name, String combinerAfter, String value, boolean multi)
    {
        NodeVariable parent = (NodeVariable) NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();

        // Update the database

        String parentValueBefore = parent.source.get ();
        Variable.ParsedValue parentPiecesBefore = new Variable.ParsedValue (parentValueBefore);
        // The minimum number of equations is 2. There should never be exactly 1 equation, because that is single-line form, which should have no child equations at all.
        if (equationCount == 0)  // We are about to switch from single-line form to multi-conditional, so make a tree node for the existing equation.
        {
            MPart equation = (MPart) parent.source.set (parentPiecesBefore.expression, "@" + parentPiecesBefore.condition);
            model.insertNodeIntoUnfiltered (new NodeEquation (equation), parent, 0);
        }
        ChangeVariable.updateRevokation (parent.source, false);
        MPart createdPart = (MPart) parent.source.set (value == null ? "0" : value, name);
        AddVariable.unkill (createdPart);
        boolean parentChanged = false;
        if (! combinerAfter.equals (parentValueBefore))
        {
            parent.source.set (combinerAfter);
            parentChanged = true;
        }

        // Update the GUI

        NodeBase createdNode = parent.child (name);
        boolean alreadyExists = createdNode != null;
        if (! alreadyExists) createdNode = new NodeEquation (createdPart);

        if (value == null) createdNode.setUserObject ("");
        if (! alreadyExists) model.insertNodeIntoUnfiltered (createdNode, parent, index);
        if (parentChanged)
        {
            parent.setUserObject ();
            parent.findHighlights (null, null);
            NodeBase grandparent = (NodeBase) parent.getParent ();
            grandparent.invalidateColumns (model);
        }
        if (value != null)  // Create was merged with change name/value, or we are un-deleting a node.
        {
            parent.invalidateColumns (null);
            TreeNode[] createdPath = createdNode.getPath ();
            pet.updateVisibility (createdPath, -2, ! multi);
            if (multi) pet.tree.addSelectionPath (new TreePath (createdPath));
            parent.allNodesChanged (model);
            pet.animate ();
        }

        return createdNode;
    }

    public boolean addEdit (UndoableEdit edit)
    {
        if (value == null  &&  edit instanceof ChangeEquation)  // null value means the edit has not merged a change yet
        {
            ChangeEquation change = (ChangeEquation) edit;
            if (name.equals (change.nameBefore))
            {
                name           = change.nameAfter;
                combinerBefore = change.combinerBefore;
                combinerAfter  = change.combinerAfter;
                value          = change.valueAfter;
                return true;
            }
        }
        return false;
    }

    public String getPresentationName ()
    {
        // Hack to allow NodeEquation.applyEdit() to distinguish between new blank equation
        // and one that already has its name/value set.
        return "AddEquation" + (killedVariable ? " killed" : "");
    }
}

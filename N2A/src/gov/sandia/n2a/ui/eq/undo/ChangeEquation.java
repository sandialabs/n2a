/*
Copyright 2017-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;

import gov.sandia.n2a.db.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeEquation;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class ChangeEquation extends UndoableView
{
    protected List<String> path;
    protected String       nameBefore;
    protected String       combinerBefore;
    protected String       valueBefore;
    protected String       nameAfter;
    protected String       combinerAfter;
    protected String       valueAfter;
    protected boolean      killed;
    protected boolean      killedVariable;
    protected boolean      multi;

    /**
        @param variable The direct container of the node being changed.
    **/
    public ChangeEquation (NodeVariable variable, String nameBefore, String combinerBefore, String valueBefore, String nameAfter, String combinerAfter, String valueAfter)
    {
        path = variable.getKeyPath ();

        this.nameBefore     = "@" + nameBefore;
        this.valueBefore    = valueBefore;
        this.combinerBefore = combinerBefore;
        this.nameAfter      = "@" + nameAfter;
        this.valueAfter     = valueAfter;
        this.combinerAfter  = combinerAfter;
        killed              = variable.source.getFlag (this.nameBefore, "$kill");
        killedVariable      = variable.source.getFlag ("$kill");
    }

    public void setMulti (boolean value)
    {
        multi = value;
    }

    public void undo ()
    {
        super.undo ();
        apply (nameAfter, nameBefore, combinerBefore, valueBefore, killed, killedVariable, multi);
    }

    public void redo ()
    {
        super.redo ();
        apply (nameBefore, nameAfter, combinerAfter, valueAfter, false, false, multi);
    }

    public void apply (String nameBefore, String nameAfter, String combinerAfter, String valueAfter, boolean killed, boolean killedVariable, boolean multi)
    {
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodeBase nodeBefore = parent.child (nameBefore);
        if (nodeBefore == null) throw new CannotRedoException ();

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();

        NodeBase nodeAfter;
        MPart mparent = parent.source;
        if (nameBefore.equals (nameAfter))
        {
            nodeAfter = nodeBefore;
            MPart mchild = nodeAfter.source;
            mchild.set (valueAfter);
            ChangeVariable.updateRevokation (mchild,  killed);
            ChangeVariable.updateRevokation (mparent, killedVariable);
        }
        else
        {
            // Update the database
            MPart newPart = (MPart) mparent.set (valueAfter, nameAfter);
            mparent.clear (nameBefore);
            MPart oldPart = (MPart) mparent.child (nameBefore);
            ChangeVariable.updateRevokation (newPart, killed);
            ChangeVariable.updateRevokation (mparent, killedVariable);

            // Update GUI
            nodeAfter = parent.child (nameAfter);
            if (oldPart == null)
            {
                if (nodeAfter == null)
                {
                    nodeAfter = nodeBefore;
                    nodeAfter.source = newPart;
                }
                else
                {
                    model.removeNodeFromParent (nodeBefore);
                }
            }
            else
            {
                if (nodeAfter == null)
                {
                    int index = parent.getIndex (nodeBefore);
                    nodeAfter = new NodeEquation (newPart);
                    model.insertNodeIntoUnfiltered (nodeAfter, parent, index);
                }
                if (nodeBefore.visible ()) model.nodeChanged (nodeBefore);
                else                       parent.hide (nodeBefore, model);
            }
        }

        if (! mparent.get ().equals (combinerAfter))
        {
            mparent.set (combinerAfter);
            parent.setUserObject ();
            NodeBase grandparent = (NodeBase) parent.getParent ();
            grandparent.invalidateColumns (model);
        }

        nodeAfter.setUserObject ();
        parent.invalidateColumns (null);
        TreeNode[] afterPath = nodeAfter.getPath ();
        pet.updateVisibility (afterPath, -2, ! multi);
        if (multi) pet.tree.addSelectionPath (new TreePath (afterPath));
        parent.allNodesChanged (model);
        pet.animate ();
    }
}

/*
Copyright 2017-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.awt.FontMetrics;
import java.util.List;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeEquation;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class ChangeEquation extends Undoable
{
    protected List<String> path;
    protected String       nameBefore;
    protected String       combinerBefore;
    protected String       valueBefore;
    protected String       nameAfter;
    protected String       combinerAfter;
    protected String       valueAfter;
    protected List<String> replacePath;

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
    }

    public ChangeEquation (NodeVariable variable, String nameBefore, String combinerBefore, String valueBefore, String nameAfter, String combinerAfter, String valueAfter, List<String> replacePath)
    {
        this (variable, nameBefore, combinerBefore, valueBefore, nameAfter, combinerAfter, valueAfter);
        this.replacePath = replacePath;
    }

    public void undo ()
    {
        super.undo ();
        apply (path, nameAfter, nameBefore, combinerBefore, valueBefore);
    }

    public void redo ()
    {
        super.redo ();
        apply (path, nameBefore, nameAfter, combinerAfter, valueAfter);
    }

    public static void apply (List<String> path, String nameBefore, String nameAfter, String combinerAfter, String valueAfter)
    {
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodeBase nodeBefore = parent.child (nameBefore);
        if (nodeBefore == null) throw new CannotRedoException ();

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();
        FontMetrics fm = nodeBefore.getFontMetrics (pet.tree);

        NodeBase nodeAfter;
        if (nameBefore.equals (nameAfter))
        {
            nodeAfter = nodeBefore;
            nodeAfter.source.set (valueAfter);
        }
        else
        {
            // Update the database
            MPart mparent = parent.source;
            MPart newPart = (MPart) mparent.set (valueAfter, nameAfter);
            mparent.clear (nameBefore);
            MPart oldPart = (MPart) mparent.child (nameBefore);

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
                if (nodeBefore.visible (FilteredTreeModel.filterLevel)) model.nodeChanged (nodeBefore);
                else                                                    parent.hide (nodeBefore, model, true);
            }
        }

        if (parent.getChildCount () > 0)
        {
            NodeBase firstChild = (NodeBase) parent.getChildAt (0);
            if (firstChild.needsInitTabs ()) firstChild.initTabs (fm);
        }

        if (! parent.source.get ().equals (combinerAfter))
        {
            parent.source.set (combinerAfter);
            parent.updateColumnWidths (fm);
            NodeBase grandparent = (NodeBase) parent.getParent ();
            grandparent.updateTabStops (fm);
            grandparent.allNodesChanged (model);
        }

        nodeAfter.updateColumnWidths (fm);
        parent.updateTabStops (fm);
        parent.allNodesChanged (model);
        pet.updateVisibility (nodeAfter.getPath ());
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddVariable)
        {
            AddVariable av = (AddVariable) edit;
            if (! av.nameIsGenerated) return false;
            return av.fullPath ().equals (replacePath);
        }

        return false;
    }
}

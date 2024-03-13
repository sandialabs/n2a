/*
Copyright 2016-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;

import gov.sandia.n2a.db.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeReference;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class ChangeReference extends UndoableView
{
    protected List<String> path;  // to the direct parent, whether a $meta block or a variable
    protected String nameBefore;
    protected String nameAfter;
    protected String valueBefore;
    protected String valueAfter;

    /**
        @param container The direct container of the node being changed.
    **/
    public ChangeReference (NodeBase container, String nameBefore, String valueBefore, String nameAfter, String valueAfter)
    {
        path = container.getKeyPath ();

        this.nameBefore  = nameBefore;
        this.valueBefore = valueBefore;
        this.nameAfter   = nameAfter;
        this.valueAfter  = valueAfter;
    }

    public void undo ()
    {
        super.undo ();
        apply (nameAfter, nameBefore, valueBefore);
    }

    public void redo ()
    {
        super.redo ();
        apply (nameBefore, nameAfter, valueAfter);
    }

    public void apply (String nameBefore, String nameAfter, String valueAfter)
    {
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodeBase nodeBefore = parent.child (nameBefore);
        if (nodeBefore == null) throw new CannotRedoException ();

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();

        NodeBase nodeAfter;
        if (nameBefore.equals (nameAfter))
        {
            nodeAfter = nodeBefore;
            nodeAfter.source.set (valueAfter);
        }
        else
        {
            // Update database
            MPart mparent;
            if (parent instanceof NodeVariable) mparent = (MPart) parent.source.child ("$ref");
            else                                mparent =         parent.source;
            MPart newPart = (MPart) mparent.set (valueAfter, nameAfter);  // should directly change destinationNode if it exists
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
                    nodeAfter = new NodeReference (newPart);
                    model.insertNodeIntoUnfiltered (nodeAfter, parent, index);
                }
            }
        }

        nodeAfter.setUserObject ();
        parent.invalidateColumns (null);
        TreeNode[] nodePath = nodeAfter.getPath ();
        pet.updateOrder (nodePath);
        pet.updateVisibility (nodePath);
        parent.allNodesChanged (model);
        pet.animate ();
    }
}

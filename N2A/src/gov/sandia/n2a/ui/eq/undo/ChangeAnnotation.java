/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.awt.FontMetrics;
import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class ChangeAnnotation extends Undoable
{
    protected List<String> path;  // to the direct parent, whether a $metadata block or a variable
    protected String nameBefore;
    protected String nameAfter;
    protected String valueBefore;
    protected String valueAfter;

    /**
        @param container The direct container of the node being changed.
    **/
    public ChangeAnnotation (NodeBase container, String nameBefore, String valueBefore, String nameAfter, String valueAfter)
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
        apply (path, nameAfter, nameBefore, valueBefore, "$metadata", new NodeFactory ()
        {
            public NodeBase create (MPart part)
            {
                return new NodeAnnotation (part);
            }
        });
    }

    public void redo ()
    {
        super.redo ();
        apply (path, nameBefore, nameAfter, valueAfter, "$metadata", new NodeFactory ()
        {
            public NodeBase create (MPart part)
            {
                return new NodeAnnotation (part);
            }
        });
    }

    public static void apply (List<String> path, String nameBefore, String nameAfter, String valueAfter, String blockName, NodeFactory factory)
    {
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodeBase nodeBefore = parent.child (nameBefore);
        if (nodeBefore == null) throw new CannotRedoException ();

        PanelEquationTree pet = PanelModel.instance.panelEquations;
        JTree tree = pet.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        FontMetrics fm = nodeBefore.getFontMetrics (tree);

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
            if (parent instanceof NodeVariable) mparent = (MPart) parent.source.child (blockName);
            else                                mparent =         parent.source;
            MPart newPart = (MPart) mparent.set (nameAfter, valueAfter);  // should directly change destinationNode if it exists
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
                    nodeAfter = factory.create (newPart);
                    model.insertNodeIntoUnfiltered (nodeAfter, parent, index);
                }
            }
        }

        nodeAfter.updateColumnWidths (fm);
        parent.updateTabStops (fm);
        parent.allNodesChanged (model);

        TreeNode[] nodePath = nodeAfter.getPath ();
        pet.updateOrder (nodePath);
        pet.updateVisibility (nodePath);

        // Only an inherited lock node can be touched by editing. It is possible to activate (make local) if the user assigns a specific value to it.
        if (path.size () == 2  &&  path.get (1).equals ("$metadata")  &&  (nameBefore.equals ("lock")  ||  nameAfter.equals ("lock"))) pet.updateLock ();
    }
}

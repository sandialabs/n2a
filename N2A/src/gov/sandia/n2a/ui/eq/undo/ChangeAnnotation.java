/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.awt.FontMetrics;
import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeBase;

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
        apply (path, nameAfter, valueAfter, nameBefore, valueBefore, new NodeFactory ()
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
        apply (path, nameBefore, valueBefore, nameAfter, valueAfter, new NodeFactory ()
        {
            public NodeBase create (MPart part)
            {
                return new NodeAnnotation (part);
            }
        });
    }

    public static void apply (List<String> path, String nameBefore, String valueBefore, String nameAfter, String valueAfter, NodeFactory factory)
    {
        NodeBase parent = locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodeBase nodeBefore = parent.child (nameBefore);
        if (nodeBefore == null) throw new CannotRedoException ();

        ModelEditPanel mep = ModelEditPanel.instance;
        JTree tree = mep.panelEquations.tree;
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
            MPart mparent = parent.source;
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
                    nodeAfter = factory.create (newPart);
                    model.insertNodeIntoUnfiltered (nodeAfter, parent, index);
                }
            }
        }

        nodeAfter.updateColumnWidths (fm);
        parent.updateTabStops (fm);
        parent.allNodesChanged (model);

        TreeNode[] nodePath = nodeAfter.getPath ();
        mep.panelEquations.updateOrder (nodePath);
        mep.panelEquations.updateVisibility (nodePath);
    }
}

/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.awt.FontMetrics;
import java.util.List;

import javax.swing.JTree;
import javax.swing.undo.CannotRedoException;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.Do;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.NodeBase;
import gov.sandia.n2a.ui.eq.NodeFactory;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;

public class ChangeAnnotation extends Do
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
        NodeBase container = locateNode (path);
        if (container == null) throw new CannotRedoException ();
        NodeBase changedNode = container.child (nameBefore);

        ModelEditPanel mep = ModelEditPanel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        FontMetrics fm = changedNode.getFontMetrics (tree);
        if (nameBefore.equals (nameAfter))
        {
            changedNode.source.set (valueAfter);
        }
        else
        {
            // Update database
            MPart metadata = changedNode.source.getParent ();
            MPart newPart = (MPart) metadata.set (valueAfter, nameAfter);  // should directly change destinationNode if it exists
            metadata.clear (nameBefore);
            MPart oldPart = (MPart) metadata.child (nameBefore);

            // Update GUI
            NodeBase destinationNode = container.child (nameAfter);
            if (oldPart == null)
            {
                if (destinationNode == null)
                {
                    changedNode.source = newPart;
                }
                else
                {
                    model.removeNodeFromParent (changedNode);
                    changedNode = destinationNode;
                }
            }
            else
            {
                if (destinationNode == null)
                {
                    destinationNode = factory.create (newPart);
                    model.insertNodeIntoUnfiltered (destinationNode, container, container.getChildCount ());
                }
                changedNode = destinationNode;
            }
        }

        changedNode.updateColumnWidths (fm);
        container.updateTabStops (fm);
        container.allNodesChanged (model);
        mep.panelEquations.updateVisibility (changedNode.getPath ());
    }
}

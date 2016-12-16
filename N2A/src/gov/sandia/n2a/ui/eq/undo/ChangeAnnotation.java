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
import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.Do;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.NodeBase;
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

        NodeBase container = locateParent (path);
        if (container == null) throw new CannotRedoException ();
        NodeBase changedNode = container.child (nameAfter);

        ModelEditPanel mep = ModelEditPanel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        FontMetrics fm = changedNode.getFontMetrics (tree);
        if (nameBefore.equals (nameAfter))
        {
            changedNode.source.set (valueBefore);
        }
        else
        {
            MPart metadata = changedNode.source.getParent ();
            NodeBase exposedNode = container.child (nameBefore);
            if (exposedNode != null)
            {
                model.removeNodeFromParent (changedNode);
                changedNode = exposedNode;
            }
            changedNode.source = (MPart) metadata.set (valueBefore, nameBefore);
            metadata.clear (nameAfter);
        }

        changedNode.updateColumnWidths (fm);
        container.updateTabStops (fm);
        container.allNodesChanged (model);

        tree.setSelectionPath (new TreePath (changedNode.getPath ()));
        mep.panelEquations.repaintSouth (new TreePath (container.getPath ()));
    }

    public void redo ()
    {
        super.redo ();

        NodeBase container = locateParent (path);
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
            MPart metadata = changedNode.source.getParent ();
            MPart newPart = (MPart) metadata.set (valueAfter, nameAfter);
            metadata.clear (nameBefore);
            if (metadata.child (nameBefore) == null)  // We were not associated with an override, so we can re-use this tree node.
            {
                changedNode.source = newPart;
            }
            else  // Make a new tree node, and leave this one to present the non-overridden value.
            {
                NodeAnnotation newAnnotation = new NodeAnnotation (newPart);
                model.insertNodeIntoUnfiltered (newAnnotation, container, container.getChildCount ());
                newAnnotation.updateColumnWidths (fm);
            }
        }

        changedNode.updateColumnWidths (fm);
        container.updateTabStops (fm);
        container.allNodesChanged (model);

        tree.setSelectionPath (new TreePath (changedNode.getPath ()));
        mep.panelEquations.repaintSouth (new TreePath (container.getPath ()));
    }
}

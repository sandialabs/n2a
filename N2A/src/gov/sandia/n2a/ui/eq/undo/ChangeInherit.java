/*
Copyright 2016,2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;

import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeInherit;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class ChangeInherit extends Undoable
{
    protected List<String> path;
    protected String valueBefore;
    protected String valueAfter;

    /**
        @param container The direct container of the node being changed.
    **/
    public ChangeInherit (NodeInherit node, String valueAfter)
    {
        path            = node.getKeyPath ();  // include "$inherit"
        valueBefore     = node.source.get ();
        this.valueAfter = valueAfter;
    }

    public void undo ()
    {
        super.undo ();
        apply (valueBefore);
    }

    public void redo ()
    {
        super.redo ();
        apply (valueAfter);
    }

    public void apply (String value)
    {
        NodeBase node = locateNode (path);
        if (node == null) throw new CannotRedoException ();

        PanelModel mep = PanelModel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        node.source.set (value);  // Complex restructuring happens here.

        NodePart parent = (NodePart) node.getParent ();
        parent.build ();
        parent.findConnections ();
        parent.filter (model.filterLevel);

        if (parent.visible (model.filterLevel)) model.nodeStructureChanged (parent);

        TreeNode[] nodePath = parent.child ("$inherit").getPath ();
        mep.panelEquations.updateOrder (nodePath);
        mep.panelEquations.updateVisibility (nodePath);
    }
}

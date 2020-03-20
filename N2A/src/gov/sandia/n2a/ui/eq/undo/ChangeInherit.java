/*
Copyright 2016-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;

import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationGraph;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeInherit;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class ChangeInherit extends UndoableView
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
        NodeBase node = NodeBase.locateNode (path);
        if (node == null) throw new CannotRedoException ();
        NodePart parent = (NodePart) node.getParent ();
        NodePart grandparent = (NodePart) parent.getParent ();

        PanelEquations pe = PanelModel.instance.panelEquations;
        PanelEquationTree pet = node.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();
        PanelEquationGraph peg = pe.panelEquationGraph;

        node.source.set (value);  // Complex restructuring happens here.

        parent.build ();
        if (grandparent == null) parent     .findConnections ();
        else                     grandparent.findConnections ();
        parent.filter (FilteredTreeModel.filterLevel);
        if (parent == pe.part)
        {
            peg.loadPart ();
            peg.repaint ();
            parent.filter (FilteredTreeModel.filterLevel);
        }
        if (parent.graph != null  ||  parent == pe.part  ||  parent.visible (FilteredTreeModel.filterLevel)) model.nodeStructureChanged (parent);

        TreeNode[] nodePath = parent.child ("$inherit").getPath ();
        pet.updateOrder (nodePath);
        pet.updateVisibility (nodePath);
        pet.animate ();
        if (grandparent == pe.part)
        {
            peg.reconnect ();
            peg.repaint ();
        }
    }
}

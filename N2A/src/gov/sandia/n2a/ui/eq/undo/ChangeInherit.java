/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.JTree;
import javax.swing.undo.CannotRedoException;

import gov.sandia.n2a.ui.eq.Do;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeInherit;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class ChangeInherit extends Do
{
    protected List<String> path;
    protected String valueBefore;
    protected String valueAfter;

    /**
        @param container The direct container of the node being changed.
    **/
    public ChangeInherit (NodeInherit node, String valueBefore, String valueAfter)
    {
        path = node.getKeyPath ();  // include "$inherit"
        this.valueBefore = valueBefore;
        this.valueAfter  = valueAfter;
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

        ModelEditPanel mep = ModelEditPanel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        node.source.set (value);  // Complex restructuring happens here.

        NodePart parent = (NodePart) node.getParent ();
        parent.build ();
        parent.filter (model.filterLevel);
        model.nodeStructureChanged (parent);

        mep.panelEquations.updateVisibility (parent.child ("$inherit").getPath ());
    }
}

/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.Do;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeInherit;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class DeleteInherit extends Do
{
    protected List<String> path;  // to parent part
    protected int          index; // where to insert among siblings (generally zero)
    protected String       value;

    public DeleteInherit (NodeInherit node)
    {
        value = node.source.get ();

        NodeBase container = (NodeBase) node.getParent ();
        index  = container.getIndex (node);
        path   = container.getKeyPath ();
    }

    public void undo ()
    {
        super.undo ();

        NodePart parent = (NodePart) locateNode (path);
        if (parent == null) throw new CannotUndoException ();

        ModelEditPanel mep = ModelEditPanel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        parent.source.set (value, "$inherit");
        parent.build ();
        parent.filter (model.filterLevel);
        model.nodeStructureChanged (parent);

        tree.setSelectionPath (new TreePath (parent.child ("$inherit").getPath ()));
        TreePath parentPath = new TreePath (parent.getPath ());
        mep.panelEquations.updateOverrides (parentPath);
        mep.panelEquations.repaintSouth (parentPath);
    }

    public void redo ()
    {
        super.redo ();

        NodePart parent = (NodePart) locateNode (path);
        if (parent == null) throw new CannotRedoException ();

        NodeBase node = parent.child ("$inherit");
        TreePath parentPath = new TreePath (parent.getPath ());

        ModelEditPanel mep = ModelEditPanel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        MPart mparent = node.source.getParent ();
        mparent.clear ("$inherit");  // Complex restructuring happens here.
        parent.build ();  // Handles all cases (complete deletion or exposed hidden node)
        parent.filter (model.filterLevel);
        if (parent.visible (model.filterLevel)) model.nodeStructureChanged (parent);
        else                                    ((NodeBase) parent.getParent ()).hide (parent, model);
        mep.panelEquations.updateAfterDelete (parentPath, index);
    }
}

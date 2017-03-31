/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class AddInherit extends Undoable
{
    protected List<String> path;  // to parent part
    protected String       value;

    public AddInherit (NodePart parent, String value)
    {
        path = parent.getKeyPath ();
        this.value = value;
    }

    public void undo ()
    {
        super.undo ();
        destroy (path, false);
    }

    public static void destroy (List<String> path, boolean canceled)
    {
        System.out.println ("destroy: " + path);
        NodePart parent = (NodePart) locateNode (path);
        if (parent == null) throw new CannotRedoException ();

        NodeBase node = parent.child ("$inherit");
        TreeNode[] nodePath = node.getPath ();
        int index = parent.getIndexFiltered (node);
        if (canceled) index--;

        PanelModel mep = PanelModel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        MPart mparent = parent.source;
        mparent.clear ("$inherit");  // Complex restructuring happens here.
        parent.build ();  // Handles all cases (complete deletion or exposed hidden node)
        parent.findConnections ();
        parent.filter (model.filterLevel);
        if (parent.visible (model.filterLevel)) model.nodeStructureChanged (parent);

        mep.panelEquations.updateOrder (nodePath);
        mep.panelEquations.updateVisibility (nodePath, index);
    }

    public void redo ()
    {
        super.redo ();
        create (path, value);
    }

    public static void create (List<String> path, String value)
    {
        NodePart parent = (NodePart) locateNode (path);
        if (parent == null) throw new CannotUndoException ();

        PanelModel mep = PanelModel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        parent.source.set ("$inherit", value);
        parent.build ();
        parent.findConnections ();
        parent.filter (model.filterLevel);
        model.nodeStructureChanged (parent);  // Since $inherit is being added, parent will almost certainly become visible, if it's not already.

        TreeNode[] createdPath = parent.child ("$inherit").getPath ();
        mep.panelEquations.updateOrder (createdPath);
        mep.panelEquations.updateVisibility (createdPath);
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddVariable)
        {
            AddVariable av = (AddVariable) edit;
            if (! av.nameIsGenerated) return false;
            if (path.equals (av.path)) return true;
        }

        return false;
    }
}

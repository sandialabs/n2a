/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationGraph;
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
        NodePart parent = (NodePart) NodeBase.locateNode (path);
        if (parent == null) throw new CannotUndoException ();
        NodePart grandparent = (NodePart) parent.getParent ();

        NodeBase node = parent.child ("$inherit");
        TreeNode[] nodePath = node.getPath ();
        int index = parent.getIndexFiltered (node);
        if (canceled) index--;

        PanelModel mep = PanelModel.instance;
        JTree tree = mep.panelEquationTree.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        PanelEquationGraph peg = mep.panelEquations.panelEquationGraph;

        MPart mparent = parent.source;
        mparent.clear ("$inherit");  // Complex restructuring happens here.
        parent.build ();  // Handles all cases (complete deletion or exposed hidden node)
        if (grandparent == null) parent     .findConnections ();
        else                     grandparent.findConnections ();
        parent.filter (model.filterLevel);
        if (parent.visible (model.filterLevel)) model.nodeStructureChanged (parent);

        mep.panelEquationTree.updateOrder (nodePath);
        mep.panelEquationTree.updateVisibility (nodePath, index);
        if (grandparent != null  &&  grandparent == peg.part)
        {
            peg.reconnect ();
            peg.paintImmediately ();
        }
    }

    public void redo ()
    {
        super.redo ();
        create (path, value);
    }

    public static void create (List<String> path, String value)
    {
        NodePart parent = (NodePart) NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodePart grandparent = (NodePart) parent.getParent ();

        PanelModel mep = PanelModel.instance;
        JTree tree = mep.panelEquationTree.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        PanelEquationGraph peg = mep.panelEquations.panelEquationGraph;

        parent.source.set (value, "$inherit");
        parent.build ();
        if (grandparent == null) parent     .findConnections ();
        else                     grandparent.findConnections ();
        parent.filter (model.filterLevel);
        model.nodeStructureChanged (parent);  // Since $inherit is being added, parent will almost certainly become visible, if it's not already.

        TreeNode[] createdPath = parent.child ("$inherit").getPath ();
        mep.panelEquationTree.updateOrder (createdPath);
        mep.panelEquationTree.updateVisibility (createdPath);
        if (grandparent != null  &&  grandparent == peg.part)
        {
            peg.reconnect ();
            peg.paintImmediately ();
        }
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

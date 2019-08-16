/*
Copyright 2017-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationGraph;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.PanelEquations.StoredView;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class AddInherit extends Undoable
{
    protected StoredView   view = PanelModel.instance.panelEquations.new StoredView ();
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
        view.restore ();
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

        PanelEquations pe = PanelModel.instance.panelEquations;
        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();
        PanelEquationGraph peg = pe.panelEquationGraph;

        MPart mparent = parent.source;
        mparent.clear ("$inherit");  // Complex restructuring happens here.
        parent.build ();  // Handles all cases (complete deletion or exposed hidden node)
        if (grandparent == null) parent     .findConnections ();
        else                     grandparent.findConnections ();
        parent.filter (FilteredTreeModel.filterLevel);
        if (parent.visible (FilteredTreeModel.filterLevel)) model.nodeStructureChanged (parent);

        pet.updateOrder (nodePath);
        pet.updateVisibility (nodePath, index);
        pet.animate ();
        if (grandparent != null  &&  grandparent == pe.part)
        {
            peg.reconnect ();
            peg.repaint ();
        }
    }

    public void redo ()
    {
        super.redo ();
        view.restore ();
        create (path, value);
    }

    public static void create (List<String> path, String value)
    {
        NodePart parent = (NodePart) NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodePart grandparent = (NodePart) parent.getParent ();

        PanelEquations pe = PanelModel.instance.panelEquations;
        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();
        PanelEquationGraph peg = pe.panelEquationGraph;

        parent.source.set (value, "$inherit");
        parent.build ();
        if (grandparent == null) parent     .findConnections ();
        else                     grandparent.findConnections ();
        parent.filter (FilteredTreeModel.filterLevel);
        model.nodeStructureChanged (parent);  // Since $inherit is being added, parent will almost certainly become visible, if it's not already.

        TreeNode[] createdPath = parent.child ("$inherit").getPath ();
        pet.updateOrder (createdPath);
        pet.updateVisibility (createdPath);
        pet.animate ();
        if (grandparent != null  &&  grandparent == pe.part)
        {
            peg.reconnect ();
            peg.repaint ();
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

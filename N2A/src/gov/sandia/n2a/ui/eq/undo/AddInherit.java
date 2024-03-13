/*
Copyright 2017-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationGraph;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class AddInherit extends UndoableView
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
        NodePart grandparent = (NodePart) parent.getTrueParent ();

        NodeBase node = parent.child ("$inherit");
        TreeNode[] nodePath = node.getPath ();
        int index = parent.getIndexFiltered (node);
        if (canceled) index--;

        PanelEquations pe = PanelModel.instance.panelEquations;
        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = null;
        if (pet != null) model = (FilteredTreeModel) pet.tree.getModel ();
        PanelEquationGraph peg = pe.panelEquationGraph;

        MPart mparent = parent.source;
        mparent.clear ("$inherit");  // Complex restructuring happens here.
        parent.build ();  // Handles all cases (complete deletion or exposed hidden node)
        if (grandparent == null) parent     .findConnections ();
        else                     grandparent.findConnections ();
        parent.rebuildPins ();
        parent.filter ();
        if (parent == pe.part)
        {
            peg.reloadPart ();  // safely disconnects old nodes, even though parent has been rebuilt with new nodes
            parent.filter ();  // Ensure that parts are not visible in parent panel.
        }

        if (pet == null)
        {
            if (parent.graph != null) parent.graph.updateTitle ();
        }
        else
        {
            model.nodeStructureChanged (parent);  // Presumably, part node is still visible. Is there any harm in doing this if it is not?
            pet.updateOrder (nodePath);
            pet.updateVisibility (nodePath, index);
            pet.animate ();
        }

        if (parent != pe.part)
        {
            peg.updatePins ();
            peg.reconnect ();
            peg.repaint ();
        }

        if (parent.getTrueParent () == null)  // root node, so update categories in search list
        {
            PanelModel.instance.panelSearch.search ();
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
        NodePart grandparent = (NodePart) parent.getTrueParent ();

        PanelEquations pe = PanelModel.instance.panelEquations;
        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = null;
        if (pet != null) model = (FilteredTreeModel) pet.tree.getModel ();
        PanelEquationGraph peg = pe.panelEquationGraph;

        parent.source.set (value, "$inherit");
        parent.build ();
        if (grandparent == null) parent     .findConnections ();
        else                     grandparent.findConnections ();
        parent.rebuildPins ();
        parent.filter ();
        if (parent == pe.part)
        {
            peg.reloadPart ();
            parent.filter ();
        }

        if (pet == null)
        {
            if (parent.graph != null) parent.graph.updateTitle ();
        }
        else
        {
            model.nodeStructureChanged (parent);  // Since $inherit is being added, parent will almost certainly become visible, if it's not already.
            TreeNode[] createdPath = parent.child ("$inherit").getPath ();
            pet.updateOrder (createdPath);
            pet.updateVisibility (createdPath);
            pet.animate ();
        }

        if (parent != pe.part)
        {
            peg.updatePins ();
            peg.reconnect ();
            peg.repaint ();
        }

        if (parent.getTrueParent () == null)  // root node, so update categories in search list
        {
            PanelModel.instance.panelSearch.search ();
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

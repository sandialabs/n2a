/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;

import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;

public class ChangeLock extends Undoable
{
    protected List<String> path;  // to $metadata block
    protected boolean valueAfter;

    public ChangeLock (NodeBase container)
    {
        path = container.getKeyPath ();
    }

    public void undo ()
    {
        super.undo ();
        apply (path, false);
    }

    public void redo ()
    {
        super.redo ();
        apply (path, true);
    }

    public static void apply (List<String> path, boolean value)
    {
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodeBase node = parent.child ("lock");
        if (node == null) throw new CannotRedoException ();

        PanelEquationTree pet = PanelModel.instance.panelEquations;

        // Since this function implements both do and undo, we need to toggle the current lock state.
        if (value) node.source.override ();  // Force entry to be local rather than inherited. Only a local entry causes the lock to take effect.
        else       node.source.clearPath ();

        TreeNode[] nodePath = node.getPath ();
        pet.updateVisibility (nodePath);
        pet.updateLock ();
    }
}

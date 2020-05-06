/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.awt.Component;
import java.util.List;

import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelEquations.StoredView;

/**
    Combines UndoableView edits into a single transaction where the view.restore()
    function is only called once for the whole set.

    The edits will be executed in the same order they are added, so the last-added edit
    has final say over focus. This also implies that the edits should not depend on each
    other.

    Optionally, the current selection will be cleared before running the transaction.
    This allows member edits to compose a new selection specific to this compound edit.
**/
@SuppressWarnings("serial")
public class CompoundEditView extends CompoundEdit
{
    protected Component    tab;            // Must re-implement tab handling similar to gov.sandia.n2a.ui.Undoable, because this is not a subclass of Undoable and because we suppress tab handling in our sub-edits.
    protected StoredView   view;
    protected int          clearSelection;
    public    List<String> leadPath;       // If non-null, then attempt to make this node the focus after redo or undo.
    protected boolean      firstDo = true; // Latch to allow redo() before undo(). Hack to get around the heavy-handed lockdown of hasBeenDone in AbstractUndoableEdit.

    public static final int DONT_CLEAR  = 0;
    public static final int CLEAR_GRAPH = 1;
    public static final int CLEAR_TREE  = 2;

    public CompoundEditView (int clearSelection)
    {
        this.clearSelection = clearSelection;
    }

    /**
        The first edit submitted to this compound provides the stored view object.
        All edits have their stored views removed, so that only this compound does anything
        to re-establish the working view.
        Also sets multi flag on each edit that supports it.
    **/
    public synchronized boolean addEdit (UndoableEdit edit)
    {
        if (! super.addEdit (edit)) return false;
        if (edit instanceof UndoableView)
        {
            UndoableView uv = (UndoableView) edit;
            if (tab == null)  // This is the first-added edit.
            {
                tab  = uv.tab;
                view = uv.view;
            }
            uv.tab  = null;
            uv.view = null;
            uv.setMulti (true);
        }
        return true;
    }

    public void undo () throws CannotUndoException
    {
        MainFrame.instance.tabs.setSelectedComponent (tab);
        view.restore ();
        clearSelection ();

        int count = edits.size ();
        if (count > 0)
        {
            UndoableEdit u = edits.get (count - 1);
            if (u instanceof UndoableView) ((UndoableView) u).setMultiLast (false);
            u = edits.get (0);
            if (u instanceof UndoableView) ((UndoableView) u).setMultiLast (true);
        }

        super.undo ();
        selectLead ();
    }

    public void clearSelection ()
    {
        PanelEquations pe = PanelModel.instance.panelEquations;
        if (clearSelection == CLEAR_GRAPH)
        {
            pe.panelEquationGraph.clearSelection ();
        }
        else if (clearSelection == CLEAR_TREE)
        {
            // active could be null immediately after model is reloaded
            if (pe.active != null) pe.active.tree.clearSelection ();
        }
    }

    public void selectLead ()
    {
        if (leadPath == null) return;
        NodeBase n = NodeBase.locateNode (leadPath);
        if (n == null) return;

        if (clearSelection == CLEAR_GRAPH)
        {
            NodePart p = (NodePart) n;  // CLEAR_GRAPH should only be used for compound operations on parts, so this should be a safe cast.
            if (p.graph != null)
            {
                p.graph.takeFocusOnTitle ();
                return;
            }
        }

        // Otherwise, assume this is a compound operation on tree nodes.
        PanelEquationTree pet = n.getTree ();
        if (pet != null) pet.tree.setLeadSelectionPath (new TreePath (n.getPath ()));
    }

    public void redo () throws CannotRedoException
    {
        MainFrame.instance.tabs.setSelectedComponent (tab);
        view.restore ();
        clearSelection ();

        int count = edits.size ();
        if (count > 0)
        {
            UndoableEdit u = edits.get (0);
            if (u instanceof UndoableView) ((UndoableView) u).setMultiLast (false);
            u = edits.get (count - 1);
            if (u instanceof UndoableView) ((UndoableView) u).setMultiLast (true);
        }

        super.redo ();
        selectLead ();
        firstDo = false;
    }

    public boolean canRedo ()
    {
        if (firstDo) return true;
        return super.canRedo ();
    }
}

/*
Copyright 2017-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
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

public class ChangePart extends Undoable
{
    protected StoredView   view = PanelModel.instance.panelEquations.new StoredView ();
    protected List<String> path;   // to the container of the part being renamed
    protected String       nameBefore;
    protected String       nameAfter;
    protected MNode        savedTree;  // The entire subtree from the top document. If not from top document, then at least a single node for the part itself.

    /**
        @param node The part being renamed.
    **/
    public ChangePart (NodePart node, String nameBefore, String nameAfter)
    {
        NodeBase parent = (NodeBase) node.getTrueParent ();
        path = parent.getKeyPath ();
        this.nameBefore = nameBefore;
        this.nameAfter  = nameAfter;

        savedTree = new MVolatile ();
        if (node.source.isFromTopDocument ()) savedTree.merge (node.source.getSource ());
    }

    public void undo ()
    {
        super.undo ();
        apply (nameAfter, nameBefore);
    }

    public void redo ()
    {
        super.redo ();
        apply (nameBefore, nameAfter);
    }

    public void apply (String nameBefore, String nameAfter)
    {
        view.restore ();
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodeBase temp = parent.child (nameBefore);
        if (! (temp instanceof NodePart)) throw new CannotRedoException ();
        NodePart nodeBefore = (NodePart) temp;

        // Update the database: move the subtree.
        MPart mparent = parent.source;
        mparent.clear (nameBefore);
        mparent.set (savedTree, nameAfter);
        MPart oldPart = (MPart) mparent.child (nameBefore);
        MPart newPart = (MPart) mparent.child (nameAfter);

        // Update GUI

        PanelEquations pe = PanelModel.instance.panelEquations;
        PanelEquationTree pet = nodeBefore.getTree ();
        FilteredTreeModel model = null;
        if (pet != null) model = (FilteredTreeModel) pet.tree.getModel ();
        PanelEquationGraph peg = pe.panelEquationGraph;
        boolean graphParent = parent == pe.part  &&  ! pe.open;

        NodePart nodeAfter = (NodePart) parent.child (nameAfter);  // It's either a NodePart or it's null. Any other case should be blocked by GUI constraints.
        if (oldPart == null)
        {
            if (nodeAfter == null)
            {
                nodeAfter = nodeBefore;
                nodeAfter.source = newPart;
                if (graphParent) peg.updatePart (nodeAfter);
            }
            else
            {
                if (model == null) FilteredTreeModel.removeNodeFromParentStatic (nodeBefore);
                else               model.removeNodeFromParent (nodeBefore);
                if (graphParent) peg.removePart (nodeAfter);
            }
        }
        else
        {
            if (nodeAfter == null)
            {
                int index = parent.getIndex (nodeBefore);
                nodeAfter = new NodePart (newPart);
                if (model == null) FilteredTreeModel.insertNodeIntoUnfilteredStatic (nodeAfter, parent, index);
                else               model.insertNodeIntoUnfiltered (nodeAfter, parent, index);
                if (graphParent) peg.addPart (nodeAfter);
            }

            nodeBefore.build ();
            nodeBefore.findConnections ();
            nodeBefore.filter (FilteredTreeModel.filterLevel);
            if (nodeBefore.visible (FilteredTreeModel.filterLevel))
            {
                if (model != null) model.nodeStructureChanged (nodeBefore);
            }
            else
            {
                parent.hide (nodeBefore, model);
            }
        }

        nodeAfter.build ();
        nodeAfter.findConnections ();
        nodeAfter.filter (FilteredTreeModel.filterLevel);

        TreeNode[] nodePath = nodeAfter.getPath ();
        if (pet == null)
        {
            PanelEquationTree.updateOrder (null, nodePath);
            PanelEquationTree.updateVisibility (null, nodePath);
        }
        else
        {
            pet.updateOrder (nodePath);
            pet.updateVisibility (nodePath);  // Will include nodeStructureChanged(), if necessary.
        }
        if (graphParent)
        {
            peg.reconnect ();
            peg.paintImmediately ();
        }
    }
}

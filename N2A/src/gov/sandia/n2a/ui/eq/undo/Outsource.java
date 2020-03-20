/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class Outsource extends UndoableView
{
    protected List<String> path;
    protected String       inherit;
    protected MNode        savedSubtree;
    protected boolean      wasExpanded;

    public Outsource (NodePart node, String inherit)
    {
        path         = node.getKeyPath ();
        this.inherit = inherit;

        savedSubtree = new MVolatile ();
        savedSubtree.merge (node.source);  // This takes the entire tree, regardless of visibility. TODO: should this match the visibility semantics used by copy/paste operations?

        if (node.graph == null)
        {
            JTree tree = node.getTree ().tree;
            wasExpanded = tree.isExpanded (new TreePath (node.getPath ()));
        }
        else
        {
            wasExpanded = node.graph.open;
        }
    }

    public void undo ()
    {
        super.undo ();
        apply (savedSubtree);
    }

    public void redo ()
    {
        super.redo ();
        MNode subtree = new MVolatile ();
        subtree.set (inherit, "$inherit");
        apply (subtree);
    }

    public void apply (MNode subtree)
    {
        // Retrieve created node
        NodePart node = (NodePart) NodeBase.locateNode (path);
        if (node == null) throw new CannotRedoException ();

        PanelEquationTree pet = node.getTree ();
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();

        // Update database
        node.source.clear ();  // remove all children
        node.source.merge (subtree);

        // Update GUI
        node.build ();
        node.findConnections ();
        node.filter (FilteredTreeModel.filterLevel);
        // The caller of this Undoable promises to only use it on top-level nodes.
        // Thus, node retains exactly the same visibility as before, so no need for full update.
        // There are some obscure cases in which this doesn't hold (node was inherited, and the
        // new value of $inherit matches the inherited value of node.$inherit, so node ceases to be top-level),
        // but we won't worry about that.
        model.nodeStructureChanged (node);
        if (node.graph == null)
        {
            TreePath nodePath = new TreePath (node.getPath ());
            pet.tree.setSelectionPath (nodePath);
            if (wasExpanded) pet.tree.expandPath (nodePath);
        }
        else
        {
            PanelEquations pe = PanelModel.instance.panelEquations;
            if (pe.view == PanelEquations.NODE) node.graph.setOpen (wasExpanded);
            node.graph.takeFocus ();
        }

        // Not necessary to update the graph, since exactly the same connections exist.
        // findConnections() merely re-establishes them.
    }
}

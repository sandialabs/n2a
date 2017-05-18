/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class Outsource extends Undoable
{
    protected List<String> path;  // to containing part
    protected String       name;  // of the part itself
    protected String       inherit;
    protected MNode        savedSubtree;
    protected boolean      wasExpanded;

    public Outsource (NodePart node, String inherit)
    {
        NodeBase parent = (NodeBase) node.getParent ();
        path         = parent.getKeyPath ();
        name         = node.source.key ();
        this.inherit = inherit;

        savedSubtree = new MVolatile ();
        savedSubtree.merge (node.source);  // This takes the entire tree, regardless of visibility. TODO: should this match the visibility semantics used by copy/paste operations?

        JTree tree = PanelModel.instance.panelEquations.tree;
        wasExpanded = ! tree.isCollapsed (new TreePath (node.getPath ()));
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
        subtree.set ("$inherit", inherit);
        apply (subtree);
    }

    public void apply (MNode subtree)
    {
        // Retrieve created node
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodePart node = (NodePart) parent.child (name);
        if (node == null) throw new CannotRedoException ();

        PanelModel mep = PanelModel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        // Update database
        node.source.clear ();  // remove all children
        node.source.merge (subtree);

        // Update GUI
        node.build ();
        node.findConnections ();
        node.filter (model.filterLevel);
        // The caller of this Undoable promises to only use it on top-level nodes.
        // Thus, node retains exactly the same visibility as before, so no need for full update.
        // There are some obscure cases in which this doesn't hold (node was inherited, and the
        // new value of $inherit matches the inherited value of node.$inherit, so node ceases to be top-level),
        // but we won't worry about that.
        model.nodeStructureChanged (node);
        TreePath nodePath = new TreePath (node.getPath ());
        tree.setSelectionPath (nodePath);
        if (wasExpanded) tree.expandPath (nodePath);
    }
}

/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.awt.FontMetrics;
import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class ChangeVariable extends Undoable
{
    protected List<String> path;
    protected String       nameBefore;
    protected String       nameAfter;
    protected String       valueBefore;
    protected String       valueAfter;
    protected MNode        savedTree;  // The entire subtree from the top document. If not from top document, then at least a single node for the variable itself.
    protected List<String> replacePath;  // If a newly-created variable turns out to modify another node, this lets us remove the AddVariable from the undo stack.

    /**
        @param variable The direct container of the node being changed.
    **/
    public ChangeVariable (NodeVariable node, String nameAfter, String valueAfter)
    {
        NodeBase parent = (NodeBase) node.getParent ();
        path = parent.getKeyPath ();

        nameBefore  = node.source.key ();
        valueBefore = node.source.get ();
        this.nameAfter  = nameAfter;
        this.valueAfter = valueAfter;

        savedTree = new MVolatile ();
        if (node.source.isFromTopDocument ()) savedTree.merge (node.source.getSource ());
    }

    public ChangeVariable (NodeVariable node, String nameAfter, String valueAfter, List<String> replacePath)
    {
        this (node, nameAfter, valueAfter);
        this.replacePath = replacePath;
    }

    public void undo ()
    {
        super.undo ();
        savedTree.set (valueBefore);
        apply (nameAfter, nameBefore);
    }

    public void redo ()
    {
        super.redo ();
        savedTree.set (valueAfter);
        apply (nameBefore, nameAfter);
    }

    public void apply (String nameBefore, String nameAfter)
    {
        NodeBase parent = NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodeVariable nodeBefore = (NodeVariable) parent.child (nameBefore);
        if (nodeBefore == null) throw new CannotRedoException ();

        PanelModel mep = PanelModel.instance;
        JTree tree = mep.panelEquationTree.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        FontMetrics fm = nodeBefore.getFontMetrics (tree);

        NodeVariable nodeAfter;
        if (nameBefore.equals (nameAfter))
        {
            nodeAfter = nodeBefore;
            nodeAfter.source.set (savedTree.get ());  // Same as valueAfter. Sub-tree is not relevant here.
        }
        else
        {
            // Update database
            MPart mparent = parent.source;
            mparent.clear (nameBefore);
            mparent.set (savedTree, nameAfter);
            MPart newPart = (MPart) mparent.child (nameAfter);
            MPart oldPart = (MPart) mparent.child (nameBefore);

            // Update GUI
            nodeAfter = (NodeVariable) parent.child (nameAfter);
            if (oldPart == null)
            {
                if (nodeAfter == null)
                {
                    nodeAfter = nodeBefore;
                    nodeAfter.source = newPart;
                }
                else
                {
                    model.removeNodeFromParent (nodeBefore);
                }
            }
            else
            {
                if (nodeAfter == null)
                {
                    int index = parent.getIndex (nodeBefore);
                    nodeAfter = new NodeVariable (newPart);
                    model.insertNodeIntoUnfiltered (nodeAfter, parent, index);
                }

                nodeBefore.build ();
                nodeBefore.findConnections ();
                if (nodeBefore.visible (model.filterLevel)) model.nodeStructureChanged (nodeBefore);
                else                                        parent.hide (nodeBefore, model, true);
            }
        }

        nodeAfter.build ();
        nodeAfter.findConnections ();
        nodeAfter.updateColumnWidths (fm);
        parent.updateTabStops (fm);
        parent.allNodesChanged (model);

        TreeNode[] nodePath = nodeAfter.getPath ();
        mep.panelEquationTree.updateOrder (nodePath);
        mep.panelEquationTree.updateVisibility (nodePath);
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddVariable)
        {
            AddVariable av = (AddVariable) edit;
            if (! av.nameIsGenerated) return false;
            return av.fullPath ().equals (replacePath);
        }

        return false;
    }
}

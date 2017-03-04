/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.JTree;
import javax.swing.undo.CannotRedoException;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class ChangePart extends Undoable
{
    protected List<String> path;   // to the container of the part being renamed
    protected int          index;  // unfiltered
    protected String       nameBefore;
    protected String       nameAfter;

    /**
        @param node The part being renamed.
    **/
    public ChangePart (NodePart node, String nameBefore, String nameAfter)
    {
        NodeBase parent = (NodeBase) node.getParent ();
        path = parent.getKeyPath ();
        index = parent.getIndex (node);
        this.nameBefore = nameBefore;
        this.nameAfter  = nameAfter;
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
        NodeBase parent = locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodeBase temp = parent.child (nameBefore);
        if (! (temp instanceof NodePart)) throw new CannotRedoException ();
        NodePart nodeBefore = (NodePart) temp;

        // Update the database: move the subtree.
        MPart mparent = parent.source;
        mparent.move (nameBefore, nameAfter);  // TODO: May be better to store subtree, just like AddPart and DeletePart. Otherwise, some top-level nodes could disappear when merged into destination, and undo will not be idempotent.
        MPart oldPart = (MPart) mparent.child (nameBefore);
        MPart newPart = (MPart) mparent.child (nameAfter);

        // Update GUI

        ModelEditPanel mep = ModelEditPanel.instance;
        JTree tree = mep.panelEquations.tree;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();

        NodePart nodeAfter = (NodePart) parent.child (nameAfter);  // It's either a NodePart or it's null. Any other case should be blocked by GUI constraints.
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
                nodeAfter = new NodePart (newPart);
                model.insertNodeIntoUnfiltered (nodeAfter, parent, index);
            }

            nodeBefore.build ();
            nodeBefore.findConnections ();
            nodeBefore.filter (model.filterLevel);
            if (nodeBefore.visible (model.filterLevel)) model.nodeStructureChanged (nodeBefore);
            else                                        parent.hide (nodeBefore, model, true);
        }

        nodeAfter.build ();
        nodeBefore.findConnections ();
        nodeAfter.filter (model.filterLevel);
        mep.panelEquations.updateVisibility (nodeAfter.getPath ());  // Will include nodeStructureChanged(), if necessary.
    }
}

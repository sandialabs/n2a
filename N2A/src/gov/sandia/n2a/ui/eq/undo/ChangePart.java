/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JTree;
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
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class ChangePart extends Undoable
{
    protected StoredView         view = PanelModel.instance.panelEquations.new StoredView ();
    protected List<String>       path;   // to the container of the part being renamed
    protected String             nameBefore;
    protected String             nameAfter;
    protected MNode              savedTree;  // The entire subtree from the top document. If not from top document, then at least a single node for the part itself.
    protected List<List<String>> connectionPaths;

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

        // Collect key paths to all nodes which are connection bindings to the given part.
        List<NodeVariable> connectionBindings = PanelModel.instance.panelEquations.root.bindingsFor (node);
        if (connectionBindings != null)
        {
            connectionPaths = new ArrayList<List<String>> ();
            for (NodeVariable v : connectionBindings) connectionPaths.add (v.getKeyPath ());
        }
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
        int viewSize = view.path.size ();
        if (viewSize > path.size ())  // The name change applies to a graph node, which should be the focus.
        {
            view.path.set (viewSize - 1, nameBefore);
        }
        view.restore ();

        NodePart parent = (NodePart) NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodeBase temp = parent.child (nameBefore);
        if (! (temp instanceof NodePart)) throw new CannotRedoException ();
        NodePart nodeBefore = (NodePart) temp;

        // Update the database
        
        //   Move the subtree
        MPart mparent = parent.source;
        mparent.clear (nameBefore);
        mparent.set (savedTree, nameAfter);
        MPart oldPart = (MPart) mparent.child (nameBefore);
        MPart newPart = (MPart) mparent.child (nameAfter);

        //   Change connection bindings
        PanelEquations pe = PanelModel.instance.panelEquations;
        if (connectionPaths != null)
        {
            MPart doc = pe.root.source;
            for (List<String> cp : connectionPaths)
            {
                Object[] keyArray = cp.subList (1, cp.size ()).toArray ();
                String value = doc.get (keyArray);
                String[] pieces = value.split ("\\.");
                pieces[pieces.length - 1] = nameAfter;
                value = pieces[0];
                for (int i = 1; i < pieces.length; i++) value += "." + pieces[i];
                doc.set (value, keyArray);
            }
        }

        // Update GUI

        boolean graphParent =  parent == pe.part;
        PanelEquationTree pet = graphParent ? null : parent.getTree ();
        FilteredTreeModel model = null;
        if (pet != null) model = (FilteredTreeModel) pet.tree.getModel ();
        PanelEquationGraph peg = pe.panelEquationGraph;  // Only used if graphParent is true.

        NodePart nodeAfter = (NodePart) parent.child (nameAfter);  // It's either a NodePart or it's null. Any other case should be blocked by GUI constraints.
        boolean addGraphNode = false;
        if (oldPart == null)  // Only one node will remain when we are done.
        {
            if (nodeAfter == null)  // This is a simple rename, with no restructuring. Keep nodeBefore.
            {
                nodeAfter = nodeBefore;
                nodeAfter.source = newPart;
                if (graphParent) peg.updatePart (nodeAfter);
            }
            else  // Use existing nodeAfter, so get rid of nodeBefore.
            {
                if (model == null) FilteredTreeModel.removeNodeFromParentStatic (nodeBefore);
                else               model.removeNodeFromParent (nodeBefore);
                if (graphParent) peg.removePart (nodeBefore);
            }
        }
        else  // Need two nodes
        {
            if (nodeAfter == null)  // Need a node to hold the new part.
            {
                int index = parent.getIndex (nodeBefore);
                nodeAfter = new NodePart (newPart);
                nodeAfter.hide = graphParent;
                if (model == null) FilteredTreeModel.insertNodeIntoUnfilteredStatic (nodeAfter, parent, index);
                else               model.insertNodeIntoUnfiltered (nodeAfter, parent, index);
                addGraphNode = true;
            }

            nodeBefore.build ();
            nodeBefore.findConnections ();
            nodeBefore.filter (FilteredTreeModel.filterLevel);
            if (nodeBefore.visible (FilteredTreeModel.filterLevel))
            {
                if (graphParent)  // Need to update entire model under fake root.
                {
                    PanelEquationTree subpet = nodeBefore.getTree ();
                    if (subpet != null)
                    {
                        FilteredTreeModel submodel = (FilteredTreeModel) subpet.tree.getModel ();
                        submodel.nodeStructureChanged (nodeBefore);
                        subpet.animate ();
                    }
                }
                else if (model != null)
                {
                    model.nodeStructureChanged (nodeBefore);
                }
            }
            else
            {
                parent.hide (nodeBefore, model);
            }
        }

        nodeAfter.build ();
        if (graphParent) parent   .findConnections ();
        else             nodeAfter.findConnections ();
        nodeAfter.filter (FilteredTreeModel.filterLevel);

        pe.resetBreadcrumbs ();
        TreeNode[] nodePath = nodeAfter.getPath ();
        if (pet == null)
        {
            PanelEquationTree.updateOrder (null, nodePath);
            PanelEquationTree.updateVisibility (null, nodePath, -2, false);
        }
        else
        {
            pet.updateOrder (nodePath);
            pet.updateVisibility (nodePath);  // Will include nodeStructureChanged(), if necessary.
            pet.animate ();
        }

        if (connectionPaths != null)
        {
            // Update connection-binding variables affected by name change.
            for (List<String> cp : connectionPaths)
            {
                NodeBase n = NodeBase.locateNode (cp);
                if (n == null) continue;
                PanelEquationTree subpet = n.getTree ();
                if (subpet == null) continue;
                JTree subtree = subpet.tree;
                FilteredTreeModel submodel = (FilteredTreeModel) subtree.getModel ();

                FontMetrics fm = n.getFontMetrics (subtree);
                n.updateColumnWidths (fm);
                ((NodeBase) n.getParent ()).updateTabStops (fm);
                submodel.nodeChanged (n);
            }
        }

        if (graphParent)
        {
            if (addGraphNode)
            {
                peg.addPart (nodeAfter);  // builds tree
            }
            else
            {
                PanelEquationTree subpet = nodeAfter.getTree ();
                if (subpet != null)
                {
                    FilteredTreeModel submodel = (FilteredTreeModel) subpet.tree.getModel ();
                    submodel.nodeStructureChanged (nodeAfter);
                    subpet.animate ();
                }
            }
            nodeAfter.hide = false;
            nodeAfter.graph.takeFocusOnTitle ();
            peg.reconnect ();
            peg.repaint ();
        }
    }
}

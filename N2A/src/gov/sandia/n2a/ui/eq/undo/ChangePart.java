/*
Copyright 2017-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationGraph;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelEquations.FocusCacheEntry;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;
import gov.sandia.n2a.ui.eq.undo.ChangeVariable.NameVisitor;

public class ChangePart extends UndoableView
{
    protected List<String> path;   // to the container of the part being renamed
    protected String       nameBefore;
    protected String       nameAfter;
    protected MNode        savedTree;  // The entire subtree from the top document. If not from top document, then at least a single node for the part itself.
    protected boolean      killed;

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

        killed = node.source.getFlag ("$kill");
    }

    public void undo ()
    {
        updatePath (nameAfter);
        super.undo ();
        apply (nameAfter, nameBefore, killed);
    }

    public void redo ()
    {
        updatePath (nameBefore);
        super.redo ();
        apply (nameBefore, nameAfter, false);
    }

    protected void updatePath (String name)
    {
        int viewSize = view.path.size ();
        if (viewSize > path.size ())  // The name change applies to a graph node, which should be the focus.
        {
            view.path.set (viewSize - 1, name);
        }
    }

    public void apply (String nameBefore, String nameAfter, boolean killed)
    {
        NodePart parent = (NodePart) NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodeBase temp = parent.child (nameBefore);
        if (! (temp instanceof NodePart)) throw new CannotRedoException ();
        NodePart nodeBefore = (NodePart) temp;

        // Update the database
        MPart mparent = parent.source;
        mparent.clear (nameBefore);
        mparent.clear (nameAfter);
        mparent.set (savedTree, nameAfter);
        MPart oldPart = (MPart) mparent.child (nameBefore);
        MPart newPart = (MPart) mparent.child (nameAfter);
        ChangeVariable.updateRevokation (newPart, killed);

        // Update GUI
        // The main GUI update is standard. OTOH, the update of name references
        // scattered through the rest of the model is a mix of DB and GUI updates,
        // with no clean separation. The reason for this is that we rely on the GUI
        // tree as a kind of compiled model, taking advantage of some cached information.

        PanelEquations pe = PanelModel.instance.panelEquations;
        boolean graphParent =  parent == pe.part;
        PanelEquationTree pet = graphParent ? null : parent.getTree ();
        FilteredTreeModel model = null;
        if (pet != null) model = (FilteredTreeModel) pet.tree.getModel ();
        PanelEquationGraph peg = pe.panelEquationGraph;  // Only used if graphParent is true.

        NodePart nodeAfter = (NodePart) parent.child (nameAfter);  // It's either a NodePart or it's null. Any other case should be blocked by GUI constraints.
        boolean addGraphNode = false;
        Map<NodePart,List<String>> rebind = new HashMap<NodePart,List<String>> ();  // for updating GUI later
        NameVisitor nameVisitor;
        if (oldPart == null)  // Only one node will remain when we are done.
        {
            pe.renameFocus (nodeBefore.getKeyPath (), nameAfter);
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
                if (graphParent) peg.removePart (nodeBefore, true);
            }

            // Change references in other parts of the model.
            nodeAfter.build ();
            nameVisitor = renameReferences (parent, nameBefore, nameAfter, nodeBefore, nodeAfter, rebind);
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
            nodeAfter.build ();
            nameVisitor = renameReferences (parent, nameBefore, nameAfter, nodeBefore, nodeAfter, rebind);

            nodeBefore.findConnections ();
            nodeBefore.rebuildPins ();
            nodeBefore.filter ();
            if (nodeBefore.visible ())
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

        if (graphParent) parent   .findConnections ();
        else             nodeAfter.findConnections ();
        nodeAfter.rebuildPins ();
        nodeAfter.filter ();

        pe.resetBreadcrumbs ();
        TreeNode[] nodePath = nodeAfter.getPath ();
        Set<PanelEquationTree> needAnimate = new HashSet<PanelEquationTree> ();
        if (pet == null)
        {
            PanelEquationTree.updateOrder (null, nodePath);
            PanelEquationTree.updateVisibility (null, nodePath, -2, false);
        }
        else
        {
            pet.updateOrder (nodePath);
            pet.updateVisibility (nodePath);  // Will include nodeStructureChanged(), if necessary.
            needAnimate.add (pet);
        }

        for (NodeVariable n : nameVisitor.references)
        {
            // Rebuild n, because equations and/or their conditions may have changed.
            n.build ();
            n.findConnections ();
            n.filter ();
            if (n.visible ())  // n's visibility won't change
            {
                PanelEquationTree subpet = n.getTree ();
                if (subpet == null) continue;
                JTree subtree = subpet.tree;
                FilteredTreeModel submodel = (FilteredTreeModel) subtree.getModel ();
                NodeBase subparent = (NodeBase) n.getParent ();

                submodel.nodeStructureChanged (n);  // Node will collapse if it was open. Don't worry about this.
                subparent.invalidateColumns (submodel);
                needAnimate.add (subpet);
            }
        }

        for (NodePart peer : rebind.keySet ())
        {
            PanelEquationTree subpet = peer.getTree ();
            NodeBase metadata = peer.child ("$meta");  // also works for parent
            for (String pinKey : rebind.get (peer))
            {
                // Retrieve GUI metadata node so it can be updated to match DB.
                NodeBase nodeBind;
                if (peer == parent) nodeBind = (NodeAnnotation) AddAnnotation.findExact (metadata, false, "gui", "pin", "out", pinKey, "bind");
                else                nodeBind = (NodeAnnotation) AddAnnotation.findExact (metadata, false, "gui", "pin", "in",  pinKey, "bind");
                nodeBind.setUserObject ();

                // Update display tree.
                if (subpet != null)
                {
                    // For simplicity, look up subtree, submodel and subparent each time,
                    // even though they could be done just once per peer part.
                    JTree subtree = subpet.tree;
                    FilteredTreeModel submodel = (FilteredTreeModel) subtree.getModel ();
                    NodeBase subparent = (NodeBase) nodeBind.getParent ();

                    submodel.nodeChanged (nodeBind);
                    subparent.invalidateColumns (submodel);
                    needAnimate.add (subpet);
                }
            }
        }

        for (PanelEquationTree ap : needAnimate) ap.animate ();

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
                    FocusCacheEntry fce = pe.createFocus (nodeAfter);
                    if (fce.sp != null) fce.sp.restore (subpet.tree, false);
                    subpet.animate ();
                }
            }
            nodeAfter.hide = false;
            nodeAfter.graph.takeFocusOnTitle ();
            peg.updatePins ();
            peg.reconnect ();
            peg.repaint ();
        }
    }

    public NameVisitor renameReferences (NodePart parent, String nameBefore, String nameAfter, NodePart nodeBefore, NodePart nodeAfter, Map<NodePart,List<String>> rebind)
    {
        NameVisitor result = new NameVisitor (nameBefore, nameAfter, nameAfter.equals (this.nameBefore), nodeBefore, nodeAfter);
        PanelEquations pe = PanelModel.instance.panelEquations;
        if (parent == pe.part)
        {
            parent.findConnections ();
        }
        else
        {
            nodeAfter.findConnections ();
            if (nodeBefore != nodeAfter  &&  nodeBefore != null) nodeBefore.findConnections ();
        }
        try {pe.root.visit (result);}
        catch (Exception e) {e.printStackTrace ();}  // Trap the exception so we can at least clearFakeObject().
        result.clearFakeObject ();

        // Scan peer parts (which is the only place a pin link can be declared) and check for "bind" keys that reference nameBefore.
        Enumeration<?> siblings = parent.children ();
        while (siblings.hasMoreElements ())
        {
            Object o = siblings.nextElement ();
            if (! (o instanceof NodePart)) continue;
            NodePart sibling = (NodePart) o;  // This can also include nodeBefore and nodeAfter, so we address self-references.
            MNode pins = sibling.source.child ("$meta", "gui", "pin", "in");
            if (pins == null) continue;
            List<String> bound = new ArrayList<String> ();
            for (MNode pin : pins)
            {
                if (pin.get ("bind").equals (nameBefore))
                {
                    pin.set (nameAfter, "bind");
                    sibling.pinIn.set (nameAfter, pin.key (), "bind");  // Also set the new name in collated pin data.
                    bound.add (pin.key ());
                }
            }
            if (! bound.isEmpty ()) rebind.put (sibling, bound);
        }

        // Check parent for pin exports.
        MNode pins = parent.source.child ("$meta", "gui", "pin", "out");
        if (pins != null)
        {
            List<String> bound = new ArrayList<String> ();
            for (MNode pin : pins)
            {
                if (pin.get ("bind").equals (nameBefore))
                {
                    pin.set (nameAfter, "bind");
                    bound.add (pin.key ());
                }
            }
            if (! bound.isEmpty ()) rebind.put (parent, bound);
        }

        return result;
    }
}

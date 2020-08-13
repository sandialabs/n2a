/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.EquationSet.ConnectionBinding;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Visitor;
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

public class ChangePart extends UndoableView
{
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
        updatePath (nameAfter);
        super.undo ();
        apply (nameAfter, nameBefore);
    }

    public void redo ()
    {
        updatePath (nameBefore);
        super.redo ();
        apply (nameBefore, nameAfter);
    }

    protected void updatePath (String name)
    {
        int viewSize = view.path.size ();
        if (viewSize > path.size ())  // The name change applies to a graph node, which should be the focus.
        {
            view.path.set (viewSize - 1, name);
        }
    }

    public void apply (String nameBefore, String nameAfter)
    {
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

        //   Change connection bindings to this part.
        //   See ChangeVariable.apply() for a similar procedure. More detailed comments appear there.
        //   We make use of static functions in that class to do the heavy work of emitting code with name changes.
        //   TODO: This approach will probably fail on parts that contain references to themselves.
        PanelEquations pe = PanelModel.instance.panelEquations;
        List<List<String>> references = new ArrayList<List<String>> ();
        try
        {
            MPart doc = pe.root.source;
            EquationSet compiled = new EquationSet (doc);
            List<String> keypath = new ArrayList<String> (path.subList (1, path.size ()));
            EquationSet eold;
            EquationSet enew;
            if (oldPart == null)
            {
                EquationSet p = (EquationSet) compiled.getObject (keypath);
                eold = new EquationSet (p, nameBefore);
                p.parts.add (eold);
                keypath.add (nameAfter);
            }
            else
            {
                keypath.add (nameBefore);
                eold = (EquationSet) compiled.getObject (keypath);
                keypath.set (keypath.size () - 1, nameAfter);
            }
            enew = (EquationSet) compiled.getObject (keypath);

            try
            {
                compiled.resolveConnectionBindings ();
            }
            catch (Exception e) {}
            try
            {
                compiled.resolveLHS ();
                compiled.resolveRHS ();
            }
            catch (Exception e) {}
            ChangeVariable.prepareConnections (compiled);

            // Collect variables that might have changed.
            List<Variable> users = collectVariables (compiled, eold);
            if (eold.dependentConnections != null)
            {
                // Each equation set tracks connection bindings which depend on it for their resolution.
                // The variable associated with such a connection binding could explicitly mention the part name.
                for (ConnectionBinding cb : eold.dependentConnections) users.add (cb.variable);
            }

            eold.name = enew.name;
            for (Variable v : users)
            {
                List<String> ref = v.getKeyPath ();
                MNode n = doc.child (ref.toArray ());
                String oldKey = n.key ();
                String newKey = ChangeVariable.changeReferences (eold, n, v);
                if (! newKey.equals (oldKey))  // Handle a change in variable name.
                {
                    NodeBase nb = pe.root.locateNodeFromHere (ref);
                    n.parent ().move (oldKey, newKey);
                    ref.set (ref.size () - 1, newKey);
                    nb.source = (MPart) doc.child (ref.toArray ());
                }
                if (v.container != enew  &&  v.container != eold) references.add (ref);  // Queue GUI updates for nodes other than the primary ones.
            }
        }
        catch (Exception e) {}

        //   Change pin links to this part.
        //   Scan peer parts (which is the only place a pin link can be declared) and check for "bind" keys that reference nameBefore.
        Map<NodePart,List<String>> rebind = new HashMap<NodePart,List<String>> ();  // for updating GUI later
        Enumeration<?> siblings = parent.children ();
        while (siblings.hasMoreElements ())
        {
            Object o = siblings.nextElement ();
            if (! (o instanceof NodePart)) continue;
            NodePart sibling = (NodePart) o;
            MNode pins;
            if (sibling == nodeBefore) pins = parent .source.child (nameAfter, "$metadata", "gui", "pin", "in");  // because the old source is no longer attached to document
            else                       pins = sibling.source.child (           "$metadata", "gui", "pin", "in");
            if (pins == null) continue;
            List<String> bound = null;
            for (MNode pin : pins)
            {
                if (pin.get ("bind").equals (nameBefore))
                {
                    pin.set (nameAfter, "bind");
                    sibling.pinIn.set (nameAfter, pin.key (), "bind");  // Also set the new name in collated pin data.
                    if (bound == null) bound = new ArrayList<String> ();
                    bound.add (pin.key ());
                }
            }
            if (bound != null) rebind.put (sibling, bound);
        }
        //   Check parent for pin exports.
        MNode pins = parent.source.child ("$metadata", "gui", "pin", "out");
        if (pins != null)
        {
            List<String> bound = null;
            for (MNode pin : pins)
            {
                if (pin.get ("bind").equals (nameBefore))
                {
                    pin.set (nameAfter, "bind");
                    if (bound == null) bound = new ArrayList<String> ();
                    bound.add (pin.key ());
                }
            }
            if (bound != null) rebind.put (parent, bound);
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

        nodeAfter.build ();
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

        for (List<String> ref : references)
        {
            NodeVariable n = (NodeVariable) pe.root.locateNodeFromHere (ref);
            if (n == null) continue;

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
            NodeBase metadata = peer.child ("$metadata");  // also works for parent
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

    public List<Variable> collectVariables (EquationSet s, EquationSet renamed)
    {
        List<Variable> result = new ArrayList<Variable> ();
        for (EquationSet p : s.parts) result.addAll (collectVariables (p, renamed));

        // Regular variables might mention the part name, on either the LHS or RHS.
        class PartVisitor implements Visitor
        {
            boolean found;
            public boolean visit (Operator op)
            {
                if (op instanceof AccessVariable)
                {
                    AccessVariable av = (AccessVariable) op;
                    if (av.reference.resolution.contains (renamed)) found = true;
                    return false;
                }
                return true;
            }
        };
        PartVisitor visitor = new PartVisitor ();
        for (Variable v : s.variables)
        {
            visitor.found = v.reference.resolution.contains (renamed);
            for (EquationEntry ee : v.equations)
            {
                if (visitor.found) break;
                ee.expression.visit (visitor);
                if (ee.condition != null) ee.condition.visit (visitor);
            }
            if (visitor.found) result.add (v);
        }

        return result;
    }
}

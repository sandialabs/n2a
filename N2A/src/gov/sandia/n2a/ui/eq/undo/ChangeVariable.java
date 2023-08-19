/*
Copyright 2017-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationGraph;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeEquation;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class ChangeVariable extends UndoableView
{
    protected List<String> path;
    protected String       nameBefore;
    protected String       nameAfter;
    protected String       valueBefore;
    protected String       valueAfter;
    protected MNode        savedTree;   // The entire subtree from the top document. If not from top document, then at least a single node for the variable itself.
    protected boolean      killed;
    protected boolean      multi;

    /**
        @param node The variable being changed.
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

        killed = node.source.getFlag ("$kill");
    }

    public void setMulti (boolean value)
    {
        multi = value;
    }

    public void undo ()
    {
        super.undo ();
        savedTree.set (valueBefore);
        apply (nameAfter, nameBefore, killed, multi);
    }

    public void redo ()
    {
        super.redo ();
        savedTree.set (valueAfter);
        apply (nameBefore, nameAfter, false, multi);
    }

    public void apply (String nameBefore, String nameAfter, boolean killed, boolean multi)
    {
        NodePart parent = (NodePart) NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodeVariable nodeBefore = (NodeVariable) parent.child (nameBefore);
        if (nodeBefore == null) throw new CannotRedoException ();

        PanelEquations pe = PanelModel.instance.panelEquations;
        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = null;
        if (pet != null) model = (FilteredTreeModel) pet.tree.getModel ();

        NodeVariable nodeAfter;
        boolean touchedBindings = false;
        boolean sameName = nameBefore.equals (nameAfter);
        if (sameName)
        {
            nodeAfter = nodeBefore;
            MPart mchild = nodeAfter.source;
            mchild.set (savedTree.get ());  // Same as valueAfter. Sub-tree is not relevant here.
            updateRevokation (mchild, killed);
        }
        else
        {
            // Update database
            MPart mparent = parent.source;
            mparent.clear (nameBefore);
            mparent.clear (nameAfter);  // Removes any reference changes in target node.
            mparent.set (savedTree, nameAfter);
            MPart newPart = (MPart) mparent.child (nameAfter);
            MPart oldPart = (MPart) mparent.child (nameBefore);
            updateRevokation (newPart, killed);

            // Update GUI
            nodeAfter = (NodeVariable) parent.child (nameAfter);
            if (oldPart == null)
            {
                if (nodeBefore.isBinding)
                {
                    if (parent.graph != null) parent.connectionBindings.remove (nameBefore);
                    touchedBindings = true;
                }

                if (nodeAfter == null)
                {
                    nodeAfter = nodeBefore;
                    nodeAfter.source = newPart;
                }
                else
                {
                    if (model == null) FilteredTreeModel.removeNodeFromParentStatic (nodeBefore);
                    else               model.removeNodeFromParent (nodeBefore);
                }
                nodeBefore = null;
            }
            else
            {
                if (nodeAfter == null)
                {
                    int index = parent.getIndex (nodeBefore);
                    nodeAfter = new NodeVariable (newPart);
                    if (model == null) FilteredTreeModel.insertNodeIntoUnfilteredStatic (nodeAfter, parent, index);
                    else               model.insertNodeIntoUnfiltered (nodeAfter, parent, index);
                }

                nodeBefore.build ();
                nodeBefore.filter ();
                if (nodeBefore.visible ())
                {
                    if (model != null) model.nodeStructureChanged (nodeBefore);
                }
                else
                {
                    parent.hide (nodeBefore, model);
                }
            }
        }

        nodeAfter.build ();
        nodeAfter.filter ();
        NameVisitor nameVisitor = null;
        if (! sameName)
        {
            nameVisitor = new NameVisitor (nameBefore, nameAfter, nameAfter.equals (this.nameBefore), nodeBefore, nodeAfter);
            if (! nameVisitor.nameBefore.equals (nameVisitor.nameAfter))  // Special case: skip $all and $each
            {
                nameVisitor.setFakeObject ();
                try {pe.root.visit (nameVisitor);}
                catch (Exception e) {e.printStackTrace ();}  // Trap the exception so we can at least clearFakeObject().
                nameVisitor.clearFakeObject ();
            }
        }
        Set<PanelEquationTree> needAnimate = new HashSet<PanelEquationTree> ();
        if (pet != null)
        {
            needAnimate.add (pet);
            pet.updateHighlights (pet.root, nodeAfter);  // Must be done before invalidateColumns(), because that function causes new columns to be fetched for renderer sizing.
            parent.invalidateColumns (model);
            TreeNode[] nodePath = nodeAfter.getPath ();
            pet.updateOrder (nodePath);
            pet.updateVisibility (nodePath, -2, ! multi);
            if (multi) pet.tree.addSelectionPath (new TreePath (nodePath));
        }

        if (nameVisitor != null)
        {
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

                    boolean added = needAnimate.add (subpet);
                    if (added) subpet.updateHighlights (subpet.root, nodeAfter);
                    else       subpet.updateHighlights (n, nodeAfter);
                    subparent.invalidateColumns (null);
                    submodel.nodeStructureChanged (n);  // Node will collapse if it was open. Don't worry about this.
                    subpet.updateVisibility (n.getPath (), -2, false);
                    subparent.allNodesChanged (submodel);
                }
            }
        }

        for (PanelEquationTree ap : needAnimate) ap.animate ();

        if (parent.updateVariableConnections ()) touchedBindings = true;
        if (touchedBindings)
        {
            parent.updateSubpartConnections ();

            // Update edges to pins, if present.
            PanelEquationGraph peg = pe.panelEquationGraph;
            if (parent.source.child ("$meta", "gui", "pin") != null)
            {
                parent.updatePins ();
                peg.updatePins ();
            }
            // Rebuild all edges on canvas, whether regular connection or pin.
            peg.reconnect ();
            peg.repaint ();
        }
        if (touchedBindings  ||  nodeAfter.isBinding)
        {
            MPart mparent = parent.source;
            if (mparent.root () == mparent) PanelModel.instance.panelSearch.updateConnectors (mparent);
        }
    }

    public static void updateRevokation (MPart mchild, boolean wasKilled)
    {
        MPart mflag = (MPart) mchild.child ("$kill");
        if (mchild.getFlag ("$kill"))  // Currently revoked, so restore it.
        {
            if (mflag.isInherited ()) mflag.set ("0");
            else                      mchild.clear ("$kill");
        }
        else if (wasKilled)  // Currently active, but we are undoing something that was originally revoked.
        {
            if (mflag == null) mchild.set ("", "$kill"); // was local
            else               mchild.clear ("$kill");   // revert to inherited
        }
    }

    public static class NameVisitor implements NodeBase.Visitor
    {
        public String   nameBefore;
        public String   nameAfter;
        public boolean  periphery;  // Indicates not to touch nodeBefore or nodeAfter.
        public NodeBase nodeBefore;
        public NodeBase nodeAfter;
        public NodeBase renamed;

        public HashSet<NodeVariable> references = new HashSet<NodeVariable> ();

        public NameVisitor (String nameBefore, String nameAfter, boolean periphery, NodeBase nodeBefore, NodeBase nodeAfter)
        {
            // Strip $up, $all and $each prefixes, because ...
            // $all and $each should not appear in references.
            // $up produces non-reversible changes in references, and it is an unlikely use-case.
            while (nameBefore.startsWith ("$up.")) nameBefore = nameBefore.substring (4);
            while (nameAfter .startsWith ("$up.")) nameAfter  = nameAfter .substring (4);
            this.nameBefore = Variable.stripContextPrefix (nameBefore);
            this.nameAfter  = Variable.stripContextPrefix (nameAfter);
            this.periphery  = periphery;
            this.nodeBefore = nodeBefore;
            this.nodeAfter  = nodeAfter;
        }

        public void setFakeObject ()
        {
            NodePart parent = (NodePart) nodeAfter.getTrueParent ();
            renamed = parent.child (nameBefore);  // Our ctor parameter may still have context prefix.
            if (renamed != null) return;  // In this case, renamed should also equal nodeBefore.
            MPart c = new MPart (new MVolatile (nameBefore));
            if (nodeAfter instanceof NodeVariable) renamed = new NodeVariable (c);
            else                                   renamed = new NodePart (c);
            parent.add (renamed);
        }

        /**
            If our ctor created a fake object, then remove it.
        **/
        public void clearFakeObject ()
        {
            if (renamed == nodeBefore) return;
            NodePart parent = (NodePart) nodeAfter.getTrueParent ();
            parent.remove (renamed);
        }

        public boolean visit (NodeBase n)
        {
            if (periphery  &&  (n == nodeBefore  ||  n == nodeAfter)) return false;
            if (n == renamed  &&  renamed != nodeBefore) return false;  // Don't process the fake target, if it exists.

            if (n instanceof NodeVariable)
            {
                if (n == nodeAfter) return true;  // Don't modify expression on variable line itself. Instead, assume the user edited it exactly as intended.
                NodeVariable nv = (NodeVariable) n;
                NodePart parent = (NodePart) n.getTrueParent ();

                // Check LHS
                MNode m = n.source;
                String key = m.key ();
                if (key.contains (nameBefore)  &&  key.contains ("."))
                {
                    // Check if key actually references the renamed object.
                    String newKey = resolve (parent, key);
                    if (newKey != null  &&  parent.child (newKey) == null)
                    {
                        MNode mparent = parent.source;
                        mparent.move (key, newKey);
                        m = nv.source = (MPart) mparent.child (newKey);
                        references.add (nv);
                    }
                }

                // Check RHS
                String value = m.get ();
                if (value.contains (nameBefore))
                {
                    // Check if any name paths in the expression actually references the renamed object.
                    Variable.ParsedValue pv = new Variable.ParsedValue (value);
                    String newExpression = null;
                    String newCondition  = null;
                    if (! pv.expression.isBlank ()) newExpression = resolveExpression (parent, pv.expression);
                    if (! pv.condition .isBlank ()) newCondition  = resolveExpression (parent, pv.condition);
                    if (newExpression != null  ||  newCondition != null)
                    {
                        if (newExpression != null) pv.expression = newExpression;
                        if (newCondition  != null) pv.condition  = newCondition;
                        m.set (pv.toString ());
                        references.add (nv);
                    }
                }

                return true;  // Drill down to any equations under this variable.
            }

            if (n instanceof NodeEquation)
            {
                NodeEquation ne = (NodeEquation) n;
                NodeVariable nv = (NodeVariable) n .getParent ();
                NodePart     np = (NodePart)     nv.getParent ();

                MNode m = ne.source;
                String key = m.key ();  // Starts with @
                if (key.contains (nameBefore))
                {
                    String newKey = resolveExpression (np, key.substring (1));  // Process without @
                    if (newKey != null)
                    {
                        newKey = "@" + newKey;
                        if (nv.child (newKey) == null)
                        {
                            MNode mparent = nv.source;
                            mparent.move (key, newKey);
                            m = ne.source = (MPart) mparent.child (newKey);
                            references.add (nv);
                        }
                    }
                }

                String expression = m.get ();
                if (expression.contains (nameBefore))
                {
                    String newExpression = resolveExpression (np, expression);
                    if (newExpression != null)
                    {
                        m.set (newExpression);
                        references.add (nv);
                    }
                }

                return false;
            }

            if (n instanceof NodePart) return true;
            return false;
        }

        /**
            Follow the name path to see if any part of it resolves to the renamed object.
            If so, construct a replacement string.
            @return The replacement string, or null if the name does no intersect with the
            renamed object.
        **/
        public String resolve (NodePart part, String name)
        {
            name = Variable.stripContextPrefix (name);
            String[] names = name.split ("\\.");
            for (int i = 0; i < names.length; i++) names[i] = names[i].trim ();

            int index = part.resolveName (names, 0, renamed);
            if (index < 0) return null;

            names[index] = nameAfter;
            String result = names[0];
            for (int i = 1; i < names.length; i++) result += "." + names[i];
            return result;
        }

        /**
            Parse the expression and for each reference to a variable, follow the name path
            to see if any part of it resolves to the renamed object. If so, construct a
            replacement string for the entire expression. The affected references will be
            rewritten in place, with minimal impact on surrounding text, including white space.
            @return The replacement string, or null if none of the variable names intersect
            with the renamed object.
        **/
        public String resolveExpression (NodePart part, String expression)
        {
            // Parse the expression.
            Operator op = null;
            try {op = Operator.parse (expression);}
            catch (Exception e) {return null;}

            // Create a sorted list of references that need to be rewritten.
            class ResolutionVisitor implements gov.sandia.n2a.language.Visitor
            {
                public TreeMap<Integer,AccessVariable> changes = new TreeMap<Integer,AccessVariable> ();

                public boolean visit (Operator op)
                {
                    if (op instanceof AccessVariable)
                    {
                        AccessVariable av = (AccessVariable) op;
                        String newName = resolve (part, av.name);
                        if (newName != null)
                        {
                            // An identifier does not have leading spaces, but can have trailing spaces. Preserve these.
                            av.name = newName;
                            for (int i = 0; i < av.trailingSpaces; i++) av.name += " ";
                            changes.put (av.columnBegin, av);
                        }
                        return false;
                    }
                    return true;
                }
            };
            ResolutionVisitor visitor = new ResolutionVisitor ();
            op.visit (visitor);
            if (visitor.changes.isEmpty ()) return null;

            // Edit the original expression.
            Entry<Integer,AccessVariable> e;
            while ((e = visitor.changes.pollLastEntry ()) != null)
            {
                AccessVariable av = e.getValue ();
                expression = expression.substring (0, av.columnBegin) + av.name + expression.substring (av.columnEnd);
            }
            return expression;
        }
    }
}

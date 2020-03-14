/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.EquationSet.ConnectionBinding;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.PanelEquations.StoredView;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

public class ChangeVariable extends Undoable
{
    protected StoredView    view = PanelModel.instance.panelEquations.new StoredView ();
    protected List<String>  path;
    protected String        nameBefore;
    protected String        nameAfter;
    protected String        valueBefore;
    protected String        valueAfter;
    protected MNode         savedTree;   // The entire subtree from the top document. If not from top document, then at least a single node for the variable itself.
    protected List<String>  replacePath; // If a newly-created variable turns out to modify another node, this lets us remove the AddVariable from the undo stack.

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
        view.restore ();
        NodePart parent = (NodePart) NodeBase.locateNode (path);
        if (parent == null) throw new CannotRedoException ();
        NodeVariable nodeBefore = (NodeVariable) parent.child (nameBefore);
        if (nodeBefore == null) throw new CannotRedoException ();

        PanelEquationTree pet = parent.getTree ();
        FilteredTreeModel model = null;
        if (pet != null) model = (FilteredTreeModel) pet.tree.getModel ();

        NodeVariable nodeAfter;
        boolean touchedBindings = false;
        if (nameBefore.equals (nameAfter))
        {
            nodeAfter = nodeBefore;
            nodeAfter.source.set (savedTree.get ());  // Same as valueAfter. Sub-tree is not relevant here.
        }
        else
        {
            //   Move the subtree
            MPart mparent = parent.source;
            mparent.clear (nameBefore);
            mparent.clear (nameAfter);  // Removes any reference changes in target node.
            mparent.set (savedTree, nameAfter);
            MPart newPart = (MPart) mparent.child (nameAfter);
            MPart oldPart = (MPart) mparent.child (nameBefore);

            //   Change references to this variable
            PanelEquations pe = PanelModel.instance.panelEquations;
            List<List<String>> references = new ArrayList<List<String>> ();  // Key paths to each variable that references this one.
            try
            {
                // "doc" is a collated model, so changes will also be made to references from inherited nodes.
                // Such changes will be saved as an override.
                MPart doc = pe.root.source;
                EquationSet compiled = new EquationSet (doc);
                List<String> vkeypath = new ArrayList<String> (path.subList (1, path.size ()));
                Variable vold;
                Variable vnew;
                if (oldPart == null)
                {
                    EquationSet p = (EquationSet) compiled.getObject (vkeypath);
                    vold = Variable.fromLHS (nameBefore);
                    vold.equations = new TreeSet<EquationEntry> ();
                    if (nodeBefore.isBinding) vold.equations.add (new EquationEntry (newPart.get ()));
                    p.add (vold);
                    vkeypath.add (nameAfter);
                }
                else
                {
                    vkeypath.add (nameBefore);
                    vold = (Variable) compiled.getObject (vkeypath);
                    vkeypath.set (vkeypath.size () - 1, nameAfter);
                }
                vnew = (Variable) compiled.getObject (vkeypath);

                try
                {
                    compiled.resolveConnectionBindings ();
                    compiled.resolveLHS ();
                    // This will very likely throw an AbortRun exception to report unresolved variables.
                    // This will do no harm. All we need is that other equations resolve to this variable.
                    compiled.resolveRHS ();
                }
                catch (Exception e) {}

                if (vold.usedBy != null)
                {
                    vold.name  = vnew.name;
                    vold.order = vnew.order;
                    for (Object o : vold.usedBy)
                    {
                        if (! (o instanceof Variable)) continue;
                        if (nameAfter.equals (this.nameBefore)  &&  (o == vnew  ||  o == vold)) continue;  // On undo, don't touch savedTree or exposed node. They should return to their exact previous values.

                        Variable v = (Variable) o;
                        List<String> ref = v.getKeyPath ();
                        MNode n = doc.child (ref.toArray ());
                        String oldKey = n.key ();
                        String newKey = changeReferences (vold, n, v);
                        if (! newKey.equals (oldKey))  // Handle a change in variable name.
                        {
                            NodeBase nb = pe.root.locateNodeFromHere (ref);
                            n.parent ().move (oldKey, newKey);
                            ref.set (ref.size () - 1, newKey);
                            nb.source = (MPart) doc.child (ref.toArray ());
                        }
                        if (o != vnew  &&  o != vold) references.add (ref);  // Queue GUI updates for nodes other than the primary ones.
                    }
                }
            }
            catch (Exception e) {e.printStackTrace ();}

            // Update GUI

            nodeAfter = (NodeVariable) parent.child (nameAfter);
            if (oldPart == null)
            {
                if (nodeBefore.isBinding)
                {
                    if (parent.graph != null) parent.graph.updateGUI (nameBefore, "");  // remove old connection edge
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
                if (nodeBefore.isBinding)
                {
                    if (parent.graph != null) parent.graph.updateGUI (nameBefore, oldPart.get ());
                    touchedBindings = true;
                }
            }

            for (List<String> ref : references)
            {
                NodeVariable n = (NodeVariable) pe.root.locateNodeFromHere (ref);
                if (n == null) continue;

                // Rebuild n, because equations and/or their conditions may have changed.
                n.build ();
                n.findConnections ();
                n.filter (FilteredTreeModel.filterLevel);
                if (n.visible (FilteredTreeModel.filterLevel))  // n's visibility won't change
                {
                    PanelEquationTree subpet = n.getTree ();
                    if (subpet == null) continue;
                    JTree subtree = subpet.tree;
                    FilteredTreeModel submodel = (FilteredTreeModel) subtree.getModel ();
                    NodeBase subparent = (NodeBase) n.getParent ();

                    submodel.nodeStructureChanged (n);  // Node will collapse if it was open. Don't worry about this.

                    FontMetrics fm = n.getFontMetrics (subtree);
                    n.updateColumnWidths (fm);
                    subparent.updateTabStops (fm);
                    subparent.allNodesChanged (submodel);
                }
            }
        }

        boolean wasBinding = nodeAfter.isBinding;
        nodeAfter.build ();
        nodeAfter.findConnections ();
        nodeAfter.filter (FilteredTreeModel.filterLevel);
        if (pet != null)
        {
            FontMetrics fm = nodeAfter.getFontMetrics (pet.tree);
            nodeAfter.updateColumnWidths (fm);
            parent.updateTabStops (fm);
            parent.allNodesChanged (model);

            TreeNode[] nodePath = nodeAfter.getPath ();
            pet.updateOrder (nodePath);
            pet.updateVisibility (nodePath, -2, ! nodeAfter.isBinding);
            pet.animate ();
        }

        if (parent.graph != null)
        {
            if (nodeAfter.isBinding) parent.graph.updateGUI (nameAfter, nodeAfter.source.get ());
            else if (wasBinding)     parent.graph.updateGUI (nameAfter, "");
        }

        if (nodeAfter.isBinding  ||  wasBinding) touchedBindings = true;
        if (touchedBindings)
        {
            MPart mparent = parent.source;
            if (mparent.getRoot () == mparent) PanelModel.instance.panelSearch.updateConnectors (mparent);
        }
    }

    /**
        Given variable "v", change all places where its code references variable "renamed".
        The name change is already embedded in the compiled model. We simply re-render the code.
        @param renamed The variable that has a new name.
        @param mv The database object associated with the compiled variable.
        @param v The compiled variable, which is part of the fully-compiled model.
    **/
    public String changeReferences (Variable renamed, MNode mv, Variable v)
    {
        if (v.equations.size () == 1)  // single-line equation
        {
            EquationEntry ee = v.equations.first ();
            String ifString = "";
            if (ee.condition != null) ifString = "@" + changeExpression (ee.condition, renamed, v.container);
            mv.set (v.combinerString () + changeExpression (ee.expression, renamed, v.container) + ifString);
        }
        else  // multi-line equation
        {
            try  // Mostly, to trap parse errors when mapping keys.
            {
                // Create a mapping from equation entries to database nodes.
                // The idea here is to re-render the keys just like they have been for EquationEntry.ifString.
                // This lets us use ifString as the key.
                Map<String,MNode> map = new HashMap<String,MNode> ();
                for (MNode e : mv)
                {
                    String key = e.key ();
                    if (! key.startsWith ("@")) continue;
                    key = key.substring (1);
                    if (! key.isEmpty ()) key = Operator.parse (key).render ();
                    map.put (key, e);
                }

                // Modify each equation entry and post to db.
                for (EquationEntry ee : v.equations)
                {
                    MNode me = map.get (ee.ifString);
                    me.set (changeExpression (ee.expression, renamed, v.container));

                    String ifString = "@";
                    if (ee.condition != null) ifString += changeExpression (ee.condition, renamed, v.container);
                    mv.move (me.key (), ifString);
                }
            }
            catch (Exception e) {e.printStackTrace ();}
        }

        // The name of v can also change, since it might describe a path through a changed connection binding. See EquationSet.resolveLHS().
        AccessVariable av = new AccessVariable (v.nameString ());
        av.reference = v.reference;
        return changeExpression (av, renamed, v.container);
    }

    public String changeExpression (Operator expression, Variable renamed, EquationSet container)
    {
        Renderer r = new Renderer ()
        {
            public boolean render (Operator op)
            {
                if (op instanceof AccessVariable)
                {
                    AccessVariable av = (AccessVariable) op;

                    // Safety checks. We won't modify code unless we are confident that it can be done correctly.
                    boolean safe =  av.reference != null  &&  av.reference.variable != null;  // Must be fully resolved.
                    if (safe  &&  av.reference.variable != renamed)
                    {
                        // If the endpoint is not "renamed", then "renamed" must occur along the resolution path.
                        safe = false;
                        for (Object o : av.reference.resolution)
                        {
                            if (! (o instanceof ConnectionBinding)) continue;
                            if (((ConnectionBinding) o).variable == renamed)
                            {
                                safe = true;
                                break;
                            }
                        }
                    }
                    if (! safe)
                    {
                        result.append (av.name);  // This is the original text from the parser.
                        return true;
                    }

                    // Walk the resolution path and emit a new variable name.
                    EquationSet current = container;
                    int last = av.reference.resolution.size () - 1;
                    for (int i = 0; i <= last; i++)
                    {
                        Object o = av.reference.resolution.get (i);
                        if (o instanceof EquationSet)  // We are following the containment hierarchy.
                        {
                            EquationSet s = (EquationSet) o;
                            if (s.container == current)  // descend into one of our contained populations
                            {
                                result.append (s.name + ".");
                            }
                            else  // ascend to our container
                            {
                                // Method of ascent depends on visibility of next symbol.
                                String nextName;
                                if (i == last)
                                {
                                    nextName = av.reference.variable.nameString ();
                                }
                                else
                                {
                                    Object nextObject = av.reference.resolution.get (i+1);
                                    if (nextObject instanceof EquationSet) nextName = ((EquationSet) nextObject).name;
                                    else                                   nextName = ((ConnectionBinding) nextObject).alias;
                                }
                                if (isVisible (current, nextName)) result.append ("$up.");
                                // else no need to emit anything. A symbol not visible in current equation set will automatically be referred up.
                                // It's also possible that the user explicitly named a container (either direct or ancestor), in which case we will be rewriting code.
                                // It would take more effort to match the original user-specified path string to the computer resolution path.
                                // That would be the most information-preserving way to do the name change.
                            }
                            current = s;
                        }
                        else if (o instanceof ConnectionBinding)  // We are following a part reference (which means we are a connection)
                        {
                            ConnectionBinding c = (ConnectionBinding) o;
                            result.append (c.variable.name + ".");
                            current = c.endpoint;
                        }
                    }
                    result.append (av.reference.variable.nameString ());

                    return true;
                }

                return false;
            }
        };

        expression.render (r);
        return r.result.toString ();
    }

    public boolean isVisible (EquationSet container, String name)
    {
        if (container.find (Variable.fromLHS (name)) != null) return true;  // Does a variable with given name exist?
        EquationSet p = container.findPart (name);
        return  p != null  &&  p.name.equals (name);
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

/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.eqset.EquationSet.ConnectionBinding;
import gov.sandia.n2a.language.AccessVariable;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Renderer;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelEquationTree;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
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
    protected List<String> replacePath; // If a newly-created variable turns out to modify another node, this lets us remove the AddVariable from the undo stack.
    protected boolean      multi;
    protected boolean      multiLast;

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

    public void setMulti (boolean value)
    {
        multi = value;
    }

    public void setMultiLast (boolean value)
    {
        multiLast = value;
    }

    public void undo ()
    {
        super.undo ();
        savedTree.set (valueBefore);
        apply (nameAfter, nameBefore, multi, multiLast);
    }

    public void redo ()
    {
        super.redo ();
        savedTree.set (valueAfter);
        apply (nameBefore, nameAfter, multi, multiLast);
    }

    public void apply (String nameBefore, String nameAfter, boolean multi, boolean multiLast)
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
        List<List<String>> references = new ArrayList<List<String>> ();  // Key paths to each variable that references this one.
        if (nameBefore.equals (nameAfter))
        {
            nodeAfter = nodeBefore;
            nodeAfter.source.set (savedTree.get ());  // Same as valueAfter. Sub-tree is not relevant here.
        }
        else
        {
            // Update database

            //   Move the subtree
            MPart mparent = parent.source;
            mparent.clear (nameBefore);
            mparent.clear (nameAfter);  // Removes any reference changes in target node.
            mparent.set (savedTree, nameAfter);
            MPart newPart = (MPart) mparent.child (nameAfter);
            MPart oldPart = (MPart) mparent.child (nameBefore);

            //   Change references to this variable
            //   See ChangePart.apply() for a similar procedure.
            try
            {
                // "doc" is a collated model, so changes will also be made to references from inherited nodes.
                // Such changes will be saved as an override.
                MPart doc = pe.root.source;
                EquationSet compiled = new EquationSet (doc);  // TODO: this is a potentially lengthy operation. For very large models, need to reduce load on EDT. Either maintain incremental compilation, or compile on separate thread.
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
                    // This will throw an AbortRun if any connection is not properly bound.
                    // However, not every connection binding necessarily leads to this variable.
                    compiled.resolveConnectionBindings ();
                }
                catch (Exception e) {}
                try
                {
                    compiled.resolveLHS ();
                    // This will very likely throw an AbortRun exception to report unresolved variables.
                    // This will do no harm. All we need is that other equations resolve to this variable.
                    compiled.resolveRHS ();
                }
                catch (Exception e) {}
                prepareConnections (compiled);

                List<Variable> users = new ArrayList<Variable> ();
                if (vold.usedBy != null)
                {
                    for (Object o : vold.usedBy)
                    {
                        if (! (o instanceof Variable)) continue;
                        if ((o == vnew  ||  o == vold)  &&  nameAfter.equals (this.nameBefore)) continue;  // On undo, don't touch savedTree or exposed node. They should return to their exact previous values.
                        users.add ((Variable) o);
                    }
                }
                // A variable depends on other variables which write to it. These appear in its "uses" member.
                // The write relationship is created by naming the target variable on the LHS of the source variable.
                // This kind of relationship is not generally reciprocal, so we must check uses as well as usedBy.
                if (vold.uses != null)
                {
                    for (Variable v : vold.uses.keySet ())
                    {
                        if (v.reference.variable == vold) users.add (v);
                    }
                }

                vold.name  = vnew.name;
                vold.order = vnew.order;
                for (Variable v : users)
                {
                    if (v == vnew  &&  v.equations.size () == 1) continue;  // Don't modify expression on variable line itself. Instead, assume the user edited it exactly as intended.

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
                    if (v != vnew  &&  v != vold) references.add (ref);  // Queue GUI updates for nodes other than the primary ones.
                }
            }
            catch (Exception e) {}


            // Update GUI

            nodeAfter = (NodeVariable) parent.child (nameAfter);
            if (oldPart == null)
            {
                if (nodeBefore.isBinding)
                {
                    if (parent.graph != null)
                    {
                        parent.connectionBindings.remove (nameBefore);
                        parent.graph.killEdge (nameBefore);  // remove old connection edge
                    }
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

                boolean wasBinding = nodeBefore.isBinding;
                nodeBefore.build ();
                nodeBefore.findConnections ();
                nodeBefore.filter ();
                if (nodeBefore.visible ())
                {
                    if (model != null) model.nodeStructureChanged (nodeBefore);
                }
                else
                {
                    parent.hide (nodeBefore, model);
                }
                if (nodeBefore.isBinding  ||  wasBinding)
                {
                    touchedBindings = true;
                    if (parent.graph != null)
                    {
                        if (nodeBefore.isBinding) parent.graph.updateEdge (nameBefore, parent.connectionBindings.get (nameBefore));
                        else if (wasBinding)      parent.graph.killEdge   (nameBefore);
                    }
                }
            }
        }

        boolean wasBinding = nodeAfter.isBinding;
        nodeAfter.build ();
        nodeAfter.findConnections ();
        nodeAfter.filter ();
        Set<PanelEquationTree> needAnimate = new HashSet<PanelEquationTree> ();
        if (pet != null)
        {
            needAnimate.add (pet);
            pet.updateHighlights (pet.root, nameAfter);  // Must be done before invalidateColumns(), because that function causes new columns to be fetched for renderer sizing.
            parent.invalidateColumns (model);
            TreeNode[] nodePath = nodeAfter.getPath ();
            pet.updateOrder (nodePath);
            boolean killed = savedTree.get ().equals ("$kill");
            boolean setSelection;
            if (killed) setSelection =  ! multi  ||  multiLast;
            else        setSelection =  ! multi;
            pet.updateVisibility (nodePath, -2, setSelection);
            if (multi  &&  ! killed) pet.tree.addSelectionPath (new TreePath (nodePath));
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

                boolean added = needAnimate.add (subpet);
                if (added) subpet.updateHighlights (subpet.root, nameAfter);
                else       subpet.updateHighlights (n, nameAfter);
                subparent.invalidateColumns (null);
                submodel.nodeStructureChanged (n);  // Node will collapse if it was open. Don't worry about this.
                subpet.updateVisibility (n.getPath (), -2, false);
                subparent.allNodesChanged (submodel);
            }
        }

        for (PanelEquationTree ap : needAnimate) ap.animate ();

        if (nodeAfter.isBinding  ||  wasBinding)
        {
            touchedBindings = true;
            if (parent.graph != null)
            {
                if (nodeAfter.isBinding) parent.graph.updateEdge (nameAfter, parent.connectionBindings.get (nameAfter));
                else if (wasBinding)     parent.graph.killEdge   (nameAfter);
            }
        }

        if (touchedBindings)
        {
            parent.updateConnections ();

            MPart mparent = parent.source;
            if (mparent.root () == mparent) PanelModel.instance.panelSearch.updateConnectors (mparent);
        }
    }

    /**
        Tags dependencies between connection binding variables.
        Also prepares the variable held in each connection binding so it can be emitted by changeExpression()
        and associated functions.
    **/
    public static void prepareConnections (EquationSet s)
    {
        for (EquationSet p : s.parts) prepareConnections (p);

        if (s.connectionBindings == null) return;
        for (ConnectionBinding cb : s.connectionBindings)
        {
            cb.addDependencies ();
            cb.variable.container = s;  // Re-attach variable to container, so we can use Variable.getKeyPath()
            cb.variable.addAttribute ("instance");

            // Hack to make cb.variable look as if it has been resolved ...
            AccessVariable av = (AccessVariable) cb.variable.equations.first ().expression;
            av.reference = new VariableReference ();
            av.reference.variable = cb.variable;
            av.reference.resolution = cb.resolution;  // must not be modified after this
        }
    }

    /**
        Given variable "v", change all places where its code references "renamed".
        The name change is already embedded in the compiled model. We simply re-render the code.
        @param renamed The object that has a new name, for safety checks.
            This code is called by both ChangeVariable and ChangePart, so "renamed" can be either a Variable or an EquationSet.
        @param mv The database object associated with the compiled variable.
        @param v The compiled variable, which is part of the fully-compiled model.
    **/
    public static String changeReferences (Object renamed, MNode mv, Variable v)
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
            catch (Exception e) {}
        }

        // The name of v can also change, since it might describe a path through a changed part or connection binding. See EquationSet.resolveLHS().
        AccessVariable av = new AccessVariable (v.nameString ());
        av.reference = v.reference;
        return changeExpression (av, renamed, v.container);
    }

    public static String changeExpression (Operator expression, Object renamed, EquationSet container)
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
                            if (o == renamed  ||  o instanceof ConnectionBinding  &&  ((ConnectionBinding) o).variable == renamed)
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
                    EquationSet current = container;  // The working target of resolution.
                    EquationSet home    = container;  // Where the resolution actually is, based on emitted path so far.
                    String path = "";
                    int last = av.reference.resolution.size () - 1;
                    for (int i = 0; i <= last; i++)
                    {
                        Object o = av.reference.resolution.get (i);
                        if (o instanceof EquationSet)  // We are following the containment hierarchy.
                        {
                            EquationSet s = (EquationSet) o;
                            if (s.container == current)  // descend into one of our contained populations
                            {
                                path += emitPath (home, current, s.name);
                                path += s.name + ".";
                                home = s;
                            }
                            // else ascend to our container
                            // The resolution for the ascent will be handled as soon as we need to reference a sub-part or variable.
                            current = s;
                        }
                        else if (o instanceof ConnectionBinding)  // We are following a part reference (which means we are a connection)
                        {
                            ConnectionBinding c = (ConnectionBinding) o;
                            String name = c.variable.nameString ();
                            path += emitPath (home, current, name);
                            path += name + ".";
                            home = c.endpoint;
                            current = c.endpoint;
                        }
                    }
                    String name = av.reference.variable.nameString ();
                    path += emitPath (home, current, name);
                    if (av.reference.variable.hasAttribute ("instance"))
                    {
                        if (! path.isEmpty ()) path = path.substring (0, path.length () - 1);  // Get rid of trailing dot.
                    }
                    else
                    {
                        path += name;
                    }
                    result.append (path);

                    return true;
                }
                if (op instanceof Constant)
                {
                    Constant c = (Constant) op;
                    if (c.unitValue != null)
                    {
                        result.append (c.unitValue);
                        return true;
                    }
                }

                return false;
            }
        };

        expression.render (r);
        return r.result.toString ();
    }

    public static String emitPath (EquationSet home, EquationSet target, String name)
    {
        // If there is an unambiguous path up to "name", then emit nothing.
        EquationSet e = home;
        while (e != target)
        {
            if (isVisible (e, name)) break;
            e = e.container;
        }
        if (e == target) return "";

        // If there is an unambiguous path to the container of "name", then use that.
        if (target.container != null)  // Only do this if not top-level container, as that container can't be referenced explicitly.
        {
            int depth = 0;  // Count depth to decide between using this method or $up. For a single level, $up is a nicer choice. For many levels, an explicit part name is more concise.
            e = home;
            while (e != target)
            {
                depth++;
                if (isVisible (e, target.name)) break;
                e = e.container;
            }
            if (e == target  &&  depth > 1) return target.name + ".";
        }

        // Otherwise, use $up.
        String result = "";
        e = home;
        while (e != target)
        {
            result += "$up.";
            e = e.container;
        }
        return result;
    }

    public static boolean isVisible (EquationSet container, String name)
    {
        // Does a variable with given name exist?
        if (container.find (Variable.fromLHS (name)) != null) return true;

        // Is there a connection binding with the given name?
        if (container.connectionBindings != null)
        {
            for (ConnectionBinding cb : container.connectionBindings)
            {
                if (cb.alias.equals (name)) return true;
            }
        }

        // Is there a sub-part with the given name?
        return container.findPart (name) != null;
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

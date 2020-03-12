/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;
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
            // Update database

            //   Move the subtree
            MPart mparent = parent.source;
            mparent.clear (nameBefore);
            mparent.set (savedTree, nameAfter);
            MPart newPart = (MPart) mparent.child (nameAfter);
            MPart oldPart = (MPart) mparent.child (nameBefore);

            //   Change references to this variable
            PanelEquations pe = PanelModel.instance.panelEquations;
            List<List<String>> references = new ArrayList<List<String>> ();  // Key paths to each variable that references this one.
            try
            {
                // doc is a collated model, so changes will also be made to references from inherited nodes.
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
                    // This will very likely throw an AbortRun exception to report unresolved variables.
                    // This will do no harm. All we need is that other equations resolve to this variable.
                    compiled.resolveRHS ();
                }
                catch (Exception e) {}

                // TODO: Handle connection bindings
                // Iterate over usedBy, but use a special renderer rather than changeReferences().
                // The renderer should get changed name from ConnectionBinding.

                if (vold.usedBy != null)
                {
                    for (Object o : vold.usedBy)
                    {
                        if (! (o instanceof Variable)) continue;
                        if (o == vnew  &&  nameAfter.equals (this.nameBefore)) continue;  // Don't touch savedTree on undo, as it is an exact snapshot of the previous value for this variable.

                        List<String> ref = ((Variable) o).getKeyPath ();
                        references.add (ref);
                        Object[] keyArray = ref.toArray ();
                        MNode n = doc.child (keyArray);
                        changeReferences (n, nameBefore, nameAfter);
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
                NodeBase n = pe.root.locateNodeFromHere (ref);
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
        Given a variable node, change all references contained in its code from one variable name to another.
    **/
    public void changeReferences (MNode v, String nameBefore, String nameAfter)
    {
        String value = v.get ();
        if (! value.isEmpty ())
        {
            Variable.ParsedValue pv = new Variable.ParsedValue (value);
            pv.expression = changeExpression (pv.expression, nameBefore, nameAfter);
            pv.condition  = changeExpression (pv.condition,  nameBefore, nameAfter);
            v.set (pv);
        }
        for (MNode e : v)
        {
            String key = e.key ();
            if (! key.startsWith ("@")) continue;
            String newKey = "@" + changeExpression (key.substring (1), nameBefore, nameAfter);
            e.set (changeExpression (e.get (), nameBefore, nameAfter));
            v.move (key, newKey);
        }
    }

    public String changeExpression (String expression, String nameBefore, String nameAfter)
    {
        // First check if a simple search-and-replace will work. This will avoid messing with the user's formatting.
        int pos = expression.indexOf (nameBefore);
        if (pos < 0) return expression;
        if (expression.lastIndexOf (nameBefore) == pos)  // only one occurrence in string
        {
            return expression.replace (nameBefore, nameAfter);
        }

        // Otherwise, parse the equation, modify and re-emit.
        try
        {
            Renderer r = new Renderer ()
            {
                public boolean render (Operator op)
                {
                    if (op instanceof AccessVariable)
                    {
                        AccessVariable av = (AccessVariable) op;
                        String[] pieces = av.name.split ("\\.");
                        if (! pieces[pieces.length - 1].equals (nameBefore)) return false;
                        pieces[pieces.length - 1] = nameAfter;
                        result.append (pieces[0]);
                        for (int i = 1; i < pieces.length; i++) result.append ("." + pieces[i]);
                        return true;
                    }

                    return false;
                }
            };

            Operator op = Operator.parse (expression);
            op.render (r);
            return r.result.toString ();
        }
        catch (Exception e) {}
        return expression;
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

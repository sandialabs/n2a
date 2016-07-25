/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeBinding;
import gov.sandia.n2a.ui.eq.tree.NodeNone;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeReference;
import gov.sandia.n2a.ui.eq.tree.NodeEquation;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;
import gov.sandia.umf.platform.UMF;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;
import gov.sandia.umf.platform.ui.search.SearchType;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import replete.event.ChangeNotifier;
import replete.gui.controls.simpletree.NodeBase;
import replete.gui.controls.simpletree.SimpleTree;
import replete.gui.controls.simpletree.TModel;
import replete.gui.controls.simpletree.TNode;
import replete.gui.windows.common.CommonFrame;

public class EquationTreeEditContext
{
    public UIController uiController;
    public SimpleTree tree;
    public Class<?> addToClass;

    public TModel model;


    protected ChangeNotifier eqChangeNotifier = new ChangeNotifier (this);

    public void addEqChangeListener (ChangeListener listener)
    {
        eqChangeNotifier.addListener (listener);
    }

    protected void fireEqChangeNotifier ()
    {
        eqChangeNotifier.fireStateChanged ();
    }

    public EquationTreeEditContext (UIController uic, SimpleTree t, Class<?> cls)
    {
        uiController = uic;
        tree = t;
        addToClass = cls;

        model = tree.getTModel();
    }

    public void reload (MNode doc)
    {
        insertEquationTree (tree.getRoot (), doc);
    }

    public void insertEquationTree (TNode targetNode, MNode doc)
    {
        try
        {
            EquationSet s = new EquationSet (doc);
            s.resolveConnectionBindings ();
            insertEquationTree (targetNode, s);
        }
        catch (Exception e)
        {
            System.err.println ("Exception while parsing model: " + e);
            e.printStackTrace ();
        }
    }

    public void insertEquationTree (TNode targetNode, EquationSet s)
    {
        targetNode.removeAllChildren ();

        // TODO: add $inherit() lines from original MNode, because they are dropped (processed away) by EquationSet

        Set<Entry<String,String>> metadata = s.getMetadata ();
        if (metadata.size () > 0)
        {
            TNode dollarnode = new TNode (new NodeNone ("$metadata"));
            model.insertNodeInto (dollarnode, targetNode, targetNode.getChildCount ());
            for (Entry<String,String> m : metadata)
            {
                TNode mnode = new TNode (new NodeAnnotation (m.getKey (), m.getValue ()));
                model.insertNodeInto (mnode, dollarnode, dollarnode.getChildCount ());
            }
        }

        // Note that references are not collated like metadata or equations. Only local references appear.
        // TODO: collate references?
        MNode references = s.source.child ("$reference");
        if (references != null  &&  references.length () > 0)
        {
            TNode dollarnode = new TNode (new NodeNone ("$reference"));
            model.insertNodeInto (dollarnode, targetNode, targetNode.getChildCount ());
            for (Entry<String,MNode> r : references)
            {
                TNode rnode = new TNode (new NodeReference (r.getKey (), r.getValue ().get ()));
                model.insertNodeInto (rnode, dollarnode, dollarnode.getChildCount ());
            }
        }

        if (s.connectionBindings != null)
        {
            for (Entry<String,EquationSet> a : s.connectionBindings.entrySet ())
            {
                TNode node = new TNode (new NodeBinding (a.getKey (), a.getValue ().name));
                model.insertNodeInto (node, targetNode, targetNode.getChildCount ());
            }
        }

        Set<Variable> unsorted = new TreeSet<Variable> (s.variables);
        String[] keys = s.getNamedValue ("gui.order").split (",");  // comma-separated list
        for (int i = 0; i < keys.length; i++)
        {
            Variable query = new Variable (keys[i]);
            Variable result = s.find (query);
            if (result != null)
            {
                unsorted.remove (result);
                insertVariableTree (targetNode, result);
            }
        }
        for (Variable v : unsorted)  // output everything else
        {
            insertVariableTree (targetNode, v);
        }

        for (EquationSet p : s.parts)
        {
            TNode node = new TNode (new NodePart (p));
            model.insertNodeInto (node, targetNode, targetNode.getChildCount ());
            insertEquationTree (node, p);
        }
    }

    public void insertVariableTree (TNode targetNode, Variable v)
    {
        targetNode.removeAllChildren ();

        TNode vnode = new TNode (new NodeVariable (v));
        model.insertNodeInto (vnode, targetNode, targetNode.getChildCount ());
        if (v.equations.size () > 1)  // for a single equation, the variable line itself suffices
        {
            for (EquationEntry e : v.equations)
            {
                TNode enode = new TNode (new NodeEquation (e));
                model.insertNodeInto (enode, vnode, vnode.getChildCount ());
            }
        }
        for (Entry<String,String> m : v.getMetadata ())
        {
            TNode mnode = new TNode (new NodeAnnotation (m.getKey (), m.getValue ()));
            model.insertNodeInto (mnode, vnode, vnode.getChildCount ());
        }
        MNode references = v.source.child ("$reference");
        if (references != null  &&  references.length () > 0)
        {
            TNode dollarnode = new TNode (new NodeNone ("$reference"));
            model.insertNodeInto (dollarnode, targetNode, targetNode.getChildCount ());
            for (Entry<String,MNode> r : references)
            {
                TNode rnode = new TNode (new NodeReference (r.getKey (), r.getValue ().get ()));
                model.insertNodeInto (rnode, dollarnode, dollarnode.getChildCount ());
            }
        }
    }


    /////////////
    // ACTIONS //
    /////////////

    // Add

    public void addEquation (String initVal)
    {
        TNode containingNodePart = getContextRoot (NodePart.class);  // This will always succeed, because root is NodePart.

        EquationInputBox input = getEquationInputBox (initVal);
        input.setVisible (true);
        if (input.getResult () == EquationInputBox.Result.OK)
        {
            try
            {
                // We need our nearest containing EquationSet in order to add the new variable, or to update an existing one.
                EquationSet s = null;
                NodePart n = (NodePart) containingNodePart.getUserObject ();
                if (n != null) s = n.part;
                if (s != null)
                {
                    String value = input.getValue ();
                    Variable v = Variable.parse (value);  // TODO: handle case where user enters only expression, not variable name
                    s.updateDB (v);

                    // The rest of this overly-complex code could be eliminated completely by reloading from the DB model document.
                    // However, that is heavy in terms of user experience, so we do a lot of complicated work here to update the gui as seamlessly as possible.

                    if (value.contains ("$include")  ||  value.contains ("$inherit"))  // Structural change. This forces a rebuild of the whole EquationSet tree and gui tree.
                    {
                        // TODO: Update existing EquationSet tree with $include, rather than doing a full rebuild.
                        reload (((NodePart) tree.getRootObject ()).part.source);
                    }
                    else  // Incremental change to existing EquationSet tree and gui tree
                    {
                        s.update (v);
                        Variable      newVariable = s.find (v);
                        EquationEntry newEquation = v.equations.first ();

                        // Do we have an existing NodeVariable that needs to be updated?
                        TNode existingTreeNodeVariable = null;
                        for (TNode t : containingNodePart.getTChildren (null))
                        {
                            NodeVariable nv = (NodeVariable) t.getUserObject ();
                            if (nv == null) continue;
                            if (nv.variable.equals (v))
                            {
                                existingTreeNodeVariable = t;
                                break;
                            }
                        }

                        if (existingTreeNodeVariable == null)  // new variable, so add single line
                        {
                            NodeVariable newNodeVariable = new NodeVariable (newVariable);
                            TNode newTreeNode = model.append (containingNodePart, newNodeVariable);
                            tree.select (newTreeNode);
                        }
                        else  // existing variable, so add or update an equation node
                        {
                            // Count existing equations and check if there is a match for the conditional on the newly-entered one.
                            int count = 0;
                            TNode existingTreeNodeEquation = null;
                            for (TNode t : existingTreeNodeVariable.getTChildren (NodeEquation.class))
                            {
                                NodeEquation ne = (NodeEquation) t.getUserObject ();
                                if (ne == null) continue;
                                count++;
                                if (ne.equation.equals (newEquation)) existingTreeNodeEquation = t;
                            }

                            if (count == 0)  // single-line
                            {
                                NodeVariable existingNodeVariable = (NodeVariable) existingTreeNodeVariable.getUserObject ();
                                EquationEntry existingEquation = existingNodeVariable.variable.equations.first ();
                                if (existingEquation.equals (newEquation))  // conditionals match, so stay one-line
                                {
                                    existingEquation.expression = newEquation.expression;
                                }
                                else  // conditionals are different, so convert to multi-line equation
                                {
                                    existingNodeVariable.variable = newVariable;
                                    insertVariableTree (existingTreeNodeVariable, newVariable);
                                }
                                tree.select (existingTreeNodeVariable);
                            }
                            else
                            {
                                if (existingTreeNodeEquation == null)  // new equation, so add an equation node under the variable
                                {
                                    NodeEquation newNodeEquation = new NodeEquation (newEquation);
                                    TNode newTreeNode = model.append (existingTreeNodeVariable, newNodeEquation);
                                    tree.select (newTreeNode);
                                }
                                else  // An existing equation line matches, so change its contents
                                {
                                    ((NodeEquation) existingTreeNodeEquation.getUserObject ()).equation = newEquation;
                                    tree.select (existingTreeNodeEquation);
                                }
                            }
                        }
                    }
                    fireEqChangeNotifier ();
                }
            }
            catch (Exception ex)
            {
                UMF.handleUnexpectedError (null, ex, "An error has occurred adding the equation to the tree.");
            }
        }
    }

    public void addAnnotationToSelected ()
    {
        TNode nearestPart     = getContextRoot (NodePart    .class);  // This will always succeed, because root is a NodePart.
        TNode nearestVariable = getContextRoot (NodeVariable.class);

        EquationInputBox input = getEquationInputBox ("");
        input.setVisible (true);
        if (input.getResult () == EquationInputBox.Result.OK)
        {
            try
            {
                String value = input.getValue ();
                String[] parts = value.split ("=", 2);
                String name = parts[0];
                if (parts.length > 1) value = parts[1];
                else                  value = "";

                NodeAnnotation newNodeAnnotation = new NodeAnnotation (name, value);
                TNode target;
                if (nearestVariable != null)
                {
                    target = nearestVariable;
                }
                else
                {
                    target = nearestPart;
                }

                // Search for $metadata under the target tree node. Create if necessary.
                TNode metadataTreeNode = null;
                for (TNode t : target.getTChildren (NodeNone.class))
                {
                    NodeNone n = (NodeNone) t.getUserObject ();
                    if (n != null  &&  n.label.equals ("$metadata"))
                    {
                        metadataTreeNode = t;
                        break;
                    }
                }
                if (metadataTreeNode == null)
                {
                    metadataTreeNode = model.append (target, new NodeNone ("$metadata"));
                    TNode nameTreeNode = model.append (metadataTreeNode, new NodeAnnotation (name, value));
                    tree.select (nameTreeNode);
                }
                else  // existing $metadata tree
                {
                    // Check if key already exists in $metadata
                    TNode nameTreeNode = null;
                    for (TNode t : target.getTChildren ())
                    {
                        NodeAnnotation a = (NodeAnnotation) t.getUserObject ();
                        if (a != null  &&  a.name.equals (name))
                        {
                            nameTreeNode = t;
                            a.value = value;  // go ahead and update it here, while we have it in hand
                            break;
                        }
                    }

                    if (nameTreeNode == null) nameTreeNode = model.append (metadataTreeNode, new NodeAnnotation (name, value));
                    tree.select (nameTreeNode);
                }

                fireEqChangeNotifier ();
            }
            catch (Exception ex)
            {
                UMF.handleUnexpectedError(null, ex, "An error has occurred adding the annotation to the tree.");
            }
        }
    }

    public void addReferenceToSelected ()
    {
        // TODO: implement references in a manner similar to $metadata
        // They are much the same, except reference tags point to reference records, while the value contains notes w.r.t. the reference.
        // Should create a dialog the pops up to help user find reference tags. Should be a 3-column listing (year, author, title)
        // with a search box on author.
    }

    // Edit

    public void editSelectedNode ()
    {
        TreePath path = tree.getSelectionPath ();
        if (path == null) return;

        // These two blocks probably could be combined in some fashion.
        TNode selected = (TNode) path.getLastPathComponent ();
        NodeBase node = (NodeBase) selected.getUserObject();

        // TODO: substantial additional work to create editing behavior that handles $inherit, $include, variables, equations, annotations and references
        if (node instanceof NodeReference)
        {
            // TODO: edit the same way as metadata
        }
        else if (node instanceof NodeEquation)
        {
            NodeEquation nodeEquation = (NodeEquation) selected.getUserObject ();
            String curVal = nodeEquation.toString ();
            EquationInputBox input = getEquationInputBox (curVal);
            input.setVisible (true);
            if (input.getResult () == EquationInputBox.Result.OK)
            {
                //nodeEquation.setEqValue (input.getValue ());
                tree.select (selected);
                fireEqChangeNotifier ();
            }
        }
        else if (node instanceof NodeAnnotation)
        {
            NodeAnnotation nodeAnnotation = (NodeAnnotation) selected.getUserObject ();
            String curVal = nodeAnnotation.toString ();
            EquationInputBox input = getEquationInputBox (curVal);
            input.setVisible (true);
            if (input.getResult () == EquationInputBox.Result.OK)
            {
                //nodeAnnotation.setAnnotation (input.getValue ());
                tree.select (selected);
                fireEqChangeNotifier ();
            }
        }
    }

    // Remove

    // TODO: Can't remove annotation and reference nodes that aren't in the right spot.
    public void removeSelectedNode() {
        if(tree.getSelectionPath() == null) {
            return;
        }

        // Group all the various nodes that are selected
        // by their user objects' classes so that the various
        // types of nodes can be deleted in the appropriate
        // order.
        Map<Class<?>, List<TNode>> toDel = new HashMap<Class<?>, List<TNode>>();
        TreePath[] paths = tree.getSelectionPaths();
        for(TreePath path : paths) {
            TNode nAny = (TNode) path.getLastPathComponent();
            Class<?> clazz = nAny.getUserObject().getClass();
            List<TNode> delNodes = toDel.get(clazz);
            if(delNodes == null) {
                delNodes = new ArrayList<TNode>();
                toDel.put(clazz, delNodes);
            }
            delNodes.add(nAny);
        }

        // First the annotations and equation references.
        if(toDel.get(NodeAnnotation.class) != null) {
            for(TNode node : toDel.get(NodeAnnotation.class)) {
                removeNode(node);
            }
        }
        if(toDel.get(NodeReference.class) != null) {
            for(TNode node : toDel.get(NodeReference.class)) {
                removeNode(node);
            }
        }

        // Then the equations.
        if(toDel.get(NodeEquation.class) != null) {
            for(TNode node : toDel.get(NodeEquation.class)) {
                removeNode(node);
            }
        }

        tree.updateUI();
        fireEqChangeNotifier();
    }

    public void removeNode(TNode node) {
        if(node.getUserObject() instanceof NodeEquation) {
            tree.remove(node);
        } else if(node.getUserObject() instanceof NodeAnnotation || node.getUserObject() instanceof NodeReference) {
            tree.remove(node);
            /*
            TNode eq = (TNode) node.getParent();
            TNode sib = (TNode) node.getNextSibling();
            if(sib == null) {
                sib = (TNode) node.getPreviousSibling();
            }
            model.removeNodeFromParent(node);
            if(sib != null) {
                tree.setSelectionPath(myPath(sib));
            } else {
                tree.setSelectionPath(myPath(eq));
            }
            */
        }
    }

    // Move

    // TODO: Group move down.
    public void moveSelectedDown() {
        TNode contextRoot = getContextRoot(null);
        if(contextRoot != null) {
            TreePath selPath = tree.getSelectionPath();
            TNode nSel = (TNode) selPath.getLastPathComponent();
            int curIndex = contextRoot.getIndex(nSel);
            if(curIndex < contextRoot.getChildCount() - 1) {
                contextRoot.insert(nSel, curIndex + 1);
                tree.updateUI();       // Model changed
                fireEqChangeNotifier();
            }
        }
    }

    // TODO: Group move up.
    public void moveSelectedUp() {
        TNode contextRoot = getContextRoot(null);
        if(contextRoot != null) {
            TreePath selPath = tree.getSelectionPath();
            TNode nSel = (TNode) selPath.getLastPathComponent();
            int curIndex = contextRoot.getIndex(nSel);
            if(curIndex > 0) {
                contextRoot.insert(nSel, curIndex - 1);
                tree.updateUI();       // Model changed
                fireEqChangeNotifier();
            }
        }
    }


    //////////
    // MISC //
    //////////

    private TNode getContextRoot (Class<?> stopAtClass)
    {
        TNode chosenNode = null;
        TreePath path = tree.getSelectionPath ();
        if (path != null) return getContextRoot (stopAtClass, (TNode) path.getLastPathComponent ());
        if (addToClass == null) return getContextRoot (stopAtClass, null);
        return chosenNode;
    }

    private TNode getContextRoot (Class<?> stopAtClass, TNode nSel)
    {
        TNode chosenNode = null;
        TNode n = nSel;
        while (n != null)
        {
            Class<?> uClass = n.getUserObject ().getClass ();
            if (stopAtClass != null)
            {
                if (stopAtClass.isAssignableFrom (uClass))
                {
                    chosenNode = n;
                    break;
                }
            }
            else if (addToClass != null  &&  addToClass.isAssignableFrom (uClass))
            {
                chosenNode = n;
                break;
            }
            n = (TNode) n.getParent ();
        }
        if (addToClass == null  &&  stopAtClass == null) chosenNode = (TNode) model.getRoot ();
        return chosenNode;
    }

    private EquationInputBox getEquationInputBox (String initVal)
    {
        Component c = SwingUtilities.getRoot (tree);
        EquationInputBox input;
        if (c instanceof JFrame) input = new EquationInputBox ((JFrame)  c, initVal);
        else                     input = new EquationInputBox ((JDialog) c, initVal);
        return input;
    }

    public boolean isInContext (TNode nAny)
    {
        return getContextRoot (addToClass, nAny) != null;
    }
}

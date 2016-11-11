/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

public class NodeVariable extends NodeBase
{
    protected static ImageIcon iconVariable = ImageUtil.getImage ("delta.png");
    protected static ImageIcon iconBinding  = ImageUtil.getImage ("connect.gif");

    protected boolean isBinding;
    protected List<Integer> columnWidths;

    public NodeVariable (MPart source)
    {
        this.source = source;
    }

    public void build ()
    {
        setUserObject (source.key () + "=" + source.get ());
        removeAllChildren ();

        for (MNode n : source)
        {
            String key = n.key ();
            if (key.startsWith ("@")) add (new NodeEquation ((MPart) n));
        }

        MPart metadata = (MPart) source.child ("$metadata");
        if (metadata != null)
        {
            for (MNode m : metadata) add (new NodeAnnotation ((MPart) m));
        }

        MPart references = (MPart) source.child ("$reference");
        if (references != null)
        {
            for (MNode r : references) add (new NodeReference ((MPart) r));
        }
    }

    /**
        Examines a fully-built tree to determine the value of the isBinding member.
    **/
    public void findConnections ()
    {
        isBinding = false;

        NodePart parent = (NodePart) getParent ();
        String value = source.get ().trim ();
        if (value.contains ("$connect"))
        {
            isBinding = true;
        }
        else
        {
            // Determine if our LHS has the right form.
            String name = source.key ().trim ();
            if (name.endsWith ("'")) return;

            // Determine if our RHS has the right form. If so, scan for the referent.
            if (value.matches ("[a-zA-Z_$][a-zA-Z0-9_$.]*"))
            {
                NodeBase referent = parent.resolveName (value);
                if (referent instanceof NodePart) isBinding = true;
            }
        }

        if (isBinding) parent.isConnection = true;
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        if (isBinding) return iconBinding;
        else           return iconVariable;
    }

    @Override
    public String getText (boolean expanded)
    {
        String result = toString ();
        if (result.isEmpty ()) return result;  // Allow user object to be "" for new nodes.
        if (! expanded  &&  children != null)  // show "..." when multi-line equation is collapsed
        {
            for (Object o : children)
            {
                if (o instanceof NodeEquation)
                {
                    return result + " ...";
                }
            }
        }
        return result;
    }

    @Override
    public boolean needsInitTabs ()
    {
        return columnWidths == null;
    }

    @Override
    public void updateColumnWidths (FontMetrics fm)
    {
        if (columnWidths == null)
        {
            columnWidths = new ArrayList<Integer> (1);
            columnWidths.add (0);
            columnWidths.add (0);
        }
        columnWidths.set (0, fm.stringWidth (source.key () + " "));
        Variable.ParsedValue pieces = new Variable.ParsedValue (source.get ());
        columnWidths.set (1, fm.stringWidth ("=" + pieces.combiner + " "));
    }

    @Override
    public List<Integer> getColumnWidths ()
    {
        return columnWidths;
    }

    @Override
    public void applyTabStops (List<Integer> tabs, FontMetrics fm)
    {
        String result = source.key ();
        Variable.ParsedValue pieces = new Variable.ParsedValue (source.get ());

        int offset = tabs.get (0).intValue () - fm.stringWidth (result);
        result = result + pad (offset, fm) + "=" + pieces.combiner;
        
        offset = tabs.get (1).intValue () - fm.stringWidth (result);
        result = result + pad (offset, fm) + pieces.expression;
        if (! pieces.conditional.isEmpty ()) result = result + " @ " + pieces.conditional;

        setUserObject (result);
    }

    @Override
    public NodeBase add (String type, JTree tree)
    {
        if (isBinding) return ((NodeBase) getParent ()).add ("Variable", tree);

        if (type.isEmpty ())
        {
            if (getChildCount () == 0  ||  tree.isCollapsed (new TreePath (getPath ()))) return ((NodeBase) getParent ()).add ("Variable", tree);
            type = "Equation";
        }

        NodeBase result;
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();
        FontMetrics fm = getFontMetrics (tree);
        if (type.equals ("Equation"))
        {
            TreeMap<String,MNode> equations = new TreeMap<String,MNode> ();
            for (MNode n : source)
            {
                String key = n.key ();
                if (key.startsWith ("@")) equations.put (key.substring (1), n);
            }

            // The minimum number of equations is 2. There should never be exactly 1 equation, because that is single-line form, which should have no child equations at all.
            if (equations.size () == 0)  // We are about to switch from single-line form to multi-conditional, so make a tree node for the existing equation.
            {
                Variable.ParsedValue pieces = new Variable.ParsedValue (source.get ());
                source.set (pieces.combiner);
                setUserObject (source.key () + "=" + pieces.combiner);
                MPart equation = (MPart) source.set (pieces.expression, "@" + pieces.conditional);
                equations.put (pieces.conditional, equation);
                model.insertNodeInto (new NodeEquation (equation), this, 0);
            }

            int suffix = equations.size ();
            String conditional;
            while (true)
            {
                conditional = String.valueOf (suffix);
                if (equations.get (conditional) == null) break;
                suffix++;
            }
            MPart equation = (MPart) source.set (conditional, "@" + conditional);
            result = new NodeEquation (equation);
            result.setUserObject ("");
            result.updateColumnWidths (fm);  // preempt initialization
            model.insertNodeInto (result, this, 0);
        }
        else if (type.equals ("Annotation"))
        {
            // Determine index at which to insert new annotation
            int firstReference = 0;
            while (firstReference < getChildCount ()  &&  ! (getChildAt (firstReference) instanceof NodeReference)) firstReference++;

            // Determine a unique key for the annotation
            MPart metadata = (MPart) source.childOrCreate ("$metadata");
            int suffix = 1;
            while (metadata.child ("a" + suffix) != null) suffix++;

            result = new NodeAnnotation ((MPart) metadata.set ("", "a" + suffix));
            result.setUserObject ("");
            result.updateColumnWidths (fm);
            model.insertNodeInto (result, this, firstReference);
        }
        else if (type.equals ("Reference"))
        {
            MPart references = (MPart) source.childOrCreate ("$reference");
            int suffix = 1;
            while (references.child ("r" + suffix) != null) suffix++;

            result = new NodeReference ((MPart) references.set ("", "r" + suffix));
            result.setUserObject ("");
            result.updateColumnWidths (fm);
            model.insertNodeInto (result, this, getChildCount ());
        }
        else
        {
            return ((NodeBase) getParent ()).add (type, tree);  // refer all other requests up the tree
        }

        return result;
    }

    @Override
    public boolean allowEdit ()
    {
        if (! getUserObject ().toString ().isEmpty ())  // An empty user object indicates a newly created node, which we want to edit as a blank.
        {
            setUserObject (source.key () + "=" + source.get ());  // We're about to go into edit, so remove tabs.
        }
        return true;
    }

    @Override
    public void applyEdit (JTree tree)
    {
        String input = (String) getUserObject ();
        if (input.isEmpty ())
        {
            delete (tree);
            return;
        }

        DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();
        FontMetrics fm = getFontMetrics (tree);
        String oldKey = source.key ();
        NodeBase parent = (NodeBase) getParent ();

        String[] parts = input.split ("=", 2);
        String name = parts[0].trim ();
        String value;
        if (parts.length > 1) value = parts[1].trim ();
        else                  value = "";
        if (! name.matches ("[a-zA-Z_$][a-zA-Z0-9_$.]*[']*"))  // Not a proper variable name. The user actually passed a naked expression, so resurrect the old (probably auto-assigned) variable name.
        {
            value = input.trim ();
            name = oldKey;
            updateColumnWidths (fm);
            parent.updateTabStops (fm);
            parent.nodesChanged (model);
        }
        Variable.ParsedValue pieces = new Variable.ParsedValue (value);

        NodeBase existing = null;
        if (! name.equals (oldKey)) existing = parent.child (name);

        // See if the other node is a variable, and if we can merge into it with an acceptably low level of damage
        NodeVariable existingVariable = null;
        if (existing instanceof NodeVariable) existingVariable = (NodeVariable) existing;
        if (existingVariable != null  &&  getChildCount () == 0  &&  ! pieces.expression.isEmpty ())
        {
            boolean existingEquationMatch = false;
            int     existingEquationCount = 0;
            if (existingVariable.children != null)
            {
                for (Object o : existingVariable.children)
                {
                    if (o instanceof NodeEquation)
                    {
                        existingEquationCount++;
                        if (((NodeEquation) o).source.key ().substring (1).equals (pieces.conditional)) existingEquationMatch = true;
                    }
                }
            }

            Variable.ParsedValue existingPieces = new Variable.ParsedValue (existingVariable.source.get ());

            if (   (existingEquationCount  > 0  &&  ! existingEquationMatch)
                || (existingEquationCount == 0  &&  ! existingPieces.conditional.equals (pieces.conditional)))
            {
                // Merge into existing variable and remove ourselves from tree.

                if (! existingPieces.expression.isEmpty ()  ||  ! existingPieces.conditional.isEmpty ())  // The existing variable has an expression, so convert it into a subordinate equation.
                {
                    MPart convertedEquation = (MPart) existingVariable.source.set (existingPieces.expression, "@" + existingPieces.conditional);
                    NodeEquation convertedEquationNode = new NodeEquation (convertedEquation);
                    model.insertNodeInto (convertedEquationNode, existingVariable, 0);
                    convertedEquationNode.updateColumnWidths (fm);
                }
                existingVariable.source.set (pieces.combiner);  // override the combiner, just as if we had entered an equation directly on the existing variable

                MPart newEquation = (MPart) existingVariable.source.set (pieces.expression, "@" + pieces.conditional);
                NodeEquation newEquationNode = new NodeEquation (newEquation);
                model.insertNodeInto (newEquationNode, existingVariable, 0);
                model.removeNodeFromParent (this);
                parent.source.clear (oldKey);

                newEquationNode.updateColumnWidths (fm);
                existingVariable.updateTabStops (fm);
                existingVariable.nodesChanged (model);

                existingVariable.updateColumnWidths (fm);
                parent.updateTabStops (fm);
                parent.nodesChanged (model);

                tree.setSelectionPath (new TreePath (newEquationNode.getPath ()));
                existingVariable.findConnections ();

                return;
            }
        }

        TreeMap<String,NodeEquation> equations = new TreeMap<String,NodeEquation> ();
        if (children != null)
        {
            for (Object o : children)
            {
                if (o instanceof NodeEquation)
                {
                    NodeEquation e = (NodeEquation) o;
                    equations.put (e.source.key ().substring (1), e);
                }
            }
        }

        if (name.equals (oldKey)  ||  name.isEmpty ()  ||  existing != null)  // No name change, or name change forbidden
        {
            // Update ourselves. Exact action depends on whether we are single-line or multi-conditional.
            if (equations.size () == 0)
            {
                source.set (value);
            }
            else
            {
                source.set (pieces.combiner);

                if (! pieces.expression.isEmpty ())
                {
                    NodeEquation e = equations.get (pieces.conditional);
                    if (e == null)  // no matching equation
                    {
                        MPart equation = (MPart) source.set (pieces.expression, "@" + pieces.conditional);
                        model.insertNodeInto (new NodeEquation (equation), this, 0);
                    }
                    else  // conditional matched an existing equation, so just replace the expression
                    {
                        e.source.set (pieces.expression);
                        e.setUserObject (pieces.expression + e.source.key ());  // key starts with "@"
                        model.nodeChanged (e);
                    }
                }
            }

            updateColumnWidths (fm);
            parent.updateTabStops (fm);
            parent.nodesChanged (model);
        }
        else  // The name was changed. Move the whole sub-tree to a new location. This may also expose an overridden variable.
        {
            MPart mparent = source.getParent ();
            MPart newPart;
            NodeInherit inherit = null;
            if (name.equals ("$inherit"))
            {
                mparent.clear (oldKey);  // We abandon everything under this node, because $inherit does not have a subtree. (It might in the future, to store UIDs of referenced parts.)
                newPart = (MPart) mparent.set (value, name);
                inherit = new NodeInherit (newPart);
                model.insertNodeInto (inherit, parent, 0);
                if (parent instanceof NodePart)  // It had better be! There is no other legal configuration.
                {
                    ((NodePart) parent).build ();
                    model.nodeStructureChanged (parent);
                }
            }
            else
            {
                // Inject the changed equation into the underlying data before renaming.
                if (equations.size () == 0)
                {
                    source.set (value);
                }
                else
                {
                    source.set (pieces.combiner);

                    if (! pieces.expression.isEmpty ())
                    {
                        NodeEquation e = equations.get (pieces.conditional);
                        if (e == null)   source.set (pieces.expression, "@" + pieces.conditional);  // create a new equation
                        else           e.source.set (pieces.expression);  // blow away the existing expression in the matching equation
                    }
                }

                mparent.move (oldKey, name);  // This copies the whole tree, not just overridden nodes, but leaves behind only the newly-exposed underrides.
                newPart = (MPart) mparent.child (name);
            }

            // Presumably at this point, newPart is never null. Unless it was a $inherit, it needs somewhere to go in the tree.
            if (mparent.child (oldKey) == null)  // This tree node can be re-used, if we need it.
            {
                if (inherit == null)
                {
                    source = newPart;
                    build ();
                    updateColumnWidths (fm);
                    parent.updateTabStops (fm);
                    parent.nodesChanged (model);
                }
            }
            else  // We exposed an overridden part, which will retain its claim on this tree node.
            {
                if (inherit == null)  // Create a new tree node for the newly-named variable, if needed.
                {
                    NodeVariable v = new NodeVariable (newPart);
                    model.insertNodeInto (v, parent, parent.getIndex (this));
                    v.build ();
                    v.updateColumnWidths (fm);
                    v.findConnections ();
                }

                // The current node's source is still set to the old part.
                build ();
                updateColumnWidths (fm);
                parent.updateTabStops (fm);
                parent.nodesChanged (model);
            }
        }

        findConnections ();
    }

    @Override
    public void delete (JTree tree)
    {
        if (! source.isFromTopDocument ()) return;

        DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();
        FontMetrics fm = getFontMetrics (tree);
        NodeBase parent = (NodeBase) getParent ();
        MPart mparent = source.getParent ();
        String key = source.key ();
        mparent.clear (key);  // If this merely clears an override, then our source object retains its identity.
        if (mparent.child (key) == null)  // but we do need to test if it is still in the tree
        {
            model.removeNodeFromParent (this);
        }
        else
        {
            build ();
            updateColumnWidths (fm);
            model.nodeStructureChanged (this);
        }
        parent.updateTabStops (fm);
        parent.nodesChanged (model);
    }
}

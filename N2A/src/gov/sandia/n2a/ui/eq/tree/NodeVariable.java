/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

import java.util.Enumeration;
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
            model.insertNodeInto (result, this, firstReference);
        }
        else if (type.equals ("Reference"))
        {
            MPart references = (MPart) source.childOrCreate ("$reference");
            int suffix = 1;
            while (references.child ("r" + suffix) != null) suffix++;

            result = new NodeReference ((MPart) references.set ("", "r" + suffix));
            result.setUserObject ("");
            model.insertNodeInto (result, this, getChildCount ());
        }
        else
        {
            return ((NodeBase) getParent ()).add (type, tree);  // refer all other requests up the tree
        }
        return result;
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

        String[] parts = input.split ("=", 2);
        String name = parts[0];
        String value;
        if (parts.length > 1) value = parts[1];
        else                  value = "";
        Variable.ParsedValue pieces = new Variable.ParsedValue (value);

        DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();
        NodeBase existing = null;
        String oldKey = source.key ();
        NodeBase parent = (NodeBase) getParent ();
        if (! name.equals (oldKey)) existing = parent.child (name);

        // See if the other node is a variable, and if we can merge into it with an acceptably low level of damage
        NodeVariable existingVariable = null;
        if (existing instanceof NodeVariable) existingVariable = (NodeVariable) existing;
        if (existingVariable != null  &&  getChildCount () == 0  &&  ! pieces.expression.isEmpty ())
        {
            boolean existingEquationMatch = false;
            int     existingEquationCount = 0;
            Enumeration i = existingVariable.children ();
            while (i.hasMoreElements ())
            {
                Object o = i.nextElement ();
                if (o instanceof NodeEquation)
                {
                    existingEquationCount++;
                    NodeEquation e = (NodeEquation) o;
                    if (e.source.key ().substring (1).equals (pieces.conditional)) existingEquationMatch = true;
                }
            }

            Variable.ParsedValue existingPieces = new Variable.ParsedValue (existingVariable.source.get ());

            if (   (existingEquationCount  > 0  &&  ! existingEquationMatch)
                || (existingEquationCount == 0  &&  ! existingPieces.conditional.equals (pieces.conditional)))
            {
                // Merge into existing variable and remove ourselves from tree.

                if (! existingPieces.expression.isEmpty ()  ||  ! existingPieces.conditional.isEmpty ())
                {
                    MPart equation = (MPart) existingVariable.source.set (existingPieces.expression, "@" + existingPieces.conditional);
                    model.insertNodeInto (new NodeEquation (equation), existingVariable, 0);
                }
                existingVariable.source.set (pieces.combiner);  // override the combiner, just as if we had entered an equation directly on the existing variable
                existingVariable.setUserObject (name + "=" + pieces.combiner);
                model.nodeChanged (existingVariable);

                MPart equation = (MPart) existingVariable.source.set (pieces.expression, "@" + pieces.conditional);
                NodeEquation e = new NodeEquation (equation);
                model.insertNodeInto (e, existingVariable, 0);
                model.removeNodeFromParent (this);
                parent.source.clear (oldKey);
                tree.setSelectionPath (new TreePath (e.getPath ()));

                existingVariable.findConnections ();

                return;
            }
        }

        TreeMap<String,NodeEquation> equations = new TreeMap<String,NodeEquation> ();
        Enumeration i = children ();
        while (i.hasMoreElements ())
        {
            Object o = i.nextElement ();
            if (o instanceof NodeEquation)
            {
                NodeEquation e = (NodeEquation) o;
                equations.put (e.source.key ().substring (1), e);
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

                NodeEquation e = equations.get (pieces.conditional);
                if (e == null)  // no matching equation
                {
                    MPart equation = (MPart) source.set (pieces.expression, "@" + pieces.conditional);
                    model.insertNodeInto (new NodeEquation (equation), this, 0);
                }
                else  // conditional matched an existing equation, so just replace the expression
                {
                    if (! pieces.expression.isEmpty ())  // but only if the expression actually contains something
                    {
                        e.source.set (pieces.expression);
                        e.setUserObject (pieces.expression + e.source.key ());  // key starts with "@"
                        model.nodeChanged (e);
                    }
                }
            }

            if (equations.size () > 0  ||  existing != null)  // Necessary to change displayed value
            {
                setUserObject (oldKey + "=" + source.get ());
                model.nodeChanged (this);
            }
        }
        else  // The name was changed. Move the whole tree under us to a new location. This may also expose an overridden variable.
        {
            // Inject the changed equation into the underlying data first, then move and rebuild the displayed nodes as necessary.
            if (equations.size () == 0)
            {
                source.set (value);
            }
            else
            {
                source.set (pieces.combiner);

                NodeEquation e = equations.get (pieces.conditional);
                if (e == null)                             source.set (pieces.expression, "@" + pieces.conditional);  // create a new equation
                else if (! pieces.expression.isEmpty ()) e.source.set (pieces.expression);  // blow away the existing expression in the matching equation
            }

            // Change ourselves into the new key=value pair
            MPart p = source.getParent ();
            p.move (oldKey, name);
            MPart newPart = (MPart) p.child (name);
            if (p.child (oldKey) == null)
            {
                // We were not associated with an override, so we can re-use this tree node.
                source = newPart;
            }
            else
            {
                // Make a new node for the renamed tree, and leave us to present the other non-overridden tree.
                // Note that our source is still set to the old part.
                NodeVariable v = new NodeVariable (newPart);
                model.insertNodeInto (v, parent, parent.getIndex (this));
                v.build ();
                model.nodeStructureChanged (v);
                v.findConnections ();
            }
            build ();
            model.nodeStructureChanged (this);
        }

        findConnections ();
    }

    @Override
    public void delete (JTree tree)
    {
        if (! source.isFromTopDocument ()) return;

        DefaultTreeModel model = (DefaultTreeModel) tree.getModel ();
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
            model.nodeStructureChanged (this);
        }
    }
}

/*
Copyright 2016,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


package gov.sandia.n2a.ui.eq.tree;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.undo.AddAnnotation;
import gov.sandia.n2a.ui.eq.undo.AddEquation;
import gov.sandia.n2a.ui.eq.undo.AddInherit;
import gov.sandia.n2a.ui.eq.undo.AddReference;
import gov.sandia.n2a.ui.eq.undo.ChangeEquation;
import gov.sandia.n2a.ui.eq.undo.ChangeVariable;
import gov.sandia.n2a.ui.eq.undo.ChangeVariableToInherit;
import gov.sandia.n2a.ui.eq.undo.DeleteVariable;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

public class NodeVariable extends NodeContainer
{
    protected static ImageIcon iconVariable = ImageUtil.getImage ("delta.png");
    protected static ImageIcon iconBinding  = ImageUtil.getImage ("connect.gif");

    protected boolean isBinding;
    protected List<Integer> columnWidths;

    public NodeVariable (MPart source)
    {
        this.source = source;
    }

    @Override
    public void build ()
    {
        removeAllChildren ();

        // While building the equation list, we also enforce the one-line rule.
        // This is because build() may be called as the result of a change in inheritance,
        // which could result in a dangling top-level override of a single equation.
        // We may actually make small changes to the database here, which is not ideal,
        // but should do little harm.
        if (source.isFromTopDocument ()) enforceOneLine (source);
        setUserObject (source.key () + "=" + source.get ());
        for (MNode n : source)
        {
            if (n.key ().startsWith ("@")) add (new NodeEquation ((MPart) n));
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
        Ensures that a variable either has multiple equations or a one-line expression,
        but not both.
        This method is independent of any given NodeVariable so it can be used at
        various points in processing.
    **/
    public static void enforceOneLine (MNode source)
    {
        // Collect info

        int equationCount = 0;
        MNode equation = null;
        for (MNode n : source)
        {
            String key = n.key ();
            if (key.startsWith ("@"))
            {
                equation = n;
                equationCount++;
            }
        }

        Variable.ParsedValue pieces = new Variable.ParsedValue (source.get ());
        boolean empty =  pieces.expression.isEmpty ()  &&  pieces.condition.isEmpty ();

        // Collapse or expand
        if (equationCount > 0)
        {
            if (! empty)
            {
                // Expand
                source.set (pieces.combiner);
                source.set ("@" + pieces.condition, pieces.expression);  // may override an existing equation
            }
            else if (equationCount == 1)
            {
                // Collapse
                source.clear (equation.key ());
                source.set (pieces.combiner + equation.get () + equation.key ());
            }
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
            if (NodePart.isValidIdentifier (value))
            {
                NodeBase referent = parent.resolveName (value);
                if (referent instanceof NodePart) isBinding = true;
            }
        }

        if (isBinding) parent.isConnection = true;
    }

    @Override
    public boolean visible (int filterLevel)
    {
        if (filterLevel == FilteredTreeModel.ALL) return true;
        if (source.isFromTopDocument ()) return true;
        if (filterLevel >= FilteredTreeModel.LOCAL) return false;  // Since we already fail the "local" requirement
        // FilteredTreeModel.PUBLIC ...
        return source.child ("$metadata", "public") != null;
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        if (isBinding) return iconBinding;
        else           return iconVariable;
    }

    @Override
    public String getText (boolean expanded, boolean editing)
    {
        String result = toString ();
        if (result.isEmpty ()) return result;  // Allow user object to be "" for new nodes.
        if (editing) return source.key () + "=" + source.get ();  // We're about to go into edit, so remove tabs.
        if (! expanded  &&  children != null)  // show "..." when multi-line equation is collapsed
        {
            for (Object o : children) if (o instanceof NodeEquation) return result + "...";
        }
        return result;
    }

    @Override
    public void invalidateTabs ()
    {
        columnWidths = null;
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
        if (! pieces.condition.isEmpty ()) result = result + " @ " + pieces.condition;

        setUserObject (result);
    }

    @Override
    public void copy (MNode result)
    {
        MNode n = result.set (source.key (), source.get ());
        Enumeration<?> cf = childrenFiltered ();
        while (cf.hasMoreElements ())
        {
            NodeBase child = (NodeBase) cf.nextElement ();
            if      (child instanceof NodeAnnotation) n.set ("$metadata",  child.source.key (), child.source.get ());
            else if (child instanceof NodeReference)  n.set ("$reference", child.source.key (), child.source.get ());
            else     child.copy (n);
        }
    }

    @Override
    public NodeBase add (String type, JTree tree, MNode data)
    {
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        if (type.isEmpty ())
        {
            if (model.getChildCount (this) == 0  ||  tree.isCollapsed (new TreePath (getPath ()))) return ((NodeBase) getParent ()).add ("Variable", tree, data);
            type = "Equation";
        }
        if (isBinding) return ((NodeBase) getParent ()).add (type, tree, data);

        if (type.equals ("Equation"))
        {
            // Determine if pasting over an existing equation
            if (data != null)
            {
                String key = data.key ();  // includes @
                NodeBase existingEquation = child (key);
                if (existingEquation != null)
                {
                    key = key.substring (1);  // remove the @, since ChangeEquation expects strings from ParsedValue
                    String combiner = new Variable.ParsedValue (source.get ()).combiner;
                    PanelModel.instance.undoManager.add (new ChangeEquation (this, key, combiner, existingEquation.source.get (), key, combiner, data.get ()));
                    return existingEquation;  // Somewhat of a cheat, since we didn't really add it. OTOH, a paste operation should not be followed by edit mode.
                }
            }

            // Determine index for new equation
            int index = 0;
            NodeBase child = (NodeBase) tree.getLastSelectedPathComponent ();
            if (child != null  &&  child.getParent () == this) index = getIndex (child);
            while (index > 0  &&  ! (getChildAt (index) instanceof NodeEquation)) index--;
            if (index < getChildCount ()  &&  getChildAt (index) instanceof NodeEquation) index++;

            // Create an AddEquation action
            AddEquation ae = new AddEquation (this, index, data);
            PanelModel.instance.undoManager.add (ae);
            return ae.createdNode;
        }
        else if (type.equals ("Annotation"))
        {
            // Determine index at which to insert new annotation
            int index = 0;
            int count = getChildCount ();
            while (index < count  &&  ! (children.get (index) instanceof NodeReference)) index++;

            AddAnnotation aa = new AddAnnotation (this, index, data);
            PanelModel.instance.undoManager.add (aa);
            return aa.createdNode;
        }
        else if (type.equals ("Reference"))
        {
            AddReference ar = new AddReference (this, getChildCount (), data);
            PanelModel.instance.undoManager.add (ar);
            return ar.createdNode;
        }

        return ((NodeBase) getParent ()).add (type, tree, data);  // refer all other requests up the tree
    }

    public static boolean isValidIdentifier (String name)
    {
        if (name.length () == 0) return false;

        char c = name.charAt (0);
        if (! Character.isJavaIdentifierStart (c)) return false;

        int i = 1;
        for (; i < name.length (); i++)
        {
            c = name.charAt (i);
            if (! Character.isJavaIdentifierPart (c)  &&  c != '.') break;
        }
        for (; i < name.length (); i++)
        {
            if (name.charAt (i) != '\'') break;
        }
        return i >= name.length ();
    }

    /**
        Enforces all the different use cases associated with editing of variables.
        This is the most complex node class, and does the most work. Some of the use cases include:
        * Create a new variable.
        * Move an existing variable tree, perhaps overriding an inherited one, perhaps also with a change of value.
        * Insert an equation under ourselves.
        * Insert an equation under another variable.
    **/
    @Override
    public void applyEdit (JTree tree)
    {
        String input = (String) getUserObject ();
        if (input.isEmpty ())
        {
            delete (tree, true);
            return;
        }

        String[] parts = input.split ("=", 2);
        String nameAfter = parts[0].trim ().replaceAll ("[ \\n\\t]", "");
        String valueAfter;
        if (parts.length > 1)
        {
            valueAfter = parts[1].trim ();
            if (valueAfter.isEmpty ())
            {
                boolean hasEquations = false;
                if (children != null)
                {
                    for (Object o : children) if (o instanceof NodeEquation)
                    {
                        hasEquations = true;
                        break;
                    }
                }
                if (! hasEquations) valueAfter = "0";  // Empty assignment is prohibited. Otherwise, it would be impossible to distinguish variables from parts.
            }
        }
        else
        {
            valueAfter = "0";  // Assume input was a variable name with no assignment.
        }

        // What follows is a series of analyses, most having to do with enforcing constraints
        // on name change (which implies moving the variable tree or otherwise modifying another variable).

        // Handle a naked expression.
        String nameBefore  = source.key ();
        String valueBefore = source.get ();
        if (! isValidIdentifier (nameAfter))  // Not a proper variable name. The user actually passed a naked expression, so resurrect the old (probably auto-assigned) variable name.
        {
            nameAfter  = nameBefore;
            valueAfter = input;
        }

        // Handle creation of $inherit node.
        PanelModel mep = PanelModel.instance;
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        boolean newlyCreated =  getChildCount () == 0  &&  valueBefore.isEmpty ();  // Only a heuristic
        NodeBase parent = (NodeBase) getParent ();
        if (nameAfter.equals ("$inherit"))
        {
            if (parent.child (nameAfter) == null)
            {
                if (newlyCreated)
                {
                    parent.source.clear (nameBefore);
                    // No need to update GUI, because AddInherit rebuilds parent.
                    mep.undoManager.add (new AddInherit ((NodePart) parent, valueAfter));
                }
                else
                {
                    mep.undoManager.add (new ChangeVariableToInherit (this, valueAfter));
                }
                return;
            }

            nameAfter = nameBefore;
        }

        // Prevent illegal name change. (Don't override another top-level node. Don't overwrite a non-variable node.)
        NodeBase nodeAfter = parent.child (nameAfter);
        if (nodeAfter != null)
        {
            if (! (nodeAfter instanceof NodeVariable)  ||  (nodeAfter != this  &&  nodeAfter.source.isFromTopDocument ()  &&  ! newlyCreated))
            {
                nameAfter = nameBefore;
                nodeAfter = this;
            }
        }

        // If there's nothing to do, then repaint the node and quit.
        FontMetrics fm = getFontMetrics (tree);
        if (nameBefore.equals (nameAfter)  &&  valueBefore.equals (valueAfter))
        {
            parent.updateTabStops (fm);
            parent.allNodesChanged (model);
            return;
        }

        // Detect and handle special cases
        if (nodeAfter != null)
        {
            Variable.ParsedValue piecesDest  = new Variable.ParsedValue (nodeAfter.source.get ());  // In this section, "dest" refers to state of target node before it is overwritten, while "after" refers to newly input values from user.
            Variable.ParsedValue piecesAfter = new Variable.ParsedValue (valueAfter);
            boolean expressionAfter = ! piecesAfter.expression.isEmpty ()  ||  ! piecesAfter.condition.isEmpty ();
            if (piecesAfter.combiner.isEmpty ()) piecesAfter.combiner = piecesDest.combiner;  // If the user doesn't specify a combiner, absorb it from our destination.

            int          equationCount = 0;
            NodeEquation equationMatch = null; 
            Enumeration<?> childrenAfter = nodeAfter.children ();
            while (childrenAfter.hasMoreElements ())
            {
                Object c = childrenAfter.nextElement ();
                if (c instanceof NodeEquation)
                {
                    equationCount++;
                    NodeEquation e = (NodeEquation) c;
                    if (e.source.key ().substring (1).equals (piecesAfter.condition)) equationMatch = e;
                }
            }

            if (nodeAfter == this)
            {
                if (equationCount > 0  &&  expressionAfter)
                {
                    if (equationMatch == null)
                    {
                        mep.undoManager.add (new AddEquation (this, piecesAfter.condition, piecesAfter.combiner, piecesAfter.expression));
                    }
                    else
                    {
                        Variable.ParsedValue piecesMatch = new Variable.ParsedValue (piecesDest.combiner + equationMatch.source.get () + equationMatch.source.key ());
                        mep.undoManager.add (new ChangeEquation (this, piecesMatch.condition, piecesMatch.combiner, piecesMatch.expression, piecesAfter.condition, piecesAfter.combiner, piecesAfter.expression));
                    }
                    parent.updateTabStops (fm);
                    parent.allNodesChanged (model);
                    return;
                }
            }
            else
            {
                if (newlyCreated)
                {
                    NodeVariable nva = (NodeVariable) nodeAfter;
                    if (equationCount == 0)
                    {
                        if (piecesAfter.condition.equals (piecesDest.condition))
                        {
                            mep.undoManager.add (new ChangeVariable (nva, nameAfter, valueAfter, getKeyPath ()));
                        }
                        else
                        {
                            mep.undoManager.add (new AddEquation (nva, piecesAfter.condition, piecesAfter.combiner, piecesAfter.expression, getKeyPath ()));
                        }
                    }
                    else
                    {
                        if (equationMatch == null)
                        {
                            mep.undoManager.add (new AddEquation (nva, piecesAfter.condition, piecesAfter.combiner, piecesAfter.expression, getKeyPath ()));
                        }
                        else
                        {
                            Variable.ParsedValue piecesMatch = new Variable.ParsedValue (piecesDest.combiner + equationMatch.source.get () + equationMatch.source.key ());
                            mep.undoManager.add (new ChangeEquation (nva, piecesMatch.condition, piecesMatch.combiner, piecesMatch.expression, piecesAfter.condition, piecesAfter.combiner, piecesAfter.expression, getKeyPath ()));
                        }
                    }
                    // commit suicide
                    parent.source.clear (nameBefore);
                    model.removeNodeFromParent (this);
                    return;
                }
            }
        }

        // The default action
        if (newlyCreated  &&  valueAfter.isEmpty ()) valueAfter = "0";
        mep.undoManager.add (new ChangeVariable (this, nameAfter, valueAfter));
    }

    @Override
    public void delete (JTree tree, boolean canceled)
    {
        if (! source.isFromTopDocument ()) return;
        PanelModel.instance.undoManager.add (new DeleteVariable (this, canceled));
    }
}

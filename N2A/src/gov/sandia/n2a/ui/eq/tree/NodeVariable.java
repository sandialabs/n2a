/*
Copyright 2016-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


package gov.sandia.n2a.ui.eq.tree;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Operator;
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

@SuppressWarnings("serial")
public class NodeVariable extends NodeContainer
{
    protected static ImageIcon iconVariable = ImageUtil.getImage ("delta.png");
    protected static ImageIcon iconBinding  = ImageUtil.getImage ("connect.gif");
    protected static ImageIcon iconWatch    = ImageUtil.getImage ("watch.png");  // gets modified in static section below

    public    boolean       isBinding;
    protected List<Integer> columnWidths;

    static
    {
        Image delta = iconVariable.getImage ();
        Image eye   = iconWatch.getImage ();
        int w = iconVariable.getIconWidth ();
        int h = iconVariable.getIconHeight ();
        BufferedImage combined = new BufferedImage (w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = combined.createGraphics ();
        Composite c = g.getComposite ();
        g.setComposite (AlphaComposite.getInstance (AlphaComposite.SRC_OVER, 0.5f));
        g.drawImage (eye, 0, 0, null);
        g.setComposite (c);
        g.drawImage (delta, 0, 0, null);
        g.dispose ();
        iconWatch = new ImageIcon (combined);
    }

    public NodeVariable (MPart source)
    {
        this.source = source;
    }

    public String getValue ()
    {
        String value = source.get ();
        if (value.startsWith ("$kill")) return "";
        return value;
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
        for (MNode n : source)
        {
            if (n.key ().startsWith ("@")) add (new NodeEquation ((MPart) n));
        }

        MPart metadata = (MPart) source.child ("$metadata");
        if (metadata != null)
        {
            for (MNode m : metadata)
            {
                NodeAnnotation a = new NodeAnnotation ((MPart) m);
                add (a);
                a.build ();
            }
        }

        MPart references = (MPart) source.child ("$reference");
        if (references != null)
        {
            for (MNode r : references) add (new NodeReference ((MPart) r));
        }
    }

    /**
        Ensures that a variable either has multiple equations or a one-line expression, but not both.
        This method is independent of any given NodeVariable so it can be used at various points in processing.
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

        String value = source.get ();
        if (value.startsWith ("$kill")) value = "";
        Variable.ParsedValue pieces = new Variable.ParsedValue (value);
        boolean empty =  pieces.expression.isEmpty ()  &&  pieces.condition.isEmpty ();

        // Collapse or expand
        if (equationCount > 0)
        {
            if (! empty)
            {
                // Expand
                source.set (pieces.combiner);
                source.set (pieces.expression, "@" + pieces.condition);  // may override an existing equation
            }
            else if (equationCount == 1)
            {
                // Collapse
                String key = equation.key ();
                source.clear (key);
                if (key.equals ("@")) source.set (pieces.combiner + equation.get ());
                else                  source.set (pieces.combiner + equation.get () + key);
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
        String name  = source.key ().trim ();
        String value = source.get ().trim ();
        NodeBase referent = null;
        if (Operator.containsConnect (value))
        {
            isBinding = true;
        }
        else
        {
            if (value.isEmpty ()  ||  value.startsWith ("$kill")) return;

            // Determine if our LHS has the right form.
            if (name.endsWith ("'")) return;

            // Determine if our RHS has the right form. If so, scan for the referent.
            if (NodePart.isIdentifierPath (value))
            {
                referent = parent.resolveName (value);
                if      (referent == null)             isBinding = ! value.contains (".");  // Ambiguous, so we make an arbitrary call that it is an unresolved variable reference rather than unresolved part reference.
                else if (referent instanceof NodePart) isBinding = true;
            }
        }

        if (isBinding)
        {
            if (parent.connectionBindings == null) parent.connectionBindings = new HashMap<String,NodePart> ();
            parent.connectionBindings.put (name, (NodePart) referent);  // referent may be null, in which case there is unconnected endpoint.
        }
    }

    @Override
    public boolean visible (int filterLevel)
    {
        if (filterLevel == FilteredTreeModel.REVOKED) return true;
        if (source.get ().startsWith ("$kill"))       return false;
        if (filterLevel == FilteredTreeModel.ALL)     return true;
        if (filterLevel == FilteredTreeModel.PARAM)   return source.getFlag ("$metadata", "param");
        // LOCAL ...
        return source.isFromTopDocument ();
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        if (isBinding) return iconBinding;
        MPart watch = (MPart) source.child ("$metadata", "watch");
        if (watch != null  &&  watch.isFromTopDocument ()) return iconWatch;
        return iconVariable;
    }

    @Override
    public String getText (boolean expanded, boolean editing)
    {
        String result = toString ();
        if (result.isEmpty ()) return result;  // Allow user object to be "" for new nodes.
        if (editing) return source.key () + "=" + getValue ();  // We're about to go into edit, so remove tabs.
        if (! expanded  &&  children != null)  // show special mark when multi-line equation is collapsed
        {
            for (Object o : children) if (o instanceof NodeEquation) return result + "🡰";
        }
        return result;
    }

    @Override
    public int getForegroundColor ()
    {
        if (source.get ().startsWith ("$kill")) return KILL;
        return super.getForegroundColor ();
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
            columnWidths = new ArrayList<Integer> (2);
            columnWidths.add (0);
            columnWidths.add (0);
        }
        columnWidths.set (0, fm.stringWidth (source.key () + " "));
        Variable.ParsedValue pieces = new Variable.ParsedValue (getValue ());
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
        Variable.ParsedValue pieces = new Variable.ParsedValue (getValue ());

        result = pad (result, tabs.get (0), fm) + "=" + pieces.combiner;
        result = pad (result, tabs.get (1), fm) + pieces.expression;
        if (! pieces.condition.isEmpty ()) result = result + " @ " + pieces.condition;

        setUserObject (result);
    }

    @Override
    public void copy (MNode result)
    {
        MNode n = result.childOrCreate (source.key ());
        if (source.data ()) n.set (source.get ());
        Enumeration<?> cf = childrenFiltered ();
        while (cf.hasMoreElements ())
        {
            NodeBase child = (NodeBase) cf.nextElement ();
            if      (child instanceof NodeAnnotation) child.copy (n.childOrCreate ("$metadata"));
            else if (child instanceof NodeReference)  child.copy (n.childOrCreate ("$reference"));
            else     child.copy (n);
        }
    }

    @Override
    public NodeBase add (String type, JTree tree, MNode data, Point location)
    {
        if (type.isEmpty ())
        {
            FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
            if (model.getChildCount (this) == 0  ||  tree.isCollapsed (new TreePath (getPath ()))) return ((NodeBase) getParent ()).add ("Variable", tree, data, location);
            type = "Equation";
        }
        if (isBinding) return ((NodeBase) getParent ()).add (type, tree, data, location);

        if (type.equals ("Equation"))
        {
            if (data != null)
            {
                String key = data.key ();  // includes @

                // Determine if pasting over empty variable (no equations of any type except a naked combiner)
                Variable.ParsedValue existing = new Variable.ParsedValue (source.get ());
                boolean hasEquations =  ! existing.condition.isEmpty ()  ||  ! existing.expression.isEmpty ();
                if (! hasEquations)
                {
                    Enumeration<?> children = children ();  // unfiltered
                    while (children.hasMoreElements ())
                    {
                        Object c = children.nextElement ();
                        if (c instanceof NodeEquation)
                        {
                            hasEquations = true;
                            break;
                        }
                    }
                }
                if (! hasEquations)  // no equations, or possibly a naked combiner
                {
                    String value = existing.combiner + data.get () + key;
                    if (value.endsWith ("@")) value = value.substring (0, value.length () - 1);
                    PanelModel.instance.undoManager.add (new ChangeVariable (this, source.key (), value));
                    return null;  // Don't edit anything
                }

                // Determine if pasting over an existing equation
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

        return ((NodeBase) getParent ()).add (type, tree, data, location);  // refer all other requests up the tree
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

    public boolean hasEquations ()
    {
        if (children != null)
        {
            for (Object o : children) if (o instanceof NodeEquation) return true;
        }
        return false;
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
            boolean canceled = PanelModel.instance.undoManager.getPresentationName ().equals ("AddVariable");
            delete (tree, canceled);
            return;
        }

        String[] parts = input.split ("=", 2);
        String nameAfter = parts[0].trim ().replaceAll ("[ \\n\\t]", "");
        String valueAfter;
        if (parts.length > 1)  // Explicit assignment
        {
            valueAfter = parts[1].trim ();
            if (valueAfter.startsWith ("$kill")) valueAfter = valueAfter.substring (5).trim ();
        }
        else
        {
            valueAfter = "0";  // Assume input was a variable name with no assignment. Note: an explicit assignment is required to revoke a variable.
        }

        // What follows is a series of analyses, most having to do with enforcing constraints
        // on name change (which implies moving the variable tree or otherwise modifying another variable).

        // Handle a naked expression.
        String nameBefore  = source.key ();
        String valueBefore = getValue ();
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
            boolean revoked = nodeAfter.source.get ().equals ("$kill");
            if (   ! (nodeAfter instanceof NodeVariable)
                || (nodeAfter != this  &&  nodeAfter.source.isFromTopDocument ()  &&  ! newlyCreated  &&  ! revoked))
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
        if (nodeAfter != null)  // There exists a variable in the target location, so we may end up injecting an equation into a multiconditional expression.
        {
            // In this section, "dest" refers to state of target node before it is overwritten, while "after" refers to newly input values from user.
            Variable.ParsedValue piecesDest  = new Variable.ParsedValue (((NodeVariable) nodeAfter).getValue ());
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
                if (equationCount > 0  &&  expressionAfter)  // Inject an equation into ourselves.
                {
                    if (equationMatch == null)  // New equation
                    {
                        // It is possible to add an equation revocation here without there being an existing equation to revoke.
                        mep.undoManager.add (new AddEquation (this, piecesAfter.condition, piecesAfter.combiner, piecesAfter.expression));
                    }
                    else  // Overwrite an existing equation
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
                if (newlyCreated)  // The newly created node has been renamed such that it will inject into/over an existing variable.
                {
                    NodeVariable nva = (NodeVariable) nodeAfter;
                    if (equationCount == 0)
                    {
                        if (piecesAfter.condition.equals (piecesDest.condition))  // Directly overwrite the target, since they share the say name and condition.
                        {
                            if (valueAfter.isEmpty ()) valueAfter = "$kill";
                            mep.undoManager.add (new ChangeVariable (nva, nameAfter, valueAfter, getKeyPath ()));
                        }
                        else  // Inject new equation and change target into a multiconditional variable.
                        {
                            // Possible to revoke non-existent equation
                            mep.undoManager.add (new AddEquation (nva, piecesAfter.condition, piecesAfter.combiner, piecesAfter.expression, getKeyPath ()));
                        }
                    }
                    else
                    {
                        if (equationMatch == null)  // Add  new equation to an existing multiconditional.
                        {
                            // Possible to revoke non-existent equation
                            mep.undoManager.add (new AddEquation (nva, piecesAfter.condition, piecesAfter.combiner, piecesAfter.expression, getKeyPath ()));
                        }
                        else  // Overwrite an existing equation in a multiconditional
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
        if (valueAfter.isEmpty ())
        {
            if (newlyCreated)
            {
                valueAfter = "0";
            }
            else
            {
                if (! hasEquations ()) valueAfter = "$kill";
            }
        }
        mep.undoManager.add (new ChangeVariable (this, nameAfter, valueAfter));
    }

    @Override
    public void delete (JTree tree, boolean canceled)
    {
        if (source.isFromTopDocument ())
        {
            PanelModel.instance.undoManager.add (new DeleteVariable (this, canceled));
        }
        else
        {
            if (! hasEquations ())
            {
                PanelModel.instance.undoManager.add (new ChangeVariable (this, source.key (), "$kill"));  // revoke the variable
            }
        }
    }
}

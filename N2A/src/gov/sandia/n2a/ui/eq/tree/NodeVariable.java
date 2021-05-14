/*
Copyright 2016-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.UndoManager;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.EquationTreeCellRenderer;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.undo.AddAnnotation;
import gov.sandia.n2a.ui.eq.undo.AddEquation;
import gov.sandia.n2a.ui.eq.undo.AddInherit;
import gov.sandia.n2a.ui.eq.undo.AddReference;
import gov.sandia.n2a.ui.eq.undo.ChangeAnnotations;
import gov.sandia.n2a.ui.eq.undo.ChangeEquation;
import gov.sandia.n2a.ui.eq.undo.ChangeReferences;
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
    public    static ImageIcon iconBinding  = ImageUtil.getImage ("connect.gif");
    protected static ImageIcon iconWatch    = ImageUtil.getImage ("watch.png");  // gets modified in static section below

    public    boolean       isBinding;
    protected List<Integer> highlights;

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
        setUserObject ();
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
        if (parent.connectionBindings != null) parent.connectionBindings.remove (name);  // Will add back later if we turn out to be a connection binding.

        NodeBase referent = null;
        if (Operator.containsConnect (value))  // As a simple heuristic, we let "connect()" override any other considerations.
        {
            isBinding = true;
        }
        else
        {
            // Constraints on form
            if (value.isEmpty ()  ||  value.startsWith ("$kill")) return;  // Must not be revoked.
            if (name.contains ("$")  ||  name.contains ("\\.")  ||  name.endsWith ("'")) return;  // LHS must be a simple identifier.
            if (! NodePart.isIdentifierPath (value)) return;  // RHS must be a valid part name path (not a derivative, no combiner, no expression, no condition).
            if (children != null)  // Must be single line.
            {
                for (Object o : children) if (o instanceof NodeEquation) return;
            }

            // Scan for the referent.
            referent = parent.resolveName (this, null, value);
            if      (referent == null)                 isBinding = ! value.contains (".");  // A name with dot is ambiguous, so we make an arbitrary call that value references a variable rather than a part.
            else if (referent instanceof NodePart)     isBinding = true;
            else if (referent instanceof NodeVariable) isBinding = ((NodeVariable) referent).isBinding;  // probably a sub-reference
        }

        if (! isBinding) return;
        if (parent.connectionBindings == null) parent.connectionBindings = new HashMap<String,NodePart> ();
        if (referent instanceof NodePart) parent.connectionBindings.put (name, (NodePart) referent);
        else                              parent.connectionBindings.put (name, null);  // Unconnected endpoint.
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
    public int getForegroundColor ()
    {
        if (source.get ().startsWith ("$kill")) return KILL;
        return super.getForegroundColor ();
    }

    public boolean findHighlights (String name)
    {
        boolean result = false;
        if (highlights != null)
        {
            result = true;
            highlights.clear ();
        }
        if (name.isEmpty ()) return result;

        // Generate 3rd column using same method as getColumns().
        Variable.ParsedValue pieces = new Variable.ParsedValue (getValue ());
        String expression = pieces.expression;
        if (! pieces.condition.isEmpty ()) expression += " @ " + pieces.condition;
        if (! expression.isEmpty ())
        {
            if (highlights == null) highlights = new ArrayList<Integer> ();
            findHighlights (name, expression, highlights);
            if (highlights.isEmpty ()) highlights = null;
        }
        return  result  ||  highlights != null;
    }

    public static void findHighlights (String name, String expression, List<Integer> inout)
    {
        inout.clear ();
        int next = 0;
        while (next < expression.length ())
        {
            int in = expression.indexOf (name, next);
            if (in < 0) return;
            next = in + name.length ();
            if (in   > 0                     &&  Character.isJavaIdentifierPart (expression.charAt (in - 1))) continue;
            if (next < expression.length ()  &&  Character.isJavaIdentifierPart (expression.charAt (next  ))) continue;
            inout.add (in);
            inout.add (next);
        }
    }

    public static String markHighlights (String value, List<Integer> inout)
    {
        if (inout == null  ||  inout.isEmpty ()) return value;

        // Create an HTML version of the value, with appropriate sections marked.
        StringBuilder result = new StringBuilder ();
        result.append ("<html>");
        int count = inout.size ();
        int next = 0;
        for (int i = 0; i < count; i += 2)
        {
            int in  = inout.get (i);
            int out = inout.get (i+1);
            result.append (escapeHTML (value.substring (next, in), true));
            result.append ("<span bgcolor=\"" + EquationTreeCellRenderer.colorHighlight + "\">");
            result.append (escapeHTML (value.substring (in, out), true));
            result.append ("</span>");
            next = out;
        }
        result.append (escapeHTML (value.substring (next), true));
        result.append ("</html>");
        return result.toString ();
    }

    @Override
    public List<String> getColumns (boolean selected, boolean expanded)
    {
        List<String> result = new ArrayList<String> (3);
        result.add (source.key ());
        Variable.ParsedValue pieces = new Variable.ParsedValue (getValue ());
        result.add ("=" + pieces.combiner);

        if (FilteredTreeModel.showParam  &&  source.get ("$metadata", "param").equals ("watch"))
        {
            result.add ("(watchable)");
            return result;
        }

        boolean hasEquations = false;
        if (! expanded  &&  children != null)
        {
            for (Object o : children)
            {
                if (o instanceof NodeEquation)
                {
                    hasEquations = true;
                    break;
                }
            }
        }

        if (hasEquations)  // show special mark when multi-line equation is collapsed
        {
            result.add (EquationTreeCellRenderer.leftArrow);
        }
        else
        {
            String expression = pieces.expression;
            if (! pieces.condition.isEmpty ()) expression += " @ " + pieces.condition;
            if (! selected) expression = markHighlights (expression, highlights);
            result.add (expression);
        }

        return result;
    }

    @Override
    public List<Integer> getColumnWidths (FontMetrics fm)
    {
        List<Integer> result = new ArrayList<Integer> (2);
        result.add (fm.stringWidth (source.key () + " "));
        Variable.ParsedValue pieces = new Variable.ParsedValue (getValue ());
        result.add (fm.stringWidth ("=" + pieces.combiner + " "));
        return result;
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
    public NodeBase containerFor (String type)
    {
        if (isBinding  &&  ! type.equals ("Annotation")) return ((NodeBase) parent).containerFor (type);
        switch (type)
        {
            case "Equation":
            case "Annotation":
            case "Annotations":
            case "Reference":
            case "References":
                return this;
        }
        return ((NodeBase) parent).containerFor (type);
    }

    @Override
    public Undoable makeAdd (String type, JTree tree, MNode data, Point location)
    {
        if (type.isEmpty ())
        {
            FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
            if (model.getChildCount (this) == 0  ||  tree.isCollapsed (new TreePath (getPath ()))) return ((NodeBase) parent).makeAdd ("Variable", tree, data, location);
            type = "Equation";
        }
        if (isBinding  &&  ! type.equals ("Annotation")) return ((NodeBase) parent).makeAdd (type, tree, data, location);

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
                    return new ChangeVariable (this, source.key (), value);
                }

                // Determine if pasting over an existing equation
                NodeBase existingEquation = child (key);
                if (existingEquation != null)
                {
                    key = key.substring (1);  // remove the @, since ChangeEquation expects strings from ParsedValue
                    String combiner = new Variable.ParsedValue (source.get ()).combiner;
                    return new ChangeEquation (this, key, combiner, existingEquation.source.get (), key, combiner, data.get ());
                }
            }

            // Determine index for new equation
            int index = 0;
            NodeBase child = null;
            TreePath path = tree.getLeadSelectionPath ();
            if (path != null) child = (NodeBase) path.getLastPathComponent ();
            if (child != null  &&  child.getParent () == this) index = getIndex (child);
            while (index > 0  &&  ! (getChildAt (index) instanceof NodeEquation)) index--;
            if (index < getChildCount ()  &&  getChildAt (index) instanceof NodeEquation) index++;

            // Create an AddEquation action
            return new AddEquation (this, index, data);
        }
        else if (type.equals ("Annotation"))
        {
            // Determine index at which to insert new annotation
            int index = 0;
            int count = getChildCount ();
            while (index < count  &&  ! (children.get (index) instanceof NodeReference)) index++;

            return new AddAnnotation (this, index, data);
        }
        else if (type.equals ("Annotations"))
        {
            // In this case, everything under this node will be rebuilt, so no need to worry about insertion index.
            return new ChangeAnnotations (this, data);
        }
        else if (type.equals ("Reference"))
        {
            return new AddReference (this, getChildCount (), data);
        }
        else if (type.equals ("References"))
        {
            return new ChangeReferences (this, data);
        }

        return ((NodeBase) parent).makeAdd (type, tree, data, location);  // refer all other requests up the tree
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

    @Override
    public boolean allowEdit ()
    {
        if (FilteredTreeModel.showParam) return ! source.get ("$metadata", "param").equals ("watch");
        return true;
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
        String input = toString ();
        UndoManager um = MainFrame.instance.undoManager;
        boolean canceled = um.getPresentationName ().equals ("AddVariable");
        if (input.isEmpty ())
        {
            delete (canceled);
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
            valueAfter = "";  // Input was a variable name with no assignment.
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
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        boolean canInject    =  getChildCount () == 0  &&  source.isFromTopDocument ();
        boolean newlyCreated =  canInject  &&  valueBefore.isEmpty ();  // Only a heuristic. Could also be an existing variable with no equation.
        NodeBase parent = (NodeBase) getParent ();
        if (nameAfter.equals ("$inherit"))
        {
            if (parent.child (nameAfter) == null)
            {
                if (newlyCreated)
                {
                    parent.source.clear (nameBefore);
                    // No need to update GUI, because AddInherit rebuilds parent.
                    um.apply (new AddInherit ((NodePart) parent, valueAfter));
                }
                else
                {
                    um.apply (new ChangeVariableToInherit (this, valueAfter));
                }
                return;
            }

            nameAfter = nameBefore;  // Reject name change, because $inherit already exists. User should edit it directly.
        }

        // Prevent illegal name change. (Don't override another top-level node. Don't overwrite a non-variable node.)
        NodeBase nodeAfter = parent.child (nameAfter);
        if (nodeAfter != null)
        {
            boolean isVariable = nodeAfter instanceof NodeVariable;
            boolean different  = nodeAfter != this;
            boolean topdoc     = nodeAfter.source.isFromTopDocument ();
            boolean revoked    = nodeAfter.source.get ().equals ("$kill");
            if (! isVariable  ||  (different  &&  topdoc  &&  ! revoked  &&  ! canInject))
            {
                nameAfter = nameBefore;
                nodeAfter = this;
            }
        }

        // If there's nothing to do, then repaint the node and quit.
        if (nameBefore.equals (nameAfter)  &&  valueBefore.equals (valueAfter))
        {
            setUserObject ();
            model.nodeChanged (this);
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
                        um.apply (new AddEquation (this, piecesAfter.condition, piecesAfter.combiner, piecesAfter.expression));
                    }
                    else  // Overwrite an existing equation
                    {
                        Variable.ParsedValue piecesMatch = new Variable.ParsedValue (piecesDest.combiner + equationMatch.source.get () + equationMatch.source.key ());
                        um.apply (new ChangeEquation (this, piecesMatch.condition, piecesMatch.combiner, piecesMatch.expression, piecesAfter.condition, piecesAfter.combiner, piecesAfter.expression));
                    }
                    return;
                }
            }
            else  // Node has been renamed.
            {
                if (canInject)  // Inject into/over an existing variable.
                {
                    // Remove this variable, regardless of what we do to nodeAfter.
                    um.addEdit (new CompoundEdit ());
                    um.apply (new DeleteVariable (this, canceled));

                    // Decide what change (if any) to apply to nodeAfter.
                    if (expressionAfter)
                    {
                        NodeVariable nva = (NodeVariable) nodeAfter;
                        if (equationCount == 0)
                        {
                            if (piecesAfter.condition.equals (piecesDest.condition))  // Directly overwrite the target, since they share the say name and condition.
                            {
                                um.apply (new ChangeVariable (nva, nameAfter, valueAfter, getKeyPath ()));
                            }
                            else  // Inject new equation and change target into a multiconditional variable.
                            {
                                // Possible to revoke non-existent equation
                                um.apply (new AddEquation (nva, piecesAfter.condition, piecesAfter.combiner, piecesAfter.expression, getKeyPath ()));
                            }
                        }
                        else
                        {
                            if (equationMatch == null)  // Add new equation to an existing multiconditional.
                            {
                                // Possible to revoke non-existent equation
                                um.apply (new AddEquation (nva, piecesAfter.condition, piecesAfter.combiner, piecesAfter.expression, getKeyPath ()));
                            }
                            else  // Overwrite an existing equation in a multiconditional
                            {
                                Variable.ParsedValue piecesMatch = new Variable.ParsedValue (piecesDest.combiner + equationMatch.source.get () + equationMatch.source.key ());
                                um.apply (new ChangeEquation (nva, piecesMatch.condition, piecesMatch.combiner, piecesMatch.expression, piecesAfter.condition, piecesAfter.combiner, piecesAfter.expression, getKeyPath ()));
                            }
                        }
                    }

                    um.endCompoundEdit ();
                    return;
                }
            }
        }

        // The default action
        if (valueAfter.isEmpty ()  &&  ! hasEquations ()) valueAfter = "@";  // The @ will be hidden most of the time, but it will distinguish a variable from a part.
        um.apply (new ChangeVariable (this, nameAfter, valueAfter));
    }

    @Override
    public Undoable makeDelete (boolean canceled)
    {
        if (source.isFromTopDocument ()) return new DeleteVariable (this, canceled);
        return new ChangeVariable (this, source.key (), "$kill");  // revoke the variable
    }
}

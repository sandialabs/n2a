/*
Copyright 2016-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.tree;

import java.awt.FontMetrics;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPart;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.undo.ChangeEquation;
import gov.sandia.n2a.ui.eq.undo.DeleteEquation;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;

@SuppressWarnings("serial")
public class NodeEquation extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("assign.png");

    protected List<Integer> highlightsExpression;
    protected List<Integer> highlightsCondition;

    public NodeEquation (MPart source)
    {
        this.source = source;
        setUserObject ();
    }

    @Override
    public void setUserObject ()
    {
        String condition  = source.key ();
        String expression = source.get ();
        if (! condition.equals ("@")  ||  expression.length () == 0)  // Condition always starts with @, but might otherwise be empty, if it is the default equation. Generally, don't show the @ for the default equation, unless it has been revoked.
        {
            expression += "@" + condition.substring (1);
        }
        setUserObject (expression);
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        return icon;
    }

    public boolean findHighlights (NodeBase target, String name)
    {
        boolean result =  highlightsExpression != null  ||  highlightsCondition != null;
        if (target == null)
        {
            highlightsExpression = null;
            highlightsCondition  = null;
            return result;
        }
        if (highlightsExpression != null) highlightsExpression.clear ();
        if (highlightsCondition  != null) highlightsCondition .clear ();

        // Generate strings using same method as getColumns().
        String expression = source.get ();
        String condition  = source.key ();

        // The following logic is excessively complicated in order to minimize number of objects created and stored.
        NodeVariable parent = (NodeVariable) getParent ();
        List<Integer> reuse = null;

        if (expression.contains (name))
        {
            if (highlightsExpression == null) highlightsExpression = new ArrayList<Integer> ();
            parent.findHighlights (target, expression, highlightsExpression);  // This is the important line.
        }
        if (highlightsExpression != null)
        {
            if (highlightsExpression.isEmpty ())
            {
                reuse = highlightsExpression;
                highlightsExpression = null;
            }
            else
            {
                result = true;
            }
        }

        if (condition.contains (name))
        {
            if (highlightsCondition == null) highlightsCondition = reuse;
            if (highlightsCondition == null) highlightsCondition = new ArrayList<Integer> ();
            parent.findHighlights (target, condition.substring (1), highlightsCondition);
        }
        if (highlightsCondition != null)
        {
            if (highlightsCondition.isEmpty ())
            {
                highlightsCondition = null;
            }
            else
            {
                result = true;
                int count = highlightsCondition.size ();
                for (int i = 0; i < count; i++) highlightsCondition.set (i, highlightsCondition.get (i) + 2);  // add 2 for "@ "
            }
        }

        return result;
    }

    @Override
    public List<String> getColumns (boolean selected, boolean expanded)
    {
        List<String> result = new ArrayList<String> (2);
        String expression = source.get ();
        String condition  = source.key ();
        if (selected) result.add (expression);
        else          result.add (NodeVariable.markHighlights (expression, highlightsExpression));
        if (! condition.equals ("@")  ||  expression.length () == 0)
        {
            condition = "@ " + condition.substring (1);
            if (! selected) condition = NodeVariable.markHighlights (condition, highlightsCondition);
            result.add (condition);
        }
        return result;
    }

    @Override
    public List<Integer> getColumnWidths (FontMetrics fm)
    {
        List<Integer> result = new ArrayList<Integer> (1);
        result.add (fm.stringWidth (source.get () + " "));
        return result;
    }

    @Override
    public Undoable makeAdd (String type, JTree tree, MNode data, Point2D.Double location)
    {
        if (type.isEmpty ()) type = "Equation";
        return ((NodeBase) parent).makeAdd (type, tree, data, location);
    }

    @Override
    public void applyEdit (JTree tree)
    {
        String input = (String) getUserObject ();
        if (input.isEmpty ())
        {
            boolean canceled = MainFrame.undoManager.getPresentationName ().startsWith ("AddEquation");
            delete (canceled);
            return;
        }

        // There are three possible outcomes of the edit:
        // 1) Nothing changed
        // 2) The name was not allowed to change
        // 3) Arbitrary change

        Variable.ParsedValue piecesBefore = new Variable.ParsedValue (source.get () + source.key ());
        Variable.ParsedValue piecesAfter  = new Variable.ParsedValue (input);

        NodeVariable parent = (NodeVariable) getParent ();
        if (! piecesBefore.condition.equals (piecesAfter.condition))
        {
            MPart partAfter = (MPart) parent.source.child ("@" + piecesAfter.condition);
            if (partAfter != null  &&  partAfter.isFromTopDocument ()  &&  ! partAfter.getFlag ("$kill"))  // Can't overwrite another top-document node, unless it is a revocation ...
            {
                piecesAfter.condition = piecesBefore.condition;  // reject key change
            }
        }

        if (piecesBefore.equals (piecesAfter))
        {
            setUserObject ();
            FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
            model.nodeChanged (this);
            return;
        }

        piecesBefore.combiner = parent.source.get ();  // The fact that we are modifying an existing equation node indicates that the variable (parent) should only contain a combiner.
        if (piecesAfter.combiner.isEmpty ()) piecesAfter.combiner = piecesBefore.combiner;

        MainFrame.undoManager.apply (new ChangeEquation (parent, piecesBefore.condition, piecesBefore.combiner, piecesBefore.expression, piecesAfter.condition, piecesAfter.combiner, piecesAfter.expression));
    }

    @Override
    public Undoable makeDelete (boolean canceled)
    {
        DeleteEquation result = new DeleteEquation (this, canceled);
        if (canceled)  // Our delete is expected to neutralize an add. (But we still verify below that the add exists.)
        {
            // Hack to ensure unkill on variable gets reversed properly.
            String last = MainFrame.undoManager.getPresentationName ();
            result.killedVariable = last.equals ("AddEquation killed");
        }
        return result;
    }
}

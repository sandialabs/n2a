/*
Copyright 2016-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


package gov.sandia.n2a.ui.eq.tree;

import java.awt.FontMetrics;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.ui.MainFrame;
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
    protected List<Integer> columnWidths;

    public NodeEquation (MPart source)
    {
        this.source = source;
    }

    @Override
    public boolean visible (int filterLevel)
    {
        if (filterLevel == FilteredTreeModel.REVOKED)         return true;
        String value = source.get ();
        if (value.isEmpty ()  ||  value.startsWith ("$kill")) return false;
        if (filterLevel == FilteredTreeModel.LOCAL)           return source.isFromTopDocument ();
        if (filterLevel == FilteredTreeModel.PARAM)           return false;
        // ALL ...
        return true;
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        return icon;
    }

    @Override
    public String getText (boolean expanded, boolean editing)
    {
        String result = toString ();
        if (editing  &&  ! result.isEmpty ())  // An empty user object indicates a newly created node, which we want to edit as a blank.
        {
            result           = source.get ();
            String condition = source.key ();
            if (! condition.equals ("@")  ||  result.length () == 0) result = result + condition;
        }
        return result;
    }

    @Override
    public int getForegroundColor ()
    {
        String value = source.get ();
        if (value.isEmpty ()  ||  value.startsWith ("$kill")) return KILL;
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
            columnWidths = new ArrayList<Integer> (1);
            columnWidths.add (0);
        }
        columnWidths.set (0, fm.stringWidth (source.get () + " "));
    }

    @Override
    public List<Integer> getColumnWidths ()
    {
        return columnWidths;
    }

    @Override
    public void applyTabStops (List<Integer> tabs, FontMetrics fm)
    {
        String key    = source.key ();
        String result = source.get ();
        if (! key.equals ("@")  ||  result.length () == 0)  // Condition always starts with @, but might otherwise be empty, if it is the default equation. Generally, don't show the @ for the default equation, unless it has been revoked.
        {
            result = pad (result, tabs.get (0), fm) + "@ " + key.substring (1);
        }
        setUserObject (result);
    }

    @Override
    public NodeBase add (String type, JTree tree, MNode data, Point location)
    {
        if (type.isEmpty ()) type = "Equation";
        return ((NodeBase) getParent ()).add (type, tree, data, location);
    }

    @Override
    public void applyEdit (JTree tree)
    {
        String input = (String) getUserObject ();
        if (input.isEmpty ())
        {
            boolean canceled = MainFrame.instance.undoManager.getPresentationName ().equals ("AddEquation");
            delete (tree, canceled);
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
            if (partAfter != null  &&  partAfter.isFromTopDocument ())  // Can't overwrite another top-document node, unless it is a revocation ...
            {
                String value = partAfter.get ();
                boolean revoked =  value.isEmpty ()  ||  value.startsWith ("$kill");
                if (! revoked) piecesAfter.condition = piecesBefore.condition;  // reject key change
            }
        }

        if (piecesBefore.equals (piecesAfter))
        {
            FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
            FontMetrics fm = getFontMetrics (tree);
            parent.updateTabStops (fm);
            parent.allNodesChanged (model);
            return;
        }

        piecesBefore.combiner = parent.source.get ();  // The fact that we are modifying an existing equation node indicates that the variable (parent) should only contain a combiner.
        if (piecesAfter.combiner.isEmpty ()) piecesAfter.combiner = piecesBefore.combiner;

        MainFrame.instance.undoManager.add (new ChangeEquation (parent, piecesBefore.condition, piecesBefore.combiner, piecesBefore.expression, piecesAfter.condition, piecesAfter.combiner, piecesAfter.expression));
    }

    @Override
    public void delete (JTree tree, boolean canceled)
    {
        if (source.isFromTopDocument ())
        {
            MainFrame.instance.undoManager.add (new DeleteEquation (this, canceled));
        }
        else
        {
            NodeVariable parent   = (NodeVariable) getParent ();
            String       combiner = parent.source.get ();
            String       name     = source.key ().substring (1);  // strip @ from name, as required by ChangeEquation
            String       value    = source.get ();
            MainFrame.instance.undoManager.add (new ChangeEquation (parent, name, combiner, value, name, combiner, ""));  // revoke the equation
        }
    }
}

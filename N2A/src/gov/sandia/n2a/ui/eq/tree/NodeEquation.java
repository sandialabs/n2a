/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.undo.ChangeEquation;
import gov.sandia.n2a.ui.eq.undo.DeleteEquation;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;

public class NodeEquation extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("equation.png");
    protected List<Integer> columnWidths;

    public NodeEquation (MPart source)
    {
        this.source = source;
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
            if (! condition.equals ("@")) result = result + condition;
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
        if (! key.equals ("@"))  // Means that there is more than blank for a condition. In all cases, condition starts with "@".
        {
            int offset = tabs.get (0).intValue () - fm.stringWidth (result);
            result = result + pad (offset, fm) + "@ " + key.substring (1);
        }
        setUserObject (result);
    }

    @Override
    public NodeBase add (String type, JTree tree)
    {
        if (type.isEmpty ()) type = "Equation";
        return ((NodeBase) getParent ()).add (type, tree);
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

        // There are three possible outcomes of the edit:
        // 1) Nothing changed
        // 2) The name was not allowed to change
        // 3) Arbitrary change

        Variable.ParsedValue piecesBefore = new Variable.ParsedValue (source.get () + source.key ());
        Variable.ParsedValue piecesAfter  = new Variable.ParsedValue (input);

        NodeVariable parent = (NodeVariable) getParent ();
        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        FontMetrics fm = getFontMetrics (tree);
        if (piecesBefore.equals (piecesAfter))
        {
            parent.updateTabStops (fm);
            parent.allNodesChanged (model);
            return;
        }

        String nameBefore = "@" + piecesBefore.conditional;
        String nameAfter  = "@" + piecesAfter.conditional;
        NodeBase nodeAfter = parent.child (nameAfter);
        if (nodeAfter != null  &&  nodeAfter.source.isFromTopDocument ())  // Can't overwrite another top-document node
        {
            nameAfter = nameBefore;
        }

        String combinerBefore = parent.source.get ();
        String combinerAfter  = piecesAfter.combiner;
        if (combinerAfter.isEmpty ()) combinerAfter = combinerBefore;

        ModelEditPanel.instance.undoManager.add (new ChangeEquation (parent, nameBefore, combinerBefore, piecesBefore.expression, nameAfter, combinerAfter, piecesAfter.expression));
    }

    @Override
    public void delete (JTree tree)
    {
        if (! source.isFromTopDocument ()) return;
        ModelEditPanel.instance.undoManager.add (new DeleteEquation (this));
    }
}

/*
Copyright 2016-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


package gov.sandia.n2a.ui.eq.tree;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.undo.ChangeReference;
import gov.sandia.n2a.ui.eq.undo.DeleteReference;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;

@SuppressWarnings("serial")
public class NodeReference extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("book.gif");
    protected List<Integer> columnWidths;

    public NodeReference (MPart source)
    {
        this.source = source;
    }

    @Override
    public boolean visible (int filterLevel)
    {
        if (filterLevel <= FilteredTreeModel.ALL)   return true;
        if (filterLevel == FilteredTreeModel.PARAM) return false;
        // FilteredTreeModel.LOCAL ...
        return source.isFromTopDocument ();
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
            result       = source.key ();
            String value = source.get ();
            if (! value.isEmpty ()) result = result + "=" + value;
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
        boolean pure = getParent () instanceof NodeReferences;
        if (columnWidths == null)
        {
            columnWidths = new ArrayList<Integer> (1);
            columnWidths.add (0);
            if (! pure)
            {
                columnWidths.add (0);
                columnWidths.add (0);
            }
        }
        int width = fm.stringWidth (source.key () + " ");
        if (pure) columnWidths.set (0, width);  // We are in a $reference block, so only need the first tab stop.
        else      columnWidths.set (2, width);  // Stash column width in higher position, so it doesn't interfere with multi-line equations or annotations under a variable.
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
        String value  = source.get ();
        if (! value.isEmpty ())
        {
            int offset = tabs.get (0);
            if (! (getParent () instanceof NodeReferences))  // not in a $reference block, so may share tab stops with equations and annotations
            {
                offset = tabs.get (2) - tabs.get (1) - offset;
            }
            int width = PanelModel.instance.panelEquationTree.tree.getWidth () - offset;  // Available width for displaying value (not including key).

            boolean addEllipsis = false;
            String[] pieces = value.split ("\n", 2);
            if (pieces.length > 1)
            {
                value = pieces[0];
                addEllipsis = true;
            }
            int valueWidth = fm.stringWidth (value);
            if (valueWidth > width)
            {
                width -= fm.getMaxAdvance () * 2;  // allow 2em for ellipsis
                int characters = (int) Math.floor ((double) value.length () * width / valueWidth);  // A crude estimate. Just take a ratio of the number of characters, rather then measuring them exactly.
                value = value.substring (0, characters);
                addEllipsis = true;
            }
            if (addEllipsis) value += " ...";

            result = pad (result, offset, fm) + "= " + value;
        }
        setUserObject (result);
    }

    @Override
    public NodeBase add (String type, JTree tree, MNode data)
    {
        if (type.isEmpty ()) type = "Reference";  // By context, we assume the user wants to add another reference.
        return ((NodeBase) getParent ()).add (type, tree, data);
    }

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
        String name = parts[0].trim ().replaceAll ("[ \\n\\t]", "");
        String value;
        if (parts.length > 1) value = parts[1].trim ();
        else                  value = "";

        NodeBase parent = (NodeBase) getParent ();
        String oldName  = source.key ();
        String oldValue = source.get ();
        if (! name.equals (oldName))
        {
            // Check if name change is forbidden
            if (name.isEmpty ())
            {
                name = oldName;
            }
            else
            {
                MPart mparent = source.parent ();
                MPart partAfter = (MPart) mparent.child (name);
                if (partAfter != null  &&  partAfter.isFromTopDocument ()) name = oldName;
            }
        }
        if (name.equals (oldName)  &&  value.equals (oldValue))
        {
            FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
            FontMetrics fm = getFontMetrics (tree);
            parent.updateTabStops (fm);
            model.nodeChanged (this);  // Our siblings should not change, because we did not really change. Just repaint in non-edit mode.
            return;
        }

        PanelModel.instance.undoManager.add (new ChangeReference (parent, oldName, oldValue, name, value));
    }

    @Override
    public void delete (JTree tree, boolean canceled)
    {
        if (! source.isFromTopDocument ()) return;
        PanelModel.instance.undoManager.add (new DeleteReference (this, canceled));
    }
}

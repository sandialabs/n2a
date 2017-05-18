/*
Copyright 2016,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import gov.sandia.n2a.ui.eq.undo.ChangeAnnotation;
import gov.sandia.n2a.ui.eq.undo.DeleteAnnotation;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;

public class NodeAnnotation extends NodeBase
{
    public static ImageIcon icon = ImageUtil.getImage ("edit.gif");
    protected List<Integer> columnWidths;

    public NodeAnnotation (MPart source)
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
        boolean pure = getParent () instanceof NodeAnnotations;
        if (columnWidths == null)
        {
            columnWidths = new ArrayList<Integer> (1);
            columnWidths.add (0);
            if (! pure) columnWidths.add (0);
        }
        int width = fm.stringWidth (source.key () + " ");
        if (pure) columnWidths.set (0, width);  // We are in a $metadata block, so only need the first tab stop.
        else      columnWidths.set (1, width);  // Stash column width in higher position, so it doesn't interfere with multi-line equations under a variable.
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
            String[] pieces = value.split ("\n", 2);
            if (pieces.length > 1) value = pieces[0] + " ...";

            int offset = tabs.get (0).intValue ();
            if (! (getParent () instanceof NodeAnnotations))  // not in a $metadata block, so may share tab stops with equations
            {
                offset = tabs.get (1).intValue () - offset;
            }
            offset -= fm.stringWidth (result);
            result = result + pad (offset, fm) + "= " + value;
        }
        setUserObject (result);
    }

    @Override
    public NodeBase add (String type, JTree tree, MNode data)
    {
        if (type.isEmpty ()) type = "Annotation";  // By context, we assume the user wants to add another annotation.
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
                MPart mparent = source.getParent ();
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

        PanelModel.instance.undoManager.add (new ChangeAnnotation (parent, oldName, oldValue, name, value));
    }

    @Override
    public void delete (JTree tree, boolean canceled)
    {
        if (! source.isFromTopDocument ()) return;
        PanelModel.instance.undoManager.add (new DeleteAnnotation (this, canceled));
    }
}

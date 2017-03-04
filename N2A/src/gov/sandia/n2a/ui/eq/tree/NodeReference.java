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
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.undo.ChangeReference;
import gov.sandia.n2a.ui.eq.undo.DeleteReference;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;

public class NodeReference extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("book.gif");
    protected List<Integer> columnWidths;

    public NodeReference (MPart source)
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
            String[] pieces = value.split ("\n", 2);
            if (pieces.length > 1) value = pieces[0] + " ...";

            int offset = tabs.get (0).intValue ();
            if (! (getParent () instanceof NodeReferences))  // not in a $reference block, so may share tab stops with equations and annotations
            {
                offset = tabs.get (2).intValue () - tabs.get (1).intValue () - offset;
            }
            offset -= fm.stringWidth (result);
            result = result + pad (offset, fm) + "= " + value;
        }
        setUserObject (result);
    }

    @Override
    public NodeBase add (String type, JTree tree)
    {
        if (type.isEmpty ()) type = "Reference";  // By context, we assume the user wants to add another reference.
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

        String[] parts = input.split ("=", 2);
        String name = parts[0].trim ().replaceAll ("[ \\n\\t]", "");
        String value;
        if (parts.length > 1) value = parts[1].trim ();
        else                  value = "";

        NodeBase existingReference = null;
        String oldName  = source.key ();
        String oldValue = source.get ();
        NodeBase parent = (NodeBase) getParent ();
        if (! name.equals (oldName)) existingReference = parent.child (name);
        if (name.isEmpty ()  ||  existingReference != null) name = oldName; // name change is forbidden
        if (name.equals (oldName)  &&  value.equals (oldValue))
        {
            FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
            FontMetrics fm = getFontMetrics (tree);
            parent.updateTabStops (fm);
            model.nodeChanged (this);  // Our siblings should not change, because we did not really change. Just repaint in non-edit mode.
        }
        else
        {
            ModelEditPanel.instance.undoManager.add (new ChangeReference (parent, oldName, oldValue, name, value));
        }
    }

    @Override
    public void delete (JTree tree)
    {
        if (! source.isFromTopDocument ()) return;
        ModelEditPanel.instance.undoManager.add (new DeleteReference (this));
    }
}
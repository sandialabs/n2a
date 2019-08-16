/*
Copyright 2016-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.undo.ChangeInherit;
import gov.sandia.n2a.ui.eq.undo.DeleteInherit;
import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

@SuppressWarnings("serial")
public class NodeInherit extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("inherit.png");
    protected List<Integer> columnWidths;
    protected boolean       showID;

    public NodeInherit (MPart source)
    {
        this.source = source;

        // Check if all IDs match
        String value = source.get ();
        String pieces[] = value.split (",");
        for (int i = 0; i < pieces.length; i++)
        {
            String parentName = pieces[i].replace ("\"", "");
            MNode  parent     = AppData.models.child (parentName);
            if (parent == null)
            {
                showID = true;
                break;
            }
            else
            {
                String parentID = parent.get ("$metadata", "id");
                String childID  = source.get (i);
                if (! parentID.equals (childID))
                {
                    showID = true;
                    break;
                }
            }
        }

        String result = source.key () + "=" + value;
        if (showID) result = result + " (" + IDs () + ")";
        setUserObject (result);
    }

    public String IDs ()
    {
        String IDs = "";
        for (MNode i : source)
        {
            if (! IDs.isEmpty ()) IDs += ",";
            IDs += i.get ();
        }
        return IDs;
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
        if (editing  &&  ! result.isEmpty ()) return source.key () + "=" + source.get ();
        return result;
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
        columnWidths.set (1, fm.stringWidth ("= "));
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

        result = pad (result, tabs.get (0), fm) + "=";
        result = pad (result, tabs.get (1), fm) + source.get ();

        if (showID) result = result + " (" + IDs () + ")";

        setUserObject (result);
    }

    @Override
    public void copy (MNode result)
    {
        result.set (source, source.key ());
    }

    @Override
    public void applyEdit (JTree tree)
    {
        String input = (String) getUserObject ();
        if (input.isEmpty ())
        {
            boolean canceled = PanelModel.instance.undoManager.getPresentationName ().equals ("AddInherit");
            delete (tree, canceled);
            return;
        }

        String[] parts = input.split ("=", 2);
        String value;
        if (parts.length > 1) value = parts[1].trim ();
        else                  value = "";

        String oldValue = source.get ();
        if (value.equals (oldValue))  // Nothing to do
        {
            if (! parts[0].equals ("$inherit"))  // name change not allowed
            {
                // Repaint the original value
                setUserObject (source.key () + "=" + source.get ());
                FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
                model.nodeChanged (this);
                Rectangle bounds = tree.getPathBounds (new TreePath (getPath ()));
                if (bounds != null) tree.repaint (bounds);
            }
            return;
        }

        PanelModel.instance.undoManager.add (new ChangeInherit (this, value));
    }

    @Override
    public void delete (JTree tree, boolean canceled)
    {
        if (! source.isFromTopDocument ()) return;
        PanelModel.instance.undoManager.add (new DeleteInherit (this, canceled));
    }
}

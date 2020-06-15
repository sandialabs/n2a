/*
Copyright 2016-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
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
    protected        boolean   showID;

    public NodeInherit (MPart source)
    {
        this.source = source;
        setUserObject ();

        // Check if all IDs match
        String[] names = source.get ().split (",");
        String[] IDs   = source.get ("$metadata", "id").split (",", -1);
        for (int i = 0; i < names.length; i++)
        {
            String parentName = names[i].replace ("\"", "");
            MNode  parent     = AppData.models.child (parentName);
            if (parent == null)
            {
                showID = true;
                break;
            }
            else
            {
                String parentID = parent.get ("$metadata", "id");
                String childID  = "";
                if (i < IDs.length) childID = IDs[i];
                if (! parentID.equals (childID))
                {
                    showID = true;
                    break;
                }
            }
        }
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        return icon;
    }

    @Override
    public List<String> getColumns (boolean selected, boolean expanded)
    {
        List<String> result = new ArrayList<String> (3);
        result.add (source.key ());
        result.add ("=");
        String value = source.get ();
        if (showID) value = value + " (" + source.get ("$metadata", "id") + ")";
        result.add (value);
        return result;
    }

    @Override
    public List<Integer> getColumnWidths (FontMetrics fm)
    {
        List<Integer> result = new ArrayList<Integer> (2);
        result.add (fm.stringWidth (source.key () + " "));
        result.add (fm.stringWidth ("= "));
        return result;
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
            boolean canceled = MainFrame.instance.undoManager.getPresentationName ().equals ("AddInherit");
            delete (canceled);
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

        MainFrame.instance.undoManager.apply (new ChangeInherit (this, value));
    }

    @Override
    public Undoable makeDelete (boolean canceled)
    {
        if (source.isFromTopDocument ()) return new DeleteInherit (this, canceled);
        return null;
    }
}

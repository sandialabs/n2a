/*
Copyright 2016,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/


package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.undo.ChangeInherit;
import gov.sandia.n2a.ui.eq.undo.DeleteInherit;
import gov.sandia.n2a.ui.images.ImageUtil;

import java.awt.Rectangle;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

public class NodeInherit extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("inherit.png");

    public NodeInherit (MPart source)
    {
        this.source = source;
        setUserObject (source.key () + "=" + source.get ());
    }

    @Override
    public boolean visible (int filterLevel)
    {
        if (filterLevel == FilteredTreeModel.ALL)    return true;
        if (filterLevel == FilteredTreeModel.PUBLIC) return true;
        // FilteredTreeModel.LOCAL ...
        return source.isFromTopDocument ();
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        return icon;
    }

    @Override
    public void copy (MNode result)
    {
        MNode n = result.set (source.key (), "");
        n.merge (source);
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
                if (bounds != null) tree.paintImmediately (bounds);
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

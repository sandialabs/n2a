/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq.tree;

import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.undo.ChangeInherit;
import gov.sandia.n2a.ui.eq.undo.DeleteInherit;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;

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
    public void applyEdit (JTree tree)
    {
        String input = (String) getUserObject ();
        if (input.isEmpty ())
        {
            delete (tree);
            return;
        }

        String[] parts = input.split ("=", 2);
        String value;
        if (parts.length > 1) value = parts[1].trim ();
        else                  value = "";

        String oldValue = source.get ();
        if (value.equals (oldValue)) return;

        ModelEditPanel.instance.undoManager.add (new ChangeInherit (this, oldValue, value));
    }

    @Override
    public void delete (JTree tree)
    {
        if (! source.isFromTopDocument ()) return;
        ModelEditPanel.instance.undoManager.add (new DeleteInherit (this));
    }
}

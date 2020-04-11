/*
Copyright 2016-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.tree;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.undo.AddReference;
import gov.sandia.n2a.ui.eq.undo.DeleteReferences;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

@SuppressWarnings("serial")
public class NodeReferences extends NodeContainer
{
    protected static ImageIcon icon = ImageUtil.getImage ("properties.gif");

    public NodeReferences (MPart source)
    {
        this.source = source;
        // This is a non-editable node, so we should never access userObject.
    }

    @Override
    public void build ()
    {
        removeAllChildren ();
        for (MNode c : source) add (new NodeReference ((MPart) c));
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
        if (expanded) return icon;
        return NodeReference.icon;
    }

    @Override
    public List<String> getColumns (boolean expanded)
    {
        List<String> result = new ArrayList<String> (1);
        result.add ("<html><i>$reference</i></html>");
        return result;
    }

    @Override
    public NodeBase add (String type, JTree tree, MNode data, Point location)
    {
        if (type.isEmpty ()  ||  type.equals ("Reference"))
        {
            // Add a new reference to our children
            int index = getChildCount () - 1;
            TreePath path = tree.getSelectionPath ();
            if (path != null)
            {
                NodeBase selected = (NodeBase) path.getLastPathComponent ();
                if (isNodeChild (selected)) index = getIndex (selected);  // unfiltered index
            }
            index++;
            AddReference ar = new AddReference ((NodeBase) getParent (), index, data);
            MainFrame.instance.undoManager.add (ar);
            return ar.createdNode;
        }
        return ((NodeBase) getParent ()).add (type, tree, data, location);
    }

    @Override
    public boolean allowEdit ()
    {
        return false;
    }

    @Override
    public void delete (JTree tree, boolean canceled)
    {
        if (! source.isFromTopDocument ()) return;
        MainFrame.instance.undoManager.add (new DeleteReferences (this));
    }
}

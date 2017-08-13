/*
Copyright 2016 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.tree;

import java.awt.Font;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.undo.AddAnnotation;
import gov.sandia.n2a.ui.eq.undo.DeleteAnnotations;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

public class NodeAnnotations extends NodeContainer
{
    protected static ImageIcon icon = ImageUtil.getImage ("properties.gif");

    public NodeAnnotations (MPart source)
    {
        this.source = source;
        setUserObject ("$metadata");
    }

    @Override
    public void build ()
    {
        removeAllChildren ();
        for (MNode c : source) add (new NodeAnnotation ((MPart) c));
    }

    @Override
    public boolean visible (int filterLevel)
    {
        if (filterLevel <= FilteredTreeModel.ALL)    return true;
        if (filterLevel == FilteredTreeModel.PUBLIC) return true;
        // FilteredTreeModel.LOCAL ...
        return source.isFromTopDocument ();
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        if (expanded) return icon;
        return NodeAnnotation.icon;
    }

    @Override
    public int getFontStyle ()
    {
        return Font.ITALIC;
    }
    
    @Override
    public NodeBase add (String type, JTree tree, MNode data)
    {
        if (type.isEmpty ()  ||  type.equals ("Annotation"))
        {
            // Add a new annotation to our children
            int index = getChildCount () - 1;
            TreePath path = tree.getSelectionPath ();
            if (path != null)
            {
                NodeBase selected = (NodeBase) path.getLastPathComponent ();
                if (isNodeChild (selected)) index = getIndex (selected);  // unfiltered index
            }
            index++;
            AddAnnotation aa = new AddAnnotation ((NodeBase) getParent (), index, data);
            PanelModel.instance.undoManager.add (aa);
            return aa.createdNode;
        }
        return ((NodeBase) getParent ()).add (type, tree, data);
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
        PanelModel.instance.undoManager.add (new DeleteAnnotations (this));
    }
}

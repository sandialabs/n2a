/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.tree;

import java.awt.Font;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.ModelEditPanel;
import gov.sandia.n2a.ui.eq.NodeBase;
import gov.sandia.n2a.ui.eq.undo.AddAnnotation;
import gov.sandia.n2a.ui.eq.undo.DeleteAnnotations;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

public class NodeAnnotations extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("properties.gif");

    public NodeAnnotations (MPart source)
    {
        this.source = source;
        setUserObject ("$metadata");
    }

    public void build ()
    {
        removeAllChildren ();
        for (MNode c : source) add (new NodeAnnotation ((MPart) c));
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
        if (expanded) return icon;
        return NodeAnnotation.icon;
    }

    @Override
    public int getFontStyle ()
    {
        return Font.ITALIC;
    }
    
    @Override
    public NodeBase add (String type, JTree tree)
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
            AddAnnotation aa = new AddAnnotation ((NodeBase) getParent (), index);
            ModelEditPanel.instance.doManager.add (aa);
            return aa.createdNode;
        }
        return ((NodeBase) getParent ()).add (type, tree);
    }

    @Override
    public boolean allowEdit ()
    {
        return false;
    }

    @Override
    public void delete (JTree tree)
    {
        if (! source.isFromTopDocument ()) return;
        ModelEditPanel.instance.doManager.add (new DeleteAnnotations (this));
    }
}

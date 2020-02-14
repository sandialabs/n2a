/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.search;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import gov.sandia.n2a.ui.eq.EquationTreeCellRenderer;

@SuppressWarnings("serial")
public class NodeBase extends DefaultMutableTreeNode
{
    public Icon getIcon (boolean expanded)
    {
        return null;  // We end up using the default icon from DefaultTreeCellRenderer.
    }

    public Color getColor (boolean selected)
    {
        if (selected) return EquationTreeCellRenderer.colorSelectedOverride;
        return               EquationTreeCellRenderer.colorOverride;
    }

    public List<String> getKeyPath ()
    {
        List<String> result;
        NodeBase parent = (NodeBase) getParent ();
        if (parent == null)
        {
            result = new ArrayList<String> ();  // Don't include root itself.
        }
        else
        {
            result = parent.getKeyPath ();
            result.add (toString ());
        }
        return result;
    }

    public NodeBase child (String key, boolean selectModel)
    {
        if (children == null) return null;
        for (Object o : children)
        {
            boolean isModel = o instanceof NodeModel;
            if (isModel != selectModel) continue;
            NodeBase n = (NodeBase) o;
            if (n.toString ().equals (key)) return n;
        }
        return null;
    }

    public NodeModel firstModel ()
    {
        if (children == null) return null;
        for (Object o : children) if (o instanceof NodeModel) return (NodeModel) o;
        return null;
    }

    public NodeModel findModel (String key)
    {
        if (children == null) return null;
        for (Object o : children)
        {
            NodeModel n = ((NodeBase) o).findModel (key);
            if (n != null) return n;
        }
        return null;
    }

    /**
        Scan and remove all NodeModel entries that match key. If a category becomes empty, remove it as well.
    **/
    public boolean purge (String key, DefaultTreeModel model)
    {
        if (children == null) return true;
        List<NodeBase> kill = new ArrayList<NodeBase> ();
        for (Object o : children)
        {
            NodeBase n = (NodeBase) o;
            if (n.purge (key, model)) kill.add (n);
        }
        for (NodeBase n : kill) model.removeNodeFromParent (n);
        return children.size () == 0;
    }

    public void replaceDoc (String oldKey, String newKey, DefaultTreeModel model)
    {
        if (children == null) return;
        for (Object o : children)
        {
            ((NodeBase) o).replaceDoc (oldKey, newKey, model);
        }
    }

    public void insert (String category, NodeModel n)
    {
        String[] pieces = category.split ("/", 2);

        NodeBase c = null;
        String ckey = pieces[0];
        int count = getChildCount ();
        int i;
        for (i = 0; i < count; i++)
        {
            NodeBase b = (NodeBase) children.get (i);
            int compare = ckey.compareToIgnoreCase (b.toString ());
            if (b instanceof NodeModel  ||  compare < 0)
            {
                c = new NodeCategory (ckey);
                insert (c, i);
                break;
            }
            else if (compare == 0)  // and b is a NodeCategory
            {
                c = b;
                break;
            }
        }
        if (i == count)
        {
            c = new NodeCategory (ckey);
            add (c);
        }

        if (pieces.length == 1)
        {
            c.add (n);
        }
        else  // pieces.length == 2, so there is at least one sub-category
        {
            c.insert (pieces[1], n);
        }
    }
}

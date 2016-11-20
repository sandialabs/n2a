/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.n2a.ui.eq;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;

import gov.sandia.n2a.eqset.MPart;
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
    public NodeBase add (String type, JTree tree)
    {
        if (type.isEmpty ()) type = "Annotation";  // By context, we assume the user wants to add another annotation.
        return ((NodeBase) getParent ()).add (type, tree);
    }

    @Override
    public boolean allowEdit ()
    {
        if (toString ().isEmpty ()) return true;  // An empty user object indicates a newly created node, which we want to edit as a blank.

        String name  = source.key ();
        String value = source.get ();
        if (value.isEmpty ()) setUserObject (name);
        else                  setUserObject (name + "=" + value);
        return true;
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
        String name = parts[0].trim ();
        String value;
        if (parts.length > 1) value = parts[1].trim ();
        else                  value = "";

        NodeBase existingAnnotation = null;
        String oldKey = source.key ();
        NodeBase parent = (NodeBase) getParent ();
        if (! name.equals (oldKey)) existingAnnotation = parent.child (name);

        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        FontMetrics fm = getFontMetrics (tree);
        if (name.equals (oldKey)  ||  name.isEmpty ()  ||  existingAnnotation != null)  // Name is the same, or name change is forbidden
        {
            source.set (value);
        }
        else  // Name is changed
        {
            MPart p = source.getParent ();
            MPart newPart = (MPart) p.set (value, name);
            p.clear (oldKey);
            if (p.child (oldKey) == null)  // We were not associated with an override, so we can re-use this tree node.
            {
                source = newPart;
            }
            else  // Make a new tree node, and leave this one to present the non-overridden value.
            {
                NodeAnnotation newAnnotation = new NodeAnnotation (newPart);
                model.insertNodeIntoUnfiltered (newAnnotation, parent, parent.getChildCount ());
                newAnnotation.updateColumnWidths (fm);
            }
        }

        updateColumnWidths (fm);
        parent.updateTabStops (fm);
        parent.allNodesChanged (model);
    }

    @Override
    public void delete (JTree tree)
    {
        if (! source.isFromTopDocument ()) return;

        FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
        FontMetrics fm = getFontMetrics (tree);

        NodeBase parent = (NodeBase) getParent ();
        MPart mparent = source.getParent ();
        String key = source.key ();
        mparent.clear (key);  // If this merely clears an override, then our source object retains its identity.
        if (mparent.child (key) == null)  // There is no overridden value, so this node goes away completely.
        {
            model.removeNodeFromParent (this);
            if (parent instanceof NodeAnnotations  &&  parent.getChildCount () == 0)
            {
                parent.delete (tree);  // Delete the $metadata node, because it is now empty.
                // No need to update tabs in grandparent, because $metadata node doesn't participate.
                return;  // parent no longer exists, so don't fall through to update below
            }
        }
        else  // Just exposed an overridden value, so update display.
        {
            if (parent.visible (model.filterLevel))  // We are always visible, but our parent could disappear.
            {
                updateColumnWidths (fm);
            }
            else
            {
                ((NodeBase) parent.getParent ()).hide (parent, model);
            }
        }

        parent.updateTabStops (fm);
        parent.allNodesChanged (model);
    }
}

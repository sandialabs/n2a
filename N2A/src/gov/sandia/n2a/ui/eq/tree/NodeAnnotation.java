/*
Copyright 2016-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.tree;

import java.awt.FontMetrics;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.undo.AddAnnotation;
import gov.sandia.n2a.ui.eq.undo.ChangeAnnotation;
import gov.sandia.n2a.ui.eq.undo.DeleteAnnotation;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

@SuppressWarnings("serial")
public class NodeAnnotation extends NodeContainer
{
    public static ImageIcon     icon = ImageUtil.getImage ("edit.gif");
    protected     List<Integer> columnWidths;
    public        MPart         folded;

    public NodeAnnotation (MPart source)
    {
        this.source = source;
        folded = source;
    }

    @Override
    public void build ()
    {
        removeAllChildren ();
        folded = source;
        buildFolded ();
    }

    public void buildFolded ()
    {
        if (folded.size () == 1  &&  folded.get ().isEmpty ())
        {
            folded = (MPart) folded.iterator ().next ();  // Retrieve the only child.
            buildFolded ();
            return;
        }
        for (MNode m : folded)
        {
            NodeAnnotation a = new NodeAnnotation ((MPart) m);
            add (a);
            a.build ();
        }
    }

    public String key ()
    {
        MPart p = folded;
        String result = p.key ();
        while (p != source)
        {
            p = p.parent ();
            result = p.key () + "." + result;
        }
        return result;
    }

    @Override
    public boolean visible (int filterLevel)
    {
        if (filterLevel <= FilteredTreeModel.ALL)   return true;
        if (filterLevel == FilteredTreeModel.PARAM) return false;
        // FilteredTreeModel.LOCAL ...
        return source.isFromTopDocument ();  // It shouldn't make a difference whether we check source or folded.
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        return icon;
    }

    @Override
    public boolean hasTruncatedText ()
    {
        return toString ().endsWith ("...")  &&  ! folded.get ().endsWith ("...");
    }

    @Override
    public String getText (boolean expanded, boolean editing)
    {
        String result = toString ();
        if (editing  &&  ! result.isEmpty ())  // An empty user object indicates a newly created node, which we want to edit as a blank.
        {
            result       = key ();
            String value = folded.get ();
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
        TreeNode parent = getParent ();
        boolean pure = parent instanceof NodeAnnotations  ||  parent instanceof NodeAnnotation;
        if (columnWidths == null)
        {
            columnWidths = new ArrayList<Integer> (1);
            columnWidths.add (0);
            if (! pure) columnWidths.add (0);  // To function alongside equations
        }
        int width = fm.stringWidth (key () + " ");
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
        String result = key ();
        String value  = folded.get ();
        if (! value.isEmpty ())
        {
            int offset = tabs.get (0);
            TreeNode parent = getParent ();
            boolean pure = parent instanceof NodeAnnotations  ||  parent instanceof NodeAnnotation;
            if (! pure)  // not in a $metadata block, so may share tab stops with equations
            {
                offset = tabs.get (1) - offset;
            }
            int width = availableWidth () - offset;

            boolean addEllipsis = false;
            String[] pieces = value.split ("\n", 2);
            if (pieces.length > 1)
            {
                value = pieces[0];
                addEllipsis = true;
            }
            int valueWidth = fm.stringWidth (value);
            if (valueWidth > width)
            {
                width = Math.max (0, width - fm.getMaxAdvance () * 2);  // allow 2em for ellipsis
                int characters = (int) Math.floor ((double) value.length () * width / valueWidth);  // A crude estimate. Just take a ratio of the number of characters, rather then measuring them exactly.
                value = value.substring (0, characters);
                addEllipsis = true;
            }
            if (addEllipsis) value += " ...";

            result = pad (result, offset, fm) + "= " + value;
        }
        setUserObject (result);
    }

    @Override
    public void copy (MNode result)
    {
        // There may be a deep tree of annotations under this node.
        // However, if this one is visible (implied by receiving this call),
        // then all the children are visible as well.
        result.set (source, source.key ());
    }

    @Override
    public NodeBase add (String type, JTree tree, MNode data, Point location)
    {
        if (type.isEmpty ()) type = "Annotation";  // By context, we assume the user wants to add another annotation.
        if (type.equals ("Annotation"))
        {
            FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
            if (model.getChildCount (this) > 0  &&  ! tree.isCollapsed (new TreePath (getPath ())))  // Open node
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
                AddAnnotation aa = new AddAnnotation (this, index, data);
                PanelModel.instance.undoManager.add (aa);
                return aa.createdNode;
            }
            // else let the request travel up to our parent
        }
        return ((NodeBase) getParent ()).add (type, tree, data, location);
    }

    @Override
    public void applyEdit (JTree tree)
    {
        String input = (String) getUserObject ();
        if (input.isEmpty ())
        {
            boolean canceled = PanelModel.instance.undoManager.getPresentationName ().equals ("AddAnnotation");
            delete (tree, canceled);
            return;
        }

        String[] parts = input.split ("=", 2);
        String name = parts[0].trim ().replaceAll ("[ \\n\\t]", "");
        String value;
        if (parts.length > 1) value = parts[1].trim ();
        else                  value = "";

        NodeBase parent = (NodeBase) getParent ();
        String oldName  = key ();
        String oldValue = folded.get ();
        if (! name.equals (oldName))
        {
            // Check if name change is forbidden
            if (name.isEmpty ())
            {
                name = oldName;
            }
            else
            {
                // If the first name along the new path matches the first name along the old path,
                // then this is merely a rename internal the current node, which is allowed.
                String[] names = name.split ("\\.");
                String[] oldNames = oldName.split ("\\.");
                if (! names[0].equals (oldNames[0]))
                {
                    // We don't want to overwrite a node that has already been defined in the top document.
                    // However, if it is part of folded path, we can define a new non-empty value for it.
                    MPart mparent = source.parent ();
                    MPart partAfter = mparent;
                    for (String n : names)
                    {
                        partAfter = (MPart) partAfter.child (n);
                        if (partAfter == null) break;
                    }
                    if (   partAfter != null  &&  partAfter.isFromTopDocument ()
                        && (source.size () > 0  ||  partAfter.size () != 1  ||  ! partAfter.get ().isEmpty ()))
                    {
                        name = oldName;
                    }
                }
            }
        }
        if (name.equals (oldName)  &&  value.equals (oldValue))
        {
            FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
            FontMetrics fm = getFontMetrics (tree);
            parent.updateTabStops (fm);
            model.nodeChanged (this);  // Our siblings should not change, because we did not really change. Just repaint in non-edit mode.
            return;
        }

        PanelModel.instance.undoManager.add (new ChangeAnnotation (this, name, value));
    }

    @Override
    public void delete (JTree tree, boolean canceled)
    {
        if (! source.isFromTopDocument ()) return;
        PanelModel.instance.undoManager.add (new DeleteAnnotation (this, canceled));
    }
}

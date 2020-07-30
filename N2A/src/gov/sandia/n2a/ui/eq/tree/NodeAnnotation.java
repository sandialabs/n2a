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
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.undo.AddAnnotation;
import gov.sandia.n2a.ui.eq.undo.ChangeAnnotation;
import gov.sandia.n2a.ui.eq.undo.DeleteAnnotation;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

@SuppressWarnings("serial")
public class NodeAnnotation extends NodeContainer
{
    protected static ImageIcon icon = ImageUtil.getImage ("edit.gif");
    public           MPart     folded;
    protected        boolean   truncated;

    public NodeAnnotation (MPart source)
    {
        this.source = source;
        folded = source;
    }

    @Override
    public void setUserObject ()
    {
        String key   = key ();
        String value = folded.get ();
        if (value.isEmpty ()) setUserObject (key);
        else                  setUserObject (key + "=" + value);
    }

    @Override
    public void build ()
    {
        removeAllChildren ();
        folded = source;
        buildFolded ();
        setUserObject ();
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

    public String[] keys ()
    {
        List<String> result = new ArrayList<String> ();
        MPart p = folded;
        result.add (p.key ());
        while (p != source)
        {
            p = p.parent ();
            result.add (0, p.key ());  // Since we are working backwards, insert at start of array.
        }
        return result.toArray (new String[result.size ()]);
    }

    @Override
    public Icon getIcon (boolean expanded)
    {
        return icon;
    }

    @Override
    public boolean allowTruncate ()
    {
        return true;
    }

    @Override
    public void wasTruncated ()
    {
        truncated = true;
    }

    @Override
    public boolean showMultiLine ()
    {
        return truncated;
    }

    @Override
    public List<String> getColumns (boolean selected, boolean expanded)
    {
        truncated = false;

        List<String> result = new ArrayList<String> (2);
        result.add (key ());
        String value = folded.get ();
        if (! value.isEmpty ()) result.add ("= " + value);
        return result;
    }

    @Override
    public int getColumnGroup ()
    {
        return 1;
    }

    @Override
    public List<Integer> getColumnWidths (FontMetrics fm)
    {
        List<Integer> columnWidths = new ArrayList<Integer> (1);
        columnWidths.add (fm.stringWidth (key () + " "));
        return columnWidths;
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
    public NodeBase containerFor (String type)
    {
        if (type.equals ("Annotation")) return this;
        return ((NodeBase) parent).containerFor (type);
    }

    @Override
    public Undoable makeAdd (String type, JTree tree, MNode data, Point location)
    {
        if (type.isEmpty ()) type = "Annotation";  // By context, we assume the user wants to add another annotation.
        if (type.equals ("Annotation"))
        {
            FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
            if (model.getChildCount (this) > 0  &&  ! tree.isCollapsed (new TreePath (getPath ())))  // Open node
            {
                // Add a new annotation to our children
                int index = getChildCount () - 1;
                TreePath path = tree.getLeadSelectionPath ();
                if (path != null)
                {
                    NodeBase selected = (NodeBase) path.getLastPathComponent ();
                    if (isNodeChild (selected)) index = getIndex (selected);  // unfiltered index
                }
                index++;
                return new AddAnnotation (this, index, data);
            }
            // else let the request travel up to our parent
        }
        return ((NodeBase) parent).makeAdd (type, tree, data, location);
    }

    @Override
    public void applyEdit (JTree tree)
    {
        String input = (String) getUserObject ();
        if (input.isEmpty ())
        {
            boolean canceled = MainFrame.instance.undoManager.getPresentationName ().equals ("AddAnnotation");
            delete (canceled);
            return;
        }

        String[] parts = input.split ("=", 2);
        String name = parts[0].trim ().replaceAll ("[ \\n\\t]", "");
        String value;
        if (parts.length > 1) value = parts[1].trim ();
        else                  value = "";

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
            setUserObject ();
            model.nodeChanged (this);  // Our siblings should not change, because we did not really change. Just repaint in non-edit mode.
            return;
        }

        MainFrame.instance.undoManager.apply (new ChangeAnnotation (this, name, value));
    }

    @Override
    public Undoable makeDelete (boolean canceled)
    {
        if (source.isFromTopDocument ()) return new DeleteAnnotation (this, canceled);
        return null;
    }
}

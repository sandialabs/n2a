/*
Copyright 2016-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.tree;

import java.awt.FontMetrics;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.language.Identifier;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.FilteredTreeModel;
import gov.sandia.n2a.ui.eq.undo.ChangeReference;
import gov.sandia.n2a.ui.eq.undo.DeleteReference;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;

@SuppressWarnings("serial")
public class NodeReference extends NodeBase
{
    protected static ImageIcon icon = ImageUtil.getImage ("book.gif");
    protected        boolean   truncated;

    public NodeReference (MPart source)
    {
        this.source = source;
        setUserObject ();
    }

    @Override
    public void setUserObject ()
    {
        String key   = source.key ();
        String value = source.get ();
        if (value.isEmpty ()) setUserObject (key);
        else                  setUserObject (key + "=" + value);
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
        result.add (source.key ());
        String value = source.get ();
        if (! value.isEmpty ()) result.add ("= " + value);
        return result;
    }

    @Override
    public int getColumnGroup ()
    {
        return 2;
    }

    @Override
    public List<Integer> getColumnWidths (FontMetrics fm)
    {
        List<Integer> result = new ArrayList<Integer> (1);
        result.add (fm.stringWidth (source.key () + " "));
        return result;
    }

    @Override
    public Undoable makeAdd (String type, JTree tree, MNode data, Point2D.Double location)
    {
        if (type.isEmpty ()) type = "Reference";  // By context, we assume the user wants to add another reference.
        return ((NodeBase) parent).makeAdd (type, tree, data, location);
    }

    @Override
    public void applyEdit (JTree tree)
    {
        String input = (String) getUserObject ();
        if (input.isEmpty ())
        {
            boolean canceled = MainFrame.instance.undoManager.getPresentationName ().equals ("AddReference");
            delete (canceled);
            return;
        }

        String[] parts = input.split ("=", 2);
        String name = Identifier.canonical (parts[0].trim ().replaceAll ("[\\n\\t]", ""));
        String value;
        if (parts.length > 1) value = parts[1].trim ();
        else                  value = "";

        NodeBase parent = (NodeBase) getParent ();
        String oldName  = source.key ();
        String oldValue = source.get ();
        if (! name.equals (oldName))
        {
            // Check if name change is forbidden
            if (name.isEmpty ())
            {
                name = oldName;
            }
            else
            {
                MPart mparent = source.parent ();
                MPart partAfter = (MPart) mparent.child (name);
                if (partAfter != null  &&  partAfter.isFromTopDocument ()) name = oldName;
            }
        }
        if (name.equals (oldName)  &&  value.equals (oldValue))
        {
            setUserObject ();
            FilteredTreeModel model = (FilteredTreeModel) tree.getModel ();
            model.nodeChanged (this);  // Our siblings should not change, because we did not really change. Just repaint in non-edit mode.
            return;
        }

        MainFrame.instance.undoManager.apply (new ChangeReference (parent, oldName, oldValue, name, value));
    }

    @Override
    public Undoable makeDelete (boolean canceled)
    {
        if (source.isFromTopDocument ()) return new DeleteReference (this, canceled);
        return null;
    }
}

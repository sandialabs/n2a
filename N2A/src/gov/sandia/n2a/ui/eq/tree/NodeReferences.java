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
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.undo.AddReference;
import gov.sandia.n2a.ui.eq.undo.DeleteReferences;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

@SuppressWarnings("serial")
public class NodeReferences extends NodeContainer
{
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
    public Icon getIcon (boolean expanded)
    {
        return NodeReference.icon;
    }

    @Override
    public List<String> getColumns (boolean selected, boolean expanded)
    {
        List<String> result = new ArrayList<String> (1);
        result.add ("<html><i>$ref</i></html>");
        return result;
    }

    @Override
    public NodeBase containerFor (String type)
    {
        if (type.equals ("Reference")) return this;
        return ((NodeBase) parent).containerFor (type);
    }

    @Override
    public Undoable makeAdd (String type, JTree tree, MNode data, Point location)
    {
        if (type.isEmpty ()  ||  type.equals ("Reference"))
        {
            // Add a new reference to our children
            int index = getChildCount () - 1;
            TreePath path = tree.getLeadSelectionPath ();
            if (path != null)
            {
                NodeBase selected = (NodeBase) path.getLastPathComponent ();
                if (isNodeChild (selected)) index = getIndex (selected);  // unfiltered index
            }
            index++;
            return new AddReference ((NodeBase) getParent (), index, data);
        }
        return ((NodeBase) parent).makeAdd (type, tree, data, location);
    }

    @Override
    public boolean allowEdit ()
    {
        return false;
    }

    @Override
    public Undoable makeDelete (boolean canceled)
    {
        if (source.isFromTopDocument ()) return new DeleteReferences (this);
        return null;
    }
}

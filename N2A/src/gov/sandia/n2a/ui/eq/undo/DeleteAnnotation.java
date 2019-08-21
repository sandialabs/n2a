/*
Copyright 2016-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.PanelEquations.StoredView;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;

public class DeleteAnnotation extends Undoable
{
    protected StoredView   view = PanelModel.instance.panelEquations.new StoredView ();
    protected List<String> path;
    protected int          index; // where to insert among siblings
    protected boolean      canceled;
    protected String       name;
    protected String       prefix;
    protected MNode        savedSubtree;
    protected boolean      neutralized;
    public    boolean      setSelection = true;

    public DeleteAnnotation (NodeAnnotation node, boolean canceled)
    {
        NodeBase container = (NodeBase) node.getParent ();
        index         = container.getIndex (node);
        this.canceled = canceled;
        name          = node.key ();
        prefix        = name.split ("\\.")[0];

        if (container instanceof NodeAnnotations)
        {
            if (container.getChildCount () == 1)  // $metadata node that will go away
            {
                NodeBase part = (NodeBase) container.getParent ();
                index = part.getIndex (container);
            }
        }
        else if (container instanceof NodeAnnotation)
        {
            if (container.getChildCount () == 2  &&  ((NodeAnnotation) container).folded.get ().isEmpty ())  // Container will get folded into grandparent.
            {
                String key = ((NodeAnnotation) container).key ();
                name   = key + "." + name;
                prefix = key + "." + prefix;
                container = (NodeBase) container.getParent ();
            }
        }
        path = container.getKeyPath ();

        savedSubtree = new MVolatile ();
        savedSubtree.merge (node.folded.getSource ());
    }

    public void undo ()
    {
        super.undo ();
        view.restore ();
        AddAnnotation.create (path, index, name, savedSubtree, false, setSelection);
    }

    public void redo ()
    {
        super.redo ();
        view.restore ();
        AddAnnotation.destroy (path, canceled, name, prefix, setSelection);
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddAnnotation)
        {
            AddAnnotation aa = (AddAnnotation) edit;
            if (path.equals (aa.path)  &&  name.equals (aa.name)  &&  aa.nameIsGenerated)
            {
                neutralized = true;
                return true;
            }
        }
        return false;
    }

    public boolean anihilate ()
    {
        return neutralized;
    }
}

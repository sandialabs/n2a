/*
Copyright 2016-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;

import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class DeleteAnnotation extends UndoableView
{
    protected List<String> path;
    protected int          index;         // where to insert among siblings
    protected boolean      canceled;
    protected String       name;
    protected String       prefix;
    protected MNode        savedSubtree;
    protected boolean      neutralized;
    protected boolean      multi;         // See AddAnnotation for explanation of multi and kin.
    protected boolean      multiLast;
    public    boolean      selectVariable;
    protected boolean      touchesPin;
    protected boolean      touchesCategory;

    public DeleteAnnotation (NodeAnnotation node, boolean canceled)
    {
        NodeBase container = (NodeBase) node.getParent ();
        index         = container.getIndex (node);
        this.canceled = canceled;
        name          = node.key ();
        prefix        = name.split ("\\.")[0];

        if (container instanceof NodeAnnotations)
        {
            if (container.getChildCount () == 1)  // $meta node that will go away
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

        touchesPin =  path.contains ("pin")  ||  name.contains ("pin")  ||  savedSubtree.containsKey ("pin");

        NodeBase p = container;
        while (! (p instanceof NodePart)) p = (NodeBase) p.getParent ();
        touchesCategory =  p.getTrueParent () == null  &&  (name.contains ("category")  ||  savedSubtree.containsKey ("category"));
    }

    /**
        Constructs an edit action which removes the referenced metadata item from the given part.
        If the metadata item does not exist, then the return value is null.
        This method is a bit imprecise. If the given path appears in a folded child, then
        it is possible that the delete will go further up the tree than the specified path.
        This could damage metadata structures where the parents of the specified node are
        acting as flags merely by their existence.
    **/
    public static DeleteAnnotation withName (NodePart part, String... names)
    {
        NodeAnnotations metadata = (NodeAnnotations) part.child ("$meta");
        if (metadata == null) return null;
        NodeBase target = AddAnnotation.findExact (metadata, true, names);
        if (target == null) return null;
        return new DeleteAnnotation ((NodeAnnotation) target, false);
    }

    public void setMulti (boolean value)
    {
        multi = value;
    }

    public void setMultiLast (boolean value)
    {
        multiLast = value;
    }

    public void undo ()
    {
        super.undo ();
        AddAnnotation.create (path, index, name, savedSubtree, false, multi, selectVariable, touchesPin, touchesCategory);
    }

    public void redo ()
    {
        super.redo ();
        AddAnnotation.destroy (path, canceled, name, prefix, multi, multiLast, selectVariable, touchesPin, touchesCategory);
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

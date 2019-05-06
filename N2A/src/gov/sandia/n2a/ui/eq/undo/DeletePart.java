/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;

public class DeletePart extends Undoable
{
    protected StoredView   view = PanelModel.instance.panelEquations.new StoredView ();
    protected List<String> path;  // to containing part
    protected int          index; // where to insert among siblings
    protected boolean      canceled;
    protected String       name;
    protected MNode        savedSubtree;
    protected boolean      neutralized;

    public DeletePart (NodePart node, boolean canceled)
    {
        NodeBase container = (NodeBase) node.getParent ();
        path          = container.getKeyPath ();
        index         = container.getIndex (node);
        this.canceled = canceled;
        name          = node.source.key ();

        savedSubtree = new MVolatile ();
        savedSubtree.merge (node.source.getSource ());  // Only take the top-doc data, not the collated tree.
    }

    public void undo ()
    {
        super.undo ();
        view.restore ();
        AddPart.create (path, index, name, savedSubtree, false);
    }

    public void redo ()
    {
        super.redo ();
        view.restore ();
        AddPart.destroy (path, canceled, name);
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddPart)
        {
            AddPart ap = (AddPart) edit;
            if (path.equals (ap.path)  &&  name.equals (ap.name)  &&  ap.nameIsGenerated)
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

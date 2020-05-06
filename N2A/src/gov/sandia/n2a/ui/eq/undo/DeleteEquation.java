/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeEquation;

public class DeleteEquation extends UndoableView
{
    protected List<String> path;  // to variable node
    protected int          equationCount;  // after deleting this equation
    protected int          index; // where to insert among siblings
    protected boolean      canceled;
    protected String       name;  // includes the leading @
    protected String       combiner;
    protected String       value;
    protected boolean      neutralized;
    protected boolean      multi;
    protected boolean      multiLast;

    public DeleteEquation (NodeEquation node, boolean canceled)
    {
        NodeBase variable = (NodeBase) node.getParent ();
        path          = variable.getKeyPath ();
        index         = variable.getIndex (node);
        this.canceled = canceled;
        name          = node.source.key ();
        combiner      = new Variable.ParsedValue (variable.source.get ()).combiner;
        value         = node.source.get ();

        // Note that equationCount is zero at start of constructor
        for (MNode n : variable.source)
        {
            String key = n.key ();
            if (key.startsWith ("@")) equationCount++;
        }
        if (! node.source.isOverridden ()) equationCount--;  // For this equation which is about to be deleted
        if (equationCount < 2) equationCount = 0;  // Because we always merge a single equation back into a one-line variable=equation.
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
        AddEquation.create (path, equationCount, index, name, combiner, value, multi);
    }

    public void redo ()
    {
        super.redo ();
        AddEquation.destroy (path, equationCount, canceled, name, combiner, ! multi  ||  multiLast);
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddEquation)
        {
            AddEquation ae = (AddEquation) edit;
            if (path.equals (ae.path)  &&  name.equals (ae.name)  &&  ae.value == null)  // null value means the edit has not merged a change node yet
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

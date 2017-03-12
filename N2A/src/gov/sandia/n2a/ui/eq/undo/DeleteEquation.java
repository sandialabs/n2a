/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.util.List;
import javax.swing.undo.UndoableEdit;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeEquation;

public class DeleteEquation extends Undoable
{
    protected List<String> path;  // to variable node
    protected int          equationCount;  // after deleting this equation
    protected int          index; // where to insert among siblings
    protected boolean      canceled;
    protected String       name;  // includes the leading @
    protected String       combiner;
    protected String       value;
    protected boolean      neutralized;

    public DeleteEquation (NodeEquation node, boolean canceled)
    {
        NodeBase variable = (NodeBase) node.getParent ();
        index         = variable.getIndex (node);
        this.canceled = canceled;
        path          = variable.getKeyPath ();
        name          = node.source.key ();
        combiner      = new Variable.ParsedValue (variable.source.get ()).combiner;
        value         = node.source.get ();

        // Note that equationCount is zero at start of constructor
        for (MNode n : variable.source)
        {
            String key = n.key ();
            if (key.startsWith ("@")) equationCount++;
        }
        equationCount--;  // For this equation which is about to be deleted
        if (equationCount < 2) equationCount = 0;  // Because we always merge a single equation back into a one-line variable=equation.
    }

    public void undo ()
    {
        super.undo ();
        AddEquation.create (path, equationCount, index, name, combiner, value);
    }

    public void redo ()
    {
        super.redo ();
        AddEquation.destroy (path, equationCount, canceled, name, combiner);
    }

    public boolean replaceEdit (UndoableEdit edit)
    {
        if (edit instanceof AddEquation)
        {
            AddEquation aa = (AddEquation) edit;
            if (name.equals (aa.name)  &&  aa.value == null)  // null value means the edit has not merged a change node yet
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

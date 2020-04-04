/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.undo;

import java.awt.Point;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;

/**
    Specifically for pasting several new parts into a model (as opposed to undoing the delete of several existing parts).
**/
public class AddPartMulti extends UndoableView
{
    protected List<String> path;  // to containing part
    protected MNode        data;  // Each child of this node is a part to add.

    public AddPartMulti (NodeBase parent, MNode data, Point location)
    {
        path      = parent.getKeyPath ();
        this.data = data;

        // Pre-process the data. Handle name collisions and set location of each part.

        Map<String,String> nameChanges = new HashMap<String,String> ();
        for (MNode p : data)
        {
            MNode bounds = p.childOrCreate ("$metadata", "gui", "bounds");
            bounds.set (location.x + bounds.getInt ("x"), "x");
            bounds.set (location.y + bounds.getInt ("y"), "y");

            String key = p.key ();
            String name = key;
            int suffix = 2;
            while (parent.child (name) != null) name = key + suffix++;
            while (data  .child (name) != null) name = key + suffix++;
            if (! name.equals (key))
            {
                nameChanges.put (key, name);
                data.move (key, name);
            }
        }

        for (String oldName : nameChanges.keySet ())
        {
            String newName = nameChanges.get (oldName);

            // For all the new parts, scan for apparent connection bindings to the changed name.
            for (MNode p : data)
            {
                for (MNode v : p)
                {
                    // Detect if v is a connection binding.
                    // Compare these criteria with NodeVariable.findConnections() and EquationSet.resolveConnectionBindings().
                    String value = v.get ();
                    if (! value.equals (oldName)) continue;
                    String vname = v.key ();
                    if (vname.contains ("$")  ||  vname.contains ("\\.")  ||  vname.endsWith ("'")) continue;  // LHS must be a simple identifier.
                    boolean hasEquations = false;
                    for (MNode e : v)
                    {
                        if (e.key ().startsWith ("@"))
                        {
                            hasEquations = true;
                            break;
                        }
                    }
                    if (hasEquations) continue;  // Must be single line.

                    // Apply name change
                    v.set (newName);
                }
            }
        }
    }

    public void undo ()
    {
        super.undo ();
        for (MNode c : data) AddPart.destroy (path, false, c.key ());
    }

    public void redo ()
    {
        super.redo ();

        PanelModel.instance.panelEquations.panelEquationGraph.clearSelection ();
        boolean multiLead = true;
        for (MNode c : data)
        {
            AddPart.create (path, -1, c.key (), c, false, true, multiLead);
            multiLead = false;
        }
    }
}

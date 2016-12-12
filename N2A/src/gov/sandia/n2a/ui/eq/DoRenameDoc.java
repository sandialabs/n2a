/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.db.AppData;

public class DoRenameDoc extends Do
{
    String before;
    String after;

    public DoRenameDoc (String before, String after)
    {
        this.before = before;
        this.after  = after;
    }

    @Override
    public void undo ()
    {
        super.undo ();

        AppData.models.move (after, before);
        ModelEditPanel mep = ModelEditPanel.instance;
        NodePart root = mep.panelEquations.root;
        root.setUserObject ();
        mep.panelEquations.model.nodeChanged (root);
        mep.panelSearch.list.repaint ();
    }

    @Override
    public void redo ()
    {
        super.redo ();

        AppData.models.move (before, after);
        ModelEditPanel mep = ModelEditPanel.instance;
        NodePart root = mep.panelEquations.root;
        root.setUserObject ();
        mep.panelEquations.model.nodeChanged (root);
        mep.panelSearch.list.repaint ();
    }

    public boolean anihilate ()
    {
        return before.equals (after);
    }
}

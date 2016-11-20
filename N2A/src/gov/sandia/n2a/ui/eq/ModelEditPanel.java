/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JPanel;
import javax.swing.JSplitPane;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;

public class ModelEditPanel extends JPanel
{
    public static ModelEditPanel instance;  ///< Technically, this class is a singleton, because only one would normally be created.

    public JSplitPane        split;
    public SearchPanel       panelSearch;
    public EquationTreePanel panelEquations;

    public ModelEditPanel ()
    {
        instance = this;

        panelEquations = new EquationTreePanel (this);
        panelSearch    = new SearchPanel       (this);

        split = new JSplitPane (JSplitPane.HORIZONTAL_SPLIT, panelSearch, panelEquations);
        split.setOneTouchExpandable(true);

        setLayout (new BorderLayout ());
        add (split, BorderLayout.CENTER);
        setFocusCycleRoot (true);

        // Determine the split position.
        int divider = AppData.state.getOrDefault (0, "ModelEditPanel", "divider");
        if (divider > 0) split.setDividerLocation (divider);
        else             split.setDividerLocation (0.25);
        split.setResizeWeight (0.25);  // always favor equation tree over search

        // TODO: Something seems to add 2px to the divider location each time the app is run. Probably during layout.
        split.addPropertyChangeListener (split.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                Object o = e.getNewValue ();
                if (o != null) AppData.state.set (o.toString (), "ModelEditPanel", "divider");
            }
        });
    }

    public void recordSelected ()
    {
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                panelEquations.loadRootFromDB (panelSearch.model.get (panelSearch.list.getSelectedIndex ()));
                panelEquations.tree.requestFocusInWindow ();
            }
        });
    }

    public void recordDeleted (MNode record)
    {
        if (panelEquations.record == record)
        {
            panelEquations.record       = null;
            panelEquations.root         = null;
            panelEquations.model.setRoot (null);
        }
    }

    public void recordRenamed ()
    {
        panelSearch.list.repaint ();
    }

    public void createRecord ()
    {
        panelEquations.createNewModel ();
        panelEquations.tree.requestFocusInWindow ();
    }

    public void searchHideSelection ()
    {
        panelSearch.hideSelection ();
    }

    public void searchInsertDoc (MNode doc)
    {
        panelSearch.insertDoc (doc);
    }

    public void searchRemoveDoc (MNode doc)
    {
        panelSearch.removeDoc (doc);
    }
}

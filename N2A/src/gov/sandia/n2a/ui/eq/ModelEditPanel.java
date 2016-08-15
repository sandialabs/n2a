/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JPanel;
import javax.swing.JSplitPane;

import gov.sandia.umf.platform.AppState;
import gov.sandia.umf.platform.ui.UIController;

public class ModelEditPanel extends JPanel
{
    public JSplitPane        split;
    public SearchPanel       panelSearch;
    public EquationTreePanel panelEquations;

    public ModelEditPanel(UIController uic)
    {
        panelEquations = new EquationTreePanel (uic);
        panelSearch    = new SearchPanel (panelEquations);

        split = new JSplitPane (JSplitPane.HORIZONTAL_SPLIT, panelSearch, panelEquations);
        split.setOneTouchExpandable(true);

        setLayout (new BorderLayout ());
        add (split, BorderLayout.CENTER);
        setFocusCycleRoot (true);

        // Determine the split position.
        int divider = AppState.getInstance ().getOrDefault (0, "ModelEditPanel", "divider");
        if (divider > 0) split.setDividerLocation (divider);
        split.setResizeWeight (0.25);  // always favor equation tree over search

        split.addPropertyChangeListener (split.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                Object o = e.getNewValue ();
                if (o != null) AppState.getInstance ().set (o.toString (), "ModelEditPanel", "divider");
            }
        });
    }
}

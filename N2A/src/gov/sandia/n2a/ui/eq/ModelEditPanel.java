/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.ui.eq.undo.UndoManager;

public class ModelEditPanel extends JPanel
{
    public static ModelEditPanel instance;  ///< Technically, this class is a singleton, because only one would normally be created.

    public JSplitPane        split;
    public SearchPanel       panelSearch;
    public EquationTreePanel panelEquations;
    public UndoManager       undoManager = new UndoManager ();

    public ModelEditPanel ()
    {
        instance = this;

        panelEquations = new EquationTreePanel ();
        panelSearch    = new SearchPanel ();

        split = new JSplitPane (JSplitPane.HORIZONTAL_SPLIT, panelSearch, panelEquations);
        split.setOneTouchExpandable(true);

        setLayout (new BorderLayout ());
        add (split, BorderLayout.CENTER);
        setFocusCycleRoot (true);

        // Determine the split position.
        split.setDividerLocation (AppData.state.getOrDefaultInt ("ModelEditPanel", "divider", "150"));
        split.addPropertyChangeListener (JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener ()
        {
            boolean gotFirstChange;
            public void propertyChange (PropertyChangeEvent e)
            {
                // Ignore first change, as it is induced by the initial layout, interacting with resize weight.
                // This often results in a different value for divider location than the set one.
                if (gotFirstChange)
                {
                    Object o = e.getNewValue ();
                    if (o instanceof Integer) AppData.state.set ("ModelEditPanel", "divider", o);
                }
                gotFirstChange = true;
            }
        });

        InputMap inputMap = getInputMap (WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put (KeyStroke.getKeyStroke ("control Z"),       "Undo");
        inputMap.put (KeyStroke.getKeyStroke ("control Y"),       "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift control Z"), "Redo");

        ActionMap actionMap = getActionMap ();
        actionMap.put ("Undo", new AbstractAction ("Undo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {undoManager.undo ();}
                catch (CannotUndoException e) {}
                catch (CannotRedoException e) {}
            }
        });
        actionMap.put ("Redo", new AbstractAction ("Redo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {undoManager.redo();}
                catch (CannotUndoException e) {}
                catch (CannotRedoException e) {}
            }
        });
    }
}

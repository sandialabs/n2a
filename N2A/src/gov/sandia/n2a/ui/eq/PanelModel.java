/*
Copyright 2013,2016,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.BorderLayout;
import java.awt.FontMetrics;
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
import gov.sandia.n2a.ui.UndoManager;

public class PanelModel extends JPanel
{
    public static PanelModel instance;  ///< Technically, this class is a singleton, because only one would normally be created.

    public JSplitPane        split;
    public JSplitPane        splitMRU;
    public PanelMRU          panelMRU;
    public PanelSearch       panelSearch;
    public PanelEquationTree panelEquations;
    public UndoManager       undoManager = new UndoManager ();

    public PanelModel ()
    {
        instance = this;

        panelMRU       = new PanelMRU ();
        panelSearch    = new PanelSearch ();
        panelEquations = new PanelEquationTree ();

        splitMRU = new JSplitPane (JSplitPane.VERTICAL_SPLIT, panelMRU, panelSearch);
        split = new JSplitPane (JSplitPane.HORIZONTAL_SPLIT, splitMRU, panelEquations);
        split.setOneTouchExpandable(true);

        setLayout (new BorderLayout ());
        add (split, BorderLayout.CENTER);
        setFocusCycleRoot (true);

        // Determine the split positions.

        FontMetrics fm = panelSearch.list.getFontMetrics (panelSearch.list.getFont ());
        splitMRU.setDividerLocation (AppData.state.getOrDefaultInt ("PanelModel", "dividerMRU", String.valueOf (fm.getHeight () * 4)));
        splitMRU.addPropertyChangeListener (JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                Object o = e.getNewValue ();
                if (o instanceof Integer) AppData.state.set ("PanelModel", "dividerMRU", o);
            }
        });

        split.setDividerLocation (AppData.state.getOrDefaultInt ("PanelModel", "divider", String.valueOf (fm.stringWidth ("Example Hodgkin-Huxley Cable"))));
        split.addPropertyChangeListener (JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                Object o = e.getNewValue ();
                if (o instanceof Integer) AppData.state.set ("PanelModel", "divider", o);
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

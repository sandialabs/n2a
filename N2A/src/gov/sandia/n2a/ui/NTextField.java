/*
Copyright 2020-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.text.Document;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

/**
    Enhanced text field that supplies common behavior.
    The added features are undo/redo, copy/paste, and a stub for the cancel action (escape key).
**/
@SuppressWarnings("serial")
public class NTextField extends JTextField
{
    protected javax.swing.undo.UndoManager undoManager = new javax.swing.undo.UndoManager ();

    public NTextField ()
    {
        this (null, null, 0);
    }

    public NTextField (String text)
    {
        this (null, text, 0);
    }

    public NTextField (int columns)
    {
        this (null, null, columns);
    }

    public NTextField (String text, int columns)
    {
        this (null, text, columns);
    }

    public NTextField (Document doc, String text, int columns)
    {
        super (doc, text, columns);

        setTransferHandler (new SafeTextTransferHandler ());
        getDocument ().addUndoableEditListener (undoManager);

        InputMap inputMap = getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("control Z"),           "Undo");  // For Windows and Linux
        inputMap.put (KeyStroke.getKeyStroke ("meta Z"),              "Undo");  // For Mac
        inputMap.put (KeyStroke.getKeyStroke ("control Y"),           "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("meta Y"),              "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift control Z"),     "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift meta Z"),        "Redo");
        inputMap.put (KeyStroke.getKeyStroke (KeyEvent.VK_ESCAPE, 0), "Cancel");

        ActionMap actionMap = getActionMap ();
        actionMap.put ("Undo", new AbstractAction ("Undo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try
                {
                    if (undoManager.canUndo ()) undoManager.undo ();
                    else                        MainFrame.instance.undoManager.undo ();
                }
                catch (CannotUndoException e) {}
            }
        });
        actionMap.put ("Redo", new AbstractAction ("Redo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try
                {
                    if (undoManager.canRedo ()) undoManager.redo();
                    else                        MainFrame.instance.undoManager.redo ();
                }
                catch (CannotRedoException e) {}
            }
        });
        actionMap.put ("Cancel", new AbstractAction ("Cancel")
        {
            public void actionPerformed (ActionEvent evt)
            {
            }
        });
    }

    public void setText (String text)
    {
        super.setText (text);
        if (undoManager != null) undoManager.discardAllEdits ();  // When text is set programmatically, user effectively starts a new edit session.
    }
}

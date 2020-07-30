/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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

@SuppressWarnings("serial")
public class NTextField extends JTextField
{
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

        javax.swing.undo.UndoManager um = new javax.swing.undo.UndoManager ();
        getDocument ().addUndoableEditListener (um);

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
                try {um.undo ();}
                catch (CannotUndoException e) {}
            }
        });
        actionMap.put ("Redo", new AbstractAction ("Redo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {um.redo();}
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
}

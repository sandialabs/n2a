/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.EventObject;
import javax.swing.AbstractAction;
import javax.swing.DefaultCellEditor;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultTreeCellEditor;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellEditor;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

/**
    Extends the standard tree cell editor to cooperate with NodeBase icon and text styles.
    Adds a few other nice behaviors:
    * Makes cell editing act more like a text document.
      - No visible border
      - Extends full width of tree panel
    * Selects the value portion of an equation, facilitating the user to make simple changes.
**/
public class EquationTreeCellEditor extends DefaultTreeCellEditor
{
    public NodeBase    editingNode;
    public UndoManager undoManager;

    public EquationTreeCellEditor (JTree tree, DefaultTreeCellRenderer renderer)
    {
        super (tree, renderer);
    }

    @Override
    public Component getTreeCellEditorComponent (JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row)
    {
        // Lie about the expansion state, to force NodePart to return the true name of the part, without parenthetical info about type.
        return super.getTreeCellEditorComponent (tree, value, isSelected, true, leaf, row);
    }

    @Override
    public Font getFont ()
    {
        return ((EquationTreeCellRenderer) renderer).getFontFor (editingNode);
    }

    @Override
    public boolean isCellEditable (EventObject e)
    {
        if (! super.isCellEditable (e)) return false;
        if (lastPath == null) return false;
        Object o = lastPath.getLastPathComponent ();
        if (! (o instanceof NodeBase)) return false;
        editingNode = (NodeBase) o;
        return editingNode.allowEdit ();
    }

    /**
        Set editingIcon and offset.
    **/
    @Override
    protected void determineOffset (JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row)
    {
        editingIcon = ((EquationTreeCellRenderer) renderer).getIconFor ((NodeBase) value, expanded, leaf);
        offset = renderer.getIconTextGap () + editingIcon.getIconWidth ();
    }

    @Override
    protected TreeCellEditor createTreeCellEditor ()
    {
        DefaultTextField textField = new DefaultTextField (new EmptyBorder (0, 0, 0, 0))
        {
            @Override
            public Dimension getPreferredSize ()
            {
                Dimension result = super.getPreferredSize ();
                result.width = Math.max (result.width, tree.getWidth () - (editingNode.getLevel () + 1) * offset - 5);  // The extra 5 pixels come from DefaultTreeCellEditor.EditorContainer.getPreferredSize()
                return result;
            }
        };

        undoManager = new UndoManager ();
        textField.getDocument ().addUndoableEditListener (undoManager);
        textField.getActionMap ().put ("Undo", new AbstractAction ("Undo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {undoManager.undo ();}
                catch (CannotUndoException e) {}
            }
        });
        textField.getActionMap ().put ("Redo", new AbstractAction ("Redo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {undoManager.redo();}
                catch (CannotRedoException e) {}
            }
        });
        textField.getInputMap ().put (KeyStroke.getKeyStroke ("control Z"),       "Undo");
        textField.getInputMap ().put (KeyStroke.getKeyStroke ("control Y"),       "Redo");
        textField.getInputMap ().put (KeyStroke.getKeyStroke ("shift control Z"), "Redo");

        textField.addFocusListener (new FocusListener ()
        {
            @Override
            public void focusGained (FocusEvent e)
            {
                // Analyze text of control and set an appropriate selection
                String text = textField.getText ();
                int equals = text.indexOf ('=');
                int at     = text.indexOf ('@');
                if (equals >= 0  &&  equals < text.length () - 1)  // also check for combiner character
                {
                    if (":+*/<>".indexOf (text.charAt (equals + 1)) >= 0) equals++;
                }
                if (at < 0)  // no condition
                {
                    if (equals >= 0)  // a single-line equation
                    {
                        textField.setCaretPosition (text.length ());
                        textField.moveCaretPosition (equals + 1);
                    }
                    else  // A part name
                    {
                        textField.setCaretPosition (text.length ());
                    }
                }
                else if (equals > at)  // a multi-conditional line that has "=" in the condition
                {
                    textField.setCaretPosition (0);
                    textField.moveCaretPosition (at);
                }
                else  // a single-line equation with a condition
                {
                    textField.setCaretPosition (equals + 1);
                    textField.moveCaretPosition (at);
                }

                undoManager.discardAllEdits ();
            }

            @Override
            public void focusLost (FocusEvent e)
            {
            }
        });

        DefaultCellEditor result = new DefaultCellEditor (textField);
        result.setClickCountToStart (1);
        return result;
    }
}

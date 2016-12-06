/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.util.EventObject;

import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.Icon;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.FontUIResource;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreePath;
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
public class EquationTreeCellEditor extends AbstractCellEditor implements TreeCellEditor, TreeSelectionListener
{
    protected JTree                    tree;
    protected EquationTreeCellRenderer renderer;
    protected UndoManager              undoManager;
    protected JTextField               oneLineEditor;

    protected TreePath                 lastPath;
    protected NodeBase                 editingNode;
    protected Container                editingContainer;
    protected Component                editingComponent;
    protected Icon                     editingIcon;
    protected int                      offset;

    public EquationTreeCellEditor (JTree tree, EquationTreeCellRenderer renderer)
    {
        setTree (tree);
        this.renderer = renderer;
        undoManager = new UndoManager ();
        editingContainer = new EditorContainer ();

        oneLineEditor = new JTextField ()
        {
            public Font getFont ()
            {
                Font font = super.getFont ();

                // Prefer the parent containers font if our font is a FontUIResource
                if (font instanceof FontUIResource)
                {
                    Container parent = getParent ();
                    if (parent != null)
                    {
                        Font parentFont = parent.getFont ();
                        if (parentFont != null) font = parentFont;
                    }
                }
                return font;
            }

            public Dimension getPreferredSize ()
            {
                Dimension result = super.getPreferredSize ();
                result.width = Math.max (result.width, tree.getWidth () - (editingNode.getLevel () + 1) * offset);
                return result;
            }
        };
        oneLineEditor.setBorder (new EmptyBorder (0, 0, 0, 0));

        oneLineEditor.getDocument ().addUndoableEditListener (undoManager);
        oneLineEditor.getActionMap ().put ("Undo", new AbstractAction ("Undo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {undoManager.undo ();}
                catch (CannotUndoException e) {}
            }
        });
        oneLineEditor.getActionMap ().put ("Redo", new AbstractAction ("Redo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {undoManager.redo();}
                catch (CannotRedoException e) {}
            }
        });
        oneLineEditor.getInputMap ().put (KeyStroke.getKeyStroke ("control Z"),       "Undo");
        oneLineEditor.getInputMap ().put (KeyStroke.getKeyStroke ("control Y"),       "Redo");
        oneLineEditor.getInputMap ().put (KeyStroke.getKeyStroke ("shift control Z"), "Redo");

        oneLineEditor.addFocusListener (new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
                // Analyze text of control and set an appropriate selection
                String text = oneLineEditor.getText ();
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
                        oneLineEditor.setCaretPosition (text.length ());
                        oneLineEditor.moveCaretPosition (equals + 1);
                    }
                    else  // A part name
                    {
                        oneLineEditor.setCaretPosition (text.length ());
                    }
                }
                else if (equals > at)  // a multi-conditional line that has "=" in the condition
                {
                    oneLineEditor.setCaretPosition (0);
                    oneLineEditor.moveCaretPosition (at);
                }
                else  // a single-line equation with a condition
                {
                    oneLineEditor.setCaretPosition (equals + 1);
                    oneLineEditor.moveCaretPosition (at);
                }

                undoManager.discardAllEdits ();
            }

            public void focusLost (FocusEvent e)
            {
            }
        });

        oneLineEditor.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                stopCellEditing ();
            }
        });
    }

    @Override
    public Component getTreeCellEditorComponent (JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row)
    {
        setTree (tree);
        editingIcon = renderer.getIconFor ((NodeBase) value, expanded, leaf);
        offset = renderer.getIconTextGap () + editingIcon.getIconWidth ();

        if (editingComponent != null) editingContainer.remove (editingComponent);
        editingComponent = oneLineEditor;  // TODO: Decide which editing component to use
        String text = tree.convertValueToText (value, isSelected, true, leaf, row, false);  // Lie about the expansion state, to force NodePart to return the true name of the part, without parenthetical info about type.
        oneLineEditor.setText (text);
        editingContainer.setFont (renderer.getFontFor (editingNode));  // Should cause the contained text editor to use that font.
        editingContainer.add (editingComponent);
        return editingContainer;
    }

    @Override
    public Object getCellEditorValue ()
    {
        // TODO: handle editor switching
        return oneLineEditor.getText ();
    }

    /**
        Indicate whether the current cell may be edited.
        Apparently, this method is called in only two situations:
        1) The left or right (but not center or scroll wheel) mouse button has been clicked.
           In this case, event is the MouseEvent. For a double-click, the first and second presses
           are delivered as separate events (with click count set to 2 on the second press).
        2) startEditingAtPath() was called, and JTree wants to verify that editing is permitted.
           In this case, event is null. 
    **/
    @Override
    public boolean isCellEditable (EventObject event)
    {
        if (event != null)
        {
            Object source = event.getSource ();
            if (! (source instanceof JTree)) return false;
            if (source != tree)
            {
                setTree ((JTree) source);  // Allow us to change trees,
                return false;              // but still avoid immediate edit.
            }
            if (event instanceof MouseEvent)
            {
                MouseEvent me = (MouseEvent) event;
                TreePath path = tree.getPathForLocation (me.getX (), me.getY ());
                if (path != null)
                {
                    if (me.getClickCount () == 1  &&  SwingUtilities.isLeftMouseButton (me)  &&  path.equals (lastPath))
                    {
                        EventQueue.invokeLater (new Runnable ()
                        {
                            public void run ()
                            {
                                tree.startEditingAtPath (path);
                            }
                        });
                    }
                }
            }
            return false;
        }

        if (lastPath == null) return false;
        Object o = lastPath.getLastPathComponent ();
        if (! (o instanceof NodeBase)) return false;
        editingNode = (NodeBase) o;
        if (! editingNode.allowEdit ()) return false;

        // We will permit the edit, so prepare the container.
        if (editingComponent != null) editingContainer.add (editingComponent);
        return true;
    }

    @Override
    public boolean stopCellEditing ()
    {
        fireEditingStopped ();
        cleanupAfterEditing ();
        return true;
    }

    @Override
    public void cancelCellEditing ()
    {
        fireEditingCanceled ();
        cleanupAfterEditing ();
    }

    @Override
    public void valueChanged (TreeSelectionEvent e)
    {
        lastPath = tree.getSelectionPath ();
    }

    protected void setTree (JTree newTree)
    {
        if (tree == newTree) return;

        if (tree != null) tree.removeTreeSelectionListener (this);
        tree = newTree;
        if (tree != null) tree.addTreeSelectionListener (this);
    }

    protected void cleanupAfterEditing ()
    {
        if (editingComponent != null) editingContainer.remove (editingComponent);
        editingComponent = null;
    }

    /**
        Draws the node icon.
    **/
    public class EditorContainer extends Container
    {
        public EditorContainer ()
        {
            setLayout (null);
        }

        public void paint (Graphics g)
        {
            int x;
            if (getComponentOrientation ().isLeftToRight ()) x = 0;
            else                                             x = getWidth () - editingIcon.getIconWidth ();

            // This complex formula to center the icon vertically was copied from DefaultTreeCellEditor.EditorContainer.
            int iconHeight = editingIcon.getIconHeight ();
            int textHeight = editingComponent.getFontMetrics (editingComponent.getFont ()).getHeight ();  // TODO: this only works for oneLineEditor
            int textY = iconHeight / 2 - textHeight / 2;  // Vertical offset where text starts w.r.t. top of icon. Can be negative if text is taller.
            int totalY = Math.min (0, textY);
            int totalHeight = Math.max (iconHeight, textY + textHeight) - totalY;
            int y = getHeight () / 2 - (totalY + (totalHeight / 2));

            editingIcon.paintIcon (this, g, x, y);

            super.paint (g);
        }

        /**
            Place editingComponent past the icon
        **/
        public void doLayout ()
        {
            if (editingComponent == null) return;

            int width  = getWidth  ();
            int height = getHeight ();
            int x;
            if (getComponentOrientation ().isLeftToRight ()) x = offset;
            else                                             x = 0;

            editingComponent.setBounds (x, 0, width - offset, height);
        }

        public Dimension getPreferredSize ()
        {
            Dimension pSize = editingComponent.getPreferredSize ();
            pSize.width += offset;

            Dimension rSize = renderer.getPreferredSize ();
            pSize.height = Math.max (pSize.height, rSize.height);

            pSize.height = Math.max (pSize.height, editingIcon.getIconHeight ());
            pSize.width  = Math.max (pSize.width, 100);

            return pSize;
        }
    }
}

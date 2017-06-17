/*
Copyright 2013-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.im.InputContext;
import java.io.IOException;
import java.util.EventObject;

import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.ActionMap;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.TransferHandler.TransferSupport;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.text.JTextComponent;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.MainTabbedPane;
import gov.sandia.n2a.ui.SafeTextTransferHandler;
import gov.sandia.n2a.ui.eq.tree.NodeBase;

/**
    Custom tree cell editor that cooperates with NodeBase icon and text styles.
    Adds a few other nice behaviors:
    * Makes cell editing act more like a text document.
      - No visible border
      - Extends full width of tree panel
    * Selects the value portion of an equation, facilitating the user to make simple changes.
    * Instant one-click edit mode, rather than 1.2s delay.
**/
public class EquationTreeCellEditor extends AbstractCellEditor implements TreeCellEditor, TreeSelectionListener
{
    protected JTree                    tree;
    protected EquationTreeCellRenderer renderer;
    protected UndoManager              undoManager;
    protected JTextField               oneLineEditor;
    protected JTextArea                multiLineEditor;
    protected JScrollPane              multiLinePane;  // provides scrolling for multiLineEditor, and acts as the editingComponent

    protected TreePath                 lastPath;
    public    boolean                  multiLineRequested;  ///< Indicates that the next getTreeCellEditorComponent() call should return multi-line, even if the node is normally single line.
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


        oneLineEditor = new JTextField ();
        oneLineEditor.setBorder (new EmptyBorder (0, 0, 0, 0));

        oneLineEditor.getDocument ().addUndoableEditListener (undoManager);
        InputMap inputMap = oneLineEditor.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("control Z"),       "Undo");
        inputMap.put (KeyStroke.getKeyStroke ("control Y"),       "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift control Z"), "Redo");
        ActionMap actionMap = oneLineEditor.getActionMap ();
        actionMap.put ("Undo", new AbstractAction ("Undo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {undoManager.undo ();}
                catch (CannotUndoException e) {}
            }
        });
        actionMap.put ("Redo", new AbstractAction ("Redo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {undoManager.redo();}
                catch (CannotRedoException e) {}
            }
        });

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
            }

            public void focusLost (FocusEvent e)
            {
                if (editingNode != null)
                {
                    stopCellEditing ();
                    ((MainTabbedPane) MainFrame.instance.tabs).setPreferredFocus (PanelModel.instance, tree);
                }
            }
        });

        oneLineEditor.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                stopCellEditing ();
            }
        });

        TransferHandler xfer = new SafeTextTransferHandler ()
        {
            public boolean importData (TransferSupport support)
            {
                boolean result = super.importData (support);
                if (! result) result = tree.getTransferHandler ().importData (support);
                return result;
            }
        };
        oneLineEditor.setTransferHandler (xfer);


        multiLineEditor = new JTextArea ();
        multiLinePane = new JScrollPane (multiLineEditor);
        multiLineEditor.setLineWrap (true);
        multiLineEditor.setWrapStyleWord (true);
        multiLineEditor.setRows (6);
        multiLineEditor.setTabSize (4);
        multiLineEditor.setTransferHandler (xfer);

        multiLineEditor.getDocument ().addUndoableEditListener (undoManager);
        inputMap = multiLineEditor.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),           "none");
        inputMap.put (KeyStroke.getKeyStroke ("control ENTER"),   "insert-break");
        inputMap.put (KeyStroke.getKeyStroke ("control Z"),       "Undo");
        inputMap.put (KeyStroke.getKeyStroke ("control Y"),       "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift control Z"), "Redo");
        actionMap = multiLineEditor.getActionMap ();
        actionMap.put ("Undo", new AbstractAction ("Undo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {undoManager.undo ();}
                catch (CannotUndoException e) {}
            }
        });
        actionMap.put ("Redo", new AbstractAction ("Redo")
        {
            public void actionPerformed (ActionEvent evt)
            {
                try {undoManager.redo();}
                catch (CannotRedoException e) {}
            }
        });

        multiLineEditor.addFocusListener (new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
            }

            public void focusLost (FocusEvent e)
            {
                if (editingNode != null)
                {
                    stopCellEditing ();
                    ((MainTabbedPane) MainFrame.instance.tabs).setPreferredFocus (PanelModel.instance, tree);
                }
            }
        });

        multiLineEditor.addKeyListener (new KeyAdapter ()
        {
            public void keyPressed (KeyEvent e)
            {
                if (e.getKeyCode () == KeyEvent.VK_ENTER  &&  ! e.isControlDown ()) stopCellEditing ();
            }
        });
    }

    @Override
    public Component getTreeCellEditorComponent (JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row)
    {
        editingIcon = renderer.getIconFor ((NodeBase) value, expanded, leaf);
        offset      = renderer.getIconTextGap () + editingIcon.getIconWidth ();
        Font font   = renderer.getFontFor (editingNode);
        String text = editingNode.getText (expanded, true);

        if (editingComponent != null) editingContainer.remove (editingComponent);
        if (text.contains ("\n")  ||  multiLineRequested)
        {
            editingComponent = multiLinePane;
            multiLineEditor.setText (text);
            multiLineEditor.setFont (font);
            int equals = text.indexOf ('=');
            if (equals >= 0) multiLineEditor.setCaretPosition (equals);
            multiLineRequested = false;
        }
        else
        {
            editingComponent = oneLineEditor;
            oneLineEditor.setText (text);
            oneLineEditor.setFont (font);
        }
        editingContainer.add (editingComponent);
        undoManager.discardAllEdits ();
        return editingContainer;
    }

    @Override
    public Object getCellEditorValue ()
    {
        if (editingComponent == oneLineEditor) return oneLineEditor.getText ();
        return multiLineEditor.getText ();  // TODO: post-process the text to remove added \n after =
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
                final TreePath path = tree.getPathForLocation (me.getX (), me.getY ());
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
        return true;
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
            // This complex formula to center the icon vertically was copied from DefaultTreeCellEditor.EditorContainer.
            // It works for both single and multiline editors.
            int iconHeight = editingIcon.getIconHeight ();
            int textHeight = editingComponent.getFontMetrics (editingComponent.getFont ()).getHeight ();
            int textY = iconHeight / 2 - textHeight / 2;  // Vertical offset where text starts w.r.t. top of icon. Can be negative if text is taller.
            int totalY = Math.min (0, textY);
            int totalHeight = Math.max (iconHeight, textY + textHeight) - totalY;
            int y = getHeight () / 2 - (totalY + (totalHeight / 2));

            editingIcon.paintIcon (this, g, 0, y);
            super.paint (g);
        }

        /**
            Place editingComponent past the icon
        **/
        public void doLayout ()
        {
            if (editingComponent == null) return;
            editingComponent.setBounds (offset, 0, getWidth () - offset, getHeight ());
        }

        public Dimension getPreferredSize ()
        {
            Dimension pSize = editingComponent.getPreferredSize ();
            pSize.width = ((JViewport) tree.getParent ()).getViewRect ().width - editingNode.getLevel () * offset;  // getLevel() will return 1 less than needed value, which exactly compensates for the icon space.
            pSize.width = Math.max (100, pSize.width);

            Dimension rSize = renderer.getPreferredSize ();
            pSize.height = Math.max (pSize.height, rSize.height);

            return pSize;
        }
    }
}

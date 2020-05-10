/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq.search;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EventObject;

import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.ActionMap;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.MainTabbedPane;
import gov.sandia.n2a.ui.SafeTextTransferHandler;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.eq.PanelSearch.MNodeRenderer;


/**
    This class is similar to EquationTreeCellEditor, but customized for the search tree.
**/
@SuppressWarnings("serial")
public class NameEditor extends AbstractCellEditor implements TreeCellEditor, TreeSelectionListener
{
    protected MNodeRenderer renderer;
    protected UndoManager   undoManager;
    public    JTextField    editor;

    protected JTree         tree;
    protected TreePath      lastPath;
    protected NodeModel     editingNode;
    protected Container     editingContainer;
    protected Icon          editingIcon;
    protected int           offset;
    protected int           offsetPerLevel;     // How much to indent per tree level to accommodate for expansion handles.

    public NameEditor (MNodeRenderer renderer)
    {
        this.renderer = renderer;

        editingContainer = new EditorContainer ();
        editor = new JTextField ();
        editingContainer.add (editor);

        undoManager = new UndoManager ();
        editor.getDocument ().addUndoableEditListener (undoManager);

        InputMap inputMap = editor.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("control Z"),       "Undo");  // For Windows and Linux
        inputMap.put (KeyStroke.getKeyStroke ("meta Z"),          "Undo");  // For Mac
        inputMap.put (KeyStroke.getKeyStroke ("control Y"),       "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("meta Y"),          "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift control Z"), "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift meta Z"),    "Redo");
        ActionMap actionMap = editor.getActionMap ();
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

        editor.addFocusListener (new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
            }

            public void focusLost (FocusEvent e)
            {
                if (editingNode != null)
                {
                    stopCellEditing ();
                    if (! tree.hasFocus ()) tree.clearSelection ();
                    ((MainTabbedPane) MainFrame.instance.tabs).setPreferredFocus (PanelModel.instance, tree);
                }
            }
        });

        editor.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                stopCellEditing ();
            }
        });

        MouseAdapter mouseListener = new MouseAdapter ()
        {
            public void mouseClicked (MouseEvent me)
            {
                if (me.getClickCount () == 2)
                {
                    stopCellEditing ();
                    PanelModel.instance.panelSearch.selectCurrent ();  // Which presumably is us.
                }
            }
        };
        editor.addMouseListener (mouseListener);

        TransferHandler xfer = new SafeTextTransferHandler ()
        {
            public boolean importData (TransferSupport support)
            {
                boolean result = super.importData (support);
                if (! result) result = tree.getTransferHandler ().importData (support);
                return result;
            }
        };
        editor.setTransferHandler (xfer);
    }

    public void updateUI ()
    {
        int left  = (Integer) UIManager.get ("Tree.leftChildIndent");
        int right = (Integer) UIManager.get ("Tree.rightChildIndent");
        offsetPerLevel = left + right;

        editor.updateUI ();
    }

    @Override
    public Component getTreeCellEditorComponent (JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row)
    {
        this.tree   = tree;
        editingNode = (NodeModel) value;
        editingIcon = renderer.getIconFor (editingNode, expanded, leaf);
        offset      = renderer.getIconTextGap () + editingIcon.getIconWidth ();

        editor.setText (editingNode.toString ());
        editor.setFont (tree.getFont ());

        undoManager.discardAllEdits ();
        return editingContainer;
    }

    @Override
    public Object getCellEditorValue ()
    {
        return editor.getText ();
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
        if (event == null)  // Just verify that editing is permitted.
        {
            if (lastPath == null) return false;  // This should never happen, so the test may be unnecessary.
            Object o = lastPath.getLastPathComponent ();
            if (! (o instanceof NodeModel)) return false;
            NodeModel node = (NodeModel) o;
            if (! node.allowEdit ()) return false;
            return true;
        }
        else if (event instanceof MouseEvent)
        {
            MouseEvent me = (MouseEvent) event;
            int x = me.getX ();
            int y = me.getY ();
            int clicks = me.getClickCount ();
            if (SwingUtilities.isLeftMouseButton (me))
            {
                if (clicks == 1)
                {
                    final TreePath path = tree.getPathForLocation (x, y);
                    if (path != null  &&  path.equals (lastPath))  // Second click on node, but not double-click.
                    {
                        Object o = path.getLastPathComponent ();
                        if (o instanceof NodeModel)
                        {
                            // Initiate edit
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
            }
        }
        // Always return false from this method. Instead, initiate editing indirectly.
        return false;
    }

    public boolean stopCellEditing ()
    {
        NodeModel node = editingNode;
        editingNode = null;

        fireEditingStopped ();
        node.applyEdit (tree);

        return true;
    }

    public void cancelCellEditing ()
    {
        NodeModel node = editingNode;
        editingNode = null;
        fireEditingCanceled ();

        // See EquationTreeCellEditor.cancelCellEditing()
        // TODO: Make insertion/cancellation of new models behave similarly to insertion/cancellation of new parts.
        if (node.toString ().isEmpty ()) node.delete (tree, true);
    }

    public void valueChanged (TreeSelectionEvent e)
    {
        if (! e.isAddedPath ()) return;
        tree = (JTree) e.getSource ();
        lastPath = e.getNewLeadSelectionPath ();
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
            int iconHeight = editingIcon.getIconHeight ();
            int textHeight = editor.getFontMetrics (editor.getFont ()).getHeight ();
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
            editor.setBounds (offset, 0, getWidth () - offset, getHeight ());
        }

        public Dimension getPreferredSize ()
        {
            Dimension pSize = editor.getPreferredSize ();
            Dimension extent = ((JViewport) tree.getParent ()).getExtentSize ();
            Insets insets = tree.getInsets ();
            pSize.width = extent.width - editingNode.getLevel () * offsetPerLevel - insets.left - insets.right;
            pSize.width = Math.max (100, pSize.width);

            Dimension rSize = renderer.getPreferredSize ();
            pSize.height = Math.max (pSize.height, rSize.height);
            // Renderer is using exactly the same icon, so no need to do separate check for icon size.

            return pSize;
        }
    }
}

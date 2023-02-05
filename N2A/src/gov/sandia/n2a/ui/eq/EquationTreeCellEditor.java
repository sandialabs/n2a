/*
Copyright 2013-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.ActionMap;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreePath;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.MainTabbedPane;
import gov.sandia.n2a.ui.SafeTextTransferHandler;
import gov.sandia.n2a.ui.eq.PanelEquationGraph.GraphPanel;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeEquation;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;

/**
    Custom tree cell editor that cooperates with NodeBase icon and text styles.
    Adds a few other nice behaviors:
    * Makes cell editing act more like a text document.
      - No visible border
      - Extends full width of tree panel
    * Selects the value portion of an equation, facilitating the user to make simple changes.
    * Instant one-click edit mode, rather than 1.2s delay.
**/
@SuppressWarnings("serial")
public class EquationTreeCellEditor extends AbstractCellEditor implements TreeCellEditor, TreeSelectionListener
{
    protected EquationTreeCellRenderer renderer;
    protected UndoManager              undoManager;
    protected JTextField               oneLineEditor;
    protected JTextArea                multiLineEditor;
    protected JScrollPane              multiLinePane;      // provides scrolling for multiLineEditor, and acts as the editingComponent
    protected JComboBox<String>        choiceEditor;
    protected JScrollBar               rangeEditor;
    protected JCheckBox                flagEditor;
    protected JLabel                   iconHolder   = new JLabel ();
    protected List<JLabel>             labels       = new ArrayList<JLabel> ();

    protected JTree                    focusTree;
    protected TreePath                 lastPath;
    protected boolean                  multiLineRequested; // Indicates that the next getTreeCellEditorComponent() call should return multi-line, even if the node is normally single line.
    protected JTree                    editingTree;        // Could be different than focusTree
    protected NodeBase                 editingNode;
    protected boolean                  editingExpanded;
    protected boolean                  editingLeaf;
    protected boolean                  editingTitle;       // Indicates that we are in a graph node title rather than a proper tree. Used for focus control.
    protected EditorContainer          editingContainer;
    protected Component                editingComponent;
    protected static int               offsetPerLevel;     // How much to indent per tree level to accommodate for expansion handles.
    protected String                   rangeUnits;
    protected double                   rangeLo;
    protected double                   rangeHi;
    protected double                   rangeStepSize;

    public EquationTreeCellEditor ()
    {
        undoManager = new UndoManager ();
        editingContainer = new EditorContainer ();

        editingContainer.add (iconHolder);
        for (int i = 0; i < 2; i++)
        {
            JLabel l = new JLabel ();
            editingContainer.add (l);
            labels.add (l);
        }


        oneLineEditor = new JTextField ();
        oneLineEditor.setBorder (new EmptyBorder (0, 0, 0, 0));

        oneLineEditor.getDocument ().addUndoableEditListener (undoManager);
        InputMap inputMap = oneLineEditor.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("control Z"),       "Undo");  // For Windows and Linux
        inputMap.put (KeyStroke.getKeyStroke ("meta Z"),          "Undo");  // For Mac
        inputMap.put (KeyStroke.getKeyStroke ("control Y"),       "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("meta Y"),          "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift control Z"), "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift meta Z"),    "Redo");
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
                    if (";:+*/<>".indexOf (text.charAt (equals + 1)) >= 0) equals++;
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
                    if (! editingTree.hasFocus ())
                    {
                        PanelEquationTree pet = (PanelEquationTree) editingTree.getParent ().getParent ();
                        pet.yieldFocus ();
                    }
                    ((MainTabbedPane) MainFrame.instance.tabs).setPreferredFocus (PanelModel.instance, editingTree);
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

        MouseAdapter mouseListener = new MouseAdapter ()
        {
            public void mouseClicked (MouseEvent me)
            {
                if (me.getClickCount () == 2  &&  editingNode instanceof NodePart)
                {
                    // Drill down
                    NodePart part = (NodePart) editingNode;  // Save node, because stopCellEditing() will set it to null.
                    stopCellEditing ();
                    PanelModel.instance.panelEquations.drill (part);
                }
            }
        };
        oneLineEditor.addMouseListener (mouseListener);

        TransferHandler xfer = new SafeTextTransferHandler ()
        {
            public boolean importData (TransferSupport support)
            {
                if      (editingNode instanceof NodeVariable) setSafeTypes ("Equation", "Variable");
                else if (editingNode instanceof NodeEquation) setSafeTypes ("Equation");
                else                                          setSafeTypes ();
                boolean result = super.importData (support);
                if (! result) result = editingTree.getTransferHandler ().importData (support);
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
        multiLineEditor.setFocusTraversalKeysEnabled (false);  // We take control of these for better user interaction.

        multiLineEditor.getDocument ().addUndoableEditListener (undoManager);
        inputMap = multiLineEditor.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),           "stopEditing");
        inputMap.put (KeyStroke.getKeyStroke ("control ENTER"),   "insert-break");
        inputMap.put (KeyStroke.getKeyStroke ("TAB"),             "transferFocus");
        inputMap.put (KeyStroke.getKeyStroke ("shift TAB"),       "transferFocusBackward");
        inputMap.put (KeyStroke.getKeyStroke ("control TAB"),     "insert-tab");
        inputMap.put (KeyStroke.getKeyStroke ("control Z"),       "Undo");
        inputMap.put (KeyStroke.getKeyStroke ("meta Z"),          "Undo");
        inputMap.put (KeyStroke.getKeyStroke ("control Y"),       "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("meta Y"),          "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift control Z"), "Redo");
        inputMap.put (KeyStroke.getKeyStroke ("shift meta Z"),    "Redo");
        actionMap = multiLineEditor.getActionMap ();
        actionMap.put ("stopEditing", new AbstractAction ("StopEditing")
        {
            public void actionPerformed (ActionEvent evt)
            {
                stopCellEditing ();
            }
        });
        actionMap.put ("transferFocus", new AbstractAction ("TransferFocusForward")
        {
            public void actionPerformed (ActionEvent evt)
            {
                multiLineEditor.transferFocus ();
            }
        });
        actionMap.put ("transferFocusBackward", new AbstractAction ("TransferFocusBackward")
        {
            public void actionPerformed (ActionEvent evt)
            {
                multiLineEditor.transferFocusBackward ();
            }
        });
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

        FocusListener focusListener = new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
            }

            public void focusLost (FocusEvent e)
            {
                if (editingNode != null) stopCellEditing ();
                if (! editingTree.hasFocus ())
                {
                    PanelEquationTree pet = (PanelEquationTree) editingTree.getParent ().getParent ();
                    pet.yieldFocus ();
                }
                ((MainTabbedPane) MainFrame.instance.tabs).setPreferredFocus (PanelModel.instance, editingTree);
            }
        };
        multiLineEditor.addFocusListener (focusListener);

        multiLineEditor.addMouseListener (mouseListener);


        choiceEditor = new JComboBox<String> ();
        choiceEditor.setUI (new BasicComboBoxUI ());  // Avoid borders on edit box, to save space.

        choiceEditor.addPopupMenuListener (new PopupMenuListener ()
        {
            public void popupMenuWillBecomeVisible (PopupMenuEvent e)
            {
            }

            public void popupMenuWillBecomeInvisible (PopupMenuEvent e)
            {
                // Have to test if we are still editing, because this may have been triggered by "finishEditing" action.
                if (editingNode != null) stopCellEditing ();
            }

            public void popupMenuCanceled (PopupMenuEvent e)
            {
            }
        });

        inputMap = choiceEditor.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),  "finishEditing");
        inputMap.put (KeyStroke.getKeyStroke ("ESCAPE"), "cancelEditing");
        actionMap = choiceEditor.getActionMap ();
        AbstractAction finishEditing = new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                stopCellEditing ();
            }
        };
        actionMap.put ("finishEditing", finishEditing);
        AbstractAction cancelEditing = new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                cancelCellEditing ();
            }
        };
        actionMap.put ("cancelEditing", cancelEditing);

        choiceEditor.addFocusListener (focusListener);


        rangeEditor = new JScrollBar (JScrollBar.HORIZONTAL);

        inputMap = rangeEditor.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),  "finishEditing");
        inputMap.put (KeyStroke.getKeyStroke ("ESCAPE"), "cancelEditing");
        actionMap = rangeEditor.getActionMap ();
        actionMap.put ("finishEditing", finishEditing);
        actionMap.put ("cancelEditing", cancelEditing);

        rangeEditor.addFocusListener (focusListener);


        flagEditor = new JCheckBox ();

        inputMap = flagEditor.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),  "finishEditing");
        inputMap.put (KeyStroke.getKeyStroke ("ESCAPE"), "cancelEditing");
        actionMap = flagEditor.getActionMap ();
        actionMap.put ("finishEditing", finishEditing);
        actionMap.put ("cancelEditing", cancelEditing);

        flagEditor.addFocusListener (focusListener);
    }

    public static void staticUpdateUI ()
    {
        int left  = (Integer) UIManager.get ("Tree.leftChildIndent");
        int right = (Integer) UIManager.get ("Tree.rightChildIndent");
        offsetPerLevel = left + right;
    }

    public void updateUI ()
    {
        oneLineEditor  .updateUI ();
        multiLinePane  .updateUI ();
        multiLineEditor.updateUI ();
    }

    public Component getTitleEditorComponent (JTree tree, NodePart value, EquationTreeCellRenderer renderer, boolean open)
    {
        return getTreeCellEditorComponent (tree, value, renderer, open, false, true);
    }

    @Override
    public Component getTreeCellEditorComponent (JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row)
    {
        return getTreeCellEditorComponent (tree, value, PanelModel.instance.panelEquations.renderer, expanded, leaf, false);
    }

    public Component getTreeCellEditorComponent (JTree tree, Object value, EquationTreeCellRenderer renderer, boolean expanded, boolean leaf, boolean isTitle)
    {
        editingTree     = tree;
        editingNode     = (NodeBase) value;
        editingExpanded = expanded;
        editingLeaf     = leaf;
        editingTitle    = isTitle;
        this.renderer   = renderer;  // For use by EditorContainer
        Font fontBase   = renderer.getBaseFont (tree);
        Font fontPlain  = editingNode.getPlainFont (fontBase);
        Font fontStyled = editingNode.getStyledFont (fontBase);
        FontMetrics fm  = tree.getFontMetrics (fontStyled);

        GraphPanel gp = PanelModel.instance.panelEquations.panelEquationGraph.graphPanel;
        boolean zoomed =  fontBase == gp.scaledTreeFont;
        Icon icon = renderer.getIconFor (editingNode, expanded, leaf, zoomed);
        iconHolder.setIcon (icon);
        int offset = icon.getIconWidth () + iconHolder.getIconTextGap ();

        String text;
        String param;
        boolean isSimpleVariable =  editingNode instanceof NodeVariable  &&  ! ((NodeVariable) editingNode).hasEquations ();
        boolean isAnnotation     =  editingNode instanceof NodeAnnotation;
        if (FilteredTreeModel.showParam  &&  editingNode.isParam ()  &&  (isSimpleVariable  ||  isAnnotation))
        {
            // Add static labels for all columns except the value. See EquationTreeCellRenderer.getTreeCellRendererComponent()
            NodeBase      p            = editingNode.getTrueParent ();
            List<Integer> columnWidths = p.getMaxColumnWidths (editingNode.getColumnGroup (), fm);
            List<String>  columns      = editingNode.getColumns (true, expanded);  // NodeVariable and NodeAnnotation return at least 3 columns.
            for (int i = 0; i < 2; i++)  // Set up the first two columns to display as fixed text in the editor.
            {
                JLabel l = labels.get (i);
                l.setText (columns.get (i));
                l.setFont (fontPlain);
                l.setVisible (true);
                l.setLocation (offset, 0);
                offset += columnWidths.get (i);
            }

            text = columns.get (2);  // 3rd column contains the value of the parameter.
            if (isAnnotation) param = editingNode.source.get ("param");
            else              param = editingNode.source.get ("$meta", "param");
        }
        else
        {
            for (int i = 0; i < 2; i++) labels.get (i).setVisible (false);
            text = editingNode.toString ();  // Fetch user object.
            param = "";
        }

        // Update editing component
        if (editingComponent != null) editingContainer.remove (editingComponent);
        editingContainer.nodeWidth = 0;
        if (editingNode instanceof NodePart)
        {
            NodePart editingPart = (NodePart) editingNode;
            if (editingPart.graph != null) editingContainer.nodeWidth = editingPart.graph.panelTitle.getWidth () / gp.em;
        }
        if (param.equals ("flag"))
        {
            editingComponent = flagEditor;
            boolean isFalse =  text.isEmpty ()  ||  text.equals ("0");
            flagEditor.setSelected (! isFalse);
        }
        else if (param.contains (","))  // Dropdown list with fixed set of options.
        {
            editingComponent = choiceEditor;
            choiceEditor.removeAllItems ();
            String[] pieces = param.split (",");
            for (String c : pieces) choiceEditor.addItem (c);
            choiceEditor.setSelectedItem (text);
        }
        else if (param.startsWith ("["))  // Numeric range
        {
            editingComponent = rangeEditor;

            String[] pieces = param.substring (1).split ("]", 2);
            rangeUnits = "";
            if (pieces.length == 2) rangeUnits = pieces[1];
            pieces = pieces[0].split (":");
            rangeLo = Double.valueOf (pieces[0]);
            rangeHi = Double.valueOf (pieces[1]);
            rangeStepSize = 1;
            if (pieces.length == 3) rangeStepSize = Double.valueOf (pieces[2]);

            int steps = (int) Math.round ((rangeHi - rangeLo) / rangeStepSize);
            double current = new UnitValue (text).value;
            int c = (int) Math.round ((current - rangeLo) / rangeStepSize);
            c = Math.max (c, 0);
            c = Math.min (c, steps);
            rangeEditor.setValues (c, 1, 0, steps + 1);
        }
        else  // Plain text
        {
            int textWidth = fm.stringWidth (text);
            int treeWidth = tree.getWidth ();
            if (! isTitle  &&  (text.contains ("\n")  ||  textWidth > treeWidth  ||  multiLineRequested))
            {
                editingComponent = multiLinePane;
                multiLineEditor.setText (text);
                multiLineEditor.setFont (fontStyled);
                multiLineEditor.setEditable (! PanelModel.instance.panelEquations.locked);
                int equals = text.indexOf ('=');
                if (equals >= 0) multiLineEditor.setCaretPosition (equals);
                multiLineRequested = false;
            }
            else
            {
                editingComponent = oneLineEditor;
                oneLineEditor.setText (text);
                oneLineEditor.setFont (fontStyled);
                oneLineEditor.setEditable (! PanelModel.instance.panelEquations.locked);
            }
            undoManager.discardAllEdits ();
        }
        editingComponent.setLocation (offset, 0);
        editingContainer.add (editingComponent);

        return editingContainer;
    }

    public void rescale ()
    {
        GraphPanel gp   = PanelModel.instance.panelEquations.panelEquationGraph.graphPanel;
        Font fontBase   = gp.scaledTreeFont;
        Font fontPlain  = editingNode.getPlainFont (fontBase);
        Font fontStyled = editingNode.getStyledFont (fontBase);
        FontMetrics fm  = editingComponent.getFontMetrics (fontStyled);

        Icon icon = renderer.getIconFor (editingNode, editingExpanded, editingLeaf, true);
        iconHolder.setIcon (icon);
        int offset = icon.getIconWidth () + iconHolder.getIconTextGap ();

        boolean isSimpleVariable =  editingNode instanceof NodeVariable  &&  ! ((NodeVariable) editingNode).hasEquations ();
        boolean isAnnotation     =  editingNode instanceof NodeAnnotation;
        if (FilteredTreeModel.showParam  &&  editingNode.isParam ()  &&  (isSimpleVariable  ||  isAnnotation))
        {
            NodeBase      p            = editingNode.getTrueParent ();
            List<Integer> columnWidths = p.getMaxColumnWidths (editingNode.getColumnGroup (), fm);
            for (int i = 0; i < 2; i++)
            {
                JLabel l = labels.get (i);
                l.setFont (fontPlain);
                l.setLocation (offset, 0);
                offset += columnWidths.get (i);
            }
        }

        editingComponent.setFont (fontStyled);
        editingComponent.setLocation (offset, 0);
    }

    @Override
    public Object getCellEditorValue ()
    {
        String value = "";
        if      (editingComponent == choiceEditor)  value = choiceEditor.getSelectedItem ().toString ();
        else if (editingComponent == oneLineEditor) value = oneLineEditor.getText ();
        else if (editingComponent == multiLinePane) value = multiLineEditor.getText ();
        else if (editingComponent == flagEditor)    value = flagEditor.isSelected () ? "1" : "0";
        else                      // rangeEditor
        {
            double c = rangeEditor.getValue () * rangeStepSize + rangeLo;
            if (isInteger (rangeLo)  &&  isInteger (rangeHi)  &&  isInteger (rangeStepSize))
            {
                value = String.valueOf ((int) Math.round (c));
            }
            else
            {
                value = String.valueOf (c);
            }
            value += rangeUnits;
        }
        if (labels.get (0).isVisible ())  // parameter mode, so add back name and assignment character
        {
            // Column 1 (assignment operator) should exist if column 0 exists.
            value = labels.get (0).getText ().trim () + labels.get (1).getText ().trim () + value;
        }
        return value;
    }

    public static boolean isInteger (double value)
    {
        // Could compute threshold using Math.ulp(1.0), which gives machine epsilon, but that is a bit too tight.
        // The number here is arbitrary, but reasonable for double. It is roughly sqrt(epsilon).
        return Math.abs (value - Math.round (value)) < 1e-8;  
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
            if (lastPath == null) return false;
            Object o = lastPath.getLastPathComponent ();
            if (! (o instanceof NodeBase)) return false;
            NodeBase node = (NodeBase) o;
            return node.allowEdit ();
        }
        else if (event instanceof MouseEvent)
        {
            MouseEvent me = (MouseEvent) event;
            if (! SwingUtilities.isLeftMouseButton (me)) return false;
            if (me.getClickCount () != 1) return false;
            if (me.isControlDown ()) return false;  // On Macintosh, reserve ctrl-click for context menu.
            if (focusTree == null) return false;

            int x = me.getX ();
            int y = me.getY ();
            final TreePath path = focusTree.getPathForLocation (x, y);
            if (path != null  &&  path.equals (lastPath))  // Second click on node, but not double-click.
            {
                // Initiate edit
                EventQueue.invokeLater (new Runnable ()
                {
                    public void run ()
                    {
                        focusTree.startEditingAtPath (path);
                    }
                });
            }
        }
        // We only get here during a mouse event.
        // In that case, always return false, because we initiate editing indirectly.
        return false;
    }

    public boolean stopCellEditing ()
    {
        NodeBase node = editingNode;
        editingNode = null;

        fireEditingStopped ();
        editingTree.setEditable (! PanelModel.instance.panelEquations.locked);  // Restore lock that may have been unset to allow user to view truncated fields.
        node.applyEdit (editingTree);

        return true;
    }

    public void cancelCellEditing ()
    {
        NodeBase node = editingNode;
        editingNode = null;

        fireEditingCanceled ();
        editingTree.setEditable (! PanelModel.instance.panelEquations.locked);

        // We only get back an empty string if we explicitly set it before editing starts.
        // Certain types of nodes do this when inserting a new instance into the tree, via NodeBase.add()
        // We desire in this case that escape cause the new node to evaporate.
        if (node.toString ().isEmpty ())
        {
            node.delete (true);
        }
        else if (node instanceof NodePart)
        {
            NodePart p = (NodePart) node;
            if (p.graph != null) p.graph.animate ();
        }
    }

    public void valueChanged (TreeSelectionEvent e)
    {
        if (! e.isAddedPath ()) return;
        focusTree = (JTree) e.getSource ();
        lastPath = e.getNewLeadSelectionPath ();  // Unlike DefaultTreeCellEditor, we allow the lead cell to edit even when there are multiple cells selected.
    }

    /**
        Draws the node icon.
    **/
    public class EditorContainer extends Container
    {
        public double nodeWidth;  // in ems

        public EditorContainer ()
        {
            setLayout (null);
        }

        /**
            Place editingComponent past the icon
        **/
        public void doLayout ()
        {
            int w = getWidth ();
            int h = getHeight ();

            Dimension d = iconHolder.getPreferredSize ();
            int y = Math.max (0, h - d.height) / 2;
            iconHolder.setBounds (0, y, d.width, d.height);

            if (labels.get (0).isVisible ())
            {
                for (int i = 0; i < 2; i++)
                {
                    JLabel l = labels.get (i);
                    d = l.getPreferredSize ();
                    int x = l.getX ();
                    y = Math.max (0, h - d.height) / 2;
                    l.setBounds (x, y, d.width, d.height);
                }
            }

            if (editingComponent != null)
            {
                // Most editors will be sized to exactly fill available area.
                // In the case of a combo-box (choiceEditor), there is no way to scroll interior content.
                // Also, it looks terrible if stretched wide. Better to show at its preferred size.
                d = editingComponent.getPreferredSize ();
                int x = editingComponent.getX ();
                if (editingComponent != choiceEditor) d.width = w - x; 
                y = Math.max (0, h - d.height) / 2;
                editingComponent.setBounds (x, y, d.width, d.height);
            }
        }

        public Dimension getPreferredSize ()
        {
            int rightMargin = -1;
            double em = PanelModel.instance.panelEquations.panelEquationGraph.graphPanel.em;
            if (nodeWidth > 0) rightMargin = (int) Math.round (nodeWidth * em);
            if (rightMargin < 0)  // Use tree boundaries
            {
                JViewport vp     = (JViewport) editingTree.getParent ();
                Dimension extent = vp.getExtentSize ();
                Point     p      = vp.getViewPosition ();
                rightMargin = p.x + extent.width;
            }

            Dimension pSize = editingComponent.getPreferredSize ();
            Insets insets = editingTree.getInsets ();
            pSize.width = rightMargin - offsetPerLevel * editingNode.getLevel () - insets.left - insets.right;
            pSize.width = Math.max ((int) Math.round (8 * em), pSize.width);

            Dimension rSize = renderer.getPreferredSize ();
            pSize.height = Math.max (pSize.height, rSize.height);
            // Renderer has exactly the same icon, so no need to do separate check for icon size.

            return pSize;
        }
    }
}

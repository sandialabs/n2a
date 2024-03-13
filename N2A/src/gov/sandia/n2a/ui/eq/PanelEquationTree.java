/*
Copyright 2013-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPart;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.UndoManager;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.PanelEquationGraph.GraphPanel;
import gov.sandia.n2a.ui.eq.PanelEquations.FocusCacheEntry;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeEquation;
import gov.sandia.n2a.ui.eq.tree.NodeInherit;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;
import gov.sandia.n2a.ui.eq.undo.AddAnnotation;
import gov.sandia.n2a.ui.eq.undo.AddDoc;
import gov.sandia.n2a.ui.eq.undo.ChangeAnnotation;
import gov.sandia.n2a.ui.eq.undo.ChangeOrder;
import gov.sandia.n2a.ui.eq.undo.CompoundEditView;
import gov.sandia.n2a.ui.eq.undo.DeleteAnnotation;
import gov.sandia.n2a.ui.eq.undo.Outsource;
import gov.sandia.n2a.ui.eq.undo.UndoableView;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

@SuppressWarnings("serial")
public class PanelEquationTree extends JScrollPane
{
    public    JTree             tree;
    public    FilteredTreeModel model;
    public    NodePart          root;
    protected PanelEquations    container;
    protected boolean           needZoom;

    public PanelEquationTree (PanelEquations container, boolean needZoom)
    {
        this.container = container;
        this.needZoom  = needZoom;

        model = new FilteredTreeModel (root);  // member "root" is null at this point
        tree  = new JTree (model)
        {
            public void startEditingAtPath (TreePath path)
            {
                super.startEditingAtPath (path);
                animate ();  // Ensure that graph node is big enough to edit name without activating scroll bars.
            }

            public String getToolTipText (MouseEvent e)
            {
                TreePath path = getPathForLocation (e.getX (), e.getY ());
                if (path == null) return null;
                NodeBase node = (NodeBase) path.getLastPathComponent ();
                return node.getToolTipText (getFontMetrics (MainFrame.instance.getFont ()));
            }

            public void updateUI ()
            {
                if (needZoom) setFont (container.panelEquationGraph.graphPanel.scaledTreeFont);
                if (root != null) root.filter ();  // Force (later) update to tab stops, in case font has changed. This can also affect cell size.
                super.updateUI ();  // Causes tree to poll for cell sizes.
            }
        };

        tree.setRootVisible (false);
        tree.setShowsRootHandles (true);
        tree.setExpandsSelectedPaths (true);
        tree.setScrollsOnExpand (true);
        tree.getSelectionModel ().setSelectionMode (TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);  // Appears to be the default, but we make it explicit.
        tree.setEditable (! container.locked);
        tree.setInvokesStopCellEditing (true);  // auto-save current edits, as much as possible
        tree.setDragEnabled (true);
        tree.setRequestFocusEnabled (false);  // Don't request focus directly when clicked. Instead, let mouse listener do it.
        ToolTipManager.sharedInstance ().registerComponent (tree);
        tree.setTransferHandler (container.transferHandler);
        tree.setCellRenderer (container.renderer);
        tree.setCellEditor (container.editor);
        tree.addTreeSelectionListener (container.editor);

        setBorder (BorderFactory.createEmptyBorder ());

        InputMap inputMap = tree.getInputMap ();                                     // Previous definition             also covered by
        inputMap.put (KeyStroke.getKeyStroke ("ctrl UP"),           "switchFocus");  // selectPreviousChangeLead        ctrl-KP_UP
        inputMap.put (KeyStroke.getKeyStroke ("ctrl LEFT"),         "switchFocus");  // scrollLeft                      ctrl-KP_LEFT or shift-mouse-wheel
        inputMap.put (KeyStroke.getKeyStroke ("ctrl DOWN"),         "nothing");      // selectNextChangeLead            ctrl-KP_DOWN
        inputMap.put (KeyStroke.getKeyStroke ("ctrl RIGHT"),        "nothing");      // scrollRight                     ctrl-KP_RIGHT or shift-mouse-wheel
        inputMap.put (KeyStroke.getKeyStroke ("shift ctrl UP"),     "moveUp");       // selectPreviousExtendSelection   shift-UP
        inputMap.put (KeyStroke.getKeyStroke ("shift ctrl DOWN"),   "moveDown");     // selectNextExtendSelection       shift-DOWN
        inputMap.put (KeyStroke.getKeyStroke ("INSERT"),            "add");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl EQUALS"),       "add");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl 1"),            "addPart");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl 2"),            "addVariable");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl 3"),            "addEquation");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl 4"),            "addAnnotation");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl 5"),            "addReference");
        inputMap.put (KeyStroke.getKeyStroke ("DELETE"),            "delete");
        inputMap.put (KeyStroke.getKeyStroke ("BACK_SPACE"),        "delete");
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),             "startEditing");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl ENTER"),        "startEditing");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl D"),            "drillDown");
        inputMap.put (KeyStroke.getKeyStroke ("shift ctrl D"),      "drillUp");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl W"),            "watch");
        inputMap.put (KeyStroke.getKeyStroke ("shift ctrl W"),      "watch");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl O"),            "outsource");

        ActionMap actionMap = tree.getActionMap ();
        Action selectPrevious   = actionMap.get ("selectPrevious");
        Action selectParent     = actionMap.get ("selectParent");
        Action aquaCollapseNode = actionMap.get ("aquaCollapseNode");
        Action selectChild      = actionMap.get ("selectChild");
        actionMap.put ("selectPrevious", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if (tree.getLeadSelectionRow () <= 0)
                {
                    switchFocus ();
                }
                else
                {
                    selectPrevious.actionPerformed (e);
                }
            }
        });
        actionMap.put ("selectParent", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                TreePath path = tree.getLeadSelectionPath ();
                if (path == null  ||  path.getPathCount () == 2  &&  tree.isCollapsed (path))  // This is a direct child of the root (which is not visible).
                {
                    switchFocus ();
                }
                else
                {
                    selectParent.actionPerformed (e);
                }
            }
        });
        actionMap.put ("aquaCollapseNode", new AbstractAction ()  // Copy of "selectParent" special for aqua (Mac OS X) L&F.
        {
            public void actionPerformed (ActionEvent e)
            {
                TreePath path = tree.getLeadSelectionPath ();
                if (path == null  ||  path.getPathCount () == 2  &&  tree.isCollapsed (path))
                {
                    switchFocus ();
                }
                else
                {
                    aquaCollapseNode.actionPerformed (e);
                }
            }
        });
        actionMap.put ("selectChild", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                NodeBase n = null;
                TreePath path = tree.getLeadSelectionPath ();
                if (path != null) n = (NodeBase) path.getLastPathComponent ();
                if (n != null  &&  model.isLeaf (n)  &&  n.showMultiLine ())
                {
                    tree.setEditable (true);  // Hack to allow users to view truncated fields in locked models. Lock will be restored to correct state when the edit ends.
                    tree.startEditingAtPath (path);
                }
                else
                {
                    selectChild.actionPerformed (e);
                }
            }
        });
        actionMap.put ("switchFocus", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                switchFocus ();
            }
        });
        actionMap.put ("moveUp", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                moveSelected (-1);
            }
        });
        actionMap.put ("moveDown", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                moveSelected (1);
            }
        });
        actionMap.put ("add", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                addAtSelected ("");
            }
        });
        actionMap.put ("addPart", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                addAtSelected ("Part");
            }
        });
        actionMap.put ("addVariable", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                addAtSelected ("Variable");
            }
        });
        actionMap.put ("addEquation", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                addAtSelected ("Equation");
            }
        });
        actionMap.put ("addAnnotation", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                addAtSelected ("Annotation");
            }
        });
        actionMap.put ("addReference", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                addAtSelected ("Reference");
            }
        });
        actionMap.put ("delete", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                deleteSelected ();
            }
        });
        actionMap.put ("startEditing", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                TreePath path = tree.getLeadSelectionPath ();
                if (path == null) return;
                NodeBase n = (NodeBase) path.getLastPathComponent ();
                if (container.locked  &&  ! n.showMultiLine ()) return;
                boolean isControlDown = (e.getModifiers () & ActionEvent.CTRL_MASK) != 0;
                if (isControlDown  &&  ! (n instanceof NodePart)) container.editor.multiLineRequested = true;  // Also possible that multiline will be selected automatically based on content.
                tree.setEditable (true);  // Hack to allow users to view truncated fields in locked models. Lock will be restored to correct state when the edit ends.
                tree.startEditingAtPath (path);
            }
        });
        actionMap.put ("drillUp", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                container.drillUp ();
            }
        });
        actionMap.put ("drillDown", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                // Find the nearest enclosing NodePart and drill into it.
                TreePath path = tree.getLeadSelectionPath ();
                if (path == null) return;
                for (int i = path.getPathCount () - 1; i >= 0; i--)
                {
                    Object o = path.getPathComponent (i);
                    if (o instanceof NodePart)
                    {
                        container.drill ((NodePart) o);
                        break;
                    }
                }
            }
        });
        actionMap.put ("watch", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                // PanelEquations also does a stopEditing() call, but it should not be needed here.
                watchSelected ((e.getModifiers () & ActionEvent.SHIFT_MASK) != 0);  // shift indicates to clear all other watches in model
            }
        });
        actionMap.put ("outsource", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                outsourceSelected ();
            }
        });

        MouseAdapter mouseAdapter = new MouseAdapter ()
        {
            /**
                Does bookkeeping for focus change caused by direct click (as opposed to keyboard navigation or undo).
            **/
            public void switchToTree ()
            {
                if (root == null) return;
                if (tree.getRowCount () == 0)  // Empty tree, so we don't want to shift focus.
                {
                    // Claw focus back to title
                    if (root.graph != null) root.graph.switchFocus (true, false);
                    else if (root == container.part) container.switchFocus (true, false);
                }
                else
                {
                    container.panelEquationGraph.clearSelection ();

                    // Set flag to indicate that tree, rather than title, is now focused.
                    if (root.graph != null) root.graph.titleFocused = false;
                    else if (root == container.part) container.titleFocused = false;
                }
            }

            public void translate (MouseEvent me, GraphPanel gp)
            {
                Point p = me.getPoint ();
                Component c = me.getComponent ();
                Point vp = SwingUtilities.convertPoint (c, p, gp);
                me.translatePoint (vp.x-p.x, vp.y-p.y);
            }

            public void mouseClicked (MouseEvent me)
            {
                if (SwingUtilities.isMiddleMouseButton (me)) return;  // Prevent control-middle click from being processed below.

                int     x       = me.getX ();
                int     y       = me.getY ();
                int     clicks  = me.getClickCount ();
                boolean control = me.isControlDown ();
                boolean shift   = me.isShiftDown ();

                TreePath path = tree.getClosestPathForLocation (x, y);
                if (path != null)
                {
                    // Constrain click to be close to node content, but allow some slack.
                    Rectangle r = tree.getPathBounds (path);
                    r.width += r.x + 100;  // These two lines:
                    r.x = 0;               //   shift left side over to margin, while shifting right side by 100px to the right
                    if (! r.contains (x, y)) path = null;
                }

                if (SwingUtilities.isRightMouseButton (me)  ||  control  &&  Host.isMac ())
                {
                    if (clicks == 1)  // Show popup menu
                    {
                        if (path != null)
                        {
                            switchToTree ();
                            tree.setSelectionPath (path);
                            takeFocus ();
                            container.menuPopup.show (tree, x, y);
                        }
                    }
                }
                else if (SwingUtilities.isLeftMouseButton (me))
                {
                    if (clicks == 1)
                    {
                        if (path != null  &&  ! control  &&  ! shift) tree.setSelectionPath (path);
                        switchToTree ();
                        takeFocus ();
                    }
                    else if (clicks == 2)  // Drill down on parts.
                    {
                        NodePart part = null;
                        if (path != null)
                        {
                            Object temp = path.getLastPathComponent ();
                            if (! (temp instanceof NodePart)) return;
                            part = (NodePart) temp;
                            // and drill down
                        }
                        if (part == null) part = root;  // Drill down without selecting a specific node.
                        container.drill (part);
                    }
                }
            }

            public void mousePressed (MouseEvent me)
            {
                if (SwingUtilities.isMiddleMouseButton (me))
                {
                    if (container.view == PanelEquations.NODE  &&  root != null  &&  root.graph != null)
                    {
                        GraphPanel gp = root.graph.parent;
                        translate (me, gp);
                        gp.mouseListener.mousePressed (me);
                    }
                }
            }

            public void mouseReleased (MouseEvent me)
            {
                if (SwingUtilities.isMiddleMouseButton (me))
                {
                    if (container.view == PanelEquations.NODE  &&  root != null  &&  root.graph != null)
                    {
                        GraphPanel gp = root.graph.parent;
                        translate (me, gp);
                        gp.mouseListener.mouseReleased (me);
                    }
                }
            }

            public void mouseWheelMoved (MouseWheelEvent me)
            {
                if (me.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL)
                {
                    if (container.view == PanelEquations.NODE  &&  root != null  &&  root.graph != null)
                    {
                        GraphPanel gp = root.graph.parent;
                        if (me.isControlDown ())  // zoom
                        {
                            translate (me, gp);
                            gp.mouseListener.mouseWheelMoved (me);
                            return;
                        }
                        else  // scroll
                        {
                            JScrollBar sb;
                            if (me.isShiftDown ()) sb = getHorizontalScrollBar ();
                            else                   sb = getVerticalScrollBar ();
                            boolean shouldForward = true;
                            if (sb.isVisible ())
                            {
                                int units   = me.getUnitsToScroll ();
                                int value   = sb.getValue ();
                                int visible = sb.getVisibleAmount ();
                                if (units < 0) shouldForward =  value           <= sb.getMinimum ();
                                else           shouldForward =  value + visible >= sb.getMaximum ();
                            }
                            if (shouldForward)
                            {
                                gp.mouseListener.mouseWheelMoved (me);
                                return;
                            }
                        }
                    }
                }
                tree.getParent ().dispatchEvent (me);  // default JTree mouse wheel processing
            }

            public void mouseDragged (MouseEvent me)
            {
                if (SwingUtilities.isMiddleMouseButton (me))
                {
                    if (container.view == PanelEquations.NODE  &&  root != null  &&  root.graph != null)
                    {
                        GraphPanel gp = root.graph.parent;
                        translate (me, gp);
                        gp.mouseListener.mouseDragged (me);
                    }
                    return;
                }

                // For some reason, a DnD gesture does not cause JTree to take focus.
                // This is a hack to grab focus in that case. At the start of a drag,
                // we receive a small handful of mouseDragged() messages. Perhaps
                // they stop because DnD takes over. In any case, this is sufficient to detect
                // the start of DnD and grab focus.
                if (! tree.isFocusOwner ()) tree.requestFocusInWindow ();
            }
        };
        tree.addMouseListener (mouseAdapter);
        tree.addMouseMotionListener (mouseAdapter);
        tree.addMouseWheelListener (mouseAdapter);

        tree.addTreeWillExpandListener (new TreeWillExpandListener ()
        {
            public void treeWillExpand (TreeExpansionEvent event) throws ExpandVetoException
            {
            }

            public void treeWillCollapse (TreeExpansionEvent event) throws ExpandVetoException
            {
                TreePath path = event.getPath ();
                NodeBase node = (NodeBase) path.getLastPathComponent ();
                if (node == root) throw new ExpandVetoException (event);
            }
        });

        tree.addTreeExpansionListener (new TreeExpansionListener ()
        {
            public void treeExpanded (TreeExpansionEvent event)
            {
                if (PanelEquationTree.this != container.panelEquationTree) animate ();
            }

            public void treeCollapsed (TreeExpansionEvent event)
            {
                if (PanelEquationTree.this != container.panelEquationTree) animate ();
            }
        });

        tree.addFocusListener (new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
                restoreFocus ();
            }

            public void focusLost (FocusEvent e)
            {
                // The shift to the editing component appears as a loss of focus.
                // The shift to a popup menu appears as a "temporary" loss of focus.

                // However, in some cases a shift to the title icon on a graph node also gets flagged as temporary.
                // Specifically, select a node in tree A, then select a node in tree B, then select title A.
                // When the code calls title.requestFocusInWindow(), focus goes to tree A for a moment, then on to title A.
                // The loss of focus is reported to tree B as temporary, so tree B never removes its selection.
                // TODO: determine the cause for this unexpected extra focus transition

                // To hack around this, explicitly ignore isTemporary() if the other component is a graph node title or equation tree.
                boolean temporary = e.isTemporary ();
                if (temporary)
                {
                    Component other = e.getOppositeComponent ();
                    temporary = ! (other instanceof GraphNode.TitleRenderer)  &&  ! (other instanceof JTree);
                }

                if (! temporary  &&  ! tree.isEditing ()) yieldFocus ();
            }
        });

        tree.addTreeSelectionListener (new TreeSelectionListener ()
        {
            public void valueChanged (TreeSelectionEvent e)
            {
                // Update highlights based on currently selected node.
                if (root == null) return;
                NodeBase n = null;
                TreePath path = e.getNewLeadSelectionPath ();
                if (path != null)
                {
                    n = (NodeBase) path.getLastPathComponent ();
                    if (! (n instanceof NodePart  ||  n instanceof NodeVariable)) n = null;
                }

                if (n == null  ||  n.toString ().isEmpty ())  // Remove old highlights.
                {
                    if (container.view == PanelEquations.NODE)
                    {
                        // If another graph is receiving focus, then it has already updated its
                        // selection before we receive this selection event. In that case, we
                        // don't want to clear the highlights, because that will undo its work.
                        NodeBase lastTarget = container.panelEquationGraph.lastHighlightTarget;
                        if (lastTarget == null  ||  lastTarget.isNodeAncestor (root))
                        {
                            container.panelEquationGraph.updateHighlights (null);
                        }
                    }
                    else
                    {
                        updateHighlights (root, null);
                    }
                }
                else
                {
                    if (container.view == PanelEquations.NODE) container.panelEquationGraph.updateHighlights (n);
                    else                                       updateHighlights (root, n);
                }
            }
        });

        setViewportView (tree);
    }

    public Dimension getMinimumSize ()
    {
        TreePath path = tree.getPathForRow (0);
        if (path == null) return super.getMinimumSize ();
        Dimension result = tree.getPathBounds (path).getSize ();
        // Add some compensation, so scroll bars don't overwrite part name.
        result.height += getHorizontalScrollBar ().getPreferredSize ().height;
        result.width  += getVerticalScrollBar   ().getPreferredSize ().width;
        return result;
    }

    public Dimension getPreferredSize ()
    {
        // Set row count so tree will request a reasonable size during processing of super.getPreferredSize()
        int rc = tree.getRowCount ();
        rc = Math.min (rc, 20);  // The default from openjdk source code for JTree
        rc = Math.max (rc, 6);   // An arbitrary lower limit
        tree.setVisibleRowCount (rc);

        Dimension result = super.getPreferredSize ();

        // If tree is empty, it could collapse to very small width. Keep this from getting smaller than cell editor preferred size.
        double em = container.panelEquationGraph.graphPanel.em;
        Insets insets = tree.getInsets ();
        result.width = Math.max (result.width, (int) Math.ceil (8 * em) + EquationTreeCellEditor.offsetPerLevel + insets.left + insets.right);

        return result;
    }

    /**
        Resize graph node based on any changes in our preferred size.
    **/
    public void animate ()
    {
        if (root == null) return;
        if (root.graph != null) root.graph.animate ();
        else if (root == container.part  &&  container.view == PanelEquations.NODE) container.panelParent.animate ();
    }

    /**
        @param part Should always be non-null. To clear the tree, call clear() rather than setting null here.
    **/
    public void loadPart (NodePart part)
    {
        tree.setEditable (! container.locked);
        if (part == root) return;
        if (root != null) root.pet = null;

        root = part;
        root.pet = this;
        updateHighlights (root, null);
        model.setRoot (root);  // triggers repaint
    }

    public void clear ()
    {
        if (container.active == this) container.active = null;
        if (root == null) return;
        root.pet = null;
        root = null;
        model.setRoot (null);
    }

    public void updateLock ()
    {
        tree.setEditable (! container.locked);
    }

    public StoredPath saveFocus (StoredPath previous)
    {
        // Save tree state, but only if it's better than the previously-saved state.
        if (previous == null  ||  tree.getLeadSelectionPath () != null) return new StoredPath (tree);
        return previous;
    }

    public void yieldFocus ()
    {
        tree.stopEditing ();
        if (root == null) return;
        FocusCacheEntry fce = container.createFocus (root);
        fce.sp = saveFocus (fce.sp);
        tree.clearSelection ();

        if (container.view == PanelEquations.NODE)
        {
            // Auto-close graph node when it loses focus, if it was auto-opened.
            if (root.graph != null)
            {
                boolean open = root.source.getBoolean ("$meta", "gui", "bounds", "open");
                if (! open) root.graph.setOpen (false);
            }
            else if (root == container.part)
            {
                boolean open = root.source.getBoolean ("$meta", "gui", "bounds", "parent");
                if (! open) container.panelParent.setOpen (false);
            }
        }
        else
        {
            if (root == container.part) container.setSelected (false);
        }
    }

    public void takeFocus ()
    {
        if (tree.isFocusOwner ()) restoreFocus ();
        else                      tree.requestFocusInWindow ();  // Triggers focus listener, which calls restoreFocus()
    }

    /**
        Subroutine of takeFocus(). Called either directly by takeFocus(), or indirectly via focus listener on tree.
    **/
    public void restoreFocus ()
    {
        container.active = this;
        if (root != null)
        {
            if (root.graph != null) root.graph.restoreFocus ();  // Raise graph node to top of z-order.
            if (container.view != PanelEquations.NODE)
            {
                if (root.graph != null) root.graph.setSelected (true);
                else if (root == container.part) container.setSelected (true);
            }
            if (tree.getSelectionCount () == 0)
            {
                FocusCacheEntry fce = container.createFocus (root);
                if (fce.sp != null) fce.sp.restore (tree, true);
                if (tree.getSelectionCount () == 0) tree.setSelectionRow (0);  // First-time display
            }
            TreePath selected = tree.getLeadSelectionPath ();
            if (selected != null) tree.scrollPathToVisible (selected);
        }
    }

    /**
        Assuming this tree has focus, moves focus back to title.
    **/
    public void switchFocus ()
    {
        if (root.graph != null)
        {
            root.graph.setSelected (false);  // Release temporary selected state that was used to mark the part showing up in the tree.
            root.graph.switchFocus (true, false);
        }
        else if (root == container.part)
        {
            container.setSelected (false);  // ditto
            container.switchFocus (true, false);
        }
    }

    public void updateFilterLevel ()
    {
        if (root == null) return;
        root.filter ();
        StoredPath sp = new StoredPath (tree);
        model.reload (root);
        animate ();
        sp.restore (tree, true);
    }

    public NodeBase getSelected ()
    {
        NodeBase result = null;
        TreePath path = tree.getLeadSelectionPath ();
        if (path != null) result = (NodeBase) path.getLastPathComponent ();
        if (result != null) return result;
        return root;
    }

    public void addAtSelected (String type)
    {
        if (container.locked) return;
        NodeBase selected = getSelected ();
        if (selected == null) return;  // empty tree (should only occur in tree view); could create new model here, but better to do that from search list

        // Don't allow insertion of types that will be invisible at current filter level.
        if (! FilteredTreeModel.showLocal  &&  ! FilteredTreeModel.showParam) return;  // Can't show any user-defined values, so give up.
        if (! FilteredTreeModel.showLocal  &&    FilteredTreeModel.showParam)   // Can only show parameters.
        {
            switch (type)
            {
                case "Annotation":
                case "Reference":
                    return;
                case "Equation":
                    type = "Variable";
            }
        }

        NodeBase editMe = selected.add (type, tree, null, null);
        if (editMe != null)
        {
            if (editMe instanceof NodePart)
            {
                GraphNode gn = ((NodePart) editMe).graph;
                if (gn != null)  // Newly-created part is root of a tree in a new graph node.
                {
                    gn.title.startEditing ();
                    return;
                }
            }

            TreePath path = new TreePath (editMe.getPath ());
            tree.scrollPathToVisible (path);
            tree.setSelectionPath (path);
            tree.startEditingAtPath (path);
        }
    }

    public void deleteSelected ()
    {
        if (container.locked) return;

        // Collect and convert selection.
        TreePath[] paths = tree.getSelectionPaths ();
        if (paths == null) return;
        TreePath leadPath = tree.getLeadSelectionPath ();

        List<NodeBase> selection = new ArrayList<NodeBase> ();
        for (TreePath path : paths) selection.add ((NodeBase) path.getLastPathComponent ());

        NodeBase leadNode = null;
        if (leadPath != null) leadNode = (NodeBase) leadPath.getLastPathComponent ();

        // Pre-process selection to enforce constraints.

        //   Detect if all equations under a variable are deleted. If so, ensure that the variable is marked for deletion.
        Map<NodeVariable,List<NodeEquation>> equations = new HashMap<NodeVariable,List<NodeEquation>> ();
        for (NodeBase n : selection)
        {
            if (! (n instanceof NodeEquation)) continue;
            NodeVariable v = (NodeVariable) n.getParent ();
            List<NodeEquation> e = equations.get (v);
            if (e == null)
            {
                e = new ArrayList<NodeEquation> ();
                equations.put (v, e);
            }
            e.add ((NodeEquation) n);
        }
        for (NodeVariable v : equations.keySet ())
        {
            int count = 0;
            Enumeration<?> children = v.childrenFiltered ();
            while (children.hasMoreElements ())
            {
                if (children.nextElement () instanceof NodeEquation) count++;
            }
            if (count == equations.get (v).size ()  &&  ! selection.contains (v))
            {
                // Delete containing variable instead. Equations will be removed from selection later.
                selection.add (v);
                tree.addSelectionPath (new TreePath (v.getPath ()));  // Needs to actually be in tree selection for parent detection.
            }
        }

        //   Eliminate any selected node that is beneath some other selected node.
        NodeInherit inherit  = null;
        List<NodeBase> filteredSelection = new ArrayList<NodeBase> ();
        for (NodeBase n : selection)
        {
            if (n.hasSelectedAncestor (tree)) continue;
            filteredSelection.add (n);
            if (n instanceof NodeInherit)
            {
                if (inherit == null  ||  inherit.getLevel () > n.getLevel ()) inherit = (NodeInherit) n;
            }
        }
        selection = filteredSelection;
        if (inherit != null)  // Since deleting $inherit rebuilds whole tree, don't allow any other deletes.
        {
            selection.clear ();
            selection.add (inherit);
        }
        else if (leadNode != null)
        {
            if (! selection.contains (leadNode)  &&  leadNode instanceof NodeEquation) leadNode = (NodeBase) leadNode.getParent ();
            if (selection.contains (leadNode))
            {
                // Ensure that leadNode is the final edit.
                selection.remove (leadNode);
                selection.add (leadNode);
            }
            else
            {
                leadNode = null;
            }
        }

        // Create transaction
        UndoManager um = MainFrame.undoManager;
        CompoundEditView compound = null;
        int count = selection.size ();
        boolean multi = count > 1;
        if (multi) um.addEdit (compound = new CompoundEditView (CompoundEditView.CLEAR_TREE));
        int i = 0;
        for (NodeBase n : selection)
        {
            i++;
            Undoable u = n.makeDelete (false);
            if (u == null) continue;
            if (u instanceof UndoableView)
            {
                UndoableView uv = (UndoableView) u;
                uv.setMulti (multi);
                if (multi  &&  i == count) uv.setMultiLast (true);
            }
            if (multi  &&  i == count) compound.leadPath = n.getKeyPath ();
            um.apply (u);
        }
        um.endCompoundEdit ();
    }

    public void watchSelected (boolean clearOthers)
    {
        if (container.locked) return;

        TreePath[] paths = tree.getSelectionPaths ();
        if (paths == null) return;
        TreePath leadPath = tree.getLeadSelectionPath ();
        NodeVariable leadVariable = null;
        List<NodeVariable> variables = new ArrayList<NodeVariable> (paths.length);
        for (TreePath path : paths)
        {
            NodeBase n = (NodeBase) path.getLastPathComponent ();
            if (! (n instanceof NodeVariable)) n = (NodeBase) n.getParent ();  // Make one attempt to walk up tree, in case an equation or metadata item is selected under a variable.
            if (! (n instanceof NodeVariable)) continue;
            NodeVariable v = (NodeVariable) n;
            if (v.isBinding) continue;
            variables.add (v);
            if (path.equals (leadPath)) leadVariable = v;
        }

        if (clearOthers) findExistingWatches (container.root, variables);

        UndoManager um = MainFrame.undoManager;
        CompoundEditView compound = null;
        boolean multi = variables.size () > 1;
        if (multi) um.addEdit (compound = new CompoundEditView (CompoundEditView.CLEAR_TREE));
        for (NodeVariable v : variables)
        {
            // Toggle watch on the selected variable
            boolean selectVariable =  multi  ||  tree.isCollapsed (new TreePath (v.getPath ()));
            MPart watch = (MPart) v.source.child ("$meta", "watch");
            UndoableView u;
            if (watch == null)  // Currently off, so turn it on.
            {
                u = new AddAnnotation (v, v.getChildCount (), new MVolatile ("", "watch"));
                ((AddAnnotation) u).selectVariable = selectVariable;
            }
            else
            {
                NodeAnnotation watchNode = (NodeAnnotation) v.child ("watch");
                if (watch.isInherited ())
                {
                    // Override and toggle
                    u = new ChangeAnnotation (watchNode, "watch", watch.getFlag () ? "0" : "1");
                    ((ChangeAnnotation) u).selectVariable = selectVariable;
                }
                else  // local only
                {
                    if (watch.getFlag ())  // Currently on, so turn it off.
                    {
                        u = new DeleteAnnotation (watchNode, false);
                        ((DeleteAnnotation) u).selectVariable = selectVariable;
                    }
                    else  // Currently off ("0"), so turn it on. This case is only necessary because the user could directly define watch.
                    {
                        u = new ChangeAnnotation (watchNode, "watch", "");
                        ((ChangeAnnotation) u).selectVariable = selectVariable;
                    }
                }
            }
            if (multi) compound.addEdit (u);
            else       um.apply (u);
        }
        if (multi)
        {
            um.endCompoundEdit ();
            if (leadVariable != null) compound.leadPath = leadVariable.getKeyPath ();
            compound.redo ();
        }
    }

    public void findExistingWatches (NodePart part, List<NodeVariable> variables)
    {
        Enumeration<?> children = part.children ();
        while (children.hasMoreElements ())
        {
            Object o = children.nextElement ();
            if (o instanceof NodePart)
            {
                findExistingWatches ((NodePart) o, variables);
            }
            else if (o instanceof NodeVariable)
            {
                NodeVariable v = (NodeVariable) o;
                if (v.source.getFlag ("$meta", "watch")  &&  ! variables.contains (v)) variables.add (v);
            }
        }
    }

    public void moveSelected (int direction)
    {
        if (container.locked) return;
        TreePath path = tree.getLeadSelectionPath ();
        if (path == null) return;

        NodeBase nodeBefore = (NodeBase) path.getLastPathComponent ();
        NodeBase parent     = (NodeBase) nodeBefore.getParent ();
        if (parent instanceof NodePart)  // Only parts support $meta.gui.order
        {
            // First check if we can move in the filtered (visible) list.
            int indexBefore = model.getIndexOfChild (parent, nodeBefore);
            int indexAfter  = indexBefore + direction;
            if (indexAfter >= 0  &&  indexAfter < model.getChildCount (parent))
            {
                // Then convert to unfiltered indices.
                NodeBase nodeAfter = (NodeBase) model.getChild (parent, indexAfter);
                indexBefore = parent.getIndex (nodeBefore);
                indexAfter  = parent.getIndex (nodeAfter);
                MainFrame.undoManager.apply (new ChangeOrder ((NodePart) parent, indexBefore, indexAfter));
            }
        }
    }

    public void outsourceSelected ()
    {
        NodeBase n = getSelected ();
        if (n == null) return;
        while (! (n instanceof NodePart)) n = (NodeBase) n.getParent ();
        outsource ((NodePart) n);
    }

    /**
        Does two things:
        1) Creates a new top-level model with contents of the given part.
        2) Modifies the part to inherit from the new model.
    **/
    public static void outsource (NodePart part)
    {
        if (! part.source.isFromTopDocument ()) return;

        // Prepare data
        MVolatile data = new MVolatile ();
        data.merge (part.source);  // This takes all data, not just visible nodes.
        data.clear ("$meta", "gui", "bounds");

        // Create transaction
        UndoManager um = MainFrame.undoManager;
        um.addEdit (new CompoundEdit ());
        AddDoc a = new AddDoc (part.source.key (), data);
        a.setSilent ();  // Necessary so that focus stays on part. Outsource ctor examines focus.
        um.apply (a);
        um.apply (new Outsource (part, a.name));
        um.endCompoundEdit ();
    }

    public void updateVisibility (TreeNode path[])
    {
        updateVisibility (this, path, -2, true);
    }

    public void updateVisibility (TreeNode path[], int index)
    {
        updateVisibility (this, path, index, true);
    }

    public void updateVisibility (TreeNode path[], int index, boolean setSelection)
    {
        updateVisibility (this, path, index, setSelection);
    }

    /**
        Ensure that the tree down to the changed node is displayed with correct visibility and override coloring.
        @param path Every node from root to changed node, including changed node itself.
        The trailing nodes are allowed to be disconnected from root in the filtered view of the model,
        and they are allowed to be deleted nodes. Note: deleted nodes will have null parents.
        Deleted nodes should already be removed from tree by the caller, with proper notification.
        @param index Position of the last node in its parent node. Only used if the last node has been deleted.
        A value of -1 causes selection to shift up to the parent.
        A value of -2 causes index to be derived from given path.
        @param setSelection Highlights the closest tree node to the given path. Only does something if tree is non-null. 
    **/
    public static void updateVisibility (PanelEquationTree pet, TreeNode path[], int index, boolean setSelection)
    {
        // Calculate index, if requested
        if (index == -2)
        {
            if (path.length < 2)
            {
                index = -1;
            }
            else
            {
                NodeBase c = (NodeBase) path[path.length - 1];
                NodeBase p = (NodeBase) path[path.length - 2];
                index = p.getIndexFiltered (c);
            }
        }

        // Prepare list of indices for final selection
        int[] selectionIndices = new int[path.length];
        for (int i = 1; i < path.length; i++)
        {
            NodeBase p = (NodeBase) path[i-1];
            NodeBase c = (NodeBase) path[i];
            selectionIndices[i] = FilteredTreeModel.getIndexOfChildStatic (p, c);  // Could be -1, if c has already been deleted.
        }

        // Adjust visibility
        int inserted = path.length;
        int removed  = path.length;
        int removedIndex = -1;
        for (int i = path.length - 1; i > 0; i--)
        {
            NodeBase p = (NodeBase) path[i-1];
            NodeBase c = (NodeBase) path[i];
            if (c.getParent () == null) continue;  // skip deleted nodes
            int filteredIndex = FilteredTreeModel.getIndexOfChildStatic (p, c);
            boolean filteredOut = filteredIndex < 0;
            if (c.visible ())
            {
                if (filteredOut)
                {
                    p.unhide (c, null);  // silently adjust the filtering
                    inserted = i; // promise to notify model
                }
            }
            else
            {
                if (! filteredOut)
                {
                    p.hide (c, null);
                    removed = i;
                    removedIndex = filteredIndex;
                }
            }
        }

        if (pet == null) return;  // Everything below this line has to do with updating tree view.
        PanelEquations pe = PanelModel.instance.panelEquations;
        FilteredTreeModel model = (FilteredTreeModel) pet.tree.getModel ();

        // update color to indicate override state
        int lastChange = Math.min (inserted, removed);
        for (int i = 1; i < lastChange; i++)
        {
            // Since it is hard to measure current color, just assume everything needs updating.
            NodeBase c = (NodeBase) path[i];
            if (c.getParent () == null) continue;
            model.nodeChanged (c);
        }
        //   also update title, if it is separate
        if (pet.root.graph != null) pet.root.graph.title.updateSelected (); // Reconfigures the cell renderer, which will also include color change.
        pe.breadcrumbRenderer.updateSelected ();
        pe.panelEquationGraph.repaint ();  // Force update of overall border. Everything gets repainted, so a bit inefficient.

        if (lastChange < path.length)
        {
            NodeBase p = (NodeBase) path[lastChange-1];
            NodeBase c = (NodeBase) path[lastChange];
            int[] childIndices = new int[1];
            if (inserted < removed)
            {
                childIndices[0] = p.getIndexFiltered (c);
                model.nodesWereInserted (p, childIndices);
            }
            else
            {
                childIndices[0] = removedIndex;
                Object[] childObjects = new Object[1];
                childObjects[0] = c;
                model.nodesWereRemoved (p, childIndices, childObjects);
            }
        }

        // select last visible node
        int i = 1;
        for (; i < path.length; i++)
        {
            NodeBase c = (NodeBase) path[i];
            if (c.getParent () == null) break;
            if (! c.visible ()) break;
        }
        i--;  // Choose the last good node
        NodeBase c = (NodeBase) path[i];
        if (i == path.length - 2)
        {
            index = Math.min (index, model.getChildCount (c) - 1);
            if (index >= 0) c = (NodeBase) model.getChild (c, index);
        }
        else if (i < path.length - 2)
        {
            int childIndex = Math.min (selectionIndices[i+1], model.getChildCount (c) - 1);
            if (childIndex >= 0) c = (NodeBase) model.getChild (c, childIndex);
        }

        TreePath selectedPath = new TreePath (c.getPath ());
        GraphNode gn = pet.root.graph;
        if (setSelection  &&  c != pet.root  &&  pe.view == PanelEquations.NODE)
        {
            // Ensure that tree is visible.
            if (gn == null) pe.panelParent.setOpen (true);
            else            gn.setOpen (true);
        }
        if (lastChange == path.length)
        {
            boolean expanded = pet.tree.isExpanded (selectedPath);
            model.nodeStructureChanged (c);  // Should this be more targeted?
            if (c != pet.root  &&  expanded) pet.tree.expandPath (selectedPath);
        }
        if (setSelection)
        {
            if (c == pet.root)
            {
                if (gn == null) pe.switchFocus (true, false);
                else            gn.switchFocus (true, false);
            }
            else
            {
                pet.tree.setSelectionPath (selectedPath);
                FocusCacheEntry fce = pe.createFocus (pet.root);
                fce.titleFocused = false;  // so pet.takeFocus() does not claw focus onto title
                if (fce.sp != null) fce.sp.updateSelection (c);  // Ensure that current name (in case of name change) is set as desired focus.
                pet.takeFocus ();
            }
        }
    }

    public void updateOrder (TreeNode path[])
    {
        updateOrder (tree, path);
    }

    /**
        Records the current order of nodes in "gui.order", provided that metadata field exists.
        Otherwise, we assume the user doesn't care.
        @param path To the node that changed (added, deleted, moved). In general, this node's
        parent will be the part that is tracking the order of its children.
    **/
    public static void updateOrder (JTree tree, TreeNode path[])
    {
        NodePart parent = null;
        for (int i = path.length - 2; i >= 0; i--)
        {
            if (path[i] instanceof NodePart)
            {
                parent = (NodePart) path[i];
                break;
            }
        }
        if (parent == null) return;  // This should never happen, because root of tree is a NodePart.

        // Find $meta/gui.order for the currently selected node. If it exists, update it.
        // Note that this is a modified version of moveSelected() which does not actually move
        // anything, and which only modifies an existing $meta/gui.order, not create a new one.
        NodeAnnotations metadataNode = null;
        String order = null;
        Enumeration<?> i = parent.children ();
        while (i.hasMoreElements ())
        {
            NodeBase c = (NodeBase) i.nextElement ();
            String key = c.source.key ();
            if (order == null) order = key;
            else               order = order + "," + key;
            if (key.equals ("$meta")) metadataNode = (NodeAnnotations) c;
        }
        if (metadataNode == null) return;

        NodeBase a = AddAnnotation.findExact (metadataNode, false, "gui", "order");
        if (a != null)  // found
        {
            MNode m = ((NodeAnnotation) a).folded;
            m.set (order);  // Value is in last column, so no need to invalidate columns.
            if (tree != null)
            {
                a.setUserObject ();  // Cause gui.order to update it's text.
                ((FilteredTreeModel) tree.getModel ()).nodeChanged (a);
            }
        }
    }

    // For now, this is fast enough to run on EDT, but larger models could bog down the UI.
    // TODO: run this on a separate thread, if the need arises
    public void updateHighlights (NodeBase container, NodeBase target)
    {
        String name = null;
        if (target != null)
        {
            name = target.source.key ();
            while (name.startsWith ("$up.")) name = name.substring (4);
            name = Variable.stripContextPrefix (name);
        }
        updateHighlights (container, target, name);
    }

    public void updateHighlights (NodeBase container, NodeBase target, String name)
    {
        int count = container.getChildCount ();
        for (int i = 0; i < count; i++)
        {
            NodeBase n = (NodeBase) container.getChildAt (i);
            if (! n.visible ()) continue;

            boolean needsRepaint = false;
            if (n instanceof NodePart)
            {
                updateHighlights (n, target, name);
            }
            else if (n instanceof NodeVariable)
            {
                needsRepaint = ((NodeVariable) n).findHighlights (target, name);
                updateHighlights (n, target, name);
            }
            else if (n instanceof NodeEquation)
            {
                needsRepaint = ((NodeEquation) n).findHighlights (target, name);
            }

            if (! needsRepaint) continue;
            TreePath path = new TreePath (n.getPath ());
            Rectangle pathBounds = tree.getPathBounds (path);
            if (pathBounds != null) tree.repaint (pathBounds);
        }
    }
}

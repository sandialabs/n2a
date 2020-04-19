/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.eq.PanelEquations.FocusCacheEntry;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotation;
import gov.sandia.n2a.ui.eq.tree.NodeAnnotations;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodeEquation;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;
import gov.sandia.n2a.ui.eq.undo.AddAnnotation;
import gov.sandia.n2a.ui.eq.undo.ChangeAnnotation;
import gov.sandia.n2a.ui.eq.undo.ChangeOrder;
import gov.sandia.n2a.ui.eq.undo.DeleteAnnotation;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
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

    public PanelEquationTree (PanelEquations container)
    {
        this.container = container;

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
                if (! (node instanceof NodeVariable)) return null;

                MPart source = node.source;
                String notes = source.get ("$metadata", "notes");
                if (notes.isEmpty ()) notes = source.get ("$metadata", "note");
                if (notes.isEmpty ()) return null;

                int paneWidth = PanelEquationTree.this.getWidth ();
                FontMetrics fm = getFontMetrics (getFont ());
                int notesWidth = fm.stringWidth (notes);
                if (notesWidth < paneWidth) return notes;

                paneWidth = Math.max (300, paneWidth);
                notes = notes.replace ("\n", "<br>");
                return "<html><p  width=\"" + paneWidth + "\">" + notes + "</p></html>";
            }

            public void updateUI ()
            {
                if (root != null) root.filter (FilteredTreeModel.filterLevel);  // Force (later) update to tab stops, in case font has changed. This can also affect cell size.
                super.updateUI ();  // Causes tree to poll for cell sizes.
            }
        };

        tree.setRootVisible (false);
        tree.setShowsRootHandles (true);
        tree.setExpandsSelectedPaths (true);
        tree.setScrollsOnExpand (true);
        tree.getSelectionModel ().setSelectionMode (TreeSelectionModel.SINGLE_TREE_SELECTION);  // No multiple selection. It only makes deletes and moves more complicated.
        tree.setEditable (! container.locked);
        tree.setInvokesStopCellEditing (true);  // auto-save current edits, as much as possible
        tree.setDragEnabled (true);
        tree.setToggleClickCount (0);  // Disable expand/collapse on double-click
        ToolTipManager.sharedInstance ().registerComponent (tree);
        tree.setTransferHandler (container.transferHandler);
        tree.setCellRenderer (container.renderer);
        tree.setCellEditor (container.editor);
        tree.addTreeSelectionListener (container.editor);

        setBorder (BorderFactory.createEmptyBorder ());

        InputMap inputMap = tree.getInputMap ();
        inputMap.put (KeyStroke.getKeyStroke ("ctrl UP"),      "switchFocus");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl LEFT"),    "switchFocus");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl DOWN"),    "nothing");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl RIGHT"),   "nothing");
        inputMap.put (KeyStroke.getKeyStroke ("shift UP"),     "moveUp");
        inputMap.put (KeyStroke.getKeyStroke ("shift DOWN"),   "moveDown");
        inputMap.put (KeyStroke.getKeyStroke ("INSERT"),       "add");
        inputMap.put (KeyStroke.getKeyStroke ("DELETE"),       "delete");
        inputMap.put (KeyStroke.getKeyStroke ("BACK_SPACE"),   "delete");
        inputMap.put (KeyStroke.getKeyStroke ("ENTER"),        "startEditing");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl ENTER"),   "startEditing");
        inputMap.put (KeyStroke.getKeyStroke ("shift ctrl D"), "drillUp");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl D"),       "drillDown");
        inputMap.put (KeyStroke.getKeyStroke ("ctrl W"),       "watch");

        ActionMap actionMap = tree.getActionMap ();
        Action selectPrevious = actionMap.get ("selectPrevious");
        Action selectParent   = actionMap.get ("selectParent");
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
                TreePath path = tree.getSelectionPath ();
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
                TreePath path = tree.getSelectionPath ();
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
                watchSelected ();
            }
        });

        MouseAdapter mouseAdapter = new MouseAdapter ()
        {
            public void mouseDragged (MouseEvent e)
            {
                // For some reason, a DnD gesture does not cause JTree to take focus.
                // This is a hack to grab focus in that case. At the start of a drag,
                // we receive a small handful of mouseDragged() messages. Perhaps
                // they stop because DnD takes over. In any case, this is sufficient to detect
                // the start of DnD and grab focus.
                if (! tree.isFocusOwner ()) tree.requestFocusInWindow ();
            }

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

            public void mouseClicked (MouseEvent e)
            {
                int x = e.getX ();
                int y = e.getY ();
                int clicks = e.getClickCount ();

                TreePath path = tree.getClosestPathForLocation (x, y);
                if (path != null)
                {
                    // Constrain click to be close to node content, but allow some slack.
                    Rectangle r = tree.getPathBounds (path);
                    r.width += 20;
                    r.width = Math.max (100, r.width);
                    if (! r.contains (x, y)) path = null;
                }

                if (SwingUtilities.isLeftMouseButton (e))
                {
                    if (clicks == 1)
                    {
                        switchToTree ();
                    }
                    else if (clicks == 2)  // Drill down on parts, or edit any other node type.
                    {
                        NodePart part = null;
                        if (path != null)
                        {
                            Object temp = path.getLastPathComponent ();
                            if (temp instanceof NodePart)
                            {
                                part = (NodePart) temp;
                                // and drill down
                            }
                            else  // any other node type
                            {
                                if (container.locked  &&  ! ((NodeBase) temp).showMultiLine ()) return;
                                tree.setSelectionPath (path);
                                tree.setEditable (true);
                                tree.startEditingAtPath (path);
                                return;
                            }
                        }
                        if (part == null) part = root;  // Drill down without selecting a specific node.
                        container.drill (part);
                    }
                }
                else if (SwingUtilities.isRightMouseButton (e))
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
            }
        };
        tree.addMouseListener (mouseAdapter);
        tree.addMouseMotionListener (mouseAdapter);

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
                // Update highlights based on currently selected variable

                if (root == null) return;
                NodeVariable v = null;
                TreePath path = e.getNewLeadSelectionPath ();
                Object o = null;
                if (path != null) o = path.getLastPathComponent ();
                if      (o instanceof NodeVariable) v = (NodeVariable) o;
                else if (o instanceof NodeEquation) v = (NodeVariable) ((NodeBase) o).getParent ();

                if (v == null  ||  v.toString ().isEmpty ()) updateHighlights (root, "");  // Remove old highlights.
                else                                         updateHighlights (root, v.source.key ());
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
        Insets insets = tree.getInsets ();
        result.width = Math.max (result.width, 100 + EquationTreeCellEditor.offsetPerLevel + insets.left + insets.right);

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
        if (previous == null  ||  tree.getSelectionPath () != null) return new StoredPath (tree);
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
                boolean open = root.source.getBoolean ("$metadata", "gui", "bounds", "open");
                if (! open) root.graph.setOpen (false);
            }
            else if (root == container.part)
            {
                boolean open = root.source.getBoolean ("$metadata", "gui", "bounds", "parent");
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
        root.filter (FilteredTreeModel.filterLevel);
        StoredPath sp = new StoredPath (tree);
        model.reload (root);
        animate ();
        sp.restore (tree, true);
    }

    public NodeBase getSelected ()
    {
        NodeBase result = null;
        TreePath path = tree.getSelectionPath ();
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
        if (FilteredTreeModel.filterLevel == FilteredTreeModel.PARAM)
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
        NodeBase selected = getSelected ();
        if (selected != null) selected.delete (tree, false);
    }

    public void watchSelected ()
    {
        if (container.locked) return;
        NodeBase selected = getSelected ();
        if (! (selected instanceof NodeVariable)) selected = (NodeBase) selected.getParent ();  // Make one attempt to walk up tree, in case an equation or metadata item is selected under a variable.
        if (! (selected instanceof NodeVariable)) return;
        if (((NodeVariable) selected).isBinding) return;

        // Toggle watch on the selected variable
        MPart watch = (MPart) selected.source.child ("$metadata", "watch");
        if (watch != null)
        {
            NodeAnnotation watchNode = (NodeAnnotation) selected.child ("watch");
            if (watch.isFromTopDocument ())  // currently on, so turn it off
            {
                DeleteAnnotation d = new DeleteAnnotation (watchNode, false);
                d.setSelection = tree.isExpanded (new TreePath (selected.getPath ()));
                MainFrame.instance.undoManager.add (d);
            }
            else  // Currently off, because it is not explicitly set in this document. Turn it on by overriding in locally. 
            {
                ChangeAnnotation c = new ChangeAnnotation (watchNode, "watch", "1");
                MainFrame.instance.undoManager.add (c);
            }
        }
        else  // currently off, so turn it on
        {
            AddAnnotation a = new AddAnnotation (selected, selected.getChildCount (), new MVolatile ("", "watch"));
            a.setSelection = tree.isExpanded (new TreePath (selected.getPath ()));
            MainFrame.instance.undoManager.add (a);
        }
    }

    public void moveSelected (int direction)
    {
        if (container.locked) return;
        TreePath path = tree.getSelectionPath ();
        if (path == null) return;

        NodeBase nodeBefore = (NodeBase) path.getLastPathComponent ();
        NodeBase parent     = (NodeBase) nodeBefore.getParent ();
        if (parent instanceof NodePart)  // Only parts support $metadata.gui.order
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
                MainFrame.instance.undoManager.add (new ChangeOrder ((NodePart) parent, indexBefore, indexAfter));
            }
        }
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
        A value of -2 cause index to be derived from given path.
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
            if (c.visible (FilteredTreeModel.filterLevel))
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
            if (! c.visible (FilteredTreeModel.filterLevel)) break;
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

        // Find $metadata/gui.order for the currently selected node. If it exists, update it.
        // Note that this is a modified version of moveSelected() which does not actually move
        // anything, and which only modifies an existing $metadata/gui.order, not create a new one.
        NodeAnnotations metadataNode = null;
        String order = null;
        Enumeration<?> i = parent.children ();
        while (i.hasMoreElements ())
        {
            NodeBase c = (NodeBase) i.nextElement ();
            String key = c.source.key ();
            if (order == null) order = key;
            else               order = order + "," + key;
            if (key.equals ("$metadata")) metadataNode = (NodeAnnotations) c;
        }
        if (metadataNode == null) return;

        NodeBase a = AddAnnotation.resolve (metadataNode, "gui.order");
        if (a != metadataNode)  // found
        {
            MNode m = ((NodeAnnotation) a).folded;
            if (m.key ().equals ("order")  &&  m.parent ().key ().equals ("gui"))  // is actually "gui.order". This check is necessary to avoid overwriting a pre-existing node folded under "gui" (for example, gui.bounds).
            {
                m.set (order);  // Value is in last column, so no need to invalidate columns.
                if (tree != null)
                {
                    a.setUserObject ();  // Cause gui.order to update it's text.
                    ((FilteredTreeModel) tree.getModel ()).nodeChanged (a);
                }
            }
        }
    }

    // For now, this is fast enough to run on EDT, but larger models could bog down the UI.
    // TODO: run this on a separate thread, if the need arises
    public void updateHighlights (NodeBase node, String name)
    {
        int count = node.getChildCount ();
        for (int i = 0; i < count; i++)
        {
            NodeBase n = (NodeBase) node.getChildAt (i);
            if (! n.visible (FilteredTreeModel.filterLevel)) continue;

            boolean needsRepaint = false;
            if (n instanceof NodePart)
            {
                updateHighlights ((NodePart) n, name);
            }
            else if (n instanceof NodeVariable)
            {
                needsRepaint = ((NodeVariable) n).findHighlights (name);
            }
            else if (n instanceof NodeEquation)
            {
                needsRepaint = ((NodeEquation) n).findHighlights (name);
            }

            if (! needsRepaint) continue;
            TreePath path = new TreePath (n.getPath ());
            Rectangle pathBounds = tree.getPathBounds (path);
            if (pathBounds != null) tree.repaint (pathBounds);
        }
    }
}

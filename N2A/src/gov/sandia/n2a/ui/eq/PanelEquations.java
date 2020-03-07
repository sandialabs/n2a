/*
Copyright 2019-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.MouseInputAdapter;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.tree.TreePath;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPersistent;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Exporter;
import gov.sandia.n2a.plugins.extpoints.Importer;
import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.MainTabbedPane;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.undo.AddDoc;
import gov.sandia.n2a.ui.eq.undo.ChangeGUI;
import gov.sandia.n2a.ui.eq.undo.ChangeOrder;
import gov.sandia.n2a.ui.eq.undo.Outsource;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.jobs.PanelRun;
import sun.swing.SwingUtilities2;

@SuppressWarnings("serial")
public class PanelEquations extends JPanel
{
    public MNode    record;
    public NodePart root;
    public NodePart part;     // The node that contains the current graph. Can be root or a deeper node (via drill-down).
    public boolean  locked;

    // Where does the equation tree for a node appear?
    public static final int NODE   = 0;  // In the node itself
    public static final int SIDE   = 1;  // In a panel to the right side
    public static final int BOTTOM = 2;  // In a panel at the bottom
    public int view = AppData.state.getOrDefault (NODE, "PanelModel", "view");

    protected JSplitPane               split;
    protected PanelGraph               panelGraph;
    protected JPanel                   panelBreadcrumb;
    public    BreadcrumbRenderer       breadcrumbRenderer;
    protected boolean                  titleFocused           = true;
    protected boolean                  parentSelected;
    public    PanelEquationGraph       panelEquationGraph;
    public    GraphParent              panelParent;
    public    PanelEquationTree        panelEquationTree;  // For property-panel style display, this is the single tree for editing.
    protected PanelEquationTree        active;             // Tree which most recently received focus. Could be panelEquationTree or a GraphNode.panelEquations.
    protected TransferHandler          transferHandler;
    protected EquationTreeCellRenderer renderer               = new EquationTreeCellRenderer ();
    protected EquationTreeCellEditor   editor                 = new EquationTreeCellEditor (renderer);
    protected MVolatile                focusCache             = new MVolatile ();

    // Controls
    protected JButton buttonAddModel;
    protected JButton buttonAddPart;
    protected JButton buttonAddVariable;
    protected JButton buttonAddEquation;
    protected JButton buttonAddAnnotation;
    protected JButton buttonAddReference;
    protected JButton buttonWatch;
    protected JButton buttonFilter;
    protected JButton buttonView;
    protected JButton buttonRun;
    protected JButton buttonExport;
    protected JButton buttonImport;

    protected JMenuItem  itemAddPart;
    protected JPopupMenu menuPopup;
    protected JPopupMenu menuView;
    protected JPopupMenu menuFilter;
    protected long       menuCanceledAt = 0;

    protected static ImageIcon iconFilterRevoked = ImageUtil.getImage ("filterRevoked.png");
    protected static ImageIcon iconFilterAll     = ImageUtil.getImage ("filter.png");
    protected static ImageIcon iconFilterParam   = ImageUtil.getImage ("filterParam.png");
    protected static ImageIcon iconFilterLocal   = ImageUtil.getImage ("filterLocal.png");

    protected static ImageIcon iconViewNode   = ImageUtil.getImage ("viewGraph.png");
    protected static ImageIcon iconViewSide   = ImageUtil.getImage ("viewSide.png");
    protected static ImageIcon iconViewBottom = ImageUtil.getImage ("viewBottom.png");

    protected static String noModel = "Select a model from the left, or click the New Model button above.";

    protected int jobCount = 0;  // for launching jobs

    public PanelEquations ()
    {
        InputMap inputMap = getInputMap (WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put (KeyStroke.getKeyStroke ("ESCAPE"), "cancel");

        ActionMap actionMap = getActionMap ();
        actionMap.put ("cancel", new AbstractAction ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if (breadcrumbRenderer.editingComponent != null) editor.cancelCellEditing ();
            }
        });

        transferHandler = new TransferHandler ()
        {
            public boolean canImport (TransferSupport xfer)
            {
                return ! locked  &&  xfer.isDataFlavorSupported (DataFlavor.stringFlavor);
            }

            public boolean importData (TransferSupport xfer)
            {
                if (locked) return false;

                MNode data = new MVolatile ();
                Schema schema;
                TransferableNode xferNode = null;  // used only to detect if the source is an equation tree
                try
                {
                    Transferable xferable = xfer.getTransferable ();
                    StringReader reader = new StringReader ((String) xferable.getTransferData (DataFlavor.stringFlavor));
                    schema = Schema.readAll (data, reader);
                    if (xferable.isDataFlavorSupported (TransferableNode.nodeFlavor)) xferNode = (TransferableNode) xferable.getTransferData (TransferableNode.nodeFlavor);
                }
                catch (IOException | UnsupportedFlavorException e)
                {
                    return false;
                }

                // Determine paste/drop target.
                JTree              tree = null;
                PanelEquationGraph peg  = null;
                GraphNode          gn   = null;
                Component comp = xfer.getComponent ();
                if      (comp instanceof JTree             ) tree = (JTree)              comp;
                else if (comp instanceof PanelEquationGraph) peg  = (PanelEquationGraph) comp;
                else if (comp instanceof BreadcrumbRenderer) peg  = panelEquationGraph;  // There's only one, so should be same as case above.
                else if (comp instanceof GraphNode.TitleRenderer)
                {
                    gn = (GraphNode) comp.getParent ().getParent ();
                    if (gn.open) tree = gn.panelEquationTree.tree;
                    else         peg  = gn.container.panelEquationGraph;
                }

                TreePath path = null;
                DropLocation dl = null;
                if (xfer.isDrop ()) dl = xfer.getDropLocation ();
                if (tree != null)
                {
                    if (xfer.isDrop ())
                    {
                        path = ((JTree.DropLocation) dl).getPath ();
                        if (path == null) path = tree.getPathForRow (tree.getRowCount () - 1);
                    }
                    else
                    {
                        path = tree.getSelectionPath ();
                        if (path == null  &&  gn != null)
                        {
                            Object[] o = new Object[1];
                            o[0] = gn.node;
                            path = new TreePath (o);
                        }
                    }
                }

                NodeBase target = null;
                Point location = null;
                if (peg != null)
                {
                    target = part;
                    if (dl != null)
                    {
                        location = dl.getDropPoint ();
                        Point vp = peg.vp.getViewPosition ();
                        location.x += vp.x - peg.graphPanel.offset.x;
                        location.y += vp.y - peg.graphPanel.offset.y;
                    }
                }

                // Handle internal DnD as a node reordering.
                PanelModel pm = PanelModel.instance;
                if (xferNode != null  &&  xferNode.panel == PanelEquations.this  &&  xfer.isDrop ())
                {
                    NodeBase source = xferNode.getSource ();
                    if (source == null) return false;  // Probably can only happen in a DnD between N2A instances.

                    if (path == null)
                    {
                        if (! (source instanceof NodePart)) return false;
                        NodePart sourcePart = (NodePart) source;
                        if (sourcePart.graph == null) return false;

                        MNode gui = new MVolatile ();
                        gui.set (location.x, "bounds", "x");
                        gui.set (location.y, "bounds", "y");
                        pm.undoManager.add (new ChangeGUI (sourcePart, gui));
                    }
                    else  // DnD operation is internal to the tree. (Could also be DnD between N2A windows. For now, reject that case.)
                    {
                        target = (NodeBase) path.getLastPathComponent ();
                        NodeBase targetParent = (NodeBase) target.getParent ();
                        if (targetParent == null) return false;  // If target is root node

                        NodeBase sourceParent = (NodeBase) source.getParent ();
                        if (targetParent != sourceParent) return false;  // Don't drag node outside its containing part.
                        if (! (targetParent instanceof NodePart)) return false;  // Only rearrange children of parts (not of variables or metadata).

                        NodePart parent = (NodePart) targetParent;
                        int indexBefore = parent.getIndex (source);
                        int indexAfter  = parent.getIndex (target);
                        pm.undoManager.add (new ChangeOrder (parent, indexBefore, indexAfter));
                    }

                    return true;
                }

                // Determine target node. Create new model if needed.
                pm.undoManager.addEdit (new CompoundEdit ());
                if (root == null)
                {
                    AddDoc ad = new AddDoc ();
                    pm.undoManager.add (ad);
                    target = root;
                }
                if (tree != null)
                {
                    if (xfer.isDrop ()) tree.setSelectionPath (path);
                    target = (NodeBase) path.getLastPathComponent ();
                }

                // Don't drop part directly on parent tree. Instead, deflect to graph panel.
                if (tree == getParentEquationTree ().tree  &&  schema.type.endsWith ("Part"))
                {
                    tree.repaint ();  // hide DnD target highlight
                    tree = null;
                }

                // An import can either be a new node in the tree, or a link (via inheritance) to an existing part.
                // In the case of a link, the part may need to be fully imported if it does not already exist in the db.
                boolean result = false;
                if (schema.type.startsWith ("Clip"))
                {
                    result = true;
                    for (MNode child : data)
                    {
                        NodeBase added = target.add (schema.type.substring (4), tree, child, location);
                        if (added == null)
                        {
                            result = false;
                            break;
                        }
                    }
                }
                else if (schema.type.equals ("Part"))
                {
                    result = true;

                    // Prepare lists for suggesting connections.
                    List<NodePart> newParts = new ArrayList<NodePart> ();
                    List<NodePart> oldParts = new ArrayList<NodePart> ();
                    Enumeration<?> children = target.children ();
                    while (children.hasMoreElements ())
                    {
                        Object c = children.nextElement ();
                        if (c instanceof NodePart) oldParts.add ((NodePart) c);
                    }

                    for (MNode child : data)  // There could be multiple parts.
                    {
                        // Ensure the part is in our db
                        String key = child.key ();
                        if (AppData.models.child (key) == null) pm.undoManager.add (new AddDoc (key, child));

                        // Create an include-style part
                        MNode include = new MVolatile ();  // Note the empty key. This enables AddPart to generate a name.
                        include.merge (child);  // TODO: What if this brings in a $inherit line, and that line does not match the $inherit line in the source part? One possibility is to add the new values to the end of the $inherit line created below.
                        include.clear ("$inherit");  // get rid of IDs from included part, so they won't override the new $inherit line ...
                        include.set (key, "$inherit");
                        NodePart added = (NodePart) target.add ("Part", tree, include, location);
                        if (added == null)
                        {
                            result = false;
                            break;
                        }
                        newParts.add (added);
                    }

                    NodePart.suggestConnections (newParts, oldParts);
                    NodePart.suggestConnections (oldParts, newParts);
                }
                if (! xfer.isDrop ()  ||  xfer.getDropAction () != MOVE  ||  xferNode == null) pm.undoManager.endCompoundEdit ();  // By not closing the compound edit on a DnD move, we allow the sending side to include any changes in it when exportDone() is called.
                return result;
            }

            public int getSourceActions (JComponent comp)
            {
                return COPY_OR_MOVE;
            }

            boolean dragInitiated;  // This is a horrible hack, but the simplest way to override the default MOVE action chosen internally by Swing.
            public void exportAsDrag (JComponent comp, InputEvent e, int action)
            {
                dragInitiated = true;
                super.exportAsDrag (comp, e, action);
            }

            protected Transferable createTransferable (JComponent comp)
            {
                boolean drag = dragInitiated;
                dragInitiated = false;

                NodeBase node = null;
                if (comp instanceof JTree)
                {
                    JTree tree = (JTree) comp;
                    TreePath path = tree.getSelectionPath ();
                    if (path != null) node = (NodeBase) path.getLastPathComponent ();
                    if (node == null) node = (NodeBase) tree.getModel ().getRoot ();
                }
                else if (comp instanceof GraphNode.TitleRenderer)
                {
                    GraphNode gn = (GraphNode) comp.getParent ().getParent ();
                    node = gn.node;
                }
                if (node == null) return null;

                MVolatile copy = new MVolatile ();
                node.copy (copy);
                if (node == root) copy.set (null, node.source.key ());  // Remove file information from root node, if that is what we are sending.

                Schema schema = Schema.latest ();
                schema.type = "Clip" + node.getTypeName ();
                StringWriter writer = new StringWriter ();
                try
                {
                    schema.write (writer);
                    for (MNode c : copy) schema.write (c, writer);
                    writer.close ();

                    TransferableNode result = new TransferableNode (writer.toString (), node, drag, null);
                    result.panel = PanelEquations.this;
                    return result;
                }
                catch (IOException e)
                {
                }

                return null;
            }

            protected void exportDone (JComponent source, Transferable data, int action)
            {
                TransferableNode tn = (TransferableNode) data;
                if (tn != null  &&  action == MOVE  &&  ! locked)
                {
                    // It is possible for the node to be removed from the tree before we get to it.
                    // For example, a local drop of an $inherit node will cause the tree to rebuild.
                    NodeBase node = tn.getSource ();
                    if (node != null)
                    {
                        if (tn.drag)
                        {
                            if (tn.newPartName != null  &&  node != root  &&  node.source.isFromTopDocument ())
                            {
                                // Change this node into an include of the newly-created part.
                                PanelModel.instance.undoManager.add (new Outsource ((NodePart) node, tn.newPartName));
                            }
                        }
                        else
                        {
                            if (source instanceof JTree) node.delete ((JTree) source, false);
                            else                         node.delete (null,           false);  // works for NodePart
                        }
                    }
                }
                PanelModel.instance.undoManager.endCompoundEdit ();  // This is safe, even if there is no compound edit in progress.
            }
        };

        // Need to wait until after transferHandler is constructed before creating breadcrumbRenderer, so that it can get a non-null reference.
        breadcrumbRenderer = new BreadcrumbRenderer ();

        buttonAddModel = new JButton (ImageUtil.getImage ("document.png"));
        buttonAddModel.setMargin (new Insets (2, 2, 2, 2));
        buttonAddModel.setFocusable (false);
        buttonAddModel.setToolTipText ("New Model");
        buttonAddModel.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                stopEditing ();
                AddDoc add = new AddDoc ();
                PanelModel.instance.undoManager.add (add);
            }
        });

        buttonAddPart = new JButton (ImageUtil.getImage ("comp.gif"));
        buttonAddPart.setMargin (new Insets (2, 2, 2, 2));
        buttonAddPart.setFocusable (false);
        buttonAddPart.setToolTipText ("Add Part");
        buttonAddPart.setActionCommand ("Part");
        buttonAddPart.addActionListener (listenerAdd);

        buttonAddVariable = new JButton (ImageUtil.getImage ("delta.png"));
        buttonAddVariable.setMargin (new Insets (2, 2, 2, 2));
        buttonAddVariable.setFocusable (false);
        buttonAddVariable.setToolTipText ("Add Variable");
        buttonAddVariable.setActionCommand ("Variable");
        buttonAddVariable.addActionListener (listenerAdd);

        buttonAddEquation = new JButton (ImageUtil.getImage ("assign.png"));
        buttonAddEquation.setMargin (new Insets (2, 2, 2, 2));
        buttonAddEquation.setFocusable (false);
        buttonAddEquation.setToolTipText ("Add Equation");
        buttonAddEquation.setActionCommand ("Equation");
        buttonAddEquation.addActionListener (listenerAdd);

        buttonAddAnnotation = new JButton (ImageUtil.getImage ("edit.gif"));
        buttonAddAnnotation.setMargin (new Insets (2, 2, 2, 2));
        buttonAddAnnotation.setFocusable (false);
        buttonAddAnnotation.setToolTipText ("Add Annotation");
        buttonAddAnnotation.setActionCommand ("Annotation");
        buttonAddAnnotation.addActionListener (listenerAdd);

        buttonAddReference = new JButton (ImageUtil.getImage ("book.gif"));
        buttonAddReference.setMargin (new Insets (2, 2, 2, 2));
        buttonAddReference.setFocusable (false);
        buttonAddReference.setToolTipText ("Add Reference");
        buttonAddReference.setActionCommand ("Reference");
        buttonAddReference.addActionListener (listenerAdd);

        buttonWatch = new JButton (ImageUtil.getImage ("watch.png"));
        buttonWatch.setMargin (new Insets (2, 2, 2, 2));
        buttonWatch.setFocusable (false);
        buttonWatch.setToolTipText ("Watch Variable");
        buttonWatch.addActionListener (listenerWatch);

        buttonFilter = new JButton ();
        FilteredTreeModel.filterLevel = AppData.state.getOrDefault (FilteredTreeModel.PARAM, "PanelModel", "filter");
        switch (FilteredTreeModel.filterLevel)
        {
            case FilteredTreeModel.REVOKED: buttonFilter.setIcon (iconFilterRevoked); break;
            case FilteredTreeModel.ALL:     buttonFilter.setIcon (iconFilterAll);     break;
            case FilteredTreeModel.PARAM:   buttonFilter.setIcon (iconFilterParam);   break;
            case FilteredTreeModel.LOCAL:   buttonFilter.setIcon (iconFilterLocal);   break;
        }
        buttonFilter.setMargin (new Insets (2, 2, 2, 2));
        buttonFilter.setFocusable (false);
        buttonFilter.setToolTipText ("Filter Equations");
        buttonFilter.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if (System.currentTimeMillis () - menuCanceledAt > 500)  // A really ugly way to prevent the button from re-showing the menu if it was canceled by clicking the button.
                {
                    menuFilter.show (buttonFilter, 0, buttonFilter.getHeight ());
                }
            }
        });

        buttonView = new JButton ();
        switch (view)
        {
            case SIDE:   buttonView.setIcon (iconViewSide);   break;
            case BOTTOM: buttonView.setIcon (iconViewBottom); break;
            default:     buttonView.setIcon (iconViewNode);
        }
        buttonView.setToolTipText ("View Equation Panel");
        buttonView.setMargin (new Insets (2, 2, 2, 2));
        buttonView.setFocusable (false);
        buttonView.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if (System.currentTimeMillis () - menuCanceledAt > 500)  // A really ugly way to prevent the button from re-showing the menu if it was canceled by clicking the button.
                {
                    menuView.show (buttonView, 0, buttonView.getHeight ());
                }
            }
        });

        buttonRun = new JButton (ImageUtil.getImage ("run.gif"));
        buttonRun.setMargin (new Insets (2, 2, 2, 2));
        buttonRun.setFocusable (false);
        buttonRun.setToolTipText ("Run");
        buttonRun.addActionListener (listenerRun);

        buttonExport = new JButton (ImageUtil.getImage ("export.gif"));
        buttonExport.setMargin (new Insets (2, 2, 2, 2));
        buttonExport.setFocusable (false);
        buttonExport.setToolTipText ("Export");
        buttonExport.addActionListener (listenerExport);

        buttonImport = new JButton (ImageUtil.getImage ("import.gif"));
        buttonImport.setMargin (new Insets (2, 2, 2, 2));
        buttonImport.setFocusable (false);
        buttonImport.setToolTipText ("Import");
        buttonImport.addActionListener (listenerImport);

        Lay.BLtg (this,
            "N", Lay.WL ("L",
                buttonAddModel,
                Box.createHorizontalStrut (15),
                buttonAddPart,
                buttonAddVariable,
                buttonAddEquation,
                buttonAddAnnotation,
                buttonAddReference,
                Box.createHorizontalStrut (15),
                buttonWatch,
                Box.createHorizontalStrut (15),
                buttonFilter,
                buttonView,
                Box.createHorizontalStrut (15),
                buttonRun,
                Box.createHorizontalStrut (15),
                buttonExport,
                buttonImport,
                "hgap=5,vgap=1"
            )
        );

        panelBreadcrumb = Lay.BL ("C", breadcrumbRenderer);
        panelBreadcrumb.setBorder (new RoundedTopBorder (5));
        panelBreadcrumb.setOpaque (false);

        panelParent        = new GraphParent (this);  // Only one of {panelParent, panelEquationTree} will be used at any given time.
        panelEquationGraph = new PanelEquationGraph (this);

        panelGraph = new PanelGraph ();
        panelGraph.add (panelParent);
        panelGraph.add (panelBreadcrumb,    BorderLayout.NORTH);
        panelGraph.add (panelEquationGraph, BorderLayout.CENTER);

        panelEquationTree = new PanelEquationTree (this);

        if (view == SIDE) split = new JSplitPane (JSplitPane.HORIZONTAL_SPLIT);
        else              split = new JSplitPane (JSplitPane.VERTICAL_SPLIT);
        split.setOneTouchExpandable(true);
        split.setResizeWeight (1);
        int dividerLocation = AppData.state.getInt ("PanelModel", "view", view);
        if (dividerLocation != 0) split.setDividerLocation (dividerLocation);
        split.addPropertyChangeListener (JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                Object o = e.getNewValue ();
                if (o instanceof Integer) AppData.state.set (o, "PanelModel", "view", view);
            }
        });

        if (view == NODE)
        {
            add (panelGraph, BorderLayout.CENTER);
        }
        else
        {
            split.add (panelGraph);         // first component, on left or top
            split.add (panelEquationTree);  // second component, on right or bottom
            add (split, BorderLayout.CENTER);
        }


        // Context Menu

        itemAddPart = new JMenuItem ("Add Part", ImageUtil.getImage ("comp.gif"));
        itemAddPart.setActionCommand ("Part");
        itemAddPart.addActionListener (listenerAdd);

        JMenuItem itemAddVariable = new JMenuItem ("Add Variable", ImageUtil.getImage ("delta.png"));
        itemAddVariable.setActionCommand ("Variable");
        itemAddVariable.addActionListener (listenerAdd);

        JMenuItem itemAddEquation = new JMenuItem ("Add Equation", ImageUtil.getImage ("assign.png"));
        itemAddEquation.setActionCommand ("Equation");
        itemAddEquation.addActionListener (listenerAdd);

        JMenuItem itemAddAnnotation = new JMenuItem ("Add Annotation", ImageUtil.getImage ("edit.gif"));
        itemAddAnnotation.setActionCommand ("Annotation");
        itemAddAnnotation.addActionListener (listenerAdd);

        JMenuItem itemAddReference = new JMenuItem ("Add Reference", ImageUtil.getImage ("book.gif"));
        itemAddReference.setActionCommand ("Reference");
        itemAddReference.addActionListener (listenerAdd);

        JMenuItem itemWatch = new JMenuItem ("Watch", ImageUtil.getImage ("watch.png"));
        itemWatch.addActionListener (listenerWatch);

        menuPopup = new JPopupMenu ();
        menuPopup.add (itemAddPart);
        menuPopup.add (itemAddVariable);
        menuPopup.add (itemAddEquation);
        menuPopup.add (itemAddAnnotation);
        menuPopup.add (itemAddReference);
        menuPopup.addSeparator ();
        menuPopup.add (itemWatch);


        // View menu

        JMenuItem itemViewNode = new JMenuItem ("Equations in Node", iconViewNode);
        itemViewNode.setActionCommand ("Node");
        itemViewNode.addActionListener (listenerView);

        JMenuItem itemViewSide = new JMenuItem ("Equations on Side", iconViewSide);
        itemViewSide.setActionCommand ("Side");
        itemViewSide.addActionListener (listenerView);

        JMenuItem itemViewBottom = new JMenuItem ("Equations at Bottom", iconViewBottom);
        itemViewBottom.setActionCommand ("Bottom");
        itemViewBottom.addActionListener (listenerView);

        menuView = new JPopupMenu ();
        menuView.add (itemViewNode);
        menuView.add (itemViewSide);
        menuView.add (itemViewBottom);

        PopupMenuListener rememberCancelTime = new PopupMenuListener ()
        {
            public void popupMenuWillBecomeVisible (PopupMenuEvent e)
            {
            }

            public void popupMenuWillBecomeInvisible (PopupMenuEvent e)
            {
            }

            public void popupMenuCanceled (PopupMenuEvent e)
            {
                menuCanceledAt = System.currentTimeMillis ();
            }
        };
        menuView.addPopupMenuListener (rememberCancelTime);


        // Filter menu

        JMenuItem itemFilterRevoked = new JMenuItem ("Revoked", iconFilterRevoked);
        itemFilterRevoked.setActionCommand ("Revoked");
        itemFilterRevoked.addActionListener (listenerFilter);

        JMenuItem itemFilterAll = new JMenuItem ("All", iconFilterAll);
        itemFilterAll.setActionCommand ("All");
        itemFilterAll.addActionListener (listenerFilter);

        JMenuItem itemFilterParameters = new JMenuItem ("Parameters", iconFilterParam);
        itemFilterParameters.setActionCommand ("Parameters");
        itemFilterParameters.addActionListener (listenerFilter);

        JMenuItem itemFilterLocal = new JMenuItem ("Local", iconFilterLocal);
        itemFilterLocal.setActionCommand ("Local");
        itemFilterLocal.addActionListener (listenerFilter);

        menuFilter = new JPopupMenu ();
        menuFilter.add (itemFilterRevoked);
        menuFilter.add (itemFilterAll);
        menuFilter.add (itemFilterParameters);
        menuFilter.add (itemFilterLocal);
        menuFilter.addPopupMenuListener (rememberCancelTime);


        // Load initial model
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                MNode lastUsedNode = null;
                String lastUsedKey = AppData.state.get ("PanelModel", "lastUsed");
                if (! lastUsedKey.isEmpty ()) lastUsedNode = AppData.models.child (lastUsedKey);
                if (lastUsedNode != null) load (lastUsedNode);
            }
        });
    }

    public void updateUI ()
    {
        EquationTreeCellRenderer.staticUpdateUI ();
        EquationTreeCellEditor  .staticUpdateUI ();
        if (editor != null) editor.updateUI ();
        super.updateUI ();
    }

    public void load (MNode doc)
    {
        if (record == doc) return;
        if (record != null) saveFocus ();
        record = doc;
        updateLock ();
        try
        {
            root = new NodePart (new MPart ((MPersistent) record));
            root.build ();
            root.findConnections ();
            loadPart (root);
            AppData.state.set (record.key (), "PanelModel", "lastUsed");
        }
        catch (Exception e)
        {
            System.err.println ("Exception while parsing model: " + e);
            e.printStackTrace ();
        }
    }

    public void loadPart (NodePart part)
    {
        if (this.part == part) return;

        this.part = part;
        active = null;
        parentSelected = false;
        panelGraph.loadPart ();  // also manages panelEquationTree via panelParent
        repaint ();
        takeFocus ();  // sets active
    }

    /**
        Informs us that some other code deleted a document from the DB.
        We only respond if it happens to be on display.
    **/
    public void recordDeleted (MNode doc)
    {
        focusCache.clear (doc.key ());
        if (doc != record) return;
        record = null;
        root   = null;
        part   = null;
        active = null;
        parentSelected = false;
        panelGraph.clear ();  // also manages panelEquationTree via panelParent
        repaint ();
    }

    public void updateLock ()
    {
        locked = ! AppData.models.isWriteable (record);
        // The following are safe to call, even when record is not fully loaded, and despite which panel is active.
        panelEquationTree.updateLock ();
        if (view == NODE) panelEquationGraph.updateLock ();
    }

    public void checkVisible ()
    {
        if (record == null) return;
        if (AppData.models.isVisible (record))
        {
            resetBreadcrumbs ();
            updateLock ();
        }
        else
        {
            recordDeleted (record);
        }
    }

    public void updateDoc (String oldKey, String newKey)
    {
        String key = "";
        if (record != null) key = record.key ();

        boolean contentOnly = oldKey.equals (newKey);
        if (key.equals (newKey))
        {
            if (contentOnly)
            {
                record = null;  // Force rebuild of display
                load (AppData.models.child (newKey));
            }
            else
            {
                checkVisible ();
            }
        }
        if (contentOnly) return;

        MNode oldDoc = AppData.models.child (oldKey);
        if (oldDoc == null)  // deleted
        {
            checkVisible ();
        }
        else  // oldDoc has changed identity
        {
            if (key.equals (oldKey))
            {
                record = null;
                load (oldDoc);
            }
        }
    }

    public void saveFocus ()
    {
        if (root == null) return;

        FocusCacheEntry fce = createFocus (part);
        fce.position = panelEquationGraph.saveFocus ();
        if (active != null)
        {
            if (active.root == part)
            {
                fce.subpart = "";
                fce.titleFocused = titleFocused;
            }
            else
            {
                fce.subpart = active.root.source.key ();
                fce = createFocus (active.root);
                if (active.root.graph != null) fce.titleFocused = active.root.graph.titleFocused;
            }

            // Only save state of the active node, rather than all nodes.
            // This seems sufficient for good user experience.
            fce.sp = active.saveFocus (fce.sp);
        }
    }

    /**
        Retrieves focus cache entry, or creates a new one if it doesn't already exist.
    **/
    public FocusCacheEntry createFocus (NodePart p)
    {
        return createFocus (p.getKeyPath ().toArray ());
    }

    public FocusCacheEntry createFocus (Object... keyArray)
    {
        FocusCacheEntry result = (FocusCacheEntry) focusCache.getObject (keyArray);
        if (result == null)
        {
            result = new FocusCacheEntry ();
            focusCache.setObject (result, keyArray);
        }
        return result;
    }

    public void yieldFocus ()
    {
        saveFocus ();
        if (active != null) active.yieldFocus ();
    }

    public void takeFocus ()
    {
        FocusCacheEntry fce = createFocus (part);
        if (fce.subpart.isEmpty ())  // focus parent
        {
            switchFocus (fce.titleFocused, false);
        }
        else  // focus a child
        {
            panelEquationGraph.takeFocus (fce);
        }
    }

    public PanelEquationTree getParentEquationTree ()
    {
        if (view == NODE) return panelParent.panelEquationTree;
        return panelEquationTree;
    }

    /**
        Moves focus between title and equation tree.
        If focus is somewhere else, pulls it to one of these two.
    **/
    public void switchFocus (boolean ontoTitle, boolean selectRow0)
    {
        PanelEquationTree pet = getParentEquationTree ();
        if (pet.tree.getRowCount () == 0) ontoTitle = true;  // Don't switch focus to an empty tree.
        if (view != NODE) pet.loadPart (part);

        titleFocused = ontoTitle;
        if (ontoTitle)
        {
            breadcrumbRenderer.requestFocusInWindow ();
        }
        else
        {
            if (view == NODE) panelParent.setOpen (true);
            if (selectRow0)
            {
                pet.tree.scrollRowToVisible (0);
                pet.tree.setSelectionRow (0);
            }
            pet.takeFocus ();
        }
    }

    public Component getTitleFocus ()
    {
    	if (titleFocused) return breadcrumbRenderer;
    	return getParentEquationTree ().tree;
    }

    public void setSelected (boolean value)
    {
        parentSelected = value;
        breadcrumbRenderer.updateSelected ();
    }

    public boolean isEditing ()
    {
        return editor.editingNode != null;
    }

    public void stopEditing ()
    {
        // editor is shared between graph node titles and equation trees
        if (editor.editingNode != null) editor.stopCellEditing ();
    }

    public void resetBreadcrumbs ()
    {
        breadcrumbRenderer.update ();
        panelBreadcrumb.validate ();
        panelBreadcrumb.repaint ();
    }

    public void drill (NodePart nextPart)
    {
        saveFocus ();
        if (nextPart.getTrueParent () == part)
        {
            FocusCacheEntry fce = createFocus (part);
            fce.subpart = nextPart.source.key ();
        }
        loadPart (nextPart);
    }

    public void drillUp ()
    {
        NodePart parent = (NodePart) part.getTrueParent ();
        if (parent != null) drill (parent);
    }

    ActionListener listenerAdd = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            String type = e.getActionCommand ();
            Point location = null;
            if (e.getSource () == itemAddPart)
            {
                if (menuPopup.getInvoker () == panelEquationGraph.graphPanel)
                {
                    location = panelEquationGraph.graphPanel.popupLocation;
                }
            }
            if (record == null)
            {
                AddDoc add = new AddDoc ();
                PanelModel.instance.undoManager.add (add);
                // After load(doc), active is null.
                // PanelEquationTree focusGained() will set active, but won't be called before the test below.
                active = getParentEquationTree ();
            }
            else
            {
                if (locked) return;
            }

            if (active == null  ||  location != null)
            {
                NodePart editMe = (NodePart) part.add (type, null, null, location);
                if (editMe == null) return;
                EventQueue.invokeLater (new Runnable ()
                {
                    public void run ()
                    {
                        editMe.graph.title.startEditing ();
                    }
                });
            }
            else
            {
                stopEditing ();
                // Because stopEditing() can trigger shifts in focus, we wait until current even queue clears before doing the add ...
                EventQueue.invokeLater (new Runnable ()
                {
                    public void run ()
                    {
                        active.addAtSelected (type);
                    }
                });
            }
        }
    };

    ActionListener listenerDelete = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            if (locked) return;
            if (active == null)  // Either nothing is selected or a graph node is selected.
            {
                panelEquationGraph.deleteSelected ();
            }
            else
            {
                stopEditing ();  // It may seem odd to save a cell just before destroying it, but this gives cleaner UI painting.
                active.deleteSelected ();
            }
        }
    };

    ActionListener listenerWatch = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            if (locked) return;
            if (active == null) return;
            stopEditing ();
            active.watchSelected ();
        }
    };

    ActionListener listenerMove = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            if (locked) return;
            if (active == null) return;
            stopEditing ();
            active.moveSelected (Integer.valueOf (e.getActionCommand ()));
        }
    };

    ActionListener listenerRun = new ActionListener ()
    {
        /**
            Fire off a simulation.
            The code below is adapted from gove.sandia.n2a.ui.model.RunDetailPanel, specifically the old-style single-run.
            uiController.prepareAndSubmitRunEnsemble() is the way to set up a run ensemble
        **/
        public void actionPerformed (ActionEvent e)
        {
            if (record == null) return;
            MainTabbedPane mtp = (MainTabbedPane) MainFrame.instance.tabs;
            if (isEditing ())
            {
                stopEditing ();
                // The following is needed because graph node components (title or tree) do not reclaim the focus before it shifts to the run tab.
                GraphNode gn = active.root.graph;
                if (gn != null  &&  gn.titleFocused) mtp.setPreferredFocus (PanelModel.instance, gn.title);
                else                                 mtp.setPreferredFocus (PanelModel.instance, active.tree);
                
            }

            String simulatorName = root.source.get ("$metadata", "backend");  // Note that "record" is the raw model, while "root.source" is the collated model.
            final Backend simulator = Backend.getBackend (simulatorName);
            MNode runs = AppData.runs;
            String jobKey = new SimpleDateFormat ("yyyy-MM-dd-HHmmss", Locale.ROOT).format (new Date ()) + "-" + jobCount++;
            final MNode job = runs.childOrCreate (jobKey);  // Create the dir and model doc
            job.merge (root.source);
            job.set ("\"" + record.key () + "\"", "$inherit");
            ((MDoc) job).save ();  // Force directory (and job file) to exist, so Backends can work with the dir.

            new Thread ()
            {
                public void run ()
                {
                    // Simulator should record all errors/warnings to a file in the job dir.
                    // If this thread throws an untrapped exception, then there is something wrong with the implementation.
                    simulator.start (job);
                }
            }.start ();

            PanelRun panelRun = (PanelRun) mtp.selectTab ("Runs");
            mtp.setPreferredFocus (panelRun, panelRun.tree);
            panelRun.addNewRun (job);
        }
    };

    ActionListener listenerExport = new ActionListener ()
    {
        // We create and customize a file chooser on the fly, display it modally, then use its result to initiate export.

        class ExporterFilter extends FileFilter
        {
            public Exporter exporter;

            ExporterFilter (Exporter exporter)
            {
                this.exporter = exporter;
            }

            @Override
            public boolean accept (File f)
            {
                return true;
            }

            @Override
            public String getDescription ()
            {
                return exporter.getName ();
            }
        }

        public void actionPerformed (ActionEvent e)
        {
            if (record == null) return;
            stopEditing ();

            // Construct and customize a file chooser
            final JFileChooser fc = new JFileChooser (AppData.properties.get ("resourceDir"));
            fc.setDialogTitle ("Export \"" + record.key () + "\"");
            ExporterFilter n2a = null;
            List<ExtensionPoint> exps = PluginManager.getExtensionsForPoint (Exporter.class);
            for (ExtensionPoint exp : exps)
            {
                ExporterFilter ef = new ExporterFilter ((Exporter) exp);
                fc.addChoosableFileFilter (ef);
                if (ef.exporter.getName ().contains ("N2A")) n2a = ef;
            }
            fc.setAcceptAllFileFilterUsed (false);
            if (n2a != null) fc.setFileFilter (n2a);

            // Display chooser and collect result
            int result = fc.showSaveDialog (MainFrame.instance);

            // Do export
            if (result == JFileChooser.APPROVE_OPTION)
            {
                Path path = fc.getSelectedFile ().toPath ();
                ExporterFilter filter = (ExporterFilter) fc.getFileFilter ();
                filter.exporter.export (record, path);
            }
        }
    };

    ActionListener listenerImport = new ActionListener ()
    {
        // We create and customize a file chooser on the fly, display it modally, then use its result to initiate export.

        class ImporterFilter extends FileFilter
        {
            public Importer importer;

            ImporterFilter (Importer importer)
            {
                this.importer = importer;
            }

            @Override
            public boolean accept (File f)
            {
                return importer.accept (f);
            }

            @Override
            public String getDescription ()
            {
                return importer.getName ();
            }
        }

        public void actionPerformed (ActionEvent e)
        {
            panelEquationTree.tree.stopEditing ();

            // Construct and customize a file chooser
            final JFileChooser fc = new JFileChooser (AppData.properties.get ("resourceDir"));
            fc.setDialogTitle ("Import");
            List<ExtensionPoint> exps = PluginManager.getExtensionsForPoint (Importer.class);
            for (ExtensionPoint exp : exps)
            {
                ImporterFilter f = new ImporterFilter ((Importer) exp);
                fc.addChoosableFileFilter (f);
            }

            // Display chooser and collect result
            int result = fc.showOpenDialog (MainFrame.instance);

            // Do import
            if (result == JFileChooser.APPROVE_OPTION)
            {
                Path path = fc.getSelectedFile ().toPath ();
                PanelModel.importFile (path);
            }
        }
    };

    ActionListener listenerView = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            panelEquationTree.tree.stopEditing ();

            int lastView = view;
            String command = e.getActionCommand ();
            switch (command)
            {
                case "Node":
                    view = NODE;
                    buttonView.setIcon (iconViewNode);
                    break;
                case "Side":
                    view = SIDE;
                    buttonView.setIcon (iconViewSide);
                    break;
                case "Bottom":
                    view = BOTTOM;
                    buttonView.setIcon (iconViewBottom);
                    break;
            }
            AppData.state.set (view, "PanelModel", "view");

            if (view == lastView) return;

            // Rearrange UI components
            if (lastView == NODE)  // next view will be a split
            {
                split.add (panelGraph,        JSplitPane.LEFT);  // removes panelGraph from current parent (this panel)
                split.add (panelEquationTree, JSplitPane.RIGHT);
                add (split, BorderLayout.CENTER);
                panelParent.setOpen (false);
                panelParent.clear ();  // release current root, since this panel won't be used for editing
            }
            else if (view == NODE)  // lastView was a split
            {
                remove (split);
                add (panelGraph, BorderLayout.CENTER);
                panelEquationTree.clear ();  // release root, as above
            }
            if      (view == SIDE)   split.setOrientation (JSplitPane.HORIZONTAL_SPLIT);
            else if (view == BOTTOM) split.setOrientation (JSplitPane.VERTICAL_SPLIT);
            if (view != NODE)
            {
                int dividerLocation = AppData.state.getInt ("PanelModel", "view", view);
                if (dividerLocation != 0) split.setDividerLocation (dividerLocation);
            }
            validate ();
            repaint ();

            // Reload, so that graph nodes can be configured correctly (with or without equation trees).
            if (view == NODE  ||  lastView == NODE)
            {
                NodePart p = part;
                part = null;  // force update to run
                loadPart (p);
            }
        }
    };

    ActionListener listenerFilter = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            panelEquationTree.tree.stopEditing ();

            switch (e.getActionCommand ())
            {
                case "Revoked":
                    FilteredTreeModel.filterLevel = FilteredTreeModel.REVOKED;
                    buttonFilter.setIcon (iconFilterRevoked);
                    break;
                case "All":
                    FilteredTreeModel.filterLevel = FilteredTreeModel.ALL;
                    buttonFilter.setIcon (iconFilterAll);
                    break;
                case "Parameters":
                    FilteredTreeModel.filterLevel = FilteredTreeModel.PARAM;
                    buttonFilter.setIcon (iconFilterParam);
                    break;
                case "Local":
                    FilteredTreeModel.filterLevel = FilteredTreeModel.LOCAL;
                    buttonFilter.setIcon (iconFilterLocal);
                    break;
            }
            AppData.state.set (FilteredTreeModel.filterLevel, "PanelModel", "filter");

            if (panelEquationTree.isVisible ()) panelEquationTree.updateFilterLevel ();
            if (view == NODE)
            {
                if (panelParent.isVisible ()) panelParent.panelEquationTree.updateFilterLevel ();
                panelEquationGraph.updateFilterLevel ();
            }
        }
    };

    public class RoundedTopBorder extends AbstractBorder
    {
        public int t;

        RoundedTopBorder (int thickness)
        {
            t = thickness;
        }

        public void paintBorder (Component c, Graphics g, int x, int y, int width, int height)
        {
            Graphics2D g2 = (Graphics2D) g.create ();
            g2.setRenderingHint (RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // To produce a curved border only on top, we simply set the bottom of the shape
            // so far down that it is clipped off.
            int t2 = t * 2;
            Shape border = new RoundRectangle2D.Double (x, y, width-1, height + 10 + t2, t2, t2);

            g2.setPaint (GraphNode.RoundedBorder.background);
            g2.fill (border);

            if (part == null) g2.setPaint (Color.black);  // part can be null if no model is currently loaded
            else              g2.setPaint (EquationTreeCellRenderer.getForegroundFor (part, false));
            g2.draw (border);

            g2.dispose ();
        }

        public Insets getBorderInsets (Component c, Insets insets)
        {
            insets.left = insets.top = insets.right = insets.bottom = t;
            return insets;
        }
    }

    /**
        Compare with GraphNode.TitleRenderer
    **/
    public class BreadcrumbRenderer extends EquationTreeCellRenderer implements CellEditorListener
    {
        protected List<NodePart> parts   = new ArrayList<NodePart> ();
        protected List<Integer>  offsets = new ArrayList<Integer> ();
        protected String         text    = noModel;
        protected Component      editingComponent;
        protected boolean        UIupdated;

        public BreadcrumbRenderer ()
        {
            nontree = true;

            Font baseFont = UIManager.getFont ("Tree.font");
            setFont (baseFont.deriveFont (Font.BOLD));
            setText (text);
            setIcon (null);
            setFocusable (false);
            setTransferHandler (transferHandler);

            InputMap inputMap = getInputMap ();
            inputMap.put (KeyStroke.getKeyStroke ("UP"),               "nothing");
            inputMap.put (KeyStroke.getKeyStroke ("DOWN"),             "selectNext");
            inputMap.put (KeyStroke.getKeyStroke ("LEFT"),             "close");
            inputMap.put (KeyStroke.getKeyStroke ("RIGHT"),            "selectChild");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl UP"),          "nothing");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl DOWN"),        "selectChild");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl LEFT"),        "nothing");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl RIGHT"),       "selectChild");
            inputMap.put (KeyStroke.getKeyStroke ("shift DELETE"),     "cut");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl X"),           "cut");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl INSERT"),      "copy");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl C"),           "copy");
            inputMap.put (KeyStroke.getKeyStroke ("shift INSERT"),     "paste");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl V"),           "paste");
            inputMap.put (KeyStroke.getKeyStroke ("INSERT"),           "add");
            inputMap.put (KeyStroke.getKeyStroke ("DELETE"),           "delete");
            inputMap.put (KeyStroke.getKeyStroke ("BACK_SPACE"),       "delete");
            inputMap.put (KeyStroke.getKeyStroke ("ENTER"),            "startEditing");
            inputMap.put (KeyStroke.getKeyStroke ("F2"),               "startEditing");
            inputMap.put (KeyStroke.getKeyStroke ("shift SPACE"),      "drillUp");

            ActionMap actionMap = getActionMap ();
            actionMap.put ("close", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    if (panelParent.isVisible ()) panelParent.toggleOpen ();
                }
            });
            actionMap.put ("selectNext", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    if (view == NODE  &&  ! panelParent.isVisible ()) panelParent.toggleOpen ();  // because switchFocus() does not set metadata "parent" open flag
                    switchFocus (false, view == NODE);
                }
            });
            actionMap.put ("selectChild", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    if (view == NODE  &&  ! panelParent.isVisible ()) panelParent.toggleOpen ();
                    else                                              switchFocus (false, false);
                }
            });
            actionMap.put ("cut",   TransferHandler.getCutAction ());
            actionMap.put ("copy",  TransferHandler.getCopyAction ());
            actionMap.put ("paste", TransferHandler.getPasteAction ());
            actionMap.put ("add", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    if (locked) return;
                    if (panelParent.isVisible ())  // parent is open
                    {
                    	// Add a NodeVariable under part
                        getParentEquationTree ().addAtSelected ("");  // No selection should be active in panelParent.panelEquations, so this should apply to its root.
                    }
                    else  // parent is closed
                    {
                    	// Add a subpart in the graph
                        NodePart editMe = (NodePart) part.add ("Part", null, null, null);
                        if (editMe == null) return;
                        EventQueue.invokeLater (new Runnable ()
                        {
                            public void run ()
                            {
                                editMe.graph.title.startEditing ();
                            }
                        });
                    }
                }
            });
            actionMap.put ("delete", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    if (! locked) part.delete (null, false);
                }
            });
            actionMap.put ("startEditing", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    startEditing ();  // guards against modifying a locked part
                }
            });
            actionMap.put ("drillUp", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    drillUp ();
                }
            });

            addMouseListener (new MouseInputAdapter ()
            {
                public void mouseClicked (MouseEvent me)
                {
                    int x = me.getX ();
                    int y = me.getY ();
                    int clicks = me.getClickCount ();

                    if (SwingUtilities.isLeftMouseButton (me))
                    {
                        if (clicks == 1)
                        {
                            if (x < getIconWidth ())  // Open/close
                            {
                                if (view == NODE) panelParent.toggleOpen ();
                                switchFocus (true, false);
                            }
                            else  // Drill up to specific path element.
                            {
                                x -= getTextOffset ();
                                int last = offsets.size () - 1;
                                for (int i = 0; i < last; i++)
                                {
                                    if (x >= offsets.get (i)) continue;
                                    drill (parts.get (i));
                                    return;
                                }

                                // Click was on last path element (which may be only path element), so take focus.
                                switchFocus (true, false);
                            }
                        }
                    }
                    else if (SwingUtilities.isRightMouseButton (me))
                    {
                        if (clicks == 1)  // Show popup menu
                        {
                            switchFocus (true, false);
                            menuPopup.show (breadcrumbRenderer, x, y);
                        }
                    }
                }
            });

            addFocusListener (new FocusListener ()
            {
                public void focusGained (FocusEvent e)
                {
                    if (view == NODE)
                    {
                        active = panelParent.panelEquationTree;
                    }
                    else
                    {
                        active = panelEquationTree;
                        panelEquationTree.loadPart (part);
                        FocusCacheEntry fce = createFocus (part);
                        if (fce.sp != null) fce.sp.restore (panelEquationTree.tree, false);
                    }
                    getTreeCellRendererComponent (true, true);
                    panelBreadcrumb.repaint ();
                }

                public void focusLost (FocusEvent e)
                {
                    getTreeCellRendererComponent (parentSelected, false);
                    panelBreadcrumb.repaint ();
                }
            });
        }

        public void updateUI ()
        {
            super.updateUI ();
            UIupdated = true;
        }

        public Dimension getPreferredSize ()
        {
            if (UIupdated)
            {
                UIupdated = false;
                // We are never the focus owner, because updateUI() is triggered from the L&F panel.
                getTreeCellRendererComponent (parentSelected, false);
            }
            return super.getPreferredSize ();
        }

        public void getTreeCellRendererComponent (boolean selected, boolean focused)
        {
            // In addition to focusGained() and focusLost() above, this method can also be called by GraphParent.setOpen()
            // to update the state of this renderer component. setOpen() can be called as part of the deletion of part,
            // so it is necessary to guard against null here.
            getTreeCellRendererComponent (getParentEquationTree ().tree, part, selected, panelParent.isVisible (), false, -1, focused);
            if (part == null)
            {
                text = noModel;
                setIcon (null);
                setFocusable (false);
            }
            Font baseFont = UIManager.getFont ("Tree.font");
            setFont (baseFont.deriveFont (Font.BOLD));
            setText (text);
        }

        public void updateSelected ()
        {
            getTreeCellRendererComponent (parentSelected, isFocusOwner ());
            panelBreadcrumb.repaint ();
        }

        public void update ()
        {
            parts.clear ();
            NodePart p = part;
            while (p != null)
            {
                parts.add (0, p);
                p = (NodePart) p.getTrueParent ();
            }
            if (parts.isEmpty ())
            {
                setText (text = noModel);
                setIcon (null);
                setFocusable (false);
                getTreeCellRendererComponent (false, false);
                return;
            }

            text = "";
            offsets.clear ();
            FontMetrics fm = getFontMetrics (getFont ());
            NodePart first = parts.get (0);
            for (NodePart b : parts)
            {
                if (b != first) text += ".";
                text += b.source.key ();
                offsets.add (fm.stringWidth (text));
            }
            setText (text);
            setIcon (part.getIcon (panelParent.isVisible ()));
            setFocusable (true);

            boolean focused = isFocusOwner ();
            getTreeCellRendererComponent (parentSelected  ||  focused, focused);
        }

        /**
            Follows example of openjdk javax.swing.plaf.basic.BasicTreeUI.startEditing()
        **/
        public void startEditing ()
        {
            if (locked) return;

            if (editor.editingNode != null) editor.stopCellEditing ();  // Edit could be in progress on a node title or on any tree, including our own.
            editor.addCellEditorListener (this);
            editingComponent = editor.getTitleEditorComponent (getParentEquationTree ().tree, part, panelParent.isVisible ());
            panelBreadcrumb.add (editingComponent, BorderLayout.CENTER, 0);  // displaces this renderer from the layout manager's center slot
            setVisible (false);  // hide this renderer

            panelBreadcrumb.validate ();
            panelBreadcrumb.repaint ();
            SwingUtilities2.compositeRequestFocus (editingComponent);  // editingComponent is really a container, so we shift focus to the first focusable child of editingComponent
        }

        public void completeEditing (boolean canceled)
        {
            editor.removeCellEditorListener (this);
            if (! canceled) part.setUserObject (editor.getCellEditorValue ());

            setVisible (true);
            panelBreadcrumb.getLayout ().addLayoutComponent (BorderLayout.CENTER, this);  // restore this renderer to the layout manger's center slot
            panelBreadcrumb.remove (editingComponent);  // triggers shift of focus back to this renderer
            editingComponent = null;
        }

        public void editingStopped (ChangeEvent e)
        {
            completeEditing (false);
        }

        public void editingCanceled (ChangeEvent e)
        {
            completeEditing (true);
        }
    };

    public class PanelGraph extends JPanel
    {
        public boolean UIupdated;

        public PanelGraph ()
        {
            setLayout (new BorderLayout ()
            {
                public void layoutContainer (Container target)
                {
                    super.layoutContainer (target);
                    panelParent.setLocation (panelEquationGraph.getLocation ());
                    if (UIupdated)
                    {
                        UIupdated = false;
                        if (panelParent.part != null) panelParent.setSize (panelParent.getPreferredSize ());
                    }
                }
            });
        }

        public void updateUI ()
        {
            super.updateUI ();
            UIupdated = true;
        }

        public boolean isOptimizedDrawingEnabled ()
        {
            return ! panelParent.isVisible ();
        }

        public void loadPart ()
        {
            // Release any existing trees, including their fake roots
            panelParent.clear ();  // Clears our panelEquationTree when not in NODE view.
            panelEquationGraph.clear ();
            // Load trees
            panelEquationGraph.loadPart ();
            panelParent.loadPart ();  // Loads our panelEquationTree when not in NODE view.

            breadcrumbRenderer.update ();
            if (view == NODE) panelParent.setOpen (part.source.getBoolean ("$metadata", "gui", "bounds", "parent")  ||  panelEquationGraph.isEmpty ());
            panelEquationGraph.restoreViewportPosition (createFocus (part));
            validate ();  // In case breadcrumbRenderer changes shape.
        }

        public void clear ()
        {
            if (view == NODE) panelParent.setOpen (false);
            panelParent.clear ();  // Clears our panelEquationTree when not in NODE view.
            panelEquationGraph.clear ();
            breadcrumbRenderer.update ();
            validate ();
        }
    }

    public static class FocusCacheEntry
    {
        boolean    titleFocused = true; // state of GraphNode.titleFocused or PanelEquations.titleFocused, whichever was set most recently
        StoredPath sp;                  // path state of tree, whether as parent or as child
        Point      position;            // when parent: offset of viewport
        String     subpart      = "";   // when parent: name of child which has keyboard focus. If empty, then the parent itself has focus.
    }

    /**
        Captures current state sufficient to replay edit actions.
        This is different than FocusCacheEntry. FCE remembers how to display a particular view,
        while StoredView remembers which view to select.
    **/
    public class StoredView
    {
        public List<String> path;     // to the active tree, which exists for all edit actions except add/change/delete document
        public boolean      asParent; // Indicates that the active tree was editing the part as a parent, rather than a child node in the graph.
        public boolean      first = true;

        public StoredView ()
        {
            saveFocus ();
            if (active == null)
            {
                path = part.getKeyPath ();
                asParent = true;
            }
            else
            {
                path = active.root.getKeyPath ();
                asParent =  active.root == part;
            }
        }

        public void restore ()
        {
            if (first)
            {
                // Avoid clawing back focus on first call, since that is the initial
                // "do" of an edit operation, which may have been completed by a loss of focus.
                first = false;
                return;
            }
            if (path == null) return;

            // Retrieve model
            int depth = path.size ();
            String key0 = path.get (0);
            MNode doc = AppData.models.child (key0);
            if (doc != record)
            {
                load (doc);
                if (depth == 1) return;  // since load() already called loadPart()
            }

            // Determine position of part, as either parent or child node, in terms of graph view
            int end;
            if (asParent)
            {
                end = depth;
                // Hack focus cache to restore open state of parent tree.
                FocusCacheEntry fce = createFocus (path.toArray ());
                fce.subpart = "";
            }
            else
            {
                end = depth - 1;
                // Hack focus cache to focus correct child.
                if (end > 0)
                {
                    FocusCacheEntry fce = createFocus (path.subList (0, end).toArray ());
                    fce.subpart = path.get (end);
                }
            }

            // Grab focus and select correct part
            NodePart p = root;
            for (int i = 1; i < end; i++) p = (NodePart) p.child (path.get (i));
            if (p == part) takeFocus ();  // loadPart() won't run in this case, but we should still take focus.
            else           loadPart (p);
        }
    }
}

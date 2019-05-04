/*
Copyright 2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
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
import gov.sandia.n2a.ui.eq.undo.ChangeOrder;
import gov.sandia.n2a.ui.eq.undo.Outsource;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.jobs.PanelRun;

@SuppressWarnings("serial")
public class PanelEquations extends JPanel
{
    public MNode    record;
    public NodePart root;
    public NodePart part;   // The node that contains the current graph. Can be root or a deeper node (via drill-down).
    public boolean  locked;
    public boolean  open;   // Indicates that top-level GUI is in "open" state (showing panelEquationTree). When in closed state, the GUI shows panelClosed instead.

    public    PanelEquationTree        panelEquationTree;  // For the part that fills the whole view. Subpart equation trees are held in their individual graph nodes.
    public    JPanel                   panelClosed;        // Does same job as panelEquationTree, but for case where tree is closed and editing is primarily in panelEquationGraph.
    public    PanelEquationGraph       panelEquationGraph;
    protected JTree                    treeBreadcrumb;
    public    List<NodePart>           listBreadcrumb = new ArrayList<NodePart> ();
    protected TransferHandler          transferHandler;
    protected EquationTreeCellRenderer renderer = new EquationTreeCellRenderer ();

    // Controls
    protected JButton buttonAddModel;
    protected JButton buttonAddPart;
    protected JButton buttonAddVariable;
    protected JButton buttonAddEquation;
    protected JButton buttonAddAnnotation;
    protected JButton buttonAddReference;
    protected JButton buttonFilter;
    protected JButton buttonRun;
    protected JButton buttonExport;
    protected JButton buttonImport;

    protected JPopupMenu menuPopup;
    protected JPopupMenu menuFilter;
    protected long       menuFilterCanceledAt = 0;

    protected static ImageIcon iconFilterRevoked = ImageUtil.getImage ("filterRevoked.png");
    protected static ImageIcon iconFilterAll     = ImageUtil.getImage ("filter.png");
    protected static ImageIcon iconFilterParam   = ImageUtil.getImage ("filterParam.png");
    protected static ImageIcon iconFilterLocal   = ImageUtil.getImage ("filterLocal.png");

    protected int jobCount = 0;  // for launching jobs

    public PanelEquations ()
    {
        panelEquationGraph = new PanelEquationGraph (this);
        panelEquationTree  = new PanelEquationTree (this, null);

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
                TransferableNode xferNode = null;  // used only to detect if the source is ourselves (equation tree)
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
                JTree tree = null;
                PanelEquationGraph peg = null;
                Component comp = xfer.getComponent ();
                if      (comp instanceof JTree             ) tree = (JTree)              comp;
                else if (comp instanceof PanelEquationGraph) peg  = (PanelEquationGraph) comp;

                TreePath path = null;
                DropLocation dl = xfer.getDropLocation ();
                if (tree != null)
                {
                    if (xfer.isDrop ()) path = ((JTree.DropLocation) dl).getPath ();
                    else                path = tree.getSelectionPath ();
                }

                // Handle internal DnD as a node reordering.
                PanelModel pm = PanelModel.instance;
                if (xferNode != null  &&  xfer.isDrop ()  &&  path != null)  // DnD operation is internal to the tree. (Could also be DnD between N2A windows. For now, reject that case.)
                {
                    NodeBase target = (NodeBase) path.getLastPathComponent ();
                    NodeBase targetParent = (NodeBase) target.getParent ();
                    if (targetParent == null) return false;  // If target is root node

                    NodeBase source = xferNode.getSource ();
                    if (source == null) return false;  // Probably can only happen in a DnD between N2A instances.
                    NodeBase sourceParent = (NodeBase) source.getParent ();

                    if (targetParent != sourceParent) return false;  // Don't drag node outside its containing part.
                    if (! (targetParent instanceof NodePart)) return false;  // Only rearrange children of parts (not of variables or metadata).

                    NodePart parent = (NodePart) targetParent;
                    int indexBefore = parent.getIndex (source);
                    int indexAfter  = parent.getIndex (target);
                    pm.undoManager.add (new ChangeOrder (parent, indexBefore, indexAfter));
                    return true;
                }

                // Determine target node. Create new model if needed.
                NodeBase target = null;
                pm.undoManager.addEdit (new CompoundEdit ());
                if (root == null)
                {
                    pm.undoManager.add (new AddDoc ());
                    target = root;
                }
                if (tree != null)
                {
                    if (xfer.isDrop ()) tree.setSelectionPath (path);
                    target = (NodeBase) path.getLastPathComponent ();
                }

                Point location = null;
                if (peg != null)
                {
                    target = part;
                    location = dl.getDropPoint ();
                    Point vp = peg.vp.getViewPosition ();
                    location.x += vp.x - peg.graphPanel.offset.x;
                    location.y += vp.y - peg.graphPanel.offset.y;
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
                if (node == null) return null;

                MVolatile copy = new MVolatile ();
                node.copy (copy);
                if (node == root) copy.set ("", node.source.key ());  // Remove file information from root node, if that is what we are sending.

                Schema schema = Schema.latest ();
                schema.type = "Clip" + node.getTypeName ();
                StringWriter writer = new StringWriter ();
                try
                {
                    schema.write (writer);
                    for (MNode c : copy) schema.write (c, writer);
                    writer.close ();

                    return new TransferableNode (writer.toString (), node, drag);
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
                            node.delete ((JTree) source, false);
                        }
                    }
                }
                PanelModel.instance.undoManager.endCompoundEdit ();  // This is safe, even if there is no compound edit in progress.
            }
        };

        NodePart nodeBreadcrumb = new NodePart (null)
        {
            @Override
            public Icon getIcon (boolean expanded)
            {
                if (part == null) return iconCompartment;
                return part.getIcon (expanded);
            }

            @Override
            public String getText (boolean expanded, boolean editing)
            {
                if (part == null) return "Select a model from the left, or click the new model button above.";

                String result = "";
                boolean closeFont = false;
                for (NodePart b : listBreadcrumb)  // includes this node
                {
                    result += "." + b.source.key ();
                    if (b == part)
                    {
                        result += "<font color=#80ff80>";
                        closeFont = true;
                    }
                }
                if (closeFont) result += "</font>";
                return "<html>" + result.substring (1) + "</html>";
            }

            @Override
            public float getFontScale ()
            {
                return 2;
            }
        };
        DefaultTreeModel modelBreadcrumb = new DefaultTreeModel (nodeBreadcrumb);
        modelBreadcrumb.insertNodeInto (new DefaultMutableTreeNode (), nodeBreadcrumb, 0);  // Add a fake child, just so nodeBreadcrumb has an expansion icon.
        treeBreadcrumb = new JTree (modelBreadcrumb);
        treeBreadcrumb.setEditable (true);
        treeBreadcrumb.setInvokesStopCellEditing (true);
        treeBreadcrumb.setDragEnabled (true);
        treeBreadcrumb.setToggleClickCount (0);  // Disable expand/collapse on double-click
        treeBreadcrumb.setTransferHandler (transferHandler);
        treeBreadcrumb.setCellRenderer (renderer);
        treeBreadcrumb.addMouseListener (new MouseAdapter ()
        {
            int indent = -1;
            public void mouseClicked (MouseEvent e)
            {
                if (part == null) return;
                if (SwingUtilities.isLeftMouseButton (e)  &&  e.getClickCount () == 1)
                {
                    if (indent < 0)
                    {
                        int left  = (Integer) UIManager.get ("Tree.leftChildIndent");
                        int right = (Integer) UIManager.get ("Tree.rightChildIndent");
                        Icon icon = nodeBreadcrumb.getIcon (false);
                        indent = left + right + icon.getIconWidth () + renderer.getIconTextGap ();
                    }

                    int x = e.getX ();
                    if (x < indent) return;
                    FontMetrics fm = part.getFontMetrics (treeBreadcrumb);
                    String prefix = "";
                    for (NodePart b : listBreadcrumb)
                    {
                        if (b != root) prefix += ".";
                        prefix += b.source.key ();
                        int textWidth = fm.stringWidth (prefix);
                        if (x < indent + textWidth)
                        {
                            panelEquationGraph.load (b);
                            break;
                        }
                    }
                }
            }
        });
        treeBreadcrumb.addTreeWillExpandListener (new TreeWillExpandListener ()
        {
            public void treeWillExpand (TreeExpansionEvent event) throws ExpandVetoException
            {
                if (root != null) setOpen (true);
                throw new ExpandVetoException (event);
            }

            public void treeWillCollapse (TreeExpansionEvent event) throws ExpandVetoException
            {
            }
        });

        panelClosed = Lay.BL (
            "N", treeBreadcrumb,
            "C", panelEquationGraph
        );

        buttonAddModel = new JButton (ImageUtil.getImage ("explore.gif"));
        buttonAddModel.setMargin (new Insets (2, 2, 2, 2));
        buttonAddModel.setFocusable (false);
        buttonAddModel.setToolTipText ("New Model");
        buttonAddModel.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                panelEquationTree.tree.stopEditing ();
                PanelModel.instance.undoManager.add (new AddDoc ());
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

        buttonFilter = new JButton (ImageUtil.getImage ("filter.png"));
        buttonFilter.setMargin (new Insets (2, 2, 2, 2));
        buttonFilter.setFocusable (false);
        buttonFilter.setToolTipText ("Filter Equations");
        buttonFilter.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if (System.currentTimeMillis () - menuFilterCanceledAt > 500)  // A really ugly way to prevent the button from re-showing the menu if it was canceled by clicking the button.
                {
                    menuFilter.show (buttonFilter, 0, buttonFilter.getHeight ());
                }
            }
        });

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
                buttonFilter,
                Box.createHorizontalStrut (15),
                buttonRun,
                Box.createHorizontalStrut (15),
                buttonExport,
                buttonImport,
                "hgap=5,vgap=1"
            ),
            "C", panelClosed
        );

        // Context Menu
        JMenuItem itemAddPart = new JMenuItem ("Add Part", ImageUtil.getImage ("comp.gif"));
        itemAddPart.setActionCommand ("Part");
        itemAddPart.addActionListener (listenerAdd);

        JMenuItem itemAddVariable = new JMenuItem ("Add Variable", ImageUtil.getImage ("delta.png"));
        itemAddVariable.setActionCommand ("Variable");
        itemAddVariable.addActionListener (listenerAdd);

        JMenuItem itemAddEquation = new JMenuItem ("Add Equation", ImageUtil.getImage ("equation.png"));
        itemAddEquation.setActionCommand ("Equation");
        itemAddEquation.addActionListener (listenerAdd);

        JMenuItem itemAddAnnotation = new JMenuItem ("Add Annotation", ImageUtil.getImage ("edit.gif"));
        itemAddAnnotation.setActionCommand ("Annotation");
        itemAddAnnotation.addActionListener (listenerAdd);

        JMenuItem itemAddReference = new JMenuItem ("Add Reference", ImageUtil.getImage ("book.gif"));
        itemAddReference.setActionCommand ("Reference");
        itemAddReference.addActionListener (listenerAdd);

        JMenuItem itemDelete = new JMenuItem ("Delete", ImageUtil.getImage ("remove.gif"));
        itemDelete.addActionListener (listenerDelete);

        menuPopup = new JPopupMenu ();
        menuPopup.add (itemAddPart);
        menuPopup.add (itemAddVariable);
        menuPopup.add (itemAddEquation);
        menuPopup.add (itemAddAnnotation);
        menuPopup.add (itemAddReference);
        menuPopup.addSeparator ();
        menuPopup.add (itemDelete);

        // Filter menu

        JRadioButtonMenuItem itemFilterRevoked = new JRadioButtonMenuItem ("Revoked");
        itemFilterRevoked.addActionListener (listenerFilter);

        JRadioButtonMenuItem itemFilterAll     = new JRadioButtonMenuItem ("All");
        itemFilterAll    .addActionListener (listenerFilter);

        JRadioButtonMenuItem itemFilterPublic  = new JRadioButtonMenuItem ("Parameters");
        itemFilterPublic .addActionListener (listenerFilter);

        JRadioButtonMenuItem itemFilterLocal   = new JRadioButtonMenuItem ("Local");
        itemFilterLocal  .addActionListener (listenerFilter);

        int filterLevel = AppData.state.getOrDefault (FilteredTreeModel.PARAM, "PanelModel", "filter");
        panelEquationTree.model.setFilterLevel (filterLevel, panelEquationTree.tree);  // root is still null, so this has no immediate effect
        switch (filterLevel)
        {
            case FilteredTreeModel.REVOKED:
                buttonFilter.setIcon (iconFilterRevoked);
                itemFilterRevoked.setSelected (true);
                break;
            case FilteredTreeModel.ALL:
                buttonFilter.setIcon (iconFilterAll);
                itemFilterAll.setSelected (true);
                break;
            case FilteredTreeModel.PARAM:
                buttonFilter.setIcon (iconFilterParam);
                itemFilterPublic.setSelected (true);
                break;
            case FilteredTreeModel.LOCAL:
                buttonFilter.setIcon (iconFilterLocal);
                itemFilterLocal.setSelected (true);
                break;
        }

        menuFilter = new JPopupMenu ();
        menuFilter.add (itemFilterRevoked);
        menuFilter.add (itemFilterAll);
        menuFilter.add (itemFilterPublic);
        menuFilter.add (itemFilterLocal);
        menuFilter.addPopupMenuListener (new PopupMenuListener ()
        {
            public void popupMenuWillBecomeVisible (PopupMenuEvent e)
            {
            }

            public void popupMenuWillBecomeInvisible (PopupMenuEvent e)
            {
            }

            public void popupMenuCanceled (PopupMenuEvent e)
            {
                menuFilterCanceledAt = System.currentTimeMillis ();
            }
        });

        ButtonGroup groupFilter = new ButtonGroup ();
        groupFilter.add (itemFilterRevoked);
        groupFilter.add (itemFilterAll);
        groupFilter.add (itemFilterPublic);
        groupFilter.add (itemFilterLocal);

        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                MNode lastUsedNode = null;
                String lastUsedKey = AppData.state.get ("PanelModel", "lastUsed");
                if (! lastUsedKey.isEmpty ()) lastUsedNode = AppData.models.child (lastUsedKey);
                if (lastUsedNode != null)
                {
                    load (lastUsedNode);
                    panelEquationTree.tree.requestFocusInWindow ();
                }
            }
        });
    }

    public void updateUI ()
    {
        EquationTreeCellRenderer.earlyUpdateUI ();
        super.updateUI ();
    }

    public void load (MNode doc)
    {
        if (record == doc) return;

        if (record != null)
        {
            panelEquationTree.tree.stopEditing ();
            panelEquationTree.saveFocus (record);
            panelEquationGraph.saveFocus ();
        }

        record = doc;
        updateLock ();
        try
        {
            root = new NodePart (new MPart ((MPersistent) record));
            root.build ();
            root.findConnections ();
            panelEquationTree.load ();
            panelEquationGraph.load (root);
        }
        catch (Exception e)
        {
            System.err.println ("Exception while parsing model: " + e);
            e.printStackTrace ();
        }
    }

    /**
        Informs us that some other code deleted a document from the DB.
        We only respond if it happens to be on display.
    **/
    public void recordDeleted (MNode doc)
    {
        if (doc != record) return;
        record = null;
        root   = null;
        panelEquationTree.recordDeleted (doc);
        panelEquationGraph.recordDeleted ();
    }

    public void updateLock ()
    {
        locked = ! AppData.models.isWriteable (record);
        panelEquationTree.tree.setEditable (! locked);
    }

    public void checkVisible ()
    {
        if (AppData.models.isVisible (record)) updateLock ();
        else                                   recordDeleted (record);
    }

    public void setOpen (boolean open)
    {
        if (this.open == open) return;
        this.open = open;
        if (open)
        {
            remove (panelClosed);
            panelEquationGraph.graphPanel.clear ();  // releases fake roots on subparts
            add (panelEquationTree, BorderLayout.CENTER);
            panelEquationTree.model.setRoot (part);
        }
        else
        {
            remove (panelEquationTree);
            panelEquationTree.model.setRoot (null);
            add (panelClosed, BorderLayout.CENTER);
            panelEquationGraph.load (part);
        }
        // TODO: repaint?
    }

    public PanelEquationTree getActiveTree ()
    {
        if (open) return panelEquationTree;
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager ().getFocusOwner ();
        if (! (focus instanceof JTree)) return null;
        Container c = focus.getParent ().getParent ();  // JTree -> JViewport -> JScrollPane (of which PanelEquationTree is an extension)
        if (c instanceof PanelEquationTree) return (PanelEquationTree) c;
        return null;
    }

    public void yieldFocus ()
    {
        PanelEquationTree pet = getActiveTree ();
        if (pet != null) pet.yieldFocus ();
    }

    public void takeFocus ()
    {
        PanelEquationTree pet = getActiveTree ();
        if (pet == null) return;
        pet.tree.requestFocusInWindow ();
    }

    public void updateBreadcrumbs (NodePart part)
    {
        int index = listBreadcrumb.indexOf (part);
        if (index < 0)
        {
            listBreadcrumb.clear ();
            NodePart p = part;
            while (p != null)
            {
                listBreadcrumb.add (0, p);
                p = (NodePart) p.getParent ();
            }
            index = listBreadcrumb.size () - 1;
        }

        if (! open)
        {
            treeBreadcrumb.paintImmediately (treeBreadcrumb.getBounds ());
        }
    }

    ActionListener listenerAdd = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            String type = e.getActionCommand ();
            if (record == null)
            {
                PanelModel.instance.undoManager.add (new AddDoc ());
                if (type.equals ("Part")) return;  // Since root is itself a Part, don't create another one. For anything else, fall through and add it to the newly-created model.
            }

            PanelEquationTree pet = getActiveTree ();
            if (pet == null)
            {
                // With no active tree, the only thing we can add is a part.
                if (! type.equals ("Part")) return;
                // TODO: 
            }
            else
            {
                pet.tree.stopEditing ();
                pet.addAtSelected (type);
            }
        }
    };

    ActionListener listenerDelete = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            panelEquationTree.tree.stopEditing ();  // It may seem odd to save a cell just before destroying it, but this gives cleaner UI painting.
            panelEquationTree.deleteSelected ();
        }
    };

    ActionListener listenerMove = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            panelEquationTree.tree.stopEditing ();
            panelEquationTree.moveSelected (Integer.valueOf (e.getActionCommand ()));
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
            if (panelEquationTree.tree.isEditing ())
            {
                panelEquationTree.tree.stopEditing ();
                mtp.setPreferredFocus (PanelModel.instance, panelEquationTree.tree);  // Because tree does not reclaim the focus before focus shifts to the run tab.
            }

            String simulatorName = root.source.get ("$metadata", "backend");  // Note that "record" is the raw model, while "root.source" is the collated model.
            final Backend simulator = Backend.getBackend (simulatorName);
            MNode runs = AppData.runs;
            String jobKey = new SimpleDateFormat ("yyyy-MM-dd-HHmmss", Locale.ROOT).format (new Date ()) + "-" + jobCount++;
            final MNode job = runs.set ("", jobKey);  // Create the dir and model doc
            job.merge (root.source);
            job.set ("\"" + record.key () + "\"", "$inherit");
            ((MDoc) job).save ();  // Force directory (and job file) to exist, so Backends can work with the dir.

            new Thread ()
            {
                public void run ()
                {
                    try
                    {
                        simulator.start (job);
                    }
                    catch (Exception e)
                    {
                        // TODO: Instead of throwing an exception, simulation should record all errors/warnings in a file in the job dir.
                        e.printStackTrace ();
                    }
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
            panelEquationTree.tree.stopEditing ();

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

    ActionListener listenerFilter = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            panelEquationTree.tree.stopEditing ();

            String action = e.getActionCommand ();
            if (action.equals ("Revoked"))
            {
                panelEquationTree.model.setFilterLevel (FilteredTreeModel.REVOKED, panelEquationTree.tree);
                buttonFilter.setIcon (iconFilterRevoked);
            }
            else if (action.equals ("All"))
            {
                panelEquationTree.model.setFilterLevel (FilteredTreeModel.ALL, panelEquationTree.tree);
                buttonFilter.setIcon (iconFilterAll);
            }
            else if (action.equals ("Parameters"))
            {
                panelEquationTree.model.setFilterLevel (FilteredTreeModel.PARAM, panelEquationTree.tree);
                buttonFilter.setIcon (iconFilterParam);
            }
            else if (action.equals ("Local"))
            {
                panelEquationTree.model.setFilterLevel (FilteredTreeModel.LOCAL, panelEquationTree.tree);
                buttonFilter.setIcon (iconFilterLocal);
            }
            AppData.state.set (FilteredTreeModel.filterLevel, "PanelModel", "filter");
        }
    };
}

/*
Copyright 2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Insets;
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
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTree;
import javax.swing.TransferHandler;
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
    public boolean  open;   // Indicates that root appears as a tree rather than a graph. (All other levels always appear as graph.)

    protected JPanel                   panelCenter;
    protected JPanel                   panelBreadcrumb;
    public    PanelEquationGraph       panelEquationGraph;
    public    PanelEquationTree        panelEquationTree;  // To display root as a tree.
    protected PanelEquationTree        active;             // Tree which most recently received focus. Could be panelEquationTree or a GraphNode.panel.
    protected List<NodePart>           listBreadcrumb = new ArrayList<NodePart> ();
    protected TransferHandler          transferHandler;
    protected EquationTreeCellRenderer renderer       = new EquationTreeCellRenderer ();
    protected MVolatile                focusCache     = new MVolatile ();

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

    protected static ImageIcon iconViewGraph = ImageUtil.getImage ("viewGraph.png");
    protected static ImageIcon iconViewTree  = ImageUtil.getImage ("explore.gif");

    protected static JLabel noModel = new JLabel ("Select from left or click New Model above.");

    protected int jobCount = 0;  // for launching jobs

    public PanelEquations ()
    {
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
                DropLocation dl = null;
                if (xfer.isDrop ()) dl = xfer.getDropLocation ();
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
                    if (dl != null)
                    {
                        location = dl.getDropPoint ();
                        Point vp = peg.vp.getViewPosition ();
                        location.x += vp.x - peg.graphPanel.offset.x;
                        location.y += vp.y - peg.graphPanel.offset.y;
                    }
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

        panelEquationGraph = new PanelEquationGraph (this);
        panelEquationTree  = new PanelEquationTree (this, null);
        panelBreadcrumb    = Lay.WL ("L", Box.createHorizontalStrut (5), noModel, "hgap=0");
        panelCenter        = Lay.BL (
            "N", panelBreadcrumb,
            "C", panelEquationGraph  // Initial state is open==false and graph showing.
        );

        buttonAddModel = new JButton (ImageUtil.getImage ("document.png"));
        buttonAddModel.setMargin (new Insets (2, 2, 2, 2));
        buttonAddModel.setFocusable (false);
        buttonAddModel.setToolTipText ("New Model");
        buttonAddModel.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if (active != null) active.tree.stopEditing ();
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
            "C", panelCenter
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

        FilteredTreeModel.filterLevel = AppData.state.getOrDefault (FilteredTreeModel.PARAM, "PanelModel", "filter");
        switch (FilteredTreeModel.filterLevel)
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
                if (lastUsedNode != null) load (lastUsedNode);
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
        boolean open = false;
        if (part == root)
        {
            FocusCacheEntry fce = getFocus (root);
            if (fce != null) open = fce.open;
        }

        if (this.open != open)
        {
            this.open = open;
            this.part = null;  // Force part update to run.
            if (open)
            {
                panelCenter.remove (panelEquationGraph);
                panelEquationGraph.clear ();  // releases fake roots on subparts
                panelCenter.add (panelEquationTree, BorderLayout.CENTER);
            }
            else
            {
                panelCenter.remove (panelEquationTree);
                panelEquationTree.clear ();
                panelCenter.add (panelEquationGraph, BorderLayout.CENTER);
            }
        }

        if (this.part == part) return;
        this.part = part;
        active = null;
        if (open) panelEquationTree.loadPart (part);
        else      panelEquationGraph.loadPart ();
        updateBreadcrumbs ();
        panelCenter.validate ();
        panelCenter.repaint ();
        takeFocus ();
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
        if (open) panelEquationTree.clear ();
        else      panelEquationGraph.clear ();
        updateBreadcrumbs ();
        panelCenter.validate ();
        panelCenter.repaint ();
    }

    public void updateLock ()
    {
        locked = ! AppData.models.isWriteable (record);
        // The following are safe to call, even when record is not fully loaded, and despite which panel is active.
        panelEquationTree.updateLock ();
        panelEquationGraph.updateLock ();
    }

    public void checkVisible ()
    {
        if (AppData.models.isVisible (record)) updateLock ();
        else                                   recordDeleted (record);
    }

    public void saveFocus ()
    {
        if (root == null) return;

        // Save open state for document root
        FocusCacheEntry fce;
        if (part == root)
        {
            fce = getFocus (root);
            if (fce == null)
            {
                fce = new FocusCacheEntry ();
                focusCache.setObject (fce, root.source.key ());
            }
            fce.open = open;
            if (open)
            {
                fce.sp = panelEquationTree.saveFocus (fce.sp);
                return;  // No graph information to save, so skip rest of this function.
            }
        }

        // Save part focus information
        fce = createFocus (part);
        fce.position = panelEquationGraph.saveFocus ();
        if (active != null)
        {
            fce.subpart = active.root.source.key ();

            // TODO: save state of all node trees?
            fce = createFocus (active.root);
            fce.sp = active.saveFocus (fce.sp);
        }
    }

    public FocusCacheEntry getFocus (NodePart p)
    {
        return (FocusCacheEntry) focusCache.getObject (p.getKeyPath ().toArray ());
    }

    /**
        Same as getFocus(), but creates an entry if it doesn't already exist.
    **/
    public FocusCacheEntry createFocus (NodePart p)
    {
        Object[] keyArray = p.getKeyPath ().toArray ();
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
        if (open) panelEquationTree .takeFocus ();
        else      panelEquationGraph.takeFocus ();
    }

    public void resetBreadcrumbs ()
    {
        listBreadcrumb.clear ();
        updateBreadcrumbs ();
        panelBreadcrumb.validate ();
        panelBreadcrumb.repaint ();
    }

    public void updateBreadcrumbs ()
    {
        panelBreadcrumb.removeAll ();
        panelBreadcrumb.add (Box.createHorizontalStrut (5));

        int index = listBreadcrumb.indexOf (part);
        if (index < 0)
        {
            listBreadcrumb.clear ();
            NodePart p = part;
            while (p != null)
            {
                listBreadcrumb.add (0, p);
                p = (NodePart) p.getTrueParent ();
            }
            index = listBreadcrumb.size () - 1;
        }
        if (part == root  &&  open) index = -1;

        final Color inactive = new Color (0x80FF80);
        int last = listBreadcrumb.size () - 1;
        if (last < 0) panelBreadcrumb.add (noModel);
        for (int i = 0; i <= last; i++)
        {
            final NodePart b = listBreadcrumb.get (i);
            String key = b.source.key ();
            String text;
            if (i > 0) text = "." + key;
            else       text = key;

            JLabel label = new JLabel (text);
            label.setToolTipText ("Select part");
            label.setForeground (i <= index ? Color.black : inactive);
            label.addMouseListener (new MouseAdapter ()
            {
                public void mouseClicked (MouseEvent me)
                {
                    drill (b);
                }
            });

            panelBreadcrumb.add (label);
        }
    }

    public void drill (NodePart nextPart)
    {
        saveFocus ();
        FocusCacheEntry fce = getFocus (part);
        if (nextPart == root) fce.open = ! open;  // Not really a drill down. Toggle between graph and tree views.
        else                  fce.subpart = nextPart.source.key ();
        loadPart (nextPart);
    }

    ActionListener listenerAdd = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            String type = e.getActionCommand ();
            if (record == null)
            {
                PanelModel.instance.undoManager.add (new AddDoc ());  // New doc should always display in graph view.
                if (! type.equals ("Part"))
                {
                    FocusCacheEntry fce = createFocus (root);
                    fce.open = true;
                    loadPart (root);
                    active = panelEquationTree;  // Force code below to add non-part type
                }
            }

            if (active == null)
            {
                // With no active tree, the only thing we can add is a part. NodePart.add() does this check for us.
                NodePart editMe = (NodePart) part.add (type, null, new MVolatile (), null);
                if (editMe == null) return;
                editMe.graph.panel.takeFocus ();
            }
            else
            {
                active.tree.stopEditing ();
                active.addAtSelected (type);
            }
        }
    };

    ActionListener listenerDelete = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            if (active == null)  // Either nothing is selected or a graph node is selected.
            {
                panelEquationGraph.deleteSelected ();
            }
            else
            {
                active.tree.stopEditing ();  // It may seem odd to save a cell just before destroying it, but this gives cleaner UI painting.
                active.deleteSelected ();
            }
        }
    };

    ActionListener listenerMove = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            if (active == null) return;
            active.tree.stopEditing ();
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
            if (active != null  &&  active.tree.isEditing ())
            {
                active.tree.stopEditing ();
                mtp.setPreferredFocus (PanelModel.instance, active.tree);  // Because tree does not reclaim the focus before focus shifts to the run tab.
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
            if (active != null) active.tree.stopEditing ();

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
            if (open) panelEquationTree.updateFilterLevel ();
            else      panelEquationGraph.updateFilterLevel ();
        }
    };

    public static class FocusCacheEntry
    {
        boolean    open;      // state of PanelEquations.open when this is the document root (only used for top-level part)
        StoredPath sp;        // of tree when this part is the root
        Point      position;  // of viewport for graph
        String     subpart;   // Name of graph node (sub-part) whose tree has keyboard focus
    }

    /**
        Captures current state sufficient to replay edit actions.
        This is different than FocusCacheEntry because it has to stand alone,
        rather than reside in a key-value store.
    **/
    public class StoredView
    {
        public List<String> path;  // to the active tree, which exists for all edit actions except add/change/delete document
        public boolean      open;  // For model root only, indicates whether tree or graph view was active.
        public boolean      first = true;

        public StoredView ()
        {
            if (active == null) path = root.getKeyPath ();  // Which should just be the model name itself.
            else                path = active.root.getKeyPath ();
            open = PanelEquations.this.open;
        }

        public void restore ()
        {
            // Hack focus cache to switch to the right view.
            int depth = path.size ();
            String key0 = path.get (0);
            if (depth == 1)
            {
                FocusCacheEntry fce = (FocusCacheEntry) focusCache.getObject (key0);
                if (fce == null)
                {
                    fce = new FocusCacheEntry ();
                    focusCache.setObject (fce, key0);
                }
                fce.open = open;
            }

            MNode doc = AppData.models.child (key0);
            if (doc != record)
            {
                load (doc);
                if (depth == 1) return;  // since load() already called loadPart()
            }

            // Hack focus cache to focus correct subpart
            int last = depth - 1;
            Object[] keyPath = path.subList (0, last).toArray ();
            FocusCacheEntry fce = (FocusCacheEntry) focusCache.getObject (keyPath);
            if (fce == null)
            {
                fce = new FocusCacheEntry ();
                focusCache.setObject (fce, keyPath);
            }
            fce.subpart = path.get (last);

            NodePart p = root;
            for (int i = 1; i < last; i++) p = (NodePart) p.child (path.get (i));
            if (p == part)
            {
                // loadPart() won't do anything, so call takeFocus() directly.
                // However, avoid clawing back focus on first call, since that is the initial
                // "do" of an edit operation, which may have been completed by a loss of focus.
                if (! first) takeFocus ();
                first = false;
            }
            else
            {
                loadPart (p);
            }
        }
    }
}

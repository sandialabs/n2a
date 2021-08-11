/*
Copyright 2019-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.eq;

import java.awt.AlphaComposite;
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
import java.awt.Image;
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
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
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

import gov.sandia.n2a.backend.internal.InternalBackend;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.db.Schema;
import gov.sandia.n2a.db.MNode.Visitor;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.host.Remote;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.PluginManager;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Export;
import gov.sandia.n2a.plugins.extpoints.ExportModel;
import gov.sandia.n2a.plugins.extpoints.Import;
import gov.sandia.n2a.plugins.extpoints.ImportModel;
import gov.sandia.n2a.ui.CompoundEdit;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.MainTabbedPane;
import gov.sandia.n2a.ui.UndoManager;
import gov.sandia.n2a.ui.Undoable;
import gov.sandia.n2a.ui.eq.PanelEquationGraph.GraphPanel;
import gov.sandia.n2a.ui.eq.tree.NodeBase;
import gov.sandia.n2a.ui.eq.tree.NodePart;
import gov.sandia.n2a.ui.eq.tree.NodeVariable;
import gov.sandia.n2a.ui.eq.undo.AddDoc;
import gov.sandia.n2a.ui.eq.undo.AddEditable;
import gov.sandia.n2a.ui.eq.undo.AddPart;
import gov.sandia.n2a.ui.eq.undo.ChangeAnnotations;
import gov.sandia.n2a.ui.eq.undo.ChangeOrder;
import gov.sandia.n2a.ui.eq.undo.CompoundEditView;
import gov.sandia.n2a.ui.eq.undo.UndoableView;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.jobs.NodeJob;
import gov.sandia.n2a.ui.jobs.PanelRun;
import gov.sandia.n2a.ui.ref.ExportBibliography;
import gov.sandia.n2a.ui.studies.PanelStudy;

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
    public int view = AppData.state.getOrDefault (BOTTOM, "PanelModel", "view");

    protected JSplitPane               split;
    protected PanelGraph               panelGraph;
    protected JPanel                   panelBreadcrumb;
    protected boolean                  titleFocused           = true;
    protected boolean                  parentSelected;
    public    PanelEquationGraph       panelEquationGraph;
    public    GraphParent              panelParent;
    public    PanelEquationTree        panelEquationTree;  // For property-panel style display, this is the single tree for editing.
    public    PanelEquationTree        active;             // Tree which most recently received focus. Could be panelEquationTree or a GraphNode.panelEquations.
    protected TransferHandler          transferHandler        = new EquationTransferHandler ();
    protected EquationTreeCellRenderer renderer               = new EquationTreeCellRenderer ();
    protected EquationTreeCellEditor   editor                 = new EquationTreeCellEditor (renderer);
    public    BreadcrumbRenderer       breadcrumbRenderer     = new BreadcrumbRenderer ();  // References transferHandler in constructor, so must wait till after transferHandler is constructed.
    protected MVolatile                focusCache             = new MVolatile ();

    // Controls
    protected JButton       buttonAddModel;
    protected JButton       buttonAddPart;
    protected JButton       buttonAddVariable;
    protected JButton       buttonAddEquation;
    protected JButton       buttonAddAnnotation;
    protected JButton       buttonAddReference;
    protected JButton       buttonMakePin;
    protected JButton       buttonWatch;
    protected JToggleButton buttonFilterInherited;
    protected JToggleButton buttonFilterLocal;
    protected JToggleButton buttonFilterParam;
    protected JToggleButton buttonFilterRevoked;
    protected JButton       buttonView;
    public    JButton       buttonRun;
    protected JButton       buttonStudy;
    protected JButton       buttonExport;
    protected JButton       buttonImport;

    protected JMenuItem  itemAddPart;
    protected JPopupMenu menuPopup;
    protected JPopupMenu menuView;
    protected JPopupMenu menuFilter;
    protected long       menuCanceledAt;

    protected static ImageIcon iconViewNode   = ImageUtil.getImage ("viewGraph.png");
    protected static ImageIcon iconViewSide   = ImageUtil.getImage ("viewSide.png");
    protected static ImageIcon iconViewBottom = ImageUtil.getImage ("viewBottom.png");

    protected static String noModel = "Select a model from the left, or click the New Model button above.";

    protected int jobCount = 0;  // for launching jobs

    public static ImageIcon colorize (ImageIcon mask, Color color)
    {
        Image image = mask.getImage ();
        int w = image.getWidth (null);
        int h = image.getHeight (null);
        BufferedImage result = new BufferedImage (w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics ();
        g.drawImage (image, 0, 0, null);
        g.setComposite (AlphaComposite.SrcAtop);
        g.setColor (color);
        g.fillRect (0, 0, w, h);
        g.dispose ();
        return new ImageIcon (result);
    }

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
                MainFrame.instance.undoManager.apply (add);
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

        buttonMakePin = new JButton (ImageUtil.getImage ("pin.png"));
        buttonMakePin.setMargin (new Insets (2, 2, 2, 2));
        buttonMakePin.setFocusable (false);
        buttonMakePin.setToolTipText ("Make Pin");
        buttonMakePin.addActionListener (listenerMakePin);

        buttonWatch = new JButton (ImageUtil.getImage ("watch.png"));
        buttonWatch.setMargin (new Insets (2, 2, 2, 2));
        buttonWatch.setFocusable (false);
        buttonWatch.setToolTipText ("Watch Variable");
        buttonWatch.addActionListener (listenerWatch);

        FilteredTreeModel.showInherited = AppData.state.getOrDefault (true,  "PanelModel", "filter", "inherited");
        FilteredTreeModel.showLocal     = AppData.state.getOrDefault (true,  "PanelModel", "filter", "local");
        FilteredTreeModel.showParam     = AppData.state.getOrDefault (true,  "PanelModel", "filter", "param");
        FilteredTreeModel.showRevoked   = AppData.state.getOrDefault (false, "PanelModel", "filter", "revoked");

        ImageIcon iconFilter       = ImageUtil.getImage ("filter.png");
        ImageIcon iconFilterFilled = ImageUtil.getImage ("filterFilled.png");

        buttonFilterInherited = new JToggleButton (colorize (iconFilter, Color.blue));
        buttonFilterInherited.setSelectedIcon (colorize (iconFilterFilled, Color.blue));
        buttonFilterInherited.setMargin (new Insets (2, 2, 2, 2));
        buttonFilterInherited.setFocusable (false);
        buttonFilterInherited.setSelected (FilteredTreeModel.showInherited);
        buttonFilterInherited.setToolTipText ("Show Inherited Equations");
        buttonFilterInherited.addActionListener (listenerFilter);

        buttonFilterLocal = new JToggleButton (iconFilter);  // black
        buttonFilterLocal.setSelectedIcon (iconFilterFilled);
        buttonFilterLocal.setMargin (new Insets (2, 2, 2, 2));
        buttonFilterLocal.setFocusable (false);
        buttonFilterLocal.setSelected (FilteredTreeModel.showLocal);
        buttonFilterLocal.setToolTipText ("Show Local Equations");
        buttonFilterLocal.addActionListener (listenerFilter);

        Color darkGreen = new Color (0, 128, 0);
        buttonFilterParam = new JToggleButton (colorize (iconFilter, darkGreen));
        buttonFilterParam.setSelectedIcon (colorize (iconFilterFilled, darkGreen));
        buttonFilterParam.setMargin (new Insets (2, 2, 2, 2));
        buttonFilterParam.setFocusable (false);
        buttonFilterParam.setSelected (FilteredTreeModel.showParam);
        buttonFilterParam.setToolTipText ("Show Parameters Only (disable other filters)");
        buttonFilterParam.addActionListener (listenerFilter);

        Color darkRed = new Color (192, 0, 0);
        buttonFilterRevoked = new JToggleButton (colorize (iconFilter, darkRed));
        buttonFilterRevoked.setSelectedIcon (colorize (iconFilterFilled, darkRed));
        buttonFilterRevoked.setMargin (new Insets (2, 2, 2, 2));
        buttonFilterRevoked.setFocusable (false);
        buttonFilterRevoked.setSelected (FilteredTreeModel.showRevoked);
        buttonFilterRevoked.setToolTipText ("Show Revoked Equations");
        buttonFilterRevoked.addActionListener (listenerFilter);

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

        buttonRun = new JButton (ImageUtil.getImage ("run-16.png"));
        buttonRun.setMargin (new Insets (2, 2, 2, 2));
        buttonRun.setFocusable (false);
        buttonRun.setEnabled (false);  // Don't let users start a run until the Runs tab loads existing jobs. See PanelRun.ctor
        buttonRun.setToolTipText ("Run");
        buttonRun.addActionListener (listenerRun);

        buttonStudy = new JButton (ImageUtil.getImage ("study-16.png"));
        buttonStudy.setMargin (new Insets (2, 2, 2, 2));
        buttonStudy.setFocusable (false);
        buttonStudy.setEnabled (false);  // Similar restriction as for Run
        buttonStudy.setToolTipText ("Multi-run Study");
        buttonStudy.addActionListener (listenerStudy);

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
                buttonMakePin,
                Box.createHorizontalStrut (15),
                buttonWatch,
                Box.createHorizontalStrut (15),
                buttonFilterParam,
                buttonFilterInherited,
                buttonFilterLocal,
                buttonFilterRevoked,
                buttonView,
                Box.createHorizontalStrut (15),
                buttonRun,
                buttonStudy,
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

        JMenuItem itemMakePin = new JMenuItem ("Make Pin", ImageUtil.getImage ("pin.png"));
        itemMakePin.addActionListener (listenerMakePin);

        JMenuItem itemWatch = new JMenuItem ("Watch", ImageUtil.getImage ("watch.png"));
        itemWatch.addActionListener (listenerWatch);

        menuPopup = new JPopupMenu ();
        menuPopup.add (itemAddPart);
        menuPopup.add (itemAddVariable);
        menuPopup.add (itemAddEquation);
        menuPopup.add (itemAddAnnotation);
        menuPopup.add (itemAddReference);
        menuPopup.add (itemMakePin);
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
            root = new NodePart (new MPart (record));
            root.build ();
            root.findConnections ();
            root.findPins ();
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
                saveFocus ();
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
                saveFocus ();
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

    public void renameFocus (List<String> oldPath, String newName)
    {
        MNode old = focusCache.child (oldPath.toArray ());
        if (old == null) return;
        old.parent ().move (old.key (), newName);
    }

    public void deleteFocus (NodePart p)
    {
        focusCache.clear (p.getKeyPath ().toArray ());
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
        if (parentSelected == value) return;
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
        else
        {
            FocusCacheEntry fce = createFocus (nextPart);
            fce.subpart = "";
        }
        loadPart (nextPart);
    }

    public void drillUp ()
    {
        NodePart parent = (NodePart) part.getTrueParent ();
        if (parent != null) drill (parent);
    }

    public void updateGUI ()
    {
        breadcrumbRenderer.updateSelected (); // Update icon.
        panelParent.animate ();               // Sets size of parent panel from metadata, in getPreferredSize().
        panelEquationGraph.updateGUI ();      // Updates canvas position.
    }

    ActionListener listenerAdd = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            String type = e.getActionCommand ();
            Point location = null;
            GraphPanel gp = panelEquationGraph.graphPanel;
            if (e.getSource () == itemAddPart)
            {
                Component invoker = menuPopup.getInvoker ();
                if (invoker == gp)
                {
                    location = gp.popupLocation;
                    location.x -= gp.offset.x;
                    location.y -= gp.offset.y;
                }
            }
            // To be strictly consistent, adding a variable (or other non-part type) on a closed
            // node should put it in the parent. However, it seems more likely that the
            // user expects it to go into the selected graph node, even if closed. OTOH,
            // inserting a part on a closed node should always go to the parent.
            if (type.equals ("Part")  &&  location == null  &&  active != null  &&  active.root.graph != null  &&  ! active.root.graph.open)
            {
                location = active.root.graph.getLocation ();
                location.x += 100 - gp.offset.x;
                location.y += 100 - gp.offset.y;
            }

            if (record == null)
            {
                AddDoc add = new AddDoc ();
                MainFrame.instance.undoManager.apply (add);
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
                JTree tree = null;
                if (view == NODE) tree = panelParent.panelEquationTree.tree;
                else if (panelEquationTree.root == part) tree = panelEquationTree.tree;

                NodePart editMe = (NodePart) part.add (type, tree, null, location);
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

    ActionListener listenerMakePin = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            // Determine context
            NodePart context = null;
            String   pin     = null;
            if (e.getSource () == buttonMakePin)  // From button bar, so use keyboard focus.
            {
                if (active != null) context = active.root;
            }
            else  // From popup menu, so use invoking object.
            {
                Component invoker = menuPopup.getInvoker ();
                GraphPanel gp = panelEquationGraph.graphPanel;
                if (invoker instanceof JTree)
                {
                    context = (NodePart) ((JTree) invoker).getModel ().getRoot ();
                }
                else if (invoker instanceof GraphNode  ||  invoker instanceof GraphNode.TitleRenderer)
                {
                    GraphNode gn = PanelModel.getGraphNode (invoker);
                    context = gn.node;
                }
                else if (invoker == gp)
                {
                    GraphNode gn = gp.findNodeAt (gp.popupLocation, true);
                    if (gn != null)
                    {
                        pin = gn.findPinAt (gp.popupLocation);
                        context = gn.node;
                    }
                }
            }
            if (context == null  ||  context == part) return;  // Only process graph nodes, not parent node.

            // Bind part to an IO pin
            UndoManager um = MainFrame.instance.undoManager;
            if (pin == null)
            {
                if (context.source.child ("$metadata", "gui", "pin") == null)
                {
                    if (context.connectionBindings != null  &&  ! context.connectionBindings.containsValue (null))
                    {
                        // Need to disconnect one endpoint for connection to expose a pin.
                        // Pick the first one in alphabetical order. This will probably be "A", which is usually the intent.
                        String alias = context.connectionBindings.keySet ().iterator ().next ();
                        NodeVariable variable = (NodeVariable) context.child (alias);
                        um.addEdit (new CompoundEdit ());
                        panelEquationGraph.graphPanel.mouseListener.disconnect (um, variable);
                    }
                    MNode metadata = new MVolatile ();
                    metadata.set ("", "gui", "pin");  // Activate default pin behavior appropriate for the part.
                    um.apply (new ChangeAnnotations (context, metadata));
                    um.endCompoundEdit ();
                }
            }
            else
            {
                String[] pieces = pin.split ("\\.", 2);
                String pinSide = pieces[0];
                String pinKey  = pieces[1];

                MNode metadata = new MVolatile ();
                if (pinSide.equals ("in"))
                {
                    metadata.set ("",     "gui", "pin", "in", pinKey, "bind");
                    metadata.set (pinKey, "gui", "pin", "in", pinKey, "bind", "pin");
                    um.apply (new ChangeAnnotations (context, metadata));
                }
                else  // pinSide is "out"
                {
                    metadata.set (context.source.key (), "gui", "pin", "out", pinKey, "bind");
                    metadata.set (pinKey,                "gui", "pin", "out", pinKey, "bind", "pin");
                    um.apply (new ChangeAnnotations (part, metadata));
                }
            }
        }
    };

    ActionListener listenerDelete = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            if (locked) return;
            if (active == null) return;
            stopEditing ();  // It may seem odd to save a cell just before destroying it, but this gives cleaner UI painting.
            active.deleteSelected ();
        }
    };

    ActionListener listenerWatch = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            if (locked) return;
            if (active == null) return;
            stopEditing ();
            active.watchSelected ((e.getModifiers () & ActionEvent.SHIFT_MASK) != 0);
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

    public void enableRuns ()
    {
        buttonRun.setEnabled (true);
    }

    public void enableStudies ()
    {
        // Studies should not be enabled until runs are enabled, since studies depend on job management.
        if (buttonRun.isEnabled ())
        {
            buttonStudy.setEnabled (true);

            PanelStudy ps = PanelStudy.instance;
            ps.buttonPause.setEnabled (ps.displayStudy != null  &&  ps.displayStudy.complete () < 1);

            return;
        }

        // Runs are not ready yet, so check again later.
        Timer t = new Timer (1000, new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                enableStudies ();
            }
        });
        t.setRepeats (false);
        t.start ();
    }

    ActionListener listenerRun = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            launchJob ();
        }
    };

    public void launchJob ()
    {
        if (record == null) return;
        prepareForTabChange ();

        String jobKey = new SimpleDateFormat ("yyyy-MM-dd-HHmmss", Locale.ROOT).format (new Date ());
        MDoc job = (MDoc) AppData.runs.childOrCreate (jobKey);
        NodeJob.collectJobParameters (root.source, record.key (), job);
        job.save ();  // Force directory (and job file) to exist, so Backends can work with the dir.
        NodeJob.saveCollatedModel (root.source, job);

        MainTabbedPane mtp = (MainTabbedPane) MainFrame.instance.tabs;
        mtp.setPreferredFocus (PanelRun.instance, PanelRun.instance.tree);
        mtp.selectTab ("Runs");
        NodeJob node = PanelRun.instance.addNewRun (job, true);

        // Hack to allow local jobs to bypass the wait-for-host queue.
        // It would be better for all jobs to check for resources before starting.
        // However, the time cost for the local check could be as long as the job itself
        // (for very simple models). There is some expectation that the user knows
        // the state of their own system when they choose to hit the play button.
        Backend backend = Backend.getBackend (job.get ("backend"));
        String backendName = backend.getName ().toLowerCase ();
        Host h = Host.get (job);
        boolean internal  = backend instanceof InternalBackend;
        boolean localhost = ! (h instanceof Remote);
        boolean forbidden = h.config.get ("backend", backendName).equals ("0");
        if (internal  ||  (localhost  &&  ! forbidden))  // use of Internal overrides host selection
        {
            job.set ("localhost", "host");  // In case it was "internal" but not "localhost", set host to correct value.
            backend.start (job);
            h.monitor (node);
            return;
        }

        Host.waitForHost (node);
    }

    public void prepareForTabChange ()
    {
        if (! isEditing ()) return;
        stopEditing ();
        // The following is needed because graph node components (title or tree) do not reclaim the focus before it shifts to the run tab.
        GraphNode gn = active.root.graph;
        MainTabbedPane mtp = (MainTabbedPane) MainFrame.instance.tabs;
        if (gn != null  &&  gn.titleFocused) mtp.setPreferredFocus (PanelModel.instance, gn.title);
        else                                 mtp.setPreferredFocus (PanelModel.instance, active.tree);
    }

    ActionListener listenerStudy = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            if (record == null) return;
            if (! buttonRun.isEnabled ()) return;  // Don't start a new study until we can also start new runs.
            if (! record.containsKey ("study"))  // Only a heuristic. Presence of "study" does not guarantee a study, but absence guarantees not a study.
            {
                launchJob ();
                return;
            }

            prepareForTabChange ();
            MainTabbedPane mtp = (MainTabbedPane) MainFrame.instance.tabs;
            mtp.setPreferredFocus (PanelStudy.instance, PanelStudy.instance.list);
            mtp.selectTab ("Studies");
            PanelStudy.instance.addNewStudy (createStudy (record.key (), root.source));
        }
    };

    public static MDoc createStudy (String inherit, MNode collated)
    {
        String key = new SimpleDateFormat ("yyyy-MM-dd-HHmmss", Locale.ROOT).format (new Date ());
        MDoc study = (MDoc) AppData.studies.childOrCreate (key);
        study.set (inherit, "$inherit");
        study.set (collated.childOrEmpty ("$metadata", "study"), "config");  // Copy top-level study tag (general parameters controlling study).
        // Collect study tags
        MNode variables = study.childOrCreate ("variables");
        collated.visit (new Visitor ()
        {
            public boolean visit (MNode n)
            {
                // Find "study" somewhere under "$metadata".
                if (! n.key ().equals ("study")) return true;  // Filter on "study" first.
                String[] keyPath = n.keyPath ();
                int i = keyPath.length - 1;  // Search backwards for "$metadata", because it is most likely to be immediate parent of "study".
                for (; i >= 0; i--) if (keyPath[i].equals ("$metadata")) break;
                if (i < 0) return true;  // move along, nothing to see here

                if (i == keyPath.length - 2)  // immediate parent
                {
                    if (keyPath.length < 3) return true;  // This is the top-level metadata block, so ignore study. It contains general parameters, rather than tagging a variable.
                    keyPath = Arrays.copyOfRange (keyPath, 0, keyPath.length - 2);  // skip up to the parent of $metadata, which should be a variable
                }
                else  // more distant parent, so a metadata key is the item to be iterated, rather than a variable
                {
                    keyPath = Arrays.copyOfRange (keyPath, 0, keyPath.length - 1);
                }

                variables.set (n, keyPath);  // Save entire subtree under n, if it exists.
                if (! variables.data (keyPath)) variables.set ("", keyPath);  // ensure node is defined so it can indicate the study variable
                return false;  // Don't descend after finding a study tag.
            }
        });
        study.save ();  // force directory to exist
        return study;
    }

    ActionListener listenerExport = new ActionListener ()
    {
        // We create and customize a file chooser on the fly, display it modally, then use its result to initiate export.

        class ExporterFilter extends FileFilter
        {
            public Export exporter;

            ExporterFilter (Export exporter)
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
            fc.setSelectedFile (new File (record.key ()));
            ExporterFilter n2a = null;
            List<ExtensionPoint> exps = PluginManager.getExtensionsForPoint (Export.class);
            for (ExtensionPoint exp : exps)
            {
                if (! (exp instanceof ExportModel)  &&  ! (exp instanceof ExportBibliography)) continue;
                ExporterFilter ef = new ExporterFilter ((Export) exp);
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
                try
                {
                    filter.exporter.export (record, path);
                }
                catch (Exception error)
                {
                    File crashdump = new File (AppData.properties.get ("resourceDir"), "crashdump");
                    try
                    {
                        PrintStream err = new PrintStream (crashdump);
                        error.printStackTrace (err);
                        err.close ();
                    }
                    catch (FileNotFoundException fnfe) {}

                    JOptionPane.showMessageDialog
                    (
                        MainFrame.instance,
                        "<html><body><p style='width:300px'>"
                        + error.getMessage () + " Exception has been recorded in "
                        + crashdump.getAbsolutePath ()
                        + "</p></body></html>",
                        "Export Failed",
                        JOptionPane.WARNING_MESSAGE
                    );
                }
            }
        }
    };

    ActionListener listenerImport = new ActionListener ()
    {
        // We create and customize a file chooser on the fly, display it modally, then use its result to initiate export.

        class ImporterFilter extends FileFilter
        {
            public ImportModel importer;

            ImporterFilter (ImportModel importer)
            {
                this.importer = importer;
            }

            @Override
            public boolean accept (File f)
            {
                return importer.accept (f.toPath ());
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
            fc.setDialogTitle ("Import Model");
            List<ExtensionPoint> exps = PluginManager.getExtensionsForPoint (Import.class);
            for (ExtensionPoint exp : exps)
            {
                if (! (exp instanceof ImportModel)) continue;
                ImporterFilter f = new ImporterFilter ((ImportModel) exp);
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

            FilteredTreeModel.showInherited = buttonFilterInherited.isSelected ();
            FilteredTreeModel.showLocal     = buttonFilterLocal    .isSelected ();
            FilteredTreeModel.showParam     = buttonFilterParam    .isSelected ();
            FilteredTreeModel.showRevoked   = buttonFilterRevoked  .isSelected ();

            AppData.state.set (FilteredTreeModel.showInherited, "PanelModel", "filter", "inherited");
            AppData.state.set (FilteredTreeModel.showLocal,     "PanelModel", "filter", "local");
            AppData.state.set (FilteredTreeModel.showParam,     "PanelModel", "filter", "param");
            AppData.state.set (FilteredTreeModel.showRevoked,   "PanelModel", "filter", "revoked");

            if (panelEquationTree.isVisible ()) panelEquationTree.updateFilterLevel ();
            if (view == NODE)
            {
                if (panelParent.isVisible ()) panelParent.panelEquationTree.updateFilterLevel ();
                panelEquationGraph.updateFilterLevel ();
            }
        }
    };

    public class EquationTransferHandler extends TransferHandler
    {
        public boolean canImport (TransferSupport xfer)
        {
            if (locked) return false;
            if (xfer.isDataFlavorSupported (DataFlavor.stringFlavor))       return true;
            if (xfer.isDataFlavorSupported (DataFlavor.imageFlavor))        return true;
            if (xfer.isDataFlavorSupported (DataFlavor.javaFileListFlavor)) return true;
            return false;
        }

        public boolean importData (TransferSupport xfer)
        {
            if (locked) return false;

            // Extract data
            MNode data = new MVolatile ();
            Schema schema = null;
            TransferableNode xferNode = null;  // used only to detect if the source is an equation tree
            int modifiers = 0;
            BufferedImage image = null;
            try
            {
                Transferable xferable = xfer.getTransferable ();
                if (xfer.isDataFlavorSupported (DataFlavor.javaFileListFlavor))
                {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) xferable.getTransferData (DataFlavor.javaFileListFlavor);
                    for (File file : files)
                    {
                        try (BufferedReader reader = Files.newBufferedReader (file.toPath ()))
                        {
                            MNode temp = new MVolatile ();
                            schema = Schema.readAll (temp, reader);  // Throws an IOException if this is not a proper N2A file.
                            schema.type = "Part";
                            data.set (temp, file.getName ());
                        }
                        catch (IOException e)
                        {
                            // See if it's an image file.
                            image = ImageIO.read (file);  // returns null if file is not an image
                        }
                    }
                }
                else if (xferable.isDataFlavorSupported (DataFlavor.imageFlavor))
                {
                    image = (BufferedImage) xferable.getTransferData (DataFlavor.imageFlavor);
                }
                else if (xferable.isDataFlavorSupported (DataFlavor.stringFlavor))
                {
                    StringReader reader = new StringReader ((String) xferable.getTransferData (DataFlavor.stringFlavor));
                    schema = Schema.readAll (data, reader);

                    if (xferable.isDataFlavorSupported (TransferableNode.nodeFlavor))
                    {
                        xferNode = (TransferableNode) xferable.getTransferData (TransferableNode.nodeFlavor);
                        modifiers = xferNode.modifiers;
                    }
                }
            }
            catch (IOException | UnsupportedFlavorException e)
            {
                return false;
            }

            // Determine paste/drop target.
            JTree              tree = null;  // Will be non-null as much as possible. The following target variables will only be non-null if they actually received the drop or paste.
            GraphNode          gn   = null;
            BreadcrumbRenderer br   = null;
            PanelEquationGraph peg  = null;  // We already know panelEquationGraph. This is used to indicate that it was the drop location.
            Component comp = xfer.getComponent ();
            if (comp instanceof JTree)
            {
                tree = (JTree) comp;
            }
            else if (comp instanceof GraphNode.TitleRenderer)
            {
                gn = (GraphNode) comp.getParent ().getParent ();
                if (view == NODE) tree = gn.panelEquationTree.tree;
                else if (panelEquationTree.root == gn.node) tree = panelEquationTree.tree;
                // else there is no tree available
            }
            else if (comp instanceof BreadcrumbRenderer)
            {
                br = breadcrumbRenderer;
                if (view == NODE) tree = panelParent.panelEquationTree.tree;
                else if (panelEquationTree.root == part) tree = panelEquationTree.tree;
            }
            else if (comp instanceof PanelEquationGraph)
            {
                // The equation graph is basically an extended body for the parent node, so use same tree as for breadcrumbRenderer.
                peg = panelEquationGraph;
                if (view == NODE) tree = panelParent.panelEquationTree.tree;
                else if (panelEquationTree.root == part) tree = panelEquationTree.tree;
            }

            TreePath path = null;
            DropLocation dl = null;
            if (xfer.isDrop ()) dl = xfer.getDropLocation ();
            if (tree != null)
            {
                if (dl instanceof JTree.DropLocation)
                {
                    path = ((JTree.DropLocation) dl).getPath ();
                }
                else
                {
                    path = tree.getLeadSelectionPath ();
                    if (path == null)
                    {
                        // Fake a path to parent title or graph node title. Both of these are effectively the root of their respective tree.
                        Object[] o = new Object[1];
                        if (gn != null) o[0] = gn.node;
                        else if (comp instanceof BreadcrumbRenderer) o[0] = part;
                        if (o[0] != null) path = new TreePath (o);
                    }
                }
                if (path == null)
                {
                    int count = tree.getRowCount ();
                    if (count > 0) path = tree.getPathForRow (count - 1);
                }
            }

            // Handle internal DnD as a node reordering.
            UndoManager um = MainFrame.instance.undoManager;
            if (xferNode != null  &&  xferNode.panel == PanelEquations.this  &&  xfer.isDrop ())
            {
                if (path == null) return false;

                NodeBase target = (NodeBase) path.getLastPathComponent ();
                NodeBase targetParent = (NodeBase) target.getParent ();
                if (targetParent == null) return false;  // If target is root node.

                NodeBase source = xferNode.sources.get (0);  // The first source is the lead selection, if the lead made it into the list at all.
                NodeBase sourceParent = (NodeBase) source.getParent ();
                if (targetParent != sourceParent) return false;  // Don't drag node outside its containing part.
                if (! (targetParent instanceof NodePart)) return false;  // Only rearrange children of parts (not of variables or metadata).

                NodePart parent = (NodePart) targetParent;
                int indexBefore = parent.getIndex (source);
                int indexAfter  = parent.getIndex (target);
                um.apply (new ChangeOrder (parent, indexBefore, indexAfter));

                return true;
            }

            // Determine target node.
            NodeBase target = null;
            um.addEdit (new CompoundEdit ());  // If handling DnD MOVE with TransferableNode, this will be closed by the sending side's exportDone(). Otherwise, we close it at the end of this importData().
            if (peg != null  ||  br != null)
            {
                // Create new model if needed.
                if (root == null)
                {
                    AddDoc ad = new AddDoc ();
                    um.apply (ad);
                }

                target = part;
            }
            else if (gn != null)
            {
                if (view == NODE)
                {
                    if (gn.open)
                    {
                        target = gn.node;
                        tree = gn.panelEquationTree.tree;
                    }
                    else
                    {
                        target = part;
                        tree = panelParent.panelEquationTree.tree;
                    }
                }
                else  // Property-panel mode. Choose target based on type of user
                {
                    boolean allParts = true;
                    if (schema != null  &&  schema.type.equals ("Clip"))
                    {
                        for (MNode c : data)
                        {
                            String type = c.get ("$clip");
                            if (! type.equals ("Part"))
                            {
                                allParts = false;
                                break;
                            }
                        }
                    }

                    if (allParts)  // Treat as node as closed, and direct paste into parent.
                    {
                        target = part;
                        if (panelEquationTree.root == part) tree = panelEquationTree.tree;
                        else                                tree = null;
                    }
                    else
                    {
                        target = gn.node;
                        if (panelEquationTree.root == gn.node) tree = panelEquationTree.tree;
                        else                                   tree = null;
                    }
                }
            }
            else if (tree != null)
            {
                if (path == null)
                {
                    target = (NodePart) tree.getModel ().getRoot ();
                }
                else
                {
                    if (xfer.isDrop ()) tree.setSelectionPath (path);
                    target = (NodeBase) path.getLastPathComponent ();
                }
            }

            // Image
            if (image != null)
            {
                target = target.containerFor ("image");

                // Limit resolution to 256x256
                int originalWidth  = image.getWidth ();
                int originalHeight = image.getHeight ();
                double w = originalWidth;
                double h = originalHeight;
                if (w > 256)
                {
                    h *= 256 / w;
                    w  = 256;
                }
                if (h > 256)
                {
                    w *= 256 / h;
                    h  = 256;
                }
                int width  = (int) Math.round (w);
                int height = (int) Math.round (h);
                if (width != originalWidth  ||  height != originalHeight)
                {
                    BufferedImage smaller = new BufferedImage (width, height, image.getType ());
                    Graphics2D g = smaller.createGraphics ();
                    g.setRenderingHint (RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);  // Most expensive method, but we're not in that big of a hurry.
                    g.drawImage (image, 0, 0, width, height, 0, 0, originalWidth, originalHeight, null);
                    g.dispose ();
                    image = smaller;
                }

                // Convert image to base64 coding
                ByteArrayOutputStream stream = new ByteArrayOutputStream ();
                try
                {
                    ImageIO.write (image, "png", stream);
                }
                catch (IOException e)
                {
                    return false;
                }
                String base64 = Base64.getEncoder ().encodeToString (stream.toByteArray ());

                // Save image in $metadata.gui.icon
                MNode metadata = new MVolatile ();
                metadata.set (base64, "gui", "icon");
                um.apply (new ChangeAnnotations (target, metadata));
                um.endCompoundEdit ();
                return true;
            }

            // Determine location
            Point location = null;
            if (dl != null)
            {
                Point offset = panelEquationGraph.getOffset ();
                if (peg != null  ||  br == null  &&  gn == null  &&  tree != null  &&  tree == panelParent.panelEquationTree.tree)  // Direct drop to graph, or a drop to parent tree (which is treated like drop to graph).
                {
                    Point vp = panelEquationGraph.getViewPosition ();
                    location = dl.getDropPoint ();
                    location.x += vp.x - offset.x;
                    location.y += vp.y - offset.y;

                    panelParent.panelEquationTree.tree.clearSelection ();  // Just in case this was a DnD to the parent tree in NODE view.
                }
                else if (gn != null  &&  ! gn.open)
                {
                    Point p = gn.getLocation ();
                    location = dl.getDropPoint ();
                    location.x += p.x - offset.x;
                    location.y += p.y - offset.y;
                }
            }

            // Import the data
            boolean result = false;
            if (schema.type.equals ("Clip"))  // From equation panel.
            {
                result = true;

                // Locate the nearest container sufficient to hold every type of node in the imported data.
                // Separate parts from other items.
                MNode parts = new MVolatile ();
                MNode items = new MVolatile ();
                for (MNode c : data)
                {
                    String type = c.get ("$clip");
                    target = target.containerFor (type);
                    if (type.equals ("Part"))
                    {
                        c.clear ("$clip");
                        parts.set (c, c.key ());
                    }
                    else
                    {
                        items.set (c, c.key ());
                    }
                }
                int itemCount = items.size ();
                int partCount = parts.size ();
                int count = partCount + itemCount;

                // Don't paste parts into a closed part.
                // This situation should only come up if we paste into a tree.
                // The test here is the same as in NodePart.makeAdd(). Because we are using AddPart.makeMulti(),
                // we don't end up calling that function, so it needs to be reproduced here.
                if (partCount > 0  &&  target instanceof NodePart  &&  tree != null  &&  gn == null)
                {
                    boolean collapsed   = tree.isCollapsed (new TreePath (target.getPath ()));
                    boolean hasChildren = ((FilteredTreeModel) tree.getModel ()).getChildCount (target) > 0;
                    if (collapsed  &&  hasChildren) target = (NodeBase) target.getParent ();  // The node is deliberately closed to indicate user intent.
                }

                // Process items
                // Ideally, these would all be packed into a CompoundEditView and executed as a group.
                // However, some edits rely on the results of others (for example AddEquation), so each needs
                // to be applied before the next is constructed. The exception to this is parts, which get special
                // pre-processing by AddPart.makeMulti(). However, they work OK with either approach method, so
                // we stick with serial application. The drawback is that we have to do a lot of work here
                // that CompoundEditView would otherwise do for us.
                CompoundEditView compound = null;
                boolean multi = count > 1;
                boolean multiShared = false;
                if (multi)
                {
                    multiShared =  target == part  &&  itemCount > 0;
                    if (multiShared) switchFocus (false, false);  // Putting focus in the tree produces cleaner undo behavior.

                    int clearMode = CompoundEditView.CLEAR_TREE;
                    if (target == part  &&  partCount > 0  &&  itemCount == 0) clearMode = CompoundEditView.CLEAR_GRAPH;
                    compound = new CompoundEditView (clearMode);
                    um.addEdit (compound);
                    compound.clearSelection ();
                }
                int i = 0;  // One-based index of current item. Pre-increments below.
                NodeBase lastAdded = null;
                if (partCount > 0)
                {
                    for (AddPart ap : AddPart.makeMulti ((NodePart) target, parts, location))
                    {
                        i++;
                        ap.setMulti (multi);
                        if (multi  &&  i == count) ap.setMultiLast (true);
                        ap.multiShared = multiShared;
                        um.apply (ap);
                        lastAdded = ap.getCreatedNode ();
                    }
                }
                for (MNode c : items)
                {
                    i++;
                    String type = c.get ("$clip");
                    c.clear ("$clip");
                    Undoable u = target.makeAdd (type, tree, c, location);
                    if (u == null) continue;  // If the last item fails to add, we won't mark the penultimate item as multiLast. This should happen very rarely, and do little harm.
                    if (u instanceof UndoableView)
                    {
                        UndoableView uv = (UndoableView) u;
                        uv.setMulti (multi);
                        if (multi  &&  i == count) uv.setMultiLast (true);
                    }
                    um.apply (u);
                    if (u instanceof AddEditable) lastAdded = ((AddEditable) u).getCreatedNode ();
                }
                if (multi)
                {
                    compound.end ();  // Must close edit directly, not through undo manager.
                    if (lastAdded != null)
                    {
                        compound.leadPath = lastAdded.getKeyPath ();
                        compound.selectLead ();
                    }
                }
            }
            else if (schema.type.equals ("Part"))  // From search panel. Could possibly come via an indirect route such as email.
            {
                result = true;

                // Prepare lists for suggesting connections.
                List<NodePart> newParts = new ArrayList<NodePart> ();
                List<NodePart> oldParts = new ArrayList<NodePart> ();
                Enumeration<?> children = target.children ();
                while (children.hasMoreElements ())
                {
                    Object c = children.nextElement ();
                    if (c instanceof NodePart)
                    {
                        NodePart p = (NodePart) c;
                        if (! p.source.getFlag ("$metadata", "gui", "pin")) oldParts.add (p);
                    }
                }

                int columns = (int) Math.sqrt (data.size ());
                int i = 0;
                for (MNode child : data)  // There could be multiple parts, though currently the search panel does not support this.
                {
                    // The plan is to create a link (via inheritance) to an existing part.
                    // The part may need to be fully imported if it does not already exist in the db.
                    String key = child.key ();
                    if (AppData.models.child (key) == null)
                    {
                        AddDoc a = new AddDoc (key, child);
                        a.setSilent ();
                        um.apply (a);
                    }

                    // Create an include-style part
                    MNode include = new MVolatile ();  // Note the empty key. This enables AddPart to generate a name.
                    include.merge (child);  // TODO: What if this brings in a $inherit line, and that line does not match the $inherit line in the source part? One possibility is to add the new values to the end of the $inherit line created below.
                    include.clear ("$inherit");  // get rid of IDs from included part, so they won't override the new $inherit line ...
                    include.set (key, "$inherit");
                    Point p = null;
                    if (location != null) p = new Point (location.x + (i % columns) * 100, location.y + (i / columns) * 100);  // Keep multiple parts from going to the same place on graph panel.
                    NodePart added = (NodePart) target.add ("Part", tree, include, p);
                    if (added != null) newParts.add (added);
                    i++;
                }

                if ((modifiers & (InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK)) == 0)  // Could filter on drop action instead. However, this allows us to discriminate any combination of modifiers, not just Swing's interpretation of them.
                {
                    NodePart.suggestConnections (newParts, oldParts);
                    NodePart.suggestConnections (oldParts, newParts);
                }
            }

            if (! xfer.isDrop ()  ||  xfer.getDropAction () != MOVE  ||  xferNode == null) um.endCompoundEdit ();  // By not closing the compound edit on a DnD move, we allow the sending side to include any changes in it when exportDone() is called.
            return result;
        }

        public int getSourceActions (JComponent comp)
        {
            return LINK | COPY | MOVE;
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

            NodeBase parent = null;  // Lowest node in hierarchy that is parent to all items to be exported.
            List<NodeBase> export = new ArrayList<NodeBase> ();  // Items to be exported. Must all be immediate children of parent.
            if (comp instanceof JTree)
            {
                // Collect nodes
                JTree tree = (JTree) comp;
                TreePath[] paths = tree.getSelectionPaths ();
                if (paths == null) return null;
                TreePath leadPath = tree.getLeadSelectionPath ();

                for (TreePath path : paths)
                {
                    NodeBase n = (NodeBase) path.getLastPathComponent ();
                    export.add (n);
                }
                if (leadPath != null)
                {
                    NodeBase n = (NodeBase) leadPath.getLastPathComponent ();
                    export.remove (n);
                    export.add (0, n);
                }

                // Locate common parent
                parent = (NodeBase) export.get (0).getParent ();  // Any node that can be selected in a tree has a non-null parent.
                for (NodeBase n : export)
                {
                    while (! parent.isNodeDescendant (n)) parent = (NodeBase) parent.getParent ();  // All nodes in the same tree must share a common ancestor, if only root.
                }
                List<NodeBase> filteredExport = new ArrayList<NodeBase> ();
                for (NodeBase n : export)
                {
                    while (n.getParent () != parent) n = (NodeBase) n.getParent ();
                    if (! filteredExport.contains (n)) filteredExport.add (n);
                }
                export = filteredExport;
            }
            else if (comp instanceof GraphNode.TitleRenderer)
            {
                GraphNode gn = (GraphNode) comp.getParent ().getParent ();
                parent = part;
                export.add (gn.node);

                List<GraphNode> selected = panelEquationGraph.getSelection ();
                selected.remove (gn);
                selected.remove (panelEquationGraph.graphPanel.pinIn);
                selected.remove (panelEquationGraph.graphPanel.pinOut);
                for (GraphNode g : selected) export.add (g.node);
            }
            else if (comp instanceof BreadcrumbRenderer)  // Graph parent
            {
                parent = part.getTrueParent ();  // If root is on display (the usual case), then this will be null.
                export.add (part);
            }
            if (export.isEmpty ()) return null;

            MVolatile copy = new MVolatile ();
            for (NodeBase n : export)
            {
                n.copy (copy);
                copy.set (n.getTypeName (), n.source.key (), "$clip");  // Embed a hint about node type.
            }
            if (parent == null)  // This is the entire document.
            {
                copy.set (null, root.source.key ());  // Remove file information.
            }

            Schema schema = Schema.latest ();
            schema.type = "Clip";
            StringWriter writer = new StringWriter ();
            try
            {
                schema.write (writer);
                for (MNode c : copy) schema.write (c, writer);
                writer.close ();

                TransferableNode result = new TransferableNode (writer.toString (), export, drag, null);
                result.panel = PanelEquations.this;
                return result;
            }
            catch (IOException e)
            {
                return null;
            }
        }

        protected void exportDone (JComponent comp, Transferable data, int action)
        {
            // The main job of this function is to delete nodes in a Cut operation.
            // They have already been copied by createTransferable().

            // Even though importData() leaves a compound edit open during the drop side of a DnD,
            // we don't currently support any DnD gestures that actually move data. (Notice tn.drag
            // as one of the conditions below for early-out.) We close the compound edit here
            // without adding anything further to it. We may create a compound delete below,
            // but it will be a separate action.
            UndoManager um = MainFrame.instance.undoManager;
            um.endCompoundEdit ();  // This is safe, even if there is no compound edit in progress.

            TransferableNode tn = (TransferableNode) data;
            if (tn == null  ||  tn.drag  ||  tn.sources == null  ||  tn.sources.isEmpty ()  ||  action != MOVE  ||  locked) return;

            // Since we aren't doing any true DnD gesture, the object identity of the tn.sources should remain valid.
            // If we add true DnD gestures, which change the tree contents before exportDone() is called, then
            // tn.sources should be stored as key paths instead of nodes.

            CompoundEditView compound = null;
            boolean fromTree = comp instanceof JTree;
            int count = tn.sources.size ();
            boolean multi = count > 1;
            if (multi) um.addEdit (compound = new CompoundEditView (fromTree ? CompoundEditView.CLEAR_TREE : CompoundEditView.CLEAR_GRAPH));
            int i = 0;
            for (NodeBase n : tn.sources)
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
    }

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
            label.setFont (baseFont.deriveFont (Font.BOLD));
            label.setText (text);
            label.setIcon (null);
            setFocusable (false);
            setTransferHandler (transferHandler);
            ToolTipManager.sharedInstance ().registerComponent (this);

            InputMap inputMap = getInputMap ();
            inputMap.put (KeyStroke.getKeyStroke ("UP"),                "nothing");
            inputMap.put (KeyStroke.getKeyStroke ("DOWN"),              "selectNext");
            inputMap.put (KeyStroke.getKeyStroke ("LEFT"),              "close");
            inputMap.put (KeyStroke.getKeyStroke ("RIGHT"),             "selectChild");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl UP"),           "nothing");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl DOWN"),         "selectChild");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl LEFT"),         "nothing");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl RIGHT"),        "selectChild");
            inputMap.put (KeyStroke.getKeyStroke ("shift DELETE"),      "cut");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl X"),            "cut");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl INSERT"),       "copy");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl C"),            "copy");
            inputMap.put (KeyStroke.getKeyStroke ("shift INSERT"),      "paste");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl V"),            "paste");
            inputMap.put (KeyStroke.getKeyStroke ("INSERT"),            "add");
            inputMap.put (KeyStroke.getKeyStroke ("ctrl shift EQUALS"), "add");
            inputMap.put (KeyStroke.getKeyStroke ("DELETE"),            "delete");
            inputMap.put (KeyStroke.getKeyStroke ("BACK_SPACE"),        "delete");
            inputMap.put (KeyStroke.getKeyStroke ("ENTER"),             "startEditing");
            inputMap.put (KeyStroke.getKeyStroke ("F2"),                "startEditing");
            inputMap.put (KeyStroke.getKeyStroke ("shift ctrl D"),      "drillUp");

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
                    panelEquationGraph.clearSelection ();
                    switchFocus (false, view == NODE);
                }
            });
            actionMap.put ("selectChild", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    if (view == NODE  &&  ! panelParent.isVisible ())
                    {
                        panelParent.toggleOpen ();
                    }
                    else
                    {
                        panelEquationGraph.clearSelection ();
                        switchFocus (false, false);
                    }
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
                        NodePart editMe = (NodePart) part.add ("Part", null, null, null);  // We could compute location, but it's not necessary. AddPart does this well enough.
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
                    if (! locked) part.delete (false);
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

                    if (SwingUtilities.isRightMouseButton (me)  ||  me.isControlDown ())
                    {
                        if (clicks == 1)  // Show popup menu
                        {
                            panelEquationGraph.clearSelection ();
                            switchFocus (true, false);
                            menuPopup.show (breadcrumbRenderer, x, y);
                        }
                    }
                    else if (SwingUtilities.isLeftMouseButton (me))
                    {
                        if (clicks == 1)
                        {
                            if (x < getIconWidth ())  // Open/close
                            {
                                if (view == NODE) panelParent.toggleOpen ();
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
                                // Click was on last path element (which may be only path element), so take focus ...
                            }
                            panelEquationGraph.clearSelection ();
                            switchFocus (true, false);
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
                        if (part != panelEquationTree.root)
                        {
                            panelEquationTree.loadPart (part);
                            FocusCacheEntry fce = createFocus (part);
                            if (fce.sp != null) fce.sp.restore (panelEquationTree.tree, false);
                        }
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

        public String getToolTipText ()
        {
            if (part == null) return null;
            FontMetrics fm = getFontMetrics (getFont ());
            return part.getToolTipText (fm);
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
            getTreeCellRendererComponent (getParentEquationTree ().tree, part, selected, panelParent.isVisible (), false, -2, focused);
            if (part == null)
            {
                text = noModel;
                label.setIcon (null);
                setFocusable (false);
            }
            else
            {
                setFocusable (true);
            }
            Font baseFont = UIManager.getFont ("Tree.font");
            label.setFont (baseFont.deriveFont (Font.BOLD));
            label.setText (text);
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
            editor.editingComponent.requestFocusInWindow ();  // The "editingComponent" returned above is just a container for the true editing component.
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
        public boolean    titleFocused = true; // state of GraphNode.titleFocused or PanelEquations.titleFocused, whichever was set most recently
        public StoredPath sp;                  // path state of tree, whether as parent or as child
        public Point      position;            // when parent: offset of viewport
        public String     subpart      = "";   // when parent: name of child which has keyboard focus. If empty, then the parent itself has focus.
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

        /**
            Create based on active tree.
        **/
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

        /**
            Create based on nearest containing tree to given node.
        **/
        public StoredView (NodeBase node)
        {
            saveFocus ();
            NodePart r = (NodePart) node.getRoot ();
            path = r.getKeyPath ();
            asParent =  r == part;
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
            if (p == part)  // Already loaded, so loadPart() won't run. Still need to take focus.
            {
                panelEquationGraph.clearSelection ();
                takeFocus ();
            }
            else
            {
                loadPart (p);
            }
        }
    }
}

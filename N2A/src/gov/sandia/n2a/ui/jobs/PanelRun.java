/*
Copyright 2013-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import gov.sandia.n2a.backend.c.VideoIn;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDir;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MNode.Visitor;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.eqset.Variable.ParsedValue;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.host.Remote;
import gov.sandia.n2a.host.Host.CopyProgress;
import gov.sandia.n2a.host.SshFileSystemProvider.SshDirectoryStream;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.settings.SettingsLookAndFeel;
import gov.sandia.n2a.ui.studies.Study;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.Caret;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

@SuppressWarnings("serial")
public class PanelRun extends JPanel
{
    public static PanelRun instance;  ///< Technically, this class is a singleton, because only one would normally be created.

    protected NodeBase         root;
    protected DefaultTreeModel model;
    public    JTree            tree;
    protected JScrollPane      treePane;

    protected JButton             buttonStop;
    protected JPopupMenu          menuHost;
    protected long                menuCanceledAt;
    protected ButtonGroup         buttons;
    protected JButton             buttonExport;
    protected JTextArea           displayText;
    protected TextPaneANSI        displayANSI;
    protected PanelChart          displayChart  = new PanelChart ();
    protected JScrollPane         displayPane   = new JScrollPane ();
    protected DisplayThread       displayThread = null;
    protected NodeBase            displayNode   = null;
    protected MDir                runs;  // Copied from AppData for convenience

    public static Map<String,NodeJob> jobNodes = new HashMap<String,NodeJob> ();  // for quick lookup of job node based on job key.

    public static ImageIcon iconConnect      = ImageUtil.getImage ("connect.gif");
    public static ImageIcon iconPause        = ImageUtil.getImage ("pause-16.png");
    public static ImageIcon iconDisconnected = ImageUtil.getImage ("disconnected.png");
    public static ImageIcon iconStop         = ImageUtil.getImage ("stop.gif");

    public PanelRun ()
    {
        instance = this;

        root  = new NodeBase ();
        model = new DefaultTreeModel (root);
        tree  = new JTree (model);
        tree.setRootVisible (false);
        tree.setShowsRootHandles (true);
        tree.getSelectionModel ().setSelectionMode (TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);  // Appears to be the default, but we make it explicit.

        tree.setCellRenderer (new DefaultTreeCellRenderer ()
        {
            @Override
            public Component getTreeCellRendererComponent (JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus)
            {
                super.getTreeCellRendererComponent (tree, value, selected, expanded, leaf, row, hasFocus);

                NodeBase node = (NodeBase) value;
                Icon icon = node.getIcon (expanded);  // A node knows whether it should hold other nodes or not, so don't pass leaf to it.
                if (icon == null)
                {
                    if      (leaf)     icon = getDefaultLeafIcon ();
                    else if (expanded) icon = getDefaultOpenIcon ();
                    else               icon = getDefaultClosedIcon ();
                }
                setIcon (icon);

                return this;
            }
        });

        tree.addTreeSelectionListener (new TreeSelectionListener ()
        {
            public void valueChanged (TreeSelectionEvent e)
            {
                TreePath newPath = e.getNewLeadSelectionPath ();
                if (newPath == null) return;
                NodeBase newNode = (NodeBase) newPath.getLastPathComponent ();
                if (newNode == null) return;
                if (newNode == displayNode) return;
                displayNode = newNode;

                NodeJob job = null;
                if (displayNode instanceof NodeFile)
                {
                    viewFile (false);
                    job = (NodeJob) displayNode.getParent ();
                }
                else if (displayNode instanceof NodeJob)
                {
                    viewJob (false);
                    job = (NodeJob) displayNode;
                }
                buttonStop.setEnabled (job.complete < 1  ||  job.complete == 3);
            }
        });

        tree.addKeyListener (new KeyAdapter ()
        {
            public void keyPressed (KeyEvent e)
            {
                int keycode = e.getKeyCode ();
                if (keycode == KeyEvent.VK_DELETE  ||  keycode == KeyEvent.VK_BACK_SPACE)
                {
                    delete ();
                }
            }
        });

        tree.addTreeWillExpandListener (new TreeWillExpandListener ()
        {
            public void treeWillExpand (TreeExpansionEvent event) throws ExpandVetoException
            {
                TreePath path = event.getPath ();
                holdFocus (path);
                Object o = path.getLastPathComponent ();
                if (o instanceof NodeJob)
                {
                    // Launch a separate (non-EDT) thread to create/update the folder contents.
                    Thread expandThread = new Thread ("NodeJob Expand")
                    {
                        public void run ()
                        {
                            ((NodeJob) o).build (tree);
                        }
                    };
                    expandThread.setDaemon (true);
                    expandThread.start ();
                }
            }

            public void treeWillCollapse (TreeExpansionEvent event) throws ExpandVetoException
            {
                TreePath path = event.getPath ();
                holdFocus (path);
            }

            /**
                Keep focus from jumping away from node associated with handle.
                If the current focus is not visible or does not exist, then
                move focus to the row associated with the handle.
            **/
            public void holdFocus (TreePath path)
            {
                boolean moveFocus = false;
                Rectangle visible = tree.getVisibleRect ();
                int row = tree.getLeadSelectionRow ();
                if (row < 0)
                {
                    moveFocus = true;
                }
                else
                {
                    Rectangle rowBounds = tree.getRowBounds (row);
                    if (! rowBounds.intersects (visible)) moveFocus = true;
                }
                if (moveFocus) tree.setSelectionPath (path);
            }
        });

        Thread loadHostMonitors = new Thread ("Prepare Host Monitors")
        {
            public void run ()
            {
                // Initial load
                // The Run button on the Models tab starts disabled. We enable it below,
                // once all the pre-existing jobs are loaded. This helps ensure consistency
                // between the UI and the run data stored on disk.
                // This also means that we don't really need to synchronize on
                // "running", because no other thread will try to access it until we give
                // the go-ahead.
                List<NodeJob> reverse = new ArrayList<NodeJob> (AppData.runs.size ());
                for (MNode n : AppData.runs) reverse.add (new NodeJob (n, false));
                for (int i = reverse.size () - 1; i >= 0; i--)  // Reverse the order, so later dates come first.
                {
                    NodeJob n = reverse.get (i);
                    root.add (n);
                    jobNodes.put (n.key, n);
                }
                EventQueue.invokeLater (new Runnable ()
                {
                    public void run ()
                    {
                        // Update display with newly loaded jobs.
                        model.nodeStructureChanged (root);
                        if (model.getChildCount (root) > 0)
                        {
                            tree.setSelectionRow (0);
                            tree.scrollRowToVisible (0);
                        }

                        PanelModel.instance.panelEquations.enableRuns ();
                    }
                });

                // Distribute jobs to host monitor threads.
                Host.restartAssignmentThread ();
                for (Host h : Host.getHosts ()) h.restartMonitorThread ();
                // Here, order doesn't matter so much, but we sill want to examine more recent jobs first.
                for (int i = reverse.size () - 1; i >= 0; i--) reverse.get (i).distribute ();
            }
        };
        loadHostMonitors.setDaemon (true);
        loadHostMonitors.start ();

        Thread refreshThreadFast = new Thread ("Monitor Focused Job")
        {
            public void run ()
            {
                try
                {
                    while (true)
                    {
                        NodeBase d = displayNode;  // Make local copy (atomic action) to prevent it changing from under us
                        if (d instanceof NodeFile) d = (NodeBase) d.getParent ();  // parent could be null, if a sub-node was just deleted
                        if (d != null) ((NodeJob) d).monitorProgress ();
                        sleep (1000);
                    }
                }
                catch (InterruptedException e)
                {
                }
            }
        };
        refreshThreadFast.setDaemon (true);
        refreshThreadFast.start ();

        Thread prepareJNI = new Thread ("Prepare JNI")
        {
            public void run ()
            {
                VideoIn.prepareJNI ();
            }
        };
        prepareJNI.setDaemon (true);
        prepareJNI.start ();

        displayText = new JTextArea ()
        {
            public void updateUI ()
            {
                super.updateUI ();
                Font f = UIManager.getFont ("TextArea.font");
                if (f == null) return;
                setFont (new Font (Font.MONOSPACED, Font.PLAIN, f.getSize ()));
            }
        };
        displayText.setEditable (false);
        displayText.addFocusListener (new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
                displayText.getCaret ().setVisible (true);
            }

            public void focusLost (FocusEvent e)
            {
                // Caret is automatically hidden when focus is lost.
            }
        });

        displayANSI = new TextPaneANSI ()
        {
            public void updateUI ()
            {
                super.updateUI ();
                Font f = UIManager.getFont ("TextPane.font");
                if (f == null) return;
                setFont (new Font (Font.MONOSPACED, Font.PLAIN, f.getSize ()));
            }
        };
        displayANSI.setEditable (false);
        displayANSI.addFocusListener (new FocusListener ()
        {
            public void focusGained (FocusEvent e)
            {
                displayANSI.getCaret ().setVisible (true);
            }

            public void focusLost (FocusEvent e)
            {
                // Caret is automatically hidden when focus is lost.
            }
        });

        buttonStop = new JButton (iconStop);
        buttonStop.setMargin (new Insets (2, 2, 2, 2));
        buttonStop.setFocusable (false);
        buttonStop.setToolTipText ("Kill Job");
        buttonStop.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if (displayNode == null) return;
                NodeJob killNode = null;
                if (displayNode instanceof NodeJob)
                {
                    killNode = (NodeJob) displayNode;
                }
                else
                {
                    TreeNode parent = displayNode.getParent ();
                    if (parent instanceof NodeJob) killNode = (NodeJob) parent;
                }
                if (killNode != null) killNode.stop ();
            }
        });

        JButton buttonHost = new JButton (iconConnect);
        buttonHost.setToolTipText ("Remote Hosts");
        buttonHost.setMargin (new Insets (2, 2, 2, 2));
        buttonHost.setFocusable (false);
        buttonHost.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if (System.currentTimeMillis () - menuCanceledAt > 500)  // A really ugly way to prevent the button from re-showing the menu if it was canceled by clicking the button.
                {
                    menuHost.show (buttonHost, 0, buttonHost.getHeight ());
                }
            }
        });

        menuHost = new JPopupMenu ();
        menuHost.addPopupMenuListener (new PopupMenuListener ()
        {
            public void popupMenuWillBecomeVisible (PopupMenuEvent e)
            {
                menuHost.removeAll ();
                for (Host h : Host.getHosts ())
                {
                    if (! (h instanceof Remote)) continue;
                    @SuppressWarnings("resource")
                    Remote remote = (Remote) h;
                    JMenuItem item = new JMenuItem (h.name);
                    if      (remote.isConnected ()) item.setIcon (iconConnect);
                    else if (remote.isEnabled ())   item.setIcon (iconDisconnected);
                    else                            item.setIcon (iconPause);
                    item.addActionListener (new ActionListener ()
                    {
                        public void actionPerformed (ActionEvent e)
                        {
                            remote.enable ();
                        }
                    });
                    menuHost.add (item);
                }
            }

            public void popupMenuWillBecomeInvisible (PopupMenuEvent e)
            {
            }

            public void popupMenuCanceled (PopupMenuEvent e)
            {
                menuCanceledAt = System.currentTimeMillis ();
            }
        });

        ActionListener graphListener = new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if (displayNode instanceof NodeFile) viewFile (false);
            }
        };

        JToggleButton buttonText = new JToggleButton (ImageUtil.getImage ("document.png"));
        buttonText.setMargin (new Insets (2, 2, 2, 2));
        buttonText.setFocusable (false);
        buttonText.setToolTipText ("Display Text");
        buttonText.addActionListener (graphListener);
        buttonText.setActionCommand ("Text");

        JToggleButton buttonTable = new JToggleButton (ImageUtil.getImage ("properties.gif"));
        buttonTable.setMargin (new Insets (2, 2, 2, 2));
        buttonTable.setFocusable (false);
        buttonTable.setToolTipText ("Display Table");
        buttonTable.addActionListener (graphListener);
        buttonTable.setActionCommand ("Table");

        JToggleButton buttonTableSorted = new JToggleButton (ImageUtil.getImage ("tableSorted.png"));
        buttonTableSorted.setMargin (new Insets (2, 2, 2, 2));
        buttonTableSorted.setFocusable (false);
        buttonTableSorted.setToolTipText ("Display Table with Sorted Columns");
        buttonTableSorted.addActionListener (graphListener);
        buttonTableSorted.setActionCommand ("TableSorted");

        JToggleButton buttonGraph = new JToggleButton (ImageUtil.getImage ("analysis.gif"));
        buttonGraph.setMargin (new Insets (2, 2, 2, 2));
        buttonGraph.setFocusable (false);
        buttonGraph.setToolTipText ("Display Graph");
        buttonGraph.addActionListener (graphListener);
        buttonGraph.setActionCommand ("Graph");

        JToggleButton buttonRaster = new JToggleButton (ImageUtil.getImage ("raster.png"));
        buttonRaster.setMargin (new Insets (2, 2, 2, 2));
        buttonRaster.setFocusable (false);
        buttonRaster.setToolTipText ("Display Spike Raster");
        buttonRaster.addActionListener (graphListener);
        buttonRaster.setActionCommand ("Raster");

        buttons = new ButtonGroup ();
        buttons.add (buttonText);
        buttons.add (buttonTable);
        buttons.add (buttonTableSorted);
        buttons.add (buttonGraph);
        buttons.add (buttonRaster);
        buttonGraph.setSelected (true);
        displayPane.setViewportView (displayChart);

        displayChart.buttonBar.setVisible (false);

        buttonExport = new JButton (ImageUtil.getImage ("export.gif"));
        buttonExport.setMargin (new Insets (2, 2, 2, 2));
        buttonExport.setFocusable (false);
        buttonExport.setToolTipText ("Export CSV");
        buttonExport.addActionListener (listenerExport);

        JSplitPane split;
        Lay.BLtg
        (
            this,
            split = Lay.SPL (
                treePane = Lay.sp (tree),
                Lay.BL (
                    "N", Lay.BL (
                        "W", Lay.FL (
                            buttonHost,
                            Box.createHorizontalStrut (15),
                            buttonStop,
                            Box.createHorizontalStrut (15),
                            buttonText,
                            buttonTable,
                            buttonTableSorted,
                            buttonGraph,
                            buttonRaster,
                            Box.createHorizontalStrut (15),
                            displayChart.buttonBar,
                            buttonExport,
                            Box.createHorizontalStrut (15),
                            "hgap=5,vgap=1"
                        )
                    ),
                    "C", displayPane
                )
            )
        );
        setFocusCycleRoot (true);

        float em = SettingsLookAndFeel.em;
        split.setDividerLocation ((int) Math.round (AppData.state.getOrDefault (19.0, "PanelRun", "divider") * em));
        split.addPropertyChangeListener (JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                Object o = e.getNewValue ();
                if (! (o instanceof Integer)) return;
                float value = ((Integer) o).floatValue () / SettingsLookAndFeel.em;
                AppData.state.setTruncated (value, 2, "PanelRun", "divider");
            }
        });
    }

    // See PanelEquations for similar code
    ActionListener listenerExport = new ActionListener ()
    {
        public void actionPerformed (ActionEvent e)
        {
            if (! (displayNode instanceof NodeFile)) return;
            NodeFile node = (NodeFile) displayNode;
            if (! node.isGraphable ()) return;

            // Construct and customize a file chooser
            final JFileChooser fc = new JFileChooser (AppData.properties.get ("resourceDir"));
            String fileName = node.path.getFileName ().toString ();
            fc.setDialogTitle ("Export \"" + fileName + "\"");
            fc.setSelectedFile (new File (fileName));
            FileFilter ff = new FileFilter ()
            {
                public boolean accept (File f)
                {
                    return true;
                }

                public String getDescription ()
                {
                    return "Comma-Separated Values (CSV)";
                }
            };
            fc.addChoosableFileFilter (ff);
            fc.setAcceptAllFileFilterUsed (false);
            fc.setFileFilter (ff);

            // Display chooser and collect result
            int result = fc.showSaveDialog (MainFrame.instance);

            // Do export
            if (result == JFileChooser.APPROVE_OPTION)
            {
                // Add csv suffix
                Path path = fc.getSelectedFile ().toPath ();
                Path dir = path.getParent ();
                fileName = path.getFileName ().toString ();
                int position = fileName.lastIndexOf ('.');
                if (position >= 0) fileName = fileName.substring (0, position);
                fileName += ".csv";
                path = dir.resolve (fileName);

                // Read data and write out as CSV.
                try
                {
                    Table table = new Table (node.path, false);
                    table.dumpCSV (path);
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

    public class DisplayThread extends Thread
    {
        public NodeFile      node;       // The target resource
        public String        viz;        // The type of visualization to show, such as table, graph or raster
        public boolean       refresh;    // Incremental update of existing display
        public DisplayThread fastThread; // Responsible for quick display, either from local files or automatic refresh of remote files.

        public DisplayThread (NodeFile node, String viz, boolean refresh)
        {
            super ("PanelRun Fetch File");
            this.node    = node;
            this.viz     = viz;
            this.refresh = refresh;
            fastThread   = this;
            setDaemon (true);
        }

        public void run ()
        {
            try
            {
                // Step 1 -- Get data into local directory
                // TODO: manage and display files that are too big for memory, or even too big to store on local system
                // There are three sizes of data:
                //   small -- can load entirely into memory
                //   big   -- too big for memory; must load/display in segments
                //   huge  -- too big to store on local filesystem, for example a supercomputer job; must be downloaded/displayed in segments
                // The current code only handles small files.
                NodeJob nodeJob = (NodeJob) node.getParent ();
                MNode job = nodeJob.getSource ();
                Host env = Host.get (job);
                if (env instanceof Remote)
                {
                    Path   localJobDir = Host.getJobDir (Host.getLocalResourceDir (), job);
                    String fileName    = node.path.getFileName ().toString ();
                    Path   localPath   = localJobDir.resolve (fileName);
                    node.path = localPath;  // Force to use local copy, regardless of whether it was local or remote before.

                    if (this == fastThread  &&  ! refresh)  // Spawn a slow thread to check remote resource.
                    {
                        DisplayThread slowThread = new DisplayThread (node, viz, true);
                        slowThread.fastThread = this;
                        slowThread.start ();
                    }
                    else
                    {
                        ((Remote) env).enable ();  // The user explicitly selected the file, which implies permission to prompt for remote password.
                        Path remoteJobDir = Host.getJobDir (env.getResourceDir (), job);
                        Path remotePath   = remoteJobDir.resolve (fileName);

                        BasicFileAttributes localAttributes  = null;
                        BasicFileAttributes remoteAttributes = null;
                        try {remoteAttributes = Files.readAttributes (remotePath, BasicFileAttributes.class);}
                        catch (Exception e) {return;}  // Can't access remote file, so no point in continuing.
                        try {localAttributes  = Files.readAttributes (localPath,  BasicFileAttributes.class);}
                        catch (Exception e) {}

                        boolean newData = false;
                        if (remoteAttributes.isDirectory ())  // An image sequence stored in a sub-directory.
                        {
                            JViewport vp = displayPane.getViewport ();
                            // Copy any remote files that are not present in local directory.
                            if (localAttributes == null) Files.createDirectories (localPath);
                            // else local should be a directory. Otherwise, this will fail silently.
                            try (DirectoryStream<Path> stream = Files.newDirectoryStream (remotePath))
                            {
                                int last = ((SshDirectoryStream) stream).count () - 1;
                                for (Path rp : stream)
                                {
                                    Path lp = localPath.resolve (rp.getFileName ().toString ());
                                    if (Files.exists (lp))
                                    {
                                        // Handle files that were only partially written when we last tried to copy them
                                        try
                                        {
                                            BasicFileAttributes ra = Files.readAttributes (rp, BasicFileAttributes.class);
                                            BasicFileAttributes la = Files.readAttributes (lp, BasicFileAttributes.class);
                                            long position = la.size ();
                                            long count    = ra.size () - position;
                                            if (count > 0)
                                            {
                                                newData = true;
                                                try (InputStream remoteStream = Files.newInputStream (rp);
                                                     OutputStream localStream = Files.newOutputStream (lp, StandardOpenOption.WRITE, StandardOpenOption.APPEND);)
                                                {
                                                    remoteStream.skipNBytes (position);
                                                    Host.copy (remoteStream, localStream, count, null);
                                                }
                                            }
                                        }
                                        catch (IOException e) {}
                                    }
                                    else
                                    {
                                        newData = true;
                                        try {Files.copy (rp, lp);}
                                        catch (IOException e) {}
                                    }

                                    if (last >= 0)  // Only prod video player once.
                                    {
                                        last = -1;
                                        Component p = vp.getView ();
                                        if (p instanceof Video)
                                        {
                                            Video v = (Video) p;
                                            if (v.path.equals (localPath)) v.refresh (last);
                                        }
                                    }
                                }
                            }
                            Component p = vp.getView ();
                            if (p instanceof Video) return;  // The video player will be installed by fastThread, so we shouldn't try to install another.
                        }
                        else  // remote is simple file
                        {
                            long position = 0;
                            if (localAttributes == null)
                            {
                                Files.createFile (localPath);
                            }
                            else
                            {
                                position = localAttributes.size ();
                                if (localPath.endsWith ("err")) position -= job.getLong ("errSize");  // Append to, rather than replace, any locally-generated error text.
                            }
                            long count = remoteAttributes.size () - position;
                            newData = count > 0;

                            CopyProgress progress = null;
                            if (position == 0)
                            {
                                new CopyProgress ()
                                {
                                    public void update (float percent)
                                    {
                                        synchronized (displayPane) {displayText.setText (String.format ("Downloading %2.0f%%", percent * 100));}
                                    }
                                };
                            }

                            if (newData)
                            {
                                try (InputStream remoteStream = Files.newInputStream (remotePath);
                                     OutputStream localStream = Files.newOutputStream (localPath, StandardOpenOption.WRITE, StandardOpenOption.APPEND);)
                                {
                                    remoteStream.skipNBytes (position);
                                    Host.copy (remoteStream, localStream, count, progress);
                                }
                            }

                            // Also download columns file, if it exists.
                            if (node.couldHaveColumns ())
                            {
                                localPath  = localJobDir .resolve (fileName + ".columns");
                                remotePath = remoteJobDir.resolve (fileName + ".columns");
                                localAttributes  = null;
                                remoteAttributes = null;
                                try {localAttributes  = Files.readAttributes (localPath,  BasicFileAttributes.class);}
                                catch (Exception e) {}
                                try {remoteAttributes = Files.readAttributes (remotePath, BasicFileAttributes.class);}
                                catch (Exception e) {}

                                if (remoteAttributes != null  &&  (localAttributes == null  ||  localAttributes.size () < remoteAttributes.size ()))
                                {
                                    newData = true;
                                    // Always overwrite column file completely, because it can change structure over
                                    // time in a way that is not amenable to incremental download.
                                    try (InputStream remoteStream = Files.newInputStream (remotePath);
                                         OutputStream localStream = Files.newOutputStream (localPath);)
                                    {
                                        Host.copy (remoteStream, localStream, remoteAttributes.size (), progress);
                                    }
                                }
                            }
                        }
                        if (! newData) return;

                        if (this != fastThread)
                        {
                            fastThread.join ();  // Wait till fast thread completes before updating the display.
                            synchronized (displayPane)
                            {
                                if (displayThread != fastThread) return;  // Another display process has already taken over.
                                displayThread = this;  // Slow thread takes the place of fast thread
                            }
                        }
                        // Fall through to create display ...
                    }
                }

                // Step 2 -- Render data
                // The exact method depends on node type and the current display mode, selected by pushbuttons and stored in viz

                if (node.render (this)) return;

                DisplayThread dt = this;
                if (! viz.equals ("Text")  &&  node.isGraphable ())
                {
                    Component panel    = null;
                    Component current  = displayPane.getViewport ().getView ();
                    double    duration = nodeJob.complete < 1 ? nodeJob.expectedSimTime : 0;
                    if (viz.startsWith ("Table"))
                    {
                        if (refresh)
                        {
                            if (current instanceof Table.OutputTable)
                            {
                                ((Table.OutputTable) current).refresh ();
                                return;
                            }
                        }
                        Table table = new Table (node.path, viz.endsWith ("Sorted"));
                        if (table.hasData ()) panel = table.createVisualization ();
                    }
                    else if (viz.equals ("Raster"))
                    {
                        if (refresh)
                        {
                            if (current == displayChart  &&  displayChart.source instanceof Raster)
                            {
                                Raster raster = (Raster) displayChart.source;
                                raster.duration = duration;
                                raster.updateChart (displayChart.chart);
                                displayChart.offscreen = true;
                                return;
                            }
                        }
                        Raster raster = new Raster (node.path);
                        raster.duration = duration;
                        displayChart.setChart (raster.createChart (), raster);
                        displayChart.offscreen = false;
                        panel = displayChart;
                    }
                    else  // "Graph"
                    {
                        if (refresh)
                        {
                            if (current == displayChart  &&  displayChart.source instanceof Plot)
                            {
                                Plot plot = (Plot) displayChart.source;
                                plot.duration = duration;
                                plot.updateChart (displayChart.chart);
                                displayChart.offscreen = true;
                                return;
                            }
                        }
                        Plot plot = new Plot (node.path);
                        plot.duration = duration;
                        displayChart.setChart (plot.createChart (), plot);
                        displayChart.offscreen = false;
                        panel = displayChart;
                    }

                    if (panel != null)
                    {
                        final Component p = panel;
                        EventQueue.invokeLater (new Runnable ()
                        {
                            public void run ()
                            {
                                synchronized (displayPane)
                                {
                                    if (dt != displayThread) return;
                                    displayChart.buttonBar.setVisible (p == displayChart);
                                    displayPane.setViewportView (p);
                                }
                            }
                        });
                        return;
                    }
                    // Otherwise, fall through ...
                }

                // Default is plain text
                String contents = Host.fileToString (node.path);  // This will return empty string if node.path is a directory. This can happen for STACS output.
                if (node instanceof NodeError)  // Special case for "err": show ANSI colors
                {
                    EventQueue.invokeLater (new Runnable ()
                    {
                        public void run ()
                        {
                            synchronized (displayPane)
                            {
                                if (dt != displayThread) return;
                                if (refresh)
                                {
                                    Caret c = displayANSI.getCaret ();
                                    int dot = c.getDot ();
                                    if (dot != displayANSI.getDocument ().getLength ())  // Not tracking end of text, so keep stable view.
                                    {
                                        int mark = c.getMark ();
                                        Point magic = c.getMagicCaretPosition ();
                                        if (magic == null) magic = new Point ();
                                        Rectangle visible = displayPane.getViewport ().getViewRect ();
                                        if (! visible.contains (magic))  // User has scrolled away from caret.
                                        {
                                            // Scroll takes precedence over caret, so move caret back into visible area.
                                            Font f = displayANSI.getFont ();
                                            FontMetrics fm = displayANSI.getFontMetrics (f);
                                            int h = fm.getHeight ();
                                            int w = fm.getMaxAdvance ();
                                            if (w < 0) w = h / 2;
                                            h += h / 2;
                                            w += w / 2;
                                            magic.x = Math.max (magic.x, visible.x == 0 ? 0 : visible.x + w);
                                            magic.x = Math.min (magic.x, visible.x + visible.width - w);
                                            magic.y = Math.max (magic.y, visible.y == 0 ? 0 : visible.y + h);
                                            magic.y = Math.min (magic.y, visible.y + visible.height - h);
                                            dot = mark = displayANSI.viewToModel2D (magic);
                                        }

                                        displayANSI.setText (contents);
                                        c.setDot (mark);
                                        if (dot != mark) c.moveDot (dot);
                                    }
                                    else  // tracking end of text
                                    {
                                        displayANSI.setText (contents);
                                    }
                                }
                                else
                                {
                                    displayANSI.setText (contents);
                                    // Don't set caret. We want to track the end of output.
                                    displayChart.buttonBar.setVisible (false);
                                    displayPane.setViewportView (displayANSI);
                                }
                            }
                        }
                    });
                }
                else  // Otherwise, show plain text
                {
                    final String finalContents = stripANSI (contents);
                    EventQueue.invokeLater (new Runnable ()
                    {
                        public void run ()
                        {
                            synchronized (displayPane)
                            {
                                if (dt != displayThread) return;
                                if (refresh)
                                {
                                    int oldLength = displayText.getText ().length ();
                                    displayText.append (finalContents.substring (oldLength));
                                }
                                else
                                {
                                    displayText.setText (finalContents);
                                    displayText.setCaretPosition (0);
                                    displayChart.buttonBar.setVisible (false);
                                    displayPane.setViewportView (displayText);
                                }
                            }
                        }
                    });
                }
            }
            catch (Exception e) {}
        };
    }

    /**
        Remove ANSI color sequences from string.
        May be able to remove some other sequences as well, but has not been written
        to be fully general yet.
    **/
    public static String stripANSI (String contents)
    {
        int e = contents.indexOf (27);
        if (e < 0) return contents;  // Early out if no escape sequences.

        StringBuilder result = new StringBuilder ();
        int b = 0;
        while (true)
        {
            result.append (contents.substring (b, e));  // b could be same as e, in which case no text is added
            if (e >= contents.length ()) break;  // no more text left

            // Find end of escape sequence.
            e++;
            b = contents.indexOf ('m', e);
            if (b < 0) break;  // escape sequence cut off by end of string
            b++;
            if (b >= contents.length ()) break;
            e = contents.indexOf (27, b);
            if (e < 0)
            {
                result.append (contents.substring (b));
                break;
            }
        }
        return result.toString ();
    }

    public void showStatus (String message)
    {
        synchronized (displayPane)
        {
            displayText.setText (message);
            Component view = displayPane.getViewport ().getView ();
            if (view != displayText) displayPane.setViewportView (displayText);
        }
    }

    public void viewFile (boolean refresh)
    {
        synchronized (displayPane)
        {
            displayThread = null;
            if (! refresh) showStatus ("loading...");
        }

        String viz = buttons.getSelection ().getActionCommand ();
        displayThread = new DisplayThread ((NodeFile) displayNode, viz, refresh);
        displayThread.start ();
    }

    public void viewJob (boolean refresh)
    {
        synchronized (displayPane)
        {
            displayThread = null;
        }

        StringBuilder contents = new StringBuilder ();
        contents.append ("Status:");
        NodeJob jobNode = (NodeJob) displayNode;
        MNode job = jobNode.getSource ();  // job can be null if it is deleted while we are preparing this status text.
        String status = "Failed";  // For complete==2 and any unrecognized state.
        if      (jobNode.complete <  0) status = "Waiting for host";
        else if (jobNode.complete == 0)
        {
            status = "Started";
            if (job != null)
            {
                if (job.get ("queue").startsWith ("PEND"))
                {
                    status = "Waiting in queue";
                }
                else
                {
                    String backendStatus = job.get ("status");
                    if (! backendStatus.isBlank ()) status = backendStatus;
                }
            }
        }
        else if (jobNode.complete <  1)
        {
            status = Math.round (jobNode.complete * 100) + "%";
            double inactive = (System.currentTimeMillis () - jobNode.lastActive) / 1000.0;
            if (inactive > 60) status += " (" + Study.scaleTime (inactive) + " ago)";
        }
        else if (jobNode.complete == 1) status = "Success";
        else if (jobNode.complete == 3) status = "Killed (lingering)";
        else if (jobNode.complete == 4) status = "Killed";
        contents.append (" " + status + "\n");
        if (jobNode.dateStarted  != null) contents.append ("  started:  " + jobNode.dateStarted  + "\n");
        if (jobNode.dateFinished != null) contents.append ("  finished: " + jobNode.dateFinished + "\n");
        contents.append ("\n");

        if (job != null) 
        {
            appendMetadata (job, contents, "backend");
            appendMetadata (job, contents, "duration");
            appendMetadata (job, contents, "host");
            appendMetadata (job, contents, "pid");
            appendMetadata (job, contents, "seed");
            contents.append ("\n");
        }

        // Walk the model and display all overridden parameters.
        if (job != null  &&  jobNode.hasSnapshot ())
        {
            // Obtain top-level model and collated model
            MNode doc;
            MNode model;
            String key = job.get ("$inherit");
            Path localJobDir  = Host.getJobDir (Host.getLocalResourceDir (), job);
            Path snapshotPath = localJobDir.resolve ("snapshot");
            if (Files.exists (snapshotPath))
            {
                MNode snapshot = new MDoc (snapshotPath);
                doc   = snapshot.child (key);
                model = MPart.fromSnapshot (key, snapshot);
            }
            else
            {
                doc   = AppData.docs.childOrEmpty ("models", key);
                model = new MDoc (localJobDir.resolve ("model"), key);
            }

            doc.visit (new Visitor ()
            {
                public boolean visit (MNode node)
                {
                    List<String> keyList   = Arrays.asList (node.keyPath (doc));
                    List<String> paramPath = new ArrayList<String> (keyList);
                    paramPath.add ("$meta");
                    paramPath.add ("param");
                    Object[] paramArray = paramPath.toArray ();
                    if (! model.getFlag (paramArray)) return true;  // node is not a parameter
                    if (model.get (paramArray).equals ("watch")) return true;  // watchable items aren't of interest for this summary

                    String[] keyPath = keyList.toArray (new String[keyList.size ()]);
                    String key = keyPath[0];
                    for (int i = 1; i < keyPath.length; i++) key += "." + keyPath[i];

                    ParsedValue pv = new ParsedValue (model.get (keyPath));
                    contents.append (key + " =" + pv.combiner + " " + pv.expression + "\n");
                    if (pv.expression.isEmpty ())  // Could be multi-valued
                    {
                        for (MNode v : model.childOrEmpty (keyPath))
                        {
                            key = v.key ();
                            if (key.contains ("@")) contents.append ("\t" + v.get () + "\t" + key + "\n");
                        }
                    }
                    return true;
                }
            });
        }

        synchronized (displayPane)
        {
            if (displayThread != null) return;
            if (refresh)
            {
                Caret c = displayText.getCaret ();
                int dot  = c.getDot ();
                int mark = c.getMark ();
                Point magic = c.getMagicCaretPosition ();
                if (magic == null) magic = new Point ();
                Rectangle visible = displayPane.getViewport ().getViewRect ();
                if (! visible.contains (magic))  // User has scrolled away from caret.
                {
                    // Scroll takes precedence over caret, so move caret back into visible area.
                    Font f = displayText.getFont ();
                    FontMetrics fm = displayText.getFontMetrics (f);
                    int h = fm.getHeight ();
                    int w = fm.getMaxAdvance ();
                    if (w < 0) w = h / 2;
                    h += h / 2;
                    w += w / 2;
                    magic.x = Math.max (magic.x, visible.x == 0 ? 0 : visible.x + w);
                    magic.x = Math.min (magic.x, visible.x + visible.width - w);
                    magic.y = Math.max (magic.y, visible.y == 0 ? 0 : visible.y + h);
                    magic.y = Math.min (magic.y, visible.y + visible.height - h);
                    dot = mark = displayText.viewToModel2D (magic);
                }

                displayText.setText (contents.toString ());
                c.setDot (mark);
                if (dot != mark) c.moveDot (dot);
            }
            else
            {
                displayText.setText (contents.toString ());
                displayText.setCaretPosition (0);
            }
            displayChart.buttonBar.setVisible (false);
            if (displayPane.getViewport ().getView () != displayText) displayPane.setViewportView (displayText);
        }
        displayPane.repaint ();
    }

    public void appendMetadata (MNode doc, StringBuilder result, String name)
    {
        MNode child = doc.child (name);
        if (child != null) result.append (name + "=" + child.get () + "\n");
    }

    public void delete ()
    {
        delete (tree.getSelectionPaths ());
    }

    public void delete (NodeJob job)
    {
        TreePath[] paths = new TreePath[1];
        paths[0] = new TreePath (job.getPath ());
        delete (paths);
    }

    public void delete (TreePath[] paths)
    {
        if (paths == null  ||  paths.length == 0) return;

        NodeBase nextSelection = null;  // The node that will be focused after all the deletes are done.
        TreePath leadSelection = tree.getLeadSelectionPath ();
        if (leadSelection != null) nextSelection = (NodeBase) leadSelection.getLastPathComponent ();

        int firstRow = Integer.MAX_VALUE;
        int lastRow  = 0;
        for (int i = 0; i < paths.length; i++)
        {
            TreePath path = paths[i];

            // Ensure that we don't try to delete something twice.
            NodeBase node = (NodeBase) path.getLastPathComponent ();
            if (node.markDelete)
            {
                paths[i] = null;
                continue;
            }
            node.markDelete = true;
            if (nextSelection == node) nextSelection = null;  // Current focus is a node that will be deleted. This is almost always the case, except when jobs are deleted by the Studies panel.

            // In some cases (such as ctrl-click), the rows may not be in ascending order.
            int row = tree.getRowForPath (path);
            firstRow = Math.min (firstRow, row);
            lastRow  = Math.max (lastRow,  row);
        }
        if (firstRow == Integer.MAX_VALUE) return;  // No rows passed the filter above (because they were already selected).

        NodeBase firstSelection = (NodeBase) tree.getPathForRow (firstRow).getLastPathComponent ();
        NodeBase lastSelection  = (NodeBase) tree.getPathForRow (lastRow ).getLastPathComponent ();
        if (nextSelection == null) nextSelection = (NodeBase) lastSelection.getNextSibling ();
        if (nextSelection != null)
        {
            NodeBase parent = (NodeBase) nextSelection.getParent ();  // Could be root. Root is never selected, so the following test works correctly in that case.
            if (tree.isPathSelected (new TreePath (parent.getPath ())))  // next sibling will also die, so need next sibling of parent.
            {
                nextSelection = (NodeBase) parent.getNextSibling ();
            }
        }
        if (nextSelection == null) nextSelection = (NodeBase) firstSelection.getPreviousSibling ();  // If this exists, then it is guaranteed to continue to exist after deletion.
        if (nextSelection == null) nextSelection = (NodeBase) firstSelection.getParent ();  // "firstSelection" could be first file under a job, or first job in tree. In the latter case, the tree will be empty after delete.

        if (nextSelection == root)  // Tree will be empty after delete.
        {
            // Shut down the display thread.
            synchronized (displayPane)
            {
                displayThread = null;
                displayNode   = null;  // All access to this happens on EDT, so safe.
                displayText.setText ("");
                displayChart.buttonBar.setVisible (false);
                if (displayPane.getViewport ().getView () != displayText) displayPane.setViewportView (displayText);
            }
        }
        else  // Set the new selection. This will not be touched by the delete process.
        {
            TreePath path = new TreePath (nextSelection.getPath ());
            tree.setSelectionPath (path);
            tree.scrollPathToVisible (path);
        }

        tree.repaint ();  // Ensure that strikethru marks will be displayed to user.

        // Spawn the rest of the delete process off to a separate thread.
        // The tree will be updated on the EDT as work progresses.
        Thread deleteThread = new Thread ("Delete Jobs")
        {
            public void run ()
            {
                Map<Host,HostDeleteThread> hostThreads = new HashMap<Host,HostDeleteThread> ();
                Set<NodeJob> parents = new HashSet<NodeJob> ();
                for (TreePath path : paths)
                {
                    if (path == null) continue;

                    final NodeBase node = (NodeBase) path.getLastPathComponent ();
                    if (node instanceof NodeJob)
                    {
                        NodeJob job = (NodeJob) node;
                        parents.add (job);
                        synchronized (job)
                        {
                            if (job.complete < 1  ||  job.complete == 3)
                            {
                                // It's important that the job not have resources locked in the directory when we try to delete it.
                                // If the job is still running, downgrade the delete request to a kill request.
                                // The user will have to hit delete again, once the job dies.
                                job.stop ();
                                job.markDelete = false;
                                EventQueue.invokeLater (new Runnable ()
                                {
                                    public void run ()
                                    {
                                        tree.repaint (tree.getPathBounds (new TreePath (job.getPath ())));
                                    }
                                });
                                continue;
                            }
                        }
                    }
                    else
                    {
                        if (parents.contains ((NodeJob) node.getParent ())) continue;
                    }

                    NodeJob job;
                    if (node instanceof NodeJob)
                    {
                        job = (NodeJob) node;
                        synchronized (job) {job.deleted = true;}  // Signal the monitor thread to drop this job.
                        synchronized (jobNodes) {jobNodes.remove (job.key);}
                    }
                    else
                    {
                        job = (NodeJob) node.getParent ();
                    }
                    Host env = Host.get (job.getSource ());

                    HostDeleteThread t = hostThreads.get (env);
                    if (t == null)
                    {
                        t = new HostDeleteThread (env);
                        t.setDaemon (true);
                        t.start ();
                        hostThreads.put (env, t);
                    }
                    synchronized (t.nodes) {t.nodes.add (node);}
                }

                for (HostDeleteThread t : hostThreads.values ()) t.allQueued = true;
            }
        };
        deleteThread.setDaemon (true);  // It's OK if this work is interrupted. Any undeleted item will simply show up in the tree on next launch, and the user can try again.
        deleteThread.start ();
    }

    public class HostDeleteThread extends Thread
    {
        public Host           env;
        public List<NodeBase> nodes = new ArrayList<NodeBase> ();
        public boolean        allQueued;

        public HostDeleteThread (Host env)
        {
            super ("Delete Jobs (" + env.name + ")");
            this.env = env;
            if (env instanceof Remote) ((Remote) env).enable ();  // Since this thread is started by user action, we have permission to go interactive.
        }

        public void run ()
        {
            while (true)
            {
                NodeBase nodeBase = null;
                synchronized (nodes)
                {
                    if (! nodes.isEmpty ()) nodeBase = nodes.remove (nodes.size () - 1);
                }
                if (nodeBase == null)
                {
                    if (allQueued) break;  // done
                    try {sleep (1000);}
                    catch (InterruptedException e) {}
                    continue;
                }

                NodeJob nodeJob;
                if (nodeBase instanceof NodeJob) nodeJob = (NodeJob) nodeBase;
                else                             nodeJob = (NodeJob) nodeBase.getParent ();
                MDoc job = (MDoc) nodeJob.getSource ();
               
                try
                {
                    if (nodeBase instanceof NodeJob)
                    {
                        if (env instanceof Remote)
                        {
                            Path remoteJobDir = Host.getJobDir (env.getResourceDir (), job);
                            env.deleteTree (remoteJobDir);
                        }
                        job.delete ();  // deletes local job directory
                    }
                    else if (nodeBase instanceof NodeFile)
                    {
                        NodeFile nf = (NodeFile) nodeBase;
                        String fileName = nf.path.getFileName ().toString ();

                        if (env instanceof Remote)
                        {
                            Path remoteJobDir = Host.getJobDir (env.getResourceDir (), job);
                            Path remoteFile = remoteJobDir.resolve (fileName);
                            env.deleteTree (remoteFile);  // In case this is a video directory rather than just a file, use deleteTree().

                            // Also delete auxiliary columns file.
                            if (nf.couldHaveColumns ())
                            {
                                remoteFile = remoteJobDir.resolve (fileName + ".columns");
                                env.deleteTree (remoteFile);  // Even though this is definitely not a tree, it is still convenient to call deleteTre(), because it absorbs a lot of potential errors.
                            }
                        }

                        Path localJobDir = Host.getJobDir (Host.getLocalResourceDir (), job);
                        Path localFile = localJobDir.resolve (fileName);
                        Host.get ().deleteTree (localFile);
                        if (nf.couldHaveColumns ())
                        {
                            localFile = localJobDir.resolve (fileName + ".columns");
                            Host.get ().deleteTree (localFile);
                        }
                    }

                    final NodeBase deleteNode = nodeBase;
                    EventQueue.invokeLater (new Runnable ()
                    {
                        public void run ()
                        {
                            model.removeNodeFromParent (deleteNode);
                        }
                    });
                }
                catch (Exception e) {e.printStackTrace ();}
            }
        }
    }

    /**
        Add a newly-created job to the list, and do all remaining setup to monitor it.
        This must be called on the EDT.
    **/
    public NodeJob addNewRun (MNode run, boolean takeFocus)
    {
        NodeJob node = new NodeJob (run, true);
        node.inherit = run.getOrDefault (node.key, "$inherit").split (",", 2)[0].replace ("\"", "");
        node.setUserObject (node.inherit);

        // In case node is inserted by background process, we need to preserve current view position.
        JViewport vp = (JViewport) tree.getParent ();
        Point p = vp.getViewPosition ();

        synchronized (jobNodes) {jobNodes.put (node.key, node);}
        model.insertNodeInto (node, root, 0);  // Since this always executes on event dispatch thread, it will not conflict with other code that accesses model.
        if (root.getChildCount () == 1) model.nodeStructureChanged (root);  // If the list was empty, we need to give the JTree a little extra kick to get started.
        if (takeFocus)
        {
            tree.expandRow (0);
            tree.setSelectionRow (0);
            tree.scrollRowToVisible (0);
        }
        else  // Node is being inserted by a background process, so don't let tree scroll.
        {
            p.y += tree.getRowBounds (0).height;
            vp.setViewPosition (p);
        }

        return node;
    }
}

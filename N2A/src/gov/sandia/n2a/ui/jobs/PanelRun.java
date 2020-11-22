/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDir;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.execenvs.Host;
import gov.sandia.n2a.execenvs.Host.CopyProgress;
import gov.sandia.n2a.execenvs.Remote;
import gov.sandia.n2a.execenvs.SshFileSystemProvider.SshDirectoryStream;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.images.ImageUtil;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
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

    public NodeBase         root;
    public DefaultTreeModel model;
    public JTree            tree;
    public JScrollPane      treePane;

    public JButton            buttonStop;
    public JPopupMenu         menuHost;
    public long               menuCanceledAt;
    public ButtonGroup        buttons;
    public JComboBox<String>  comboScript;
    public JTextArea          displayText;
    public PanelChart         displayChart = new PanelChart ();
    public JScrollPane        displayPane = new JScrollPane ();
    public DisplayThread      displayThread = null;
    public NodeBase           displayNode = null;
    public MDir               runs;  // Copied from AppData for convenience

    public static ImageIcon iconConnect    = ImageUtil.getImage ("connect.gif");
    public static ImageIcon iconPause      = ImageUtil.getImage ("pause-16.png");
    //public static ImageIcon iconDisconnect = ImageUtil.getImage ("disconnect.gif");
    public static ImageIcon iconStop       = ImageUtil.getImage ("stop.gif");

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
                    viewFile (true);
                    job = (NodeJob) displayNode.getParent ();
                }
                else if (displayNode instanceof NodeJob)
                {
                    viewJob ();
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
            }
        });

        Thread refreshThreadSlow = new Thread ("Start Job Monitors")
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
                for (int i = reverse.size () - 1; i >= 0; i--) root.add (reverse.get (i));  // Reverse the order, so later dates come first.
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
                // Here, order doesn't matter so much, but we sill want to examine more recent jobs first.
                for (int i = reverse.size () - 1; i >= 0; i--) reverse.get (i).distribute ();
                for (Host h : Host.getHosts ()) h.restartMonitorThread ();
            }
        };
        refreshThreadSlow.setDaemon (true);
        refreshThreadSlow.start ();

        Thread refreshThreadFast = new Thread ("Monitor Focused Job")
        {
            public void run ()
            {
                try
                {
                    while (true)
                    {
                        NodeBase d = displayNode;  // Make local copy (atomic action) to prevent it changing from under us
                        if (d != null)
                        {
                            if (d instanceof NodeFile) d = (NodeBase) d.getParent ();  // parent could be null, if a sub-node was just deleted
                            if (d != null) ((NodeJob) d).monitorProgress ();
                        }
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

        // Icon options:
        // a cloud, similar to the pull or push icons on git tab
        // "connect.gif" -- connection icon
        // "disconnect.gif" -- connection with X
        // "refresh.gif" -- yin-yang-ish arrows
        // "warn.gif" -- yellow sign with exclamation point
        JButton buttonHost = new JButton (ImageUtil.getImage ("connect.gif"));
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
                    else if (! remote.isEnabled ()) item.setIcon (iconPause);
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
                if (displayNode instanceof NodeFile) viewFile (true);
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

        comboScript = new JComboBox<String> ();
        comboScript.setEditable (true);
        comboScript.setToolTipText ("Run Script");
        comboScript.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if (e.getActionCommand ().equals ("comboBoxEdited"))
                {
                    String script = (String) comboScript.getSelectedItem ();
                    script = script.trim ();
                    comboScript.removeItem (script);
                    comboScript.insertItemAt (script, 0);
                    comboScript.setSelectedItem (script);
                    saveScripts ();

                    // Execute script
                    Path path = null;
                    if      (displayNode instanceof NodeJob ) path = ((NodeJob ) displayNode).getJobPath ();
                    else if (displayNode instanceof NodeFile) path = ((NodeFile) displayNode).path;
                    if (path != null)
                    {
                        String jobDirString      = path.getParent ().toAbsolutePath ().toString ();
                        String pathString        = path.toAbsolutePath ().toString ();
                        String resourceDirString = AppData.properties.get ("resourceDir");
                        script = script.replaceAll ("\\%d", Matcher.quoteReplacement (jobDirString));
                        script = script.replaceAll ("\\%f", Matcher.quoteReplacement (pathString));
                        script = script.replaceAll ("\\%r", Matcher.quoteReplacement (resourceDirString));
                        System.out.println ("script = " + script);
                        try {Runtime.getRuntime ().exec (script);}
                        catch (IOException error) {error.printStackTrace ();}
                    }
                }
            }
        });
        comboScript.getEditor ().getEditorComponent ().addKeyListener (new KeyAdapter ()
        {
            public void keyPressed (KeyEvent e)
            {
                if (e.getKeyCode () == KeyEvent.VK_DELETE  &&  e.isControlDown ())
                {
                    String script = (String) comboScript.getSelectedItem ();
                    comboScript.removeItem (script);
                    saveScripts ();
                    e.consume ();
                }
            }
        });
        for (MNode n : AppData.state.childOrCreate ("PanelRun", "scripts")) comboScript.addItem (n.get ());

        JSplitPane split;
        Lay.BLtg
        (
            this,
            split = Lay.SPL
            (
                treePane = Lay.sp (tree),
                Lay.BL
                (
                    "N", Lay.BL
                    (
                        "W", Lay.FL
                        (
                            "L",
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
                            "hgap=5,vgap=1"
                        ),
                        "C", comboScript
                    ),
                    "C", displayPane
                )
            )
        );
        setFocusCycleRoot (true);

        split.setDividerLocation (AppData.state.getOrDefault (250, "PanelRun", "divider"));
        split.addPropertyChangeListener (JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                Object o = e.getNewValue ();
                if (o instanceof Integer) AppData.state.set (o, "PanelRun", "divider");
            }
        });
    }

    public void saveScripts ()
    {
        MNode scripts = AppData.state.childOrCreate ("PanelRun", "scripts");
        scripts.clear ();
        for (int i = 0; i < comboScript.getItemCount (); i++)
        {
            scripts.set (comboScript.getItemAt (i), String.valueOf (i));
        }
    }

    public class DisplayThread extends Thread
    {
        public NodeFile node;
        public String   viz;  ///< The type of visualization to show, such as table, graph or raster
        public boolean  stop = false;

        public DisplayThread (NodeFile node, String viz)
        {
            super ("PanelRun Fetch File");
            this.node = node;
            this.viz  = viz;
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
                MNode job = ((NodeJob) node.getParent ()).getSource ();
                Host env = Host.get (job);
                if (env instanceof Remote)
                {
                    ((Remote) env).enable ();  // The user explicitly selected the file, which implies permission to prompt for remote password.
                    try
                    {
                        String fileName = node.path.getFileName ().toString ();
                        Path localFile  = Host.getJobDir (Host.getLocalResourceDir (), job).resolve (fileName);
                        Path remoteFile = Host.getJobDir (env.getResourceDir (),       job).resolve (fileName);

                        BasicFileAttributes localAttributes  = null;
                        BasicFileAttributes remoteAttributes = null;
                        try {localAttributes  = Files.readAttributes (localFile,  BasicFileAttributes.class);}
                        catch (Exception e) {}
                        try {remoteAttributes = Files.readAttributes (remoteFile, BasicFileAttributes.class);}
                        catch (Exception e) {}

                        if (remoteAttributes != null)
                        {
                            if (remoteAttributes.isDirectory ())  // An image sequence stored in a sub-directory.
                            {
                                // Copy any remote files that are not present in local directory.
                                if (localAttributes == null) Files.createDirectories (localFile);
                                // else local should be a directory. Otherwise, this will fail silently.
                                try (DirectoryStream<Path> stream = Files.newDirectoryStream (remoteFile))
                                {
                                    int total = ((SshDirectoryStream) stream).count ();
                                    int count = 0;
                                    for (Path rp : stream)
                                    {
                                        Path lp = localFile.resolve (rp.getFileName ().toString ());
                                        if (! Files.exists (lp))
                                        {
                                            try {Files.copy (rp, lp);}
                                            catch (IOException e) {}
                                        }
                                        count++;
                                        synchronized (displayText) {displayText.setText (String.format ("Downloading %2.0f%%", 100.0 * count / total));}
                                    }
                                }
                            }
                            else
                            {
                                long position = 0;
                                if (localAttributes == null) Files.createFile (localFile);
                                else                         position = localAttributes.size ();
                                long count = remoteAttributes.size () - position;
                                if (count > 0)
                                {
                                    try (InputStream remoteStream = Files.newInputStream (remoteFile);
                                         OutputStream localStream = Files.newOutputStream (localFile, StandardOpenOption.WRITE, StandardOpenOption.APPEND);)
                                    {
                                        remoteStream.skip (position);
                                        Host.copy (remoteStream, localStream, count, new CopyProgress ()
                                        {
                                            public void update (float percent)
                                            {
                                                synchronized (displayText) {displayText.setText (String.format ("Downloading %2.0f%%", percent * 100));}
                                            }
                                        });
                                    }
                                }
                            }
                            node.path = localFile;  // Force to use local copy, regardless of whether it was local or remote before.
                        }
                    }
                    catch (Exception e) {}
                }

                // Step 2 -- Load data
                // The exact method depends on node type and the current display mode, selected by pushbuttons and stored in viz
                if (node.type == NodeFile.Type.Video)
                {
                    final Video v = new Video (node);
                    EventQueue.invokeLater (new Runnable ()
                    {
                        public void run ()
                        {
                            if (stop) return;
                            displayChart.buttonBar.setVisible (false);
                            displayPane.setViewportView (v);
                            v.play ();
                        }
                    });
                    // Don't signal done. That will keep file polling from constantly calling play().
                    // However, play() also has a guard to avoid restarting the play thread.
                    return;
                }
                else if (node.type == NodeFile.Type.Picture)
                {
                    final Picture p = new Picture (node.path);
                    EventQueue.invokeLater (new Runnable ()
                    {
                        public void run ()
                        {
                            if (stop) return;
                            displayChart.buttonBar.setVisible (false);
                            displayPane.setViewportView (p);
                        }
                    });
                    signalDone ();
                    return;
                }
                else if (! viz.equals ("Text"))
                {
                    // Determine if the file is actually a table that can be graphed
                    Path dir = node.path.getParent ();
                    String fileName = node.path.getFileName ().toString ();
                    boolean graphable = Files.exists (dir.resolve (fileName + ".columns"));  // An auxiliary column file is sufficient evidence that this is tabular data.
                    if (! graphable)
                    {
                        BufferedReader reader = Files.newBufferedReader (node.path);
                        String line = reader.readLine ();
                        graphable = line.startsWith ("$t")  ||  line.startsWith ("Index");
                        if (! graphable)
                        {
                            // Try an alternate heuristic: Does the line appear to be a set of tab-delimited fields?
                            // Don't allow spaces, because it could look too much like ordinary text.
                            line = reader.readLine ();  // Get a second line, just to ensure we get past textual headers.
                            if (line != null)
                            {
                                String[] pieces = line.split ("\\t");
                                int columns = 0;
                                for (String p : pieces)
                                {
                                    if (p.length () < 20)
                                    {
                                        try
                                        {
                                            Float.parseFloat (p);
                                            columns++;  // If that didn't throw an exception, then p is likely a number.
                                        }
                                        catch (Exception e) {}
                                    }
                                }
                                // At least 3 viable columns, and more than half are interpretable as numbers.
                                graphable = columns >= 3  &&  (double) columns / pieces.length > 0.7;
                            }
                        }
                        reader.close ();
                    }

                    if (graphable)
                    {
                        Component panel = null;
                        if (viz.equals ("Table"))
                        {
                            Table table = new Table (node.path, false);
                            if (table.hasData ()) panel = table.createVisualization ();
                        }
                        else if (viz.equals ("TableSorted"))
                        {
                            Table table = new Table (node.path, true);
                            if (table.hasData ()) panel = table.createVisualization ();
                        }
                        else if (viz.equals ("Graph"))
                        {
                            Plot plot = new Plot (node.path);
                            if (plot.hasData ())
                            {
                                displayChart.setChart (plot.createChart ());
                                panel = displayChart;
                            }
                        }
                        else if (viz.equals ("Raster"))
                        {
                            Raster raster = new Raster (node.path);
                            if (raster.hasData ())
                            {
                                displayChart.setChart (raster.createChart ());
                                panel = displayChart;
                            }
                        }

                        if (stop)
                        {
                            signalDone ();
                            return;
                        }
                        if (panel != null)
                        {
                            final Component p = panel;
                            EventQueue.invokeLater (new Runnable ()
                            {
                                public void run ()
                                {
                                    if (stop) return;
                                    displayChart.buttonBar.setVisible (p == displayChart);
                                    displayPane.setViewportView (p);
                                }
                            });

                            signalDone ();
                            return;
                        }
                        // Otherwise, fall through ...
                    }
                }

                // Default is plain text
                final String contents = Host.fileToString (node.path);
                if (stop)
                {
                    signalDone ();
                    return;
                }

                EventQueue.invokeLater (new Runnable ()
                {
                    public void run ()
                    {
                        synchronized (displayText)
                        {
                            if (stop) return;
                            displayText.setText (contents);
                            displayText.setCaretPosition (0);
                            displayChart.buttonBar.setVisible (false);
                            displayPane.setViewportView (displayText);
                        }
                    }
                });
            }
            catch (Exception e)
            {
            }

            signalDone ();
        };

        public void signalDone ()
        {
            synchronized (displayText)
            {
                if (displayThread == this) displayThread = null;
            }
        }
    }

    public void viewFile (boolean showLoading)
    {
        synchronized (displayText)
        {
            if (displayThread != null)
            {
                displayThread.stop = true;
                displayThread = null;
            }
            if (showLoading) displayText.setText ("loading...");
        }

        if (showLoading)
        {
            Component view = displayPane.getViewport ().getView ();
            if (view != displayText) displayPane.setViewportView (displayText);
        }

        String viz = buttons.getSelection ().getActionCommand ();
        displayThread = new DisplayThread ((NodeFile) displayNode, viz);
        displayThread.start ();
    }

    public void viewJob ()
    {
        if (displayThread != null)
        {
            synchronized (displayText)
            {
                displayThread.stop = true;
                displayThread = null;
            }
        }

        NodeJob job = (NodeJob) displayNode;
        MNode doc = job.getSource ();

        StringBuilder contents = new StringBuilder ();
        contents.append ("Status:");
        if      (job.complete < 0)                       contents.append (" Waiting");
        else if (job.complete == 0)                      contents.append (" Started");
        else if (job.complete > 0  &&  job.complete < 1) contents.append (" " + Math.round (job.complete * 100) + "%");
        else if (job.complete == 1)                      contents.append (" Success");
        else if (job.complete == 3)                      contents.append (" Killed (lingering)");
        else if (job.complete == 4)                      contents.append (" Killed");
        else                                             contents.append (" Failed");  // complete==2, or any value not specified above
        contents.append ("\n");
        if (job.dateStarted  != null) contents.append ("  started:  " + job.dateStarted  + "\n");
        if (job.dateFinished != null) contents.append ("  finished: " + job.dateFinished + "\n");
        contents.append ("\n");
        appendMetadata (doc, contents, "backend");
        appendMetadata (doc, contents, "duration");
        appendMetadata (doc, contents, "host");
        appendMetadata (doc, contents, "pid");
        appendMetadata (doc, contents, "seed");

        synchronized (displayText)
        {
            displayText.setText (contents.toString ());
            displayText.setCaretPosition (0);
        }
        if (displayPane.getViewport ().getView () != displayText) displayPane.setViewportView (displayText);
        displayPane.repaint (displayPane.getBounds ());
    }

    public void appendMetadata (MNode doc, StringBuilder result, String... indices)
    {
        MNode child = doc.child ("$metadata");
        if (child == null) return;
        child = child.child (indices);
        if (child == null) return;
        String name = "";
        for (String i : indices) name += "." + i;
        name = name.substring (1);
        result.append (name + "=" + child.get () + "\n");
    }

    public void delete ()
    {
        TreePath[] paths = tree.getSelectionPaths ();
        if (paths.length < 1) return;

        boolean nextSelectionIsParent = false;
        NodeBase firstSelection = (NodeBase) paths[0             ].getLastPathComponent ();
        NodeBase lastSelection  = (NodeBase) paths[paths.length-1].getLastPathComponent ();
        NodeBase                   nextSelection = (NodeBase) lastSelection .getNextSibling ();
        if (nextSelection == null) nextSelection = (NodeBase) firstSelection.getPreviousSibling ();
        if (nextSelection == null)
        {
            nextSelection = (NodeBase) firstSelection.getParent ();  // could be root
            nextSelectionIsParent = true;
        }

        // Anything being displayed must also be a selected item, so always shut down the display thread.
        synchronized (displayText)
        {
            if (displayThread != null)
            {
                displayThread.stop = true;
                displayThread = null;
            }
            displayNode = null;  // All access to this happens on EDT, so safe.
            displayText.setText ("");
        }
        if (displayPane.getViewport ().getView () != displayText) displayPane.setViewportView (displayText);

        Set<NodeJob> parents = new HashSet<NodeJob> ();
        for (TreePath path : paths)
        {
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
                        continue;
                    }
                }
            }
            else
            {
                if (parents.contains ((NodeJob) node.getParent ())) continue;
            }
            model.removeNodeFromParent (node);

            // It may seem insane to start a separate thread for each path, but it actually makes sense
            // to do all this work in parallel. In particular, if there are remote jobs, there may be
            // some delay in confirming they are stopped. No reason to do that serially.
            // The downside is that we could end up spawning thousands of threads at the same time.
            new Thread ("PanelRun Delete")
            {
                public void run ()
                {
                    if (node instanceof NodeJob)
                    {
                        NodeJob job = (NodeJob) node;
                        synchronized (job) {job.deleted = true;}  // Signal the monitor thread to drop this job.

                        MDoc doc = (MDoc) job.getSource ();
                        if (job.complete < 1  ||  job.complete == 3)
                        {
                            // It's important that the job not have resources locked in the directory when we try to delete it.
                            // If the job is still running, downgrade the delete request to a kill request.
                            // The user will have to hit delete again, once the job dies.
                            job.stop ();
                            return;
                        }
                        doc.delete ();  // deletes local job directory
                        Host env = Host.get (doc);
                        if (env instanceof Remote)
                        {
                            // We have already "forgotten" the job locally. If the remote files cannot be removed,
                            // for example because the network is down, we leak disk space on the remote machine.
                            // This creates a user-interface quandary. The user should be able to fully delete
                            // local records, for example if the remote host has been permanently retired.
                            // Yet there should be some way to indicate the unknown state of remote jobs.
                            // A possible compromise is some utility (perhaps in Settings/Hosts) that lets
                            // the user scan for zombie jobs and add a placeholder back into the local jobs list.
                            try
                            {
                                Path resourceDir  = env.getResourceDir ();
                                Path remoteJobDir = Host.getJobDir (resourceDir, doc);
                                Host.deleteTree (remoteJobDir, true);
                            }
                            catch (Exception e) {}
                        }
                    }
                    else if (node instanceof NodeFile)
                    {
                        try {Files.delete (((NodeFile) node).path);}
                        catch (IOException e) {}
                    }
                };
            }.start ();
        }

        if (nextSelectionIsParent  &&  nextSelection.getChildCount () > 0)
        {
            nextSelection = (NodeBase) nextSelection.getChildAt (0);
        }
        if (nextSelection != root)
        {
            TreePath path = new TreePath (nextSelection.getPath ());
            tree.setSelectionPath (path);
            tree.scrollPathToVisible (path);
        }

        tree.repaint (treePane.getViewport ().getViewRect ());
    }

    /**
        Add a newly-created job to the list, and do all remaining setup to monitor it.
        This must be called on the EDT.
    **/
    public void addNewRun (MNode run)
    {
        NodeJob node = new NodeJob (run, true);
        node.setUserObject (run.getOrDefault (node.key, "$inherit").split (",", 2)[0].replace ("\"", ""));

        model.insertNodeInto (node, root, 0);  // Since this always executes on event dispatch thread, it will not conflict with other code that accesses model.
        if (root.getChildCount () == 1) model.nodeStructureChanged (root);  // If the list was empty, we need to give the JTree a little extra kick to get started.
        tree.expandRow (0);
        tree.setSelectionRow (0);
        tree.scrollRowToVisible (0);
        tree.requestFocusInWindow ();

        Host env = Host.get (run);
        synchronized (env.running) {env.running.add (node);}
    }
}

/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDir;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.execenvs.HostSystem;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.images.ImageUtil;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;

import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
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
    public NodeBase         root;
    public DefaultTreeModel model;
    public JTree            tree;
    public JScrollPane      treePane;

    public JButton           buttonStop;
    public ButtonGroup       buttons;
    public JComboBox<String> comboScript;
    public JTextArea         displayText;
    public JScrollPane       displayPane = new JScrollPane ();
    public DisplayThread     displayThread = null;
    public NodeBase          displayNode = null;
    public MDir              runs;  // Copied from AppData for convenience
    public List<NodeJob>     running = new LinkedList<NodeJob> ();  // Jobs that we are actively monitoring because they may still be running.

    public PanelRun ()
    {
        root  = new NodeBase ();
        model = new DefaultTreeModel (root);
        tree  = new JTree (model);
        tree.setRootVisible (false);
        tree.setShowsRootHandles (true);
        tree.getSelectionModel ().setSelectionMode (TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);

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
            Rectangle oldBounds;

            public void valueChanged (TreeSelectionEvent e)
            {
                NodeBase newNode = (NodeBase) tree.getLastSelectedPathComponent ();
                if (newNode == null) return;
                if (newNode == displayNode) return;

                if (oldBounds != null) tree.paintImmediately (oldBounds);
                Rectangle newBounds = tree.getPathBounds (e.getPath ());
                if (newBounds != null) tree.paintImmediately (newBounds);
                oldBounds = newBounds;

                displayNode = newNode;
                if      (displayNode instanceof NodeFile) viewFile ();
                else if (displayNode instanceof NodeJob)  viewJob ();
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
                TreePath path = event.getPath ();  // TODO: can this ever be null?
                Object o = path.getLastPathComponent ();
                if (o instanceof NodeJob) ((NodeJob) o).build (tree);
            }

            public void treeWillCollapse (TreeExpansionEvent event) throws ExpandVetoException
            {
            }
        });

        tree.addTreeExpansionListener (new TreeExpansionListener ()
        {
            public void treeExpanded (TreeExpansionEvent event)
            {
                repaintSouth (event.getPath ());
            }

            public void treeCollapsed (TreeExpansionEvent event)
            {
                repaintSouth (event.getPath ());
            }
        });

        Thread refreshThreadSlow = new Thread ("Job Refresh Slow")
        {
            public void run ()
            {
                try
                {
                    // Initial load
                    synchronized (running)
                    {
                        for (MNode n : AppData.runs) running.add (0, new NodeJob (n, false));  // Insert at front to create reverse time order. This should be efficient on a doubly-linked list.
                        for (NodeJob job : running) root.add (job);
                    }
                    EventQueue.invokeLater (new Runnable ()
                    {
                        public void run ()
                        {
                            // Update display with newly loaded jobs.
                            // If a job was added before we finish load, and if the user focused a row under it,
                            // then we want to retain that selection.
                            int row = tree.getLeadSelectionRow ();
                            model.nodeStructureChanged (root);
                            if (model.getChildCount (root) > 0) tree.setSelectionRow (Math.max (0, row));
                        }
                    });

                    // Periodic refresh to show status of running jobs
                    while (true)
                    {
                        synchronized (running)
                        {
                            Iterator<NodeJob> i = running.iterator ();
                            while (i.hasNext ())
                            {
                                NodeJob job = i.next ();
                                job.monitorProgress (PanelRun.this);
                                if (job.complete >= 1  ||  job.deleted) i.remove ();
                            }
                        }
                        sleep (20000);
                    }
                }
                catch (InterruptedException e)
                {
                }
            }
        };
        refreshThreadSlow.setDaemon (true);
        refreshThreadSlow.start ();

        Thread refreshThreadFast = new Thread ("Job Refresh Fast")
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
                            if (d != null) ((NodeJob) d).monitorProgress (PanelRun.this);
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

        displayText = new JTextArea ();
        displayText.setEditable(false);
        displayText.setFont (new Font (Font.MONOSPACED, Font.PLAIN, displayText.getFont ().getSize ()));
        displayPane.setViewportView (displayText);

        buttonStop = new JButton (ImageUtil.getImage ("stop.gif"));
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

        ActionListener graphListener = new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if (displayNode instanceof NodeFile) viewFile ();
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
        buttonText.setSelected (true);

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
                    if      (displayNode instanceof NodeJob ) path = Paths.get (((NodeJob ) displayNode).source.get ());
                    else if (displayNode instanceof NodeFile) path =            ((NodeFile) displayNode).path;
                    if (path != null)
                    {
                        String pathString = path.toAbsolutePath ().toString ();
                        String dirString = path.getParent ().toAbsolutePath ().toString ();
                        System.out.println ("script=" + script);
                        System.out.println ("path=" + pathString);
                        System.out.println ("dir=" + dirString);
                        script = script.replaceAll ("\\%d", Matcher.quoteReplacement (dirString));
                        script = script.replaceAll ("\\%f", Matcher.quoteReplacement (pathString));
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
                Lay.BL (treePane = Lay.sp (tree)),
                Lay.BL
                (
                    "N", Lay.BL
                    (
                        "W", Lay.FL
                        (
                            "L",
                            Lay.BL (buttonStop, "eb=20r"),
                            buttonText,
                            buttonTable,
                            buttonTableSorted,
                            buttonGraph,
                            buttonRaster, "eb=20r",
                            "hgap=5,vgap=1"
                        ),
                        "C", comboScript
                    ),
                    "C", displayPane
                )
            )
        );
        setFocusCycleRoot (true);

        split.setDividerLocation (AppData.state.getOrDefaultInt ("PanelRun", "divider", "250"));
        split.addPropertyChangeListener (JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                Object o = e.getNewValue ();
                if (o instanceof Integer) AppData.state.set ("PanelRun", "divider", o);
            }
        });
    }

    public void saveScripts ()
    {
        MNode scripts = AppData.state.childOrCreate ("PanelRun", "scripts");
        scripts.clear ();
        for (int i = 0; i < comboScript.getItemCount (); i++)
        {
            scripts.set (String.valueOf (i), comboScript.getItemAt (i));
        }
    }

    public class DisplayThread extends Thread
    {
        public NodeFile node;
        public String   viz;  ///< The type of visualization to show, such as table, graph or raster
        public boolean stop = false;

        public DisplayThread (NodeFile node, String viz)
        {
            super ("PanelRun Fetch File");
            this.node = node;
            this.viz  = viz;
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
                //   huge  -- too big to store on local system, for example a supercomputer job; must be downloaded/displayed in segments
                // The current code only handles small files. In particular, we don't actually do Step 1, but simply assume data is local.
                MNode job = ((NodeJob) node.getParent ()).source;
                HostSystem env = HostSystem.get (job.getOrDefault ("$metadata", "host", "localhost"));

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
                            displayPane.setViewportView (v);
                            v.play ();
                        }
                    });
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
                            displayPane.setViewportView (p);
                            displayPane.paintImmediately (displayPane.getBounds ());
                        }
                    });
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
                                            double v = Double.parseDouble (p);
                                            if (v != 0) columns++;
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
                            if (plot.hasData ()) panel = plot.createGraphPanel ();
                        }
                        else if (viz.equals ("Raster"))
                        {
                            Raster raster = new Raster (node.path);
                            panel = raster.createGraphPanel ();
                        }

                        if (stop) return;
                        if (panel != null)
                        {
                            final Component p = panel;
                            EventQueue.invokeLater (new Runnable ()
                            {
                                public void run ()
                                {
                                    if (stop) return;
                                    displayPane.setViewportView (p);
                                    displayPane.paintImmediately (displayPane.getBounds ());
                                }
                            });

                            return;
                        }
                        // Otherwise, fall through ...
                    }
                }

                // Default is plain text
                final String contents = env.getFileContents (node.path.toString ());
                if (stop) return;

                EventQueue.invokeLater (new Runnable ()
                {
                    public void run ()
                    {
                        synchronized (displayText)
                        {
                            if (stop) return;
                            displayText.setText (contents);
                            displayText.setCaretPosition (0);
                        }
                        displayPane.paintImmediately (displayPane.getBounds ());
                    }
                });
            }
            catch (Exception e)
            {
            }
        };
    }

    public void viewFile ()
    {
        synchronized (displayText)
        {
            if (displayThread != null)
            {
                displayThread.stop = true;
                displayThread = null;
            }
            displayText.setText ("loading...");
        }
        if (displayPane.getViewport ().getView () != displayText) displayPane.setViewportView (displayText);

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
        MNode doc = job.source;

        StringBuilder contents = new StringBuilder ();
        contents.append ("Status:");
        if      (job.complete < 0)                       contents.append (" Waiting");
        else if (job.complete == 0)                      contents.append (" Started");
        else if (job.complete > 0  &&  job.complete < 1) contents.append (" " + Math.round (job.complete * 100) + "%");
        else if (job.complete == 1)                      contents.append (" Success");
        else if (job.complete == 3)                      contents.append (" Killed");
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
        displayPane.paintImmediately (displayPane.getBounds ());
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
            nextSelection = (NodeBase) firstSelection.getParent ();
            nextSelectionIsParent = true;
        }

        for (TreePath path : paths)
        {
            final NodeBase node = (NodeBase) path.getLastPathComponent ();
            model.removeNodeFromParent (node);
            if (displayNode == node)
            {
                synchronized (displayText)
                {
                    displayText.setText ("");
                }
                if (displayPane.getViewport ().getView () != displayText) displayPane.setViewportView (displayText);
            }

            new Thread ("PanelRun Delete")
            {
                public void run ()
                {
                    if (node instanceof NodeJob)
                    {
                        NodeJob job = (NodeJob) node;
                        synchronized (job) {job.deleted = true;}

                        MDoc doc = (MDoc) job.source;
                        HostSystem env = HostSystem.get (doc.getOrDefault ("$metadata", "host", "localhost"));
                        String jobName = doc.key ();
                        try
                        {
                            if (job.complete < 1) job.stop ();
                            env.deleteJob (jobName);
                        }
                        catch (Exception e) {}

                        doc.delete ();
                    }
                    else if (node instanceof NodeFile)
                    {
                        try {Files.delete (((NodeFile) node).path);}
                        catch (IOException e) {}
                    }
                };
            }.start ();
        }

        if (nextSelectionIsParent)
        {
            if (nextSelection.getChildCount () > 0) tree.setSelectionPath (new TreePath (((NodeBase) nextSelection.getChildAt (0)).getPath ()));
            else if (nextSelection != root)         tree.setSelectionPath (new TreePath (            nextSelection                .getPath ()));
        }
        else
        {
            tree.setSelectionPath (new TreePath (nextSelection.getPath ()));
        }

        tree.paintImmediately (treePane.getViewport ().getViewRect ());
    }

    public void addNewRun (MNode run)
    {
        final NodeJob node = new NodeJob (run, true);
        model.insertNodeInto (node, root, 0);  // Since this always executes on event dispatch thread, it will not conflict with other code that accesses model.
        if (root.getChildCount () == 1) model.nodeStructureChanged (root);  // If the list was empty, we need to give the JTree a little extra kick to get started.
        tree.setSelectionRow (0);
        tree.requestFocusInWindow ();

        new Thread ("PanelRun Add New Run")
        {
            public void run ()
            {
                try
                {
                    // Wait just a little bit, so backend has a chance to deposit a "started" file in the job directory.
                    // Backends should do this as early as possible.
                    // TODO: Add a state to represent ready to run but not yet running. Waiting on queue in a supercomputer would fall in this category.
                    Thread.sleep (500);
                }
                catch (InterruptedException e)
                {
                }

                node.monitorProgress (PanelRun.this);
                if (node.complete < 1) synchronized (running) {running.add (0, node);}  // It could take a very long time for this job to get added, but no longer than one complete update pass over running jobs.
            };
        }.start ();
    }

    public void repaintSouth (TreePath path)
    {
        Rectangle node    = tree.getPathBounds (path);
        Rectangle visible = treePane.getViewport ().getViewRect ();
        visible.height -= node.y - visible.y;
        visible.y       = node.y;
        tree.paintImmediately (visible);
    }
}

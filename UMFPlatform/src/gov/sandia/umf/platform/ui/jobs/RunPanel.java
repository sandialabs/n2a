/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.jobs;

import gov.sandia.umf.platform.db.AppData;
import gov.sandia.umf.platform.db.MDir;
import gov.sandia.umf.platform.db.MDoc;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.jfree.chart.ChartPanel;

import replete.util.Lay;

public class RunPanel extends JPanel
{
    public NodeBase         root;
    public DefaultTreeModel model;
    public JTree            tree;

    public JTextArea        displayText;
    public JScrollPane      displayPane = new JScrollPane ();
    public JButton          buttonGraph;
    public DisplayThread    displayThread = null;
    public NodeBase         displayNode = null;
    public MDir             runs;  // Copied from AppData for convenience
    public List<NodeJob>    running = new LinkedList<NodeJob> ();  // Jobs that we are actively monitoring because they may still be running.

    public RunPanel ()
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
            public void valueChanged (TreeSelectionEvent e)
            {
                NodeBase newNode = (NodeBase) tree.getLastSelectedPathComponent ();
                if (newNode == null) return;
                if (newNode == displayNode) return;

                if (displayThread != null) synchronized (displayText) {displayThread.stop = true;}
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
                if (keycode == KeyEvent.VK_DELETE)
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

        Thread refreshThread = new Thread ()
        {
            public void run ()
            {
                try
                {
                    // Initial load
                    synchronized (running)
                    {
                        for (MNode n : AppData.getInstance ().runs) running.add (0, new NodeJob (n));  // This should be efficient on a doubly-linked list.
                        for (NodeJob job : running) root.add (job);
                    }
                    EventQueue.invokeLater (new Runnable ()
                    {
                        public void run ()
                        {
                            model.nodeStructureChanged (root);
                        }
                    });

                    // Periodic refresh to show status of running jobs
                    int shortCycles = 100;  // Force full scan on first cycle.
                    while (true)
                    {
                        NodeBase d = displayNode;  // Make local copy (atomic action) to prevent it changing from under us
                        if (d instanceof NodeJob) ((NodeJob) d).monitorProgress (RunPanel.this);
                        if (shortCycles++ < 20)
                        {
                            Thread.sleep (1000);
                            continue;
                        }
                        shortCycles = 0;

                        synchronized (running)
                        {
                            Iterator<NodeJob> i = running.iterator ();
                            while (i.hasNext ())
                            {
                                NodeJob job = i.next ();
                                if (job != d) job.monitorProgress (RunPanel.this);
                                if (job.complete >= 1) i.remove ();
                            }
                        }
                    }
                }
                catch (InterruptedException e)
                {
                }
            }
        };
        refreshThread.setDaemon (true);
        refreshThread.start ();

        displayText = new JTextArea ();
        displayText.setEditable(false);

        final JCheckBox chkFixedWidth = new JCheckBox ("Fixed-Width Font");
        chkFixedWidth.addActionListener (new ActionListener()
        {
            public void actionPerformed (ActionEvent e)
            {
                int size  = displayText.getFont ().getSize ();
                if (chkFixedWidth.isSelected ())
                {
                    displayText.setFont (new Font (Font.MONOSPACED, Font.PLAIN, size));
                }
                else
                {
                    displayText.setFont (new Font (Font.SANS_SERIF, Font.PLAIN, size));
                }
            }
        });

        displayPane.setViewportView (displayText);
        buttonGraph = new JButton ("Graph", ImageUtil.getImage ("analysis.gif"));
        buttonGraph.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if (displayNode instanceof NodeFile)
                {
                    NodeFile nf = (NodeFile) displayNode;
                    if (nf.type == NodeFile.Type.Output)
                    {
                    	if (displayPane.getViewport ().getView () instanceof ChartPanel)
                    	{
                    		viewFile ();
                    	}
                    	else
                    	{
                            Plot plot = new Plot (nf.path.getAbsolutePath ());
                            if (! plot.columns.isEmpty ()) displayPane.setViewportView (plot.createGraphPanel ());
                    	}
                    }
                }
            }
        });

        Lay.BLtg
        (
            this,
            "C", Lay.SPL
            (
                Lay.BL
                (
                    "C", Lay.sp(tree),
                    "vgap=5"
                ),
                Lay.BL
                (
                    "N", Lay.FL (chkFixedWidth, buttonGraph, "hgap=50"),
                    "C", displayPane
                ),
                "divpixel=250"
            )
        );
    }

    public class DisplayThread extends Thread
    {
        public NodeFile node;
        public boolean stop = false;

        public DisplayThread (NodeFile node)
        {
            super ("RunPanel Fetch File");
            this.node = node;
        }

        public void run ()
        {
            try
            {
                MNode job = ((NodeJob) node.getParent ()).source;
                ExecutionEnv env = ExecutionEnv.factory (job.getOrDefault ("localhost", "$metadata", "host"));

                // This is the potentially long operation.
                // TODO: What if the file is too big to load & show? Need a viewer that can work with partial segments of a file.
                // TODO: handling of paths for remote files needs work. There are actually two paths: our local dir and the remote dir, and in general they are different.
                final String contents = env.getFileContents (node.path.getAbsolutePath ());
                if (stop) return;

                EventQueue.invokeLater (new Runnable ()
                {
                    @Override
                    public void run ()
                    {
                        synchronized (displayText)
                        {
                            if (stop) return;
                            displayText.setText (contents);
                            displayText.setCaretPosition (0);
                        }
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
        synchronized (displayText) {displayText.setText ("loading...");}
        if (displayPane.getViewport ().getView () != displayText) displayPane.setViewportView (displayText);

        // Any previous displayThread has already been signaled by our caller
        displayThread = new DisplayThread ((NodeFile) displayNode);
        displayThread.start ();
    }

    public void viewJob ()
    {
        NodeJob job = (NodeJob) displayNode;
        MNode doc = job.source;

        StringBuilder contents = new StringBuilder ();
        contents.append ("Status:");
        if (job.complete < 0) contents.append (" Waiting");
        else if (job.complete > 0  &&  job.complete < 1) contents.append (" " + Math.round (job.complete * 100) + "%");
        contents.append ("\n");
        if (job.dateStarted  != null) contents.append ("  started:  " + job.dateStarted  + "\n");
        if (job.dateFinished != null) contents.append ("  finished: " + job.dateFinished + "\n");
        MNode metadata = doc.child ("$metadata");
        if (metadata != null) contents.append ("\n" + metadata);  // TODO: filter out irrelevant items, like "gui.order"

        synchronized (displayText)
        {
            displayText.setText (contents.toString ());
            displayText.setCaretPosition (0);
        }
        if (displayPane.getViewport ().getView () != displayText) displayPane.setViewportView (displayText);
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

            new Thread ("RunPanel Delete")
            {
                public void run ()
                {
                    if (node instanceof NodeJob)
                    {
                        NodeJob job = (NodeJob) node;
                        synchronized (running) {running.remove (job);}

                        MDoc doc = (MDoc) job.source;
                        ExecutionEnv env = ExecutionEnv.factory (doc.getOrDefault ("localhost", "$metadata", "host"));
                        String jobName = doc.key ();
                        try {env.deleteJob (jobName);}  // TODO: Also terminate execution, if possible.
                        catch (Exception e) {}

                        doc.delete ();
                    }
                    else if (node instanceof NodeFile)
                    {
                        ((NodeFile) node).path.delete ();
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
    }

    public void addNewRun (MNode run)
    {
        final NodeJob node = new NodeJob (run);
        model.insertNodeInto (node, root, 0);
        if (root.getChildCount () == 1) model.nodeStructureChanged (root);  // If the list was empty, wee need to give the JTree a little extra kick to get started.
        tree.setSelectionRow (0);

        new Thread ("RunPanel Add New Run")
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

                node.monitorProgress (RunPanel.this);
                if (node.complete < 1) synchronized (running) {running.add (0, node);}
            };
        }.start ();
    }
}
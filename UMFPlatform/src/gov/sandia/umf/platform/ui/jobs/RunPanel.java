/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.jobs;

import gov.sandia.umf.platform.db.AppData;
import gov.sandia.umf.platform.db.MDir;
import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.execenvs.ExecutionEnv;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
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
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
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
    public String           displayPath;
    public MDir             runs;  // Copied from AppData for convenience
    public List<NodeJob>    running = new LinkedList<NodeJob> ();  // Jobs that we are actively monitoring because they may still be running.

    public RunPanel ()
    {
        root  = new NodeBase ();
        model = new DefaultTreeModel (root);
        tree  = new JTree (model);
        tree.setRootVisible (false);
        tree.setShowsRootHandles (true);
        tree.getSelectionModel ().setSelectionMode (TreeSelectionModel.SINGLE_TREE_SELECTION);

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

        tree.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (SwingUtilities.isLeftMouseButton (e)  &&  e.getClickCount () == 2)
                {
                    TreePath path = tree.getPathForLocation (e.getX (), e.getY ());
                    if (path == null) return;
                    Object node = path.getLastPathComponent ();
                    if (node instanceof NodeFile) doView ();
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
                    while (true)
                    {
                        synchronized (running)
                        {
                            Iterator<NodeJob> i = running.iterator ();
                            while (i.hasNext ())
                            {
                                NodeJob job = i.next ();
                                job.monitorProgress (model);
                                if (job.complete >= 1) i.remove ();
                            }
                        }
                        Thread.sleep (20000);
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
                TreePath path = tree.getSelectionPath ();
                if (path == null) return;
                Object o = path.getLastPathComponent ();
                if (o instanceof NodeFile)
                {
                    NodeFile nf = (NodeFile) o;
                    if (nf != null  &&  nf.type == NodeFile.Type.Output)
                    {
                    	if (displayPane.getViewport ().getView () instanceof ChartPanel)
                    	{
                    		doView ();
                    	}
                    	else
                    	{
                    	    displayPath = nf.path.getAbsolutePath ();
                            Plot plot = new Plot (displayPath);
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

    public void doView ()
    {
        TreePath path = tree.getSelectionPath ();
        if (path == null) return;

        if (displayPane.getViewport ().getView () != displayText) displayPane.setViewportView (displayText);
        displayText.setText ("loading...");

        NodeFile node = (NodeFile) path.getLastPathComponent ();
        displayPath = node.path.getAbsolutePath ();
        MNode job = ((NodeJob) node.getParent ()).source;
        final ExecutionEnv env = ExecutionEnv.factory (job.getOrDefault ("localhost", "$metadata", "host"));

        new Thread ("RunPanel Fetch File")
        {
            public void run ()
            {
                try
                {
                    // This is the potentially long operation.
                    // TODO: What if the file is too big to load & show? Need a viewer that can work with partial segments of a file.
                    // TODO: handling of paths for remote files needs work. There are actually two paths: our local dir and the remote dir, and in general they are different.
                    final String contents = env.getFileContents (displayPath);

                    EventQueue.invokeLater (new Runnable ()
                    {
                        @Override
                        public void run ()
                        {
                            displayText.setText (contents);
                            displayText.setCaretPosition (0);
                        }
                    });
                }
                catch (Exception e)
                {
                }
            };
        }.start ();
    }

    public void doDelete ()
    {
        TreePath path = tree.getSelectionPath ();
        if (path == null) return;
        final NodeJob job = (NodeJob) path.getLastPathComponent ();
        model.removeNodeFromParent (job);

        new Thread ("RunPanel Delete Job")
        {
            public void run ()
            {
                ExecutionEnv env = ExecutionEnv.factory (job.source.getOrDefault ("localhost", "$metadata", "host"));
                String jobDir = new File (job.source.get ()).getParent ();
                try
                {
                    env.deleteJob (jobDir);
                    job.complete = 2;  // Force the monitor thread to remove it. This is not perfectly thread-safe, but should cause no harm.
                }
                catch (Exception e)
                {
                }
            };
        }.start ();
    }

    public void addNewRun (MNode run)
    {
        final NodeJob node = new NodeJob (run);
        model.insertNodeInto (node, root, 0);

        new Thread ("RunPanel Add New Run")
        {
            public void run ()
            {
                node.monitorProgress (model);
                if (node.complete < 1)
                {
                    synchronized (running)
                    {
                        running.add (0, node);
                    }
                }
            };
        }.start ();
    }
}

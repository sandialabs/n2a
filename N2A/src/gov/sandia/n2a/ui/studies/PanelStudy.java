/*
Copyright 2020-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.studies;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MNode.Visitor;
import gov.sandia.n2a.language.type.Scalar;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.MainTabbedPane;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.jobs.NodeJob;
import gov.sandia.n2a.ui.jobs.OutputParser;
import gov.sandia.n2a.ui.jobs.OutputParser.Column;
import gov.sandia.n2a.ui.settings.SettingsLookAndFeel;
import gov.sandia.n2a.ui.jobs.PanelRun;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.TreePath;

@SuppressWarnings("serial")
public class PanelStudy extends JPanel
{
    public static PanelStudy instance;  ///< Technically, this class is a singleton, because only one would normally be created.

    protected JSplitPane              split;
    protected DefaultListModel<Study> model = new DefaultListModel<Study> ();
    public    JList<Study>            list  = new JList<Study> (model);
    protected JScrollPane             listPane;

    public    Study            displayStudy;                  // The study currently in focus.
    protected JPanel           displayPanel;
    public    JButton          buttonPause;
    protected JLabel           labelStatus   = new JLabel (); // Gives brief summary of remaining work and time.
    protected JTabbedPane      tabbedResults = new JTabbedPane ();
    protected SampleTableModel modelSamples  = new SampleTableModel ();
    public    SampleTable      tableSamples  = new SampleTable (modelSamples);

    protected static ImageIcon iconPause    = ImageUtil.getImage ("pause-16.png");
    protected static ImageIcon iconStop     = ImageUtil.getImage ("stop.gif");
    protected static ImageIcon iconComplete = ImageUtil.getImage ("complete.gif");
    protected static ImageIcon iconFailed   = ImageUtil.getImage ("remove.gif");

    public PanelStudy ()
    {
        instance = this;

        list.getSelectionModel ().setSelectionMode (ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        list.setCellRenderer (new DefaultListCellRenderer ()
        {
            @Override
            public Component getListCellRendererComponent (JList<?> list, Object value, int index, boolean selected, boolean focused)
            {
                super.getListCellRendererComponent (list, value, index, selected, focused);

                Study study = (Study) value;
                setIcon (study.getIcon ());
                // Text will be supplied directly by study.toString()
                return this;
            }
        });

        list.addListSelectionListener (new ListSelectionListener ()
        {
            public void valueChanged (ListSelectionEvent e)
            {
                if (e.getValueIsAdjusting ()) return;
                Study study = list.getSelectedValue ();
                if (study == displayStudy) return;

                displayStudy = study;
                view ();
            }
        });

        list.addKeyListener (new KeyAdapter ()
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

        Thread startThread = new Thread ("Studies")
        {
            public void run ()
            {
                // Initial load
                // The Study button on the Models tab starts disabled. We enable it below,
                // once all the pre-existing studies are loaded. This helps ensure consistency
                // between the UI and data stored on disk.
                List<Study> reverse = new ArrayList<Study> (AppData.studies.size ());
                for (MNode n : AppData.studies) reverse.add (new Study (n));
                EventQueue.invokeLater (new Runnable ()
                {
                    public void run ()
                    {
                        // Update display with newly loaded jobs.
                        for (int i = reverse.size () - 1; i >= 0; i--) model.addElement (reverse.get (i));  // Reverse the order, so later dates come first.
                        if (model.getSize () > 0)
                        {
                            list.setSelectedIndex (0);
                            list.scrollRectToVisible (list.getCellBounds (0, 0));
                        }

                        PanelModel.instance.panelEquations.enableStudies ();
                    }
                });
            }
        };
        startThread.setDaemon (true);
        startThread.start ();

        buttonPause = new JButton (iconPause);
        buttonPause.setMargin (new Insets (2, 2, 2, 2));
        buttonPause.setFocusable (false);
        buttonPause.setEnabled (false);  // Until a specific study is selected.
        buttonPause.setToolTipText ("Pause");
        buttonPause.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if (displayStudy != null) displayStudy.togglePause ();
            }
        });

        tabbedResults.addTab ("Samples", Lay.sp (tableSamples));  // No icons for tabs, at least for now.

        displayPanel = Lay.BL
        (
            "N", Lay.FL (buttonPause, labelStatus, "hgap=5,vgap=1"),
            "C", Lay.sp (tabbedResults)
        );

        Lay.BLtg
        (
            this,
            split = Lay.SPL
            (
                listPane = Lay.sp (list),
                displayPanel
            )
        );
        setFocusCycleRoot (true);

        setSplit ();
        split.addPropertyChangeListener (JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                if (SettingsLookAndFeel.rescaling) return;  // Don't record change if we are handling a change in screen resolution.
                float value = (Integer) e.getNewValue ();
                AppData.state.setTruncated (value / SettingsLookAndFeel.em, 2, "PanelStudy", "divider");
            }
        });
    }

    public void updateUI ()
    {
        super.updateUI ();
        if (split != null) setSplit ();
    }

    public void setSplit ()
    {
        float em = SettingsLookAndFeel.em;
        split.setDividerLocation ((int) Math.round (AppData.state.getOrDefault (19.0, "PanelStudy", "divider") * em));
    }

    /**
        Thread-safe method to update status field.
    **/
    public void showStatus (Study study, String status)
    {
        if (displayStudy != study) return;  // Early-out if we won't do anything.
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                if (displayStudy == study) labelStatus.setText (status);
            }
        });
    }

    /**
        Set up displayPanel with tabs appropriate to the newly-selected study.
        Execute only on EDT.
    **/
    public void view ()
    {
        // Remove all result tabs, leaving only the main sample tab.
        int tabCount = tabbedResults.getTabCount ();
        for (int i = tabCount - 1; i > 0; i--) tabbedResults.remove (i);

        // TODO: Add result tabs appropriate to the type of study.

        tableSamples.changeStudy ();
        buttonPause.setEnabled (displayStudy != null  &&  displayStudy.complete () < 1  &&  PanelModel.instance.panelEquations.buttonRun.isEnabled ());
        if (displayStudy == null) labelStatus.setText ("");
        else                      displayStudy.showProgress ();
    }

    /**
        Delete studies associated with currently selected items in list.
        Execute on EDT only.
    **/
    public void delete ()
    {
        List<Study> studies = list.getSelectedValuesList ();
        if (studies.size () < 1) return;
        int nextSelection = list.getSelectedIndex ();

        displayStudy = null;
        for (Study study : studies)
        {
            study.stop ();  // stop the worker thread; does not stop individual jobs that are currently running
            model.removeElement (study);
        }

        int count = model.getSize ();
        if (nextSelection < 0) nextSelection = 0;
        if (nextSelection >= count) nextSelection = count - 1;
        if (nextSelection >= 0)  // make new selection and load display pane
        {
            list.setSelectedIndex (nextSelection);
            displayStudy = list.getSelectedValue ();
        }
        view ();

        // Purge data
        Thread purgeThread = new Thread ("Delete Studies")
        {
            public void run ()
            {
                for (Study study : studies)
                {
                    // It does no harm to clear the record out from under the worker thread.
                    // Any further access will simply not be written to disk.
                    String studyKey = study.source.key ();
                    AppData.studies.clear (studyKey);

                    // Purge any jobs that were started directly by the study.
                    List<TreePath> paths = new ArrayList<TreePath> ();
                    int jobCount = study.getJobCount ();
                    for (int index = 0; index < jobCount; index++)
                    {
                        String jobKey = study.getJobKey (index);
                        if (! jobKey.startsWith (studyKey)) continue;  // Test whether this job was started directly by the study.
                        NodeJob jobNode;
                        synchronized (PanelRun.jobNodes) {jobNode = PanelRun.jobNodes.get (jobKey);}
                        if (jobNode != null) paths.add (new TreePath (jobNode.getPath ()));
                    }
                    EventQueue.invokeLater (new Runnable ()  // Because PanelRun.delete() expects to run on EDT.
                    {
                        public void run ()
                        {
                            PanelRun.instance.delete (paths.toArray (new TreePath[paths.size ()]));
                        }
                    });
                }
            }
        };
        purgeThread.setDaemon (true);
        purgeThread.start ();
    }

    /**
        Add a newly-created study to the list. This must be called on the EDT.
    **/
    public void addNewStudy (MNode node)
    {
        Study study = new Study (node); // constructed in paused state
        study.togglePause ();           // start

        model.add (0, study);  // Since this always executes on event dispatch thread, it will not conflict with other code that accesses model.
        list.clearSelection ();
        list.setSelectedValue (study, true);  // Should trigger call of view() via selection listener.
    }

    public void quit ()
    {
        // Signal all active studies to stop.
        int count = model.getSize ();
        for (int i = 0; i < count; i++)
        {
            Study s = model.get (i);
            if (s.thread != null) s.thread.stop = true;
        }

        // Block until they exit.
        // No single thread should take longer than 1 second to exit.
        // The serial wait will be roughly 1 second (since they all shut down in parallel)
        // plus the longest time spent launching a single job in a given thread.
        // We will only wait 1 additional second for this. Large jobs are likely to fail,
        // which may leave the study in a broken state.
        Thread shutdownThread = new Thread ("Stop Study Threads")
        {
            public void run ()
            {
                for (int i = 0; i < count; i++)
                {
                    Study s = model.get (i);
                    if (s.thread != null)
                    {
                        // s.thread could still become null before this next line of code
                        try {s.thread.join (1000);}
                        catch (Exception e) {}  // both InterruptedException and NullPointerException
                    }
                }
            }
        };
        shutdownThread.setDaemon (true);
        shutdownThread.start ();
        try {shutdownThread.join (2000);}
        catch (InterruptedException e) {}
    }

    public class SampleTable extends JTable
    {
        protected SampleProgressRenderer progressRenderer = new SampleProgressRenderer ();

        public SampleTable (SampleTableModel model)
        {
            super (model);

            setAutoResizeMode (JTable.AUTO_RESIZE_OFF);
            ((DefaultTableCellRenderer) getTableHeader ().getDefaultRenderer ()).setHorizontalAlignment (JLabel.LEFT);

            InputMap inputMap = getInputMap (WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            inputMap.put (KeyStroke.getKeyStroke ("ENTER"), "viewJob");

            ActionMap actionMap = getActionMap ();
            actionMap.put ("viewJob", new AbstractAction ()
            {
                public void actionPerformed (ActionEvent e)
                {
                    viewJob ();
                }
            });

            addMouseListener (new MouseAdapter ()
            {
                public void mouseClicked (MouseEvent me)
                {
                    if (me.getClickCount () == 2)
                    {
                        // This assumes that the first clicked caused change of selections to the desired row.
                        viewJob ();
                    }
                }
            });
        }

        public void updateUI ()
        {
            super.updateUI ();

            FontMetrics fm = getFontMetrics (getFont ());
            setRowHeight (fm.getHeight () + getRowMargin ());
            updateColumnWidths ();
        }

        public void updateColumnWidths ()
        {
            // Base column width solely on headers, rather than contents.

            FontMetrics fm = getFontMetrics (getFont ());
            int digitWidth = fm.charWidth ('0');
            int em         = fm.charWidth ('M');
            int minWidth = 10 * digitWidth;
            int maxWidth = 20 * em;

            TableColumnModel cols = getColumnModel ();
            TableColumn col0 = cols.getColumn (0);
            col0.setPreferredWidth (32);  // more than enough for a 16px icon
            col0.setCellRenderer (progressRenderer);

            int columnCount = modelSamples.getColumnCount ();
            for (int i = 1; i < columnCount; i++)
            {
                String columnTitle = modelSamples.getColumnName (i);
                int width = Math.min (maxWidth, Math.max (minWidth, fm.stringWidth (columnTitle)));
                cols.getColumn (i).setPreferredWidth (width);
            }
        }

        public void changeStudy ()
        {
            modelSamples.update (displayStudy);
            modelSamples.fireTableStructureChanged ();
            updateColumnWidths ();
        }

        /**
            Notifies GUI of new jobs created by study thread.
            Execute only on EDT.
        **/
        public void addJobs ()
        {
            int oldRowCount = modelSamples.getRowCount ();
            modelSamples.update (displayStudy);
            int addedRows = modelSamples.getRowCount () - oldRowCount;
            if (addedRows == 0) return;  // Nothing to do.
            modelSamples.fireTableRowsInserted (0, addedRows - 1);  // Does not move the viewport

            // Scroll viewport if needed.
            JViewport vp = (JViewport) getParent ();
            Point p = vp.getViewPosition ();
            int h = getRowHeight ();
            if (p.y < h) return;  // Scroll-lock: Don't let rows slide by the viewport unless we're actually looking at the top row.
            p.y += h * addedRows;
            vp.setViewPosition (p);
        }

        public void updateJob (String jobKey)
        {
            if (displayStudy == null) return;
            int index = displayStudy.getIndex (jobKey);
            int rowCount = modelSamples.getRowCount ();
            int row = rowCount - index - 1;  // Because jobs are displayed in reverse order.
            modelSamples.fireTableRowsUpdated (row, row);
        }

        public void viewJob ()
        {
            if (displayStudy == null) return;
            int row = getSelectedRow ();
            if (row < 0) return;
            int rowCount = modelSamples.getRowCount ();
            int index = rowCount - row - 1;

            NodeJob node = displayStudy.getJob (index);
            if (node == null) return;
            TreePath path = new TreePath (node.getPath ());
            PanelRun pr = PanelRun.instance;
            pr.tree.setSelectionPath (path);
            pr.tree.scrollPathToVisible (path);

            MainTabbedPane mtp = (MainTabbedPane) MainFrame.instance.tabs;
            mtp.setPreferredFocus (PanelStudy.instance, this);
            mtp.selectTab ("Runs");
        }
    }

    public static class SampleTableModel extends AbstractTableModel
    {
        protected Study          currentStudy;
        protected List<String[]> variablePaths = new ArrayList<String[]> ();
        protected List<String[]> rowValues     = new ArrayList<String[]> ();
        protected MNode          loss;
        protected LoadSamples    thread;

        public int getRowCount ()
        {
            return rowValues.size ();
        }

        public int getColumnCount ()
        {
            return variablePaths.size () + 1;
        }

        public String getColumnName (int column)
        {
            if (column <= 0  ||  column > variablePaths.size ()) return "";

            String[] keys = variablePaths.get (column - 1);
            String key = keys[0];
            for (int i = 1; i < keys.length; i++) key += "." + keys[i];
            return key;
        }

        public Object getValueAt (int row, int column)
        {
            int variableCount = variablePaths.size ();
            int columnCount   = variableCount + 1;
            int rowCount      = rowValues.size ();

            if (column < 0  ||  column >= columnCount) return null;
            if (row < 0  ||  row >= rowCount) return null;

            int index = rowCount - row - 1;  // Reverse row order, so most-recently created jobs show at top.
            String jobKey = currentStudy.getJobKey (index);

            if (column == 0)
            {
                // Determine status icon
                NodeJob node;
                synchronized (PanelRun.jobNodes) {node = PanelRun.jobNodes.get (jobKey);}
                if (node == null) return NodeJob.iconUnknown;
                if (node.deleted) return NodeJob.iconFailed;
                return node.getIcon (false);
            }

            String[] values = rowValues.get (index);
            if (values == null)
            {
                restartThread ();
                return null;
            }
            if (column == 1  &&  loss != null  &&  values[0] == null)
            {
                values[0] = "";  // Run only one LoadLoss thread per row. This will be set back to null if the data is not ready yet.
                NodeJob node;
                synchronized (PanelRun.jobNodes) {node = PanelRun.jobNodes.get (jobKey);}
                new LoadLoss (node, index, values).start ();
            }
            return values[column-1];
        }

        /**
            Switches to new study or updates contents of current study.
            Execute only on EDT.
        **/
        public synchronized void update (Study displayStudy)
        {
            if (currentStudy != displayStudy)
            {
                currentStudy = displayStudy;

                variablePaths.clear ();
                rowValues.clear ();
                loss = null;

                if (currentStudy == null) return;

                MNode variables = currentStudy.source.childOrEmpty ("variables");
                variables.visit (new Visitor ()
                {
                    public boolean visit (MNode n)
                    {
                        if (! n.data ()) return true;  // The first non-null node along a branch is the study variable. Everything under that is extra metadata.
                        MNode temp = n.child ("loss");
                        if (temp == null)
                        {
                            variablePaths.add (n.keyPath (variables));  // add to end
                        }
                        else if (loss == null)  // This is a loss variable. Only keep the first one found.
                        {
                            loss = temp;
                            variablePaths.add (0, n.keyPath (variables));  // insert in front
                        }
                        return false;
                    }
                });
            }

            if (currentStudy != null)
            {
                // Launch thread to load samples.
                int rows = currentStudy.getJobCount ();
                while (rowValues.size () < rows) rowValues.add (null);
                restartThread ();
            }
        }

        protected synchronized void restartThread ()
        {
            if (thread != null  &&  thread.study == currentStudy  &&  thread.isAlive ())
            {
                thread.restart = true;
            }
            else
            {
                thread = new LoadSamples ();
                thread.start ();
            }
        }

        protected class LoadSamples extends Thread
        {
            Study   study         = currentStudy;
            int     variableCount = variablePaths.size ();
            int     index         = rowValues.size ();
            boolean restart;  // After finishing with the current index, start at top row again.

            public LoadSamples ()
            {
                super ("Load samples for " + currentStudy);
                setDaemon (true);
            }

            public void run ()
            {
                while (index > 0)
                {
                    String[] values;
                    synchronized (SampleTableModel.this)
                    {
                        if (thread != this) return;
                        if (restart)
                        {
                            index = rowValues.size ();
                            restart = false;
                        }
                        index--;
                        values = rowValues.get (index);
                    }
                    if (values != null) continue;

                    String jobKey = study.getJobKey (index);
                    MNode model = study.getSampleModel (jobKey);
                    if (model == null) continue;  // This could happen if the job was deleted outside the study.
                    values = new String[variableCount];
                    float blanks = 0;
                    int i =  loss == null ? 0 : 1;  // Skip loss variable if it exists.
                    for (; i < values.length; i++)
                    {
                        String value = model.get (variablePaths.get (i));
                        values[i] = value;
                        if (value.isEmpty ()) blanks++;
                    }

                    // Only save if enough entries have non-empty values.
                    // Otherwise, leave the row null so we can try again. Very likely, the model is missing or incomplete.
                    // This can happen if we try to read it before it is written out or when it is only partially written.
                    // We can't require 100% non-blank, because empty string could be a legitimate value.
                    // However, it is probably be quite rare.
                    int threshold = (int) Math.floor (values.length * 0.25);  // 75% non-blank
                    if (values.length > 1) threshold = Math.max (threshold, 1);
                    if (blanks > threshold) continue;

                    if (loss != null)
                    {
                        NodeJob node = study.getJob (jobKey);
                        loadLoss (node, values);
                    }

                    synchronized (SampleTableModel.this)
                    {
                        if (study != currentStudy) return;
                        rowValues.set (index, values);
                    }

                    // Update UI
                    final int finalIndex = index;
                    EventQueue.invokeLater (new Runnable ()
                    {
                        public void run ()
                        {
                            int row = rowValues.size () - finalIndex - 1;
                            if (row >= 0) fireTableRowsUpdated (row, row);
                        }
                    });
                }

                synchronized (SampleTableModel.this)
                {
                    if (thread == this) thread = null;
                }
            }
        }

        protected class LoadLoss extends Thread
        {
            NodeJob  node;
            int      index;
            String[] values;

            public LoadLoss (NodeJob node, int index, String[] values)
            {
                super ("Load loss for " + index);
                setDaemon (true);
                this.node   = node;
                this.index  = index;
                this.values = values;
            }

            public void run ()
            {
                loadLoss (node, values);
                if (values[0] == null) return;
                EventQueue.invokeLater (new Runnable ()
                {
                    public void run ()
                    {
                        int row = rowValues.size () - index - 1;
                        if (row >= 0) fireTableCellUpdated (row, 1);
                    }
                });
            }
        }

        public void loadLoss (NodeJob node, String[] values)
        {
            double error = 0;
            if (node != null  &&  node.complete >= 1)
            {
                Path jobDir = node.getJobPath ().getParent ();
                OutputParser parser = new OutputParser ();
                parser.parse (jobDir.resolve ("study"));
                for (Column c : parser.columns)
                {
                    if (c == parser.time) continue;
                    // TODO: handle different methods for expressing loss. This version only handles squared error over time series.
                    for (float e : c.values) error += e * e;
                }
            }
            if (error > 0) values[0] = Scalar.print (Math.sqrt (error));
            else           values[0] = null;
        }
    }

    public class SampleProgressRenderer extends JLabel implements TableCellRenderer
    {
        public SampleProgressRenderer ()
        {
            setHorizontalAlignment (JLabel.CENTER);
        }

        public Component getTableCellRendererComponent (JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
        {
            if (! (value instanceof Icon)) setIcon (NodeJob.iconFailed);
            else                           setIcon ((Icon) value);
            return this;
        }
    }
}

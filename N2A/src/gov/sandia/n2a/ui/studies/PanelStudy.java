/*
Copyright 2020-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.studies;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MNode.Visitor;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.execenvs.Host;
import gov.sandia.n2a.execenvs.Remote;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.Utility;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.jobs.NodeJob;
import gov.sandia.n2a.ui.jobs.PanelRun;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

@SuppressWarnings("serial")
public class PanelStudy extends JPanel
{
    public static PanelStudy instance;  ///< Technically, this class is a singleton, because only one would normally be created.

    protected DefaultListModel<Study> model = new DefaultListModel<Study> ();
    public    JList<Study>            list  = new JList<Study> (model);
    protected JScrollPane             listPane;

    protected Study            displayStudy;                  // The study currently in focus.
    protected JPanel           displayPanel;
    protected JButton          buttonPause;
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
                for (MNode n : AppData.studies) reverse.add (new Study (n, true));
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

                // Start worker thread for each study that needs it.
                // TODO: don't do this, or else make resumption at startup optional
                for (int i = reverse.size () - 1; i >= 0; i--) reverse.get (i).start ();
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
            "N", Lay.BL ("W", Lay.FL ("H", buttonPause, labelStatus, "hgap=5,vgap=1")),
            "C", Lay.sp (tabbedResults)
        );

        JSplitPane split;
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

        split.setDividerLocation (AppData.state.getOrDefault (250, "PanelStudy", "divider"));
        split.addPropertyChangeListener (JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener ()
        {
            public void propertyChange (PropertyChangeEvent e)
            {
                Object o = e.getNewValue ();
                if (o instanceof Integer) AppData.state.set (o, "PanelStudy", "divider");
            }
        });
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
    **/
    public void view ()
    {
        // Remove all result tabs, leaving only the main sample tab.
        int tabCount = tabbedResults.getTabCount ();
        for (int i = tabCount - 1; i > 0; i--) tabbedResults.remove (i);

        // TODO: Add result tabs appropriate to the type of study.

        tableSamples.changeStudy ();
        buttonPause.setEnabled (displayStudy != null  &&  displayStudy.complete () < 1);
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
            // It does no harm to clear the record out from under the worker thread.
            // Any further access will simply not be written to disk.
            AppData.studies.clear (study.source.key ());
        }

        int count = model.getSize ();
        if (nextSelection < 0) nextSelection = 0;
        if (nextSelection >= count) nextSelection = count - 1;
        if (nextSelection >= 0)  // make new selection and load display pane
        {
            list.setSelectedIndex (nextSelection);
            displayStudy = list.getSelectedValue ();
        }
        else  // Studies list is completely empty
        {
            displayStudy = null;
        }
        view ();
    }

    /**
        Add a newly-created study to the list. This must be called on the EDT.
    **/
    public void addNewStudy (MNode node)
    {
        Study study = new Study (node, false);
        study.start ();

        model.add (0, study);  // Since this always executes on event dispatch thread, it will not conflict with other code that accesses model.
        list.setSelectedValue (study, true);  // Should trigger call of view() via selection listener.
        //list.requestFocusInWindow ();
    }

    public class Study
    {
        protected MNode               source;
        protected StudyThread         thread;
        protected StudyIterator       iterator;
        protected int                 count;                    // Total number of samples that will be generated
        protected int                 index;                    // Of next sample that should be created. Always 1 greater than last completed sample. When 0, study is about to start. When equal to count, study has completed.
        protected Random              random   = new Random (); // random number generator used by iterator
        protected Map<String,Integer> jobIndex = new HashMap<String,Integer> ();

        public Study (MNode source, boolean pause)
        {
            this.source = source;
            if (pause) source.set ("", "pause");

            int i = 0;
            for (MNode j : source.childOrEmpty ("jobs")) jobIndex.put (j.get (), i++);
        }

        public void buildIterator ()
        {
            if (iterator != null) return;

            MNode variables = source.childOrEmpty ("variables");
            variables.visit (new Visitor ()
            {
                public boolean visit (MNode n)
                {
                    if (! n.data ()) return true;  // This is merely an intermediate node, not a study variable.
                    String[] keys = n.keyPath (variables);
                    String value = n.get ().trim ();

                    StudyIterator it = null;
                    if (value.startsWith ("["))
                    {
                        value = value.substring (1);
                        value = value.split ("]", 2)[0];
                        it = new StudyIteratorRange (keys, value);
                    }
                    else if (value.contains (","))
                    {
                        it = new StudyIteratorList (keys, value);
                    }
                    // TODO: how to handle unrecognized study type?
                    if (iterator != null)
                    {
                        iterator.next ();  // Move to first item in sequence. At least one must exist.
                        it.inner = iterator;
                    }
                    iterator = it;
                    return false;
                }
            });
            if (iterator != null) count = iterator.count ();
        }

        /**
            Starts the worker thread for this study, but only if needed.
            Called at application startup for all existing studies.
            Also called when a new study is created by the user.
        **/
        public synchronized void start ()
        {
            if (thread != null) return;
            if (! source.get ("finished").isEmpty ()) return;

            thread = new StudyThread ();
            thread.setDaemon (true);
            thread.start ();
        }

        public synchronized void togglePause ()
        {
            boolean currentValue = source.getFlag ("pause");
            if (currentValue)  // currently paused, so un-pause
            {
                source.clear ("pause");
                if (thread == null)
                {
                    thread = new StudyThread ();
                    thread.setDaemon (true);
                    thread.start ();
                }
            }
            else  // currently not paused, so pause
            {
                source.set ("", "pause");
                if (thread != null)
                {
                    thread.stop = true;
                    thread = null;
                }
            }
        }

        public synchronized void stop ()
        {
            source.set (System.currentTimeMillis () / 1000, "finished");
            if (thread != null)
            {
                thread.stop = true;
                thread = null;
            }
        }

        public float complete ()
        {
            if (count == 0) return 1;  // Nothing to do, so we are done. This happens when study turns out to be vacuous.
            return (float) index / count;
        }

        public Icon getIcon ()
        {
            if (! source.get ("finished").isEmpty ()) return iconComplete;
            if (source.getFlag ("pause")) return iconPause;
            return Utility.makeProgressIcon (complete ());
        }

        public String toString ()
        {
            return source.get ("$inherit");
        }

        public class StudyThread extends Thread
        {
            public boolean stop;
            public long    startTime;

            public StudyThread ()
            {
                super ("Study " + source.get ("$inherit") + " " + source.key ());
            }

            public void run ()
            {
                buildIterator ();
                if (iterator == null)  // Failed to find any study variables.
                {
                    // Convert this to a single run.
                    EventQueue.invokeLater (new Runnable ()
                    {
                        public void run ()
                        {
                            list.setSelectedValue (Study.this, false); // Make ourself the current selection.
                            delete ();                                 // Delete current selection.

                            PanelEquations pe = PanelModel.instance.panelEquations;
                            MNode doc = AppData.models.child (source.get ("$inherit"));
                            pe.load (doc);  // Usually this record will already be loaded, since the study was launched from there.
                            pe.launchJob ();
                        }
                    });

                    return;
                }

                int jobCount = source.childOrEmpty ("jobs").size ();
                // TODO: support re-running failed jobs.
                if (jobCount >= count  ||  source.getFlag ("pause"))
                {
                    synchronized (Study.this) {if (thread == this) thread = null;}
                    return;
                }

                String inherit = source.get ("$inherit");
                MNode model = AppData.models.childOrEmpty (inherit);
                MNode modelCopy = new MVolatile ();
                modelCopy.merge (model);  // "model" is never touched. We only use "modelCopy".

                // Restart iterator at appropriate place.
                MNode seed = model.child ("$metadata", "study", "seed");
                if (seed != null) random.setSeed (seed.getLong ());
                else              random.setSeed (System.currentTimeMillis ());
                int lastIndex = jobCount - 1;
                System.out.println ("index, lastIndex = " + index + " " + lastIndex);
                if (index <= lastIndex)
                {
                    MNode lastRun = null;
                    if (seed == null)  // User is not concerned about repeatability, so any state will do.
                    {
                        // Retrieve last run.
                        String lastRunKey = source.get ("jobs", lastIndex);
                        lastRun = AppData.runs.child (lastRunKey);
                    }

                    if (lastRun == null)  // Either the user cares about repeatable random numbers, or we failed to retrieve the last run.
                    {
                        // By actually executing the iterator again, we ensure that any random
                        // draws repeat exactly the same sequence as before.
                        showStatus (Study.this, "Recapitulating samples");
                        while (! stop  &&  index <= lastIndex)
                        {
                            iterator.next ();
                            index++;
                        }
                    }
                    else  // Use lastRun to set iterator
                    {
                        iterator.fastForward (lastRun);
                        index = lastIndex + 1;
                    }
                }

                // Get next sample, but don't advance index until it is actually launched.
                // This ensures that we can restart at the right sample if interrupted.
                startTime = System.currentTimeMillis ();
                while (! stop  &&  iterator.next ())
                {
                    showProgress ();

                    // Expand model
                    // This allows iteration on model structure itself, for example by changing $inherit.
                    iterator.assign (modelCopy);
                    MNode collated = new MPart (modelCopy);

                    // Use the model to guide host selection.
                    // This allows host itself to be a study variable.
                    // Specifically, the user can set up one and only one of these cases:
                    // 1) $metadata.host specifies one name -- Use only the specified host. Poll until it becomes available.
                    // 2) $metadata.host specifies several names -- Use first available host, repeatedly polling the list.
                    // 3) $metadata.host is tagged as a study variable -- Use only the host from the iterator's current position, similar to #1 above.
                    List<Host> hosts = new ArrayList<Host> ();
                    for (String hostname : collated.get ("$metadata", "host").split (","))
                    {
                        Host h = Host.get (hostname.trim ());
                        if (h != null) hosts.add (h);
                    }
                    if (hosts.isEmpty ()) hosts.add (Host.get ("localhost"));

                    // Likewise for backend
                    Backend backend = Backend.getBackend (collated.get ("$metadata", "backend"));

                    // Wait until a host becomes available.
                    while (! stop)
                    {
                        Host chosenHost = null;
                        for (Host h : hosts)
                        {
                            // If a particular host is requested, then the user implicitly gives permission to prompt for password.
                            if (h instanceof Remote) ((Remote) h).enable ();

                            if (backend.canRunNow (h, collated))
                            {
                                chosenHost = h;
                                break;
                            }
                        }
                        if (chosenHost != null)
                        {
                            // Launch job and maintain all records
                            // See PanelEquations.listenerRun for similar code.
                            String jobKey = new SimpleDateFormat ("yyyy-MM-dd-HHmmss", Locale.ROOT).format (new Date ()) + "-" + Host.jobCount++;
                            final MNode job = AppData.runs.childOrCreate (jobKey);  // Create the dir and model doc
                            job.merge (collated);
                            job.set (inherit,         "$inherit");
                            job.set (chosenHost.name, "$metadata", "host");
                            ((MDoc) job).save ();  // Force directory (and job file) to exist, so Backends can work with the dir.

                            Thread thread = new Thread ()
                            {
                                public void run ()
                                {
                                    backend.start (job);
                                }
                            };
                            thread.setDaemon (true);
                            thread.start ();

                            jobIndex.put (jobKey, index);
                            source.set (jobKey, "jobs", index++);
                            EventQueue.invokeLater (new Runnable ()
                            {
                                public void run ()
                                {
                                    if (displayStudy == Study.this) tableSamples.addJob ();
                                    PanelRun.instance.addNewRun (job);
                                }
                            });

                            break;  // Skips the next call to sleep().
                        }
                        // Wait 1 second before re-polling host(s).
                        try {sleep (1000);}
                        catch (InterruptedException e) {}
                    }

                    // Wait 1 second between launching jobs. This is mainly to give jobs that
                    // share the same host some time allocate resources, so that we know they
                    // are in use when deciding to launch more processes.
                    try {if (! stop) sleep (1000);}
                    catch (InterruptedException e) {}
                }

                long now = System.currentTimeMillis ();
                source.set (source.getLong ("time") + now - startTime, "time");
                if (index >= count) source.set (now, "finished");
                showProgress ();

                synchronized (Study.this) {if (thread == this) thread = null;}
            }

            public String formatTime (double t)
            {
                return String.valueOf (Math.round (t * 10) / 10.0);
            }

            public void showProgress ()
            {
                // TODO: this should be based on jobs completed rather than merely started.
                String status = "" + index + "/" + count + " samples; ";
                if (index == 0)
                {
                    status += "Unknonw time remaining";
                }
                else
                {
                    long totalTime = source.getLong ("time") + System.currentTimeMillis () - startTime;
                    double averageTime = totalTime / (index + 1);
                    double ETA = averageTime * (count - index) / 1000;  // ETA is in seconds rather than milliseconds. It is only precise to 1/10th of a second.
                    if      (ETA > 4.3425e17) status += "This will take longer than the age of the universe.";  // 13.77 billion years, give or take a few
                    else if (ETA > 2.3652e14) status += "Deep Thought got done sooner.";                        // 7.5 million years
                    else if (ETA >  31536000) status += formatTime (ETA / 31536000) + " years remaining";
                    else if (ETA >   2592000) status += formatTime (ETA /  2592000) + " months remaining";
                    else if (ETA >    604800) status += formatTime (ETA /   604800) + " weeks remaining";
                    else if (ETA >     86400) status += formatTime (ETA /    86400) + " days remaining";
                    else if (ETA >      3600) status += formatTime (ETA /     3600) + " hours remaining";
                    else if (ETA >        60) status += formatTime (ETA /       60) + " minutes remaining";
                    else                      status += formatTime (ETA           ) + " seconds remaining";
                }
                showStatus (Study.this, status);

                EventQueue.invokeLater (new Runnable ()
                {
                    public void run ()
                    {
                        int row = model.indexOf (Study.this);
                        list.repaint (list.getCellBounds (row, row));
                    }
                });
            }
        }
    }

    public class SampleTable extends JTable
    {
        protected SampleProgressRenderer progressRenderer = new SampleProgressRenderer ();

        public SampleTable (SampleTableModel model)
        {
            super (model);

            setAutoResizeMode (JTable.AUTO_RESIZE_OFF);
            ((DefaultTableCellRenderer) getTableHeader ().getDefaultRenderer ()).setHorizontalAlignment (JLabel.LEFT);
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
            modelSamples.fireTableStructureChanged ();
            updateColumnWidths ();
        }

        public void addJob ()
        {
            modelSamples.fireTableRowsInserted (0, 0);
        }

        public void updateJob (String jobKey)
        {
            if (displayStudy == null) return;
            Integer index = displayStudy.jobIndex.get (jobKey);
            if (index == null) return;
            int rowCount = displayStudy.source.childOrEmpty ("jobs").size ();
            int row = rowCount - index - 1;  // Because jobs are displayed in reverse order.
            modelSamples.fireTableRowsUpdated (row, row);
        }
    }

    public class SampleTableModel extends AbstractTableModel
    {
        protected Study          currentStudy;
        protected List<String[]> variablePaths = new ArrayList<String[]> ();

        public int getRowCount ()
        {
            if (displayStudy == null) return 0;
            return displayStudy.source.childOrEmpty ("jobs").size ();
        }

        public int getColumnCount ()
        {
            checkIndices ();
            return variablePaths.size () + 1;
        }

        public String getColumnName (int column)
        {
            checkIndices ();
            if (column <= 0  ||  column > variablePaths.size ()) return "";
            String[] keys = variablePaths.get (column - 1);
            String key = keys[0];
            for (int i = 1; i < keys.length; i++) key += "." + keys[i];
            return key;
        }

        public Object getValueAt (int row, int column)
        {
            checkIndices ();
            int columnCount = variablePaths.size () + 1;
            int rowCount = displayStudy.source.childOrEmpty ("jobs").size ();

            if (column < 0  ||  column >= columnCount) return null;
            if (row < 0  ||  row >= rowCount) return null;

            String jobKey = displayStudy.source.get ("jobs", rowCount - row - 1);  // Reverse row order, so most-recently created jobs show at top.
            if (jobKey.isEmpty ()) return null;
            MNode job = AppData.runs.child (jobKey);
            if (job == null) return null;
            if (column > 0) return job.get (variablePaths.get (column - 1));

            // Determine status icon (column 0)
            NodeJob node = PanelRun.instance.jobNodes.get (jobKey);
            if (node == null  ||  node.deleted) return NodeJob.iconFailed;
            return node.getIcon (false);
        }

        public synchronized void checkIndices ()
        {
            if (currentStudy == displayStudy) return;
            currentStudy = displayStudy;

            variablePaths = new ArrayList<String[]> ();
            if (displayStudy == null) return;

            MNode variables = displayStudy.source.childOrEmpty ("variables");
            variables.visit (new Visitor ()
            {
                public boolean visit (MNode n)
                {
                    if (n.size () > 0) return true;  // If there are children, then this is merely an intermediate node, not a study variable.
                    variablePaths.add (n.keyPath (variables));
                    return false;
                }
            });
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

    /**
        A hierarchical iterator for enumerating combinations of parameters.
        When chained with other iterators, the outermost iterator varies the fastest.
        An empty iterator is not allowed. Every iterator must offer a sequence at least 1 item long.
    **/
    public static abstract class StudyIterator
    {
        protected StudyIterator inner;   // sub-iterator. If null, then this is the last iterator in the chain.
        protected String[]      keyPath; // full set of keys that address the location of the variable
        // concrete classes will also include information on the specific values to iterate over

        /**
            Immediately after construction, this iterator points before the beginning of the sequence.
            A call to next() must come before a call to assign().
            When composing with an inner iterator, call restart() or next() on the inner iterator to properly prepare it.
        **/
        public StudyIterator (String[] keys)
        {
            this.keyPath = keys;
        }

        public abstract int     count ();                   // Returns the total number of samples that will be generated by one complete sequence.
        public abstract void    fastForward (MNode values); // Given a collated model from a run, move this and inner iterators to a state where a call to assign() will hand out the given item in the sequence.
        public abstract void    restart ();                 // Moves this iterator to start of sequence, without regard to inner iterators. An immediate call to assign() will hand out the first item. This is similar to next() but unlike the initial state after construction.
        public abstract boolean step ();                    // Moves this iterator to next item in sequence, without regard to inner iterator. Returns false if no more items are available.
        public abstract void    assign (MNode model);       // Applies current value to model, then calls inner.assign()

        public boolean next ()
        {
            if (step ()) return true;
            // Past end of sequence. We can only restart if there is an inner iterator that has another item to offer.
            if (inner != null  &&  inner.next ())
            {
                restart ();
                return true;
            }
            return false;
        }
    }

    public static class StudyIteratorList extends StudyIterator
    {
        protected List<String> items;
        protected int          index = -1;

        public StudyIteratorList (String[] keys, String items)
        {
            super (keys);
            this.items = Arrays.asList (items.split (","));
        }

        public int count ()
        {
            int result = items.size ();
            if (inner != null) result *= inner.count ();
            return result;
        }

        public void fastForward (MNode model)
        {
            if (inner != null) inner.fastForward (model);
            String current = model.get (keyPath);
            index = items.indexOf (current);  // "current" should always be found in items.
            if (index < 0) index = 0;         // fallback if improper value was passed
        }

        public void restart ()
        {
            index = 0;
        }

        public boolean step ()
        {
            index++;
            return index < items.size ();
        }

        public void assign (MNode model)
        {
            if (inner != null) inner.assign (model);
            model.set (items.get (index), keyPath);
        }
    }

    public static class StudyIteratorRange extends StudyIterator
    {
        protected double lo;
        protected double hi;
        protected double step;
        protected int index = -1;
        protected int count;

        public StudyIteratorRange (String[] keys, String range)
        {
            super (keys);
            String[] pieces = range.split (",");
            lo = Double.valueOf (pieces[0]);
            if (pieces.length > 1) hi = Double.valueOf (pieces[0]);
            else                   hi = lo;
            if (pieces.length > 2)
            {
                step = hi;
                hi = Double.valueOf (pieces[2]);
            }
            else
            {
                step = 1;
            }
            count = (int) Math.floor ((hi - lo) / step);
        }

        public int count ()
        {
            if (inner != null) return count * inner.count ();
            return count;
        }

        public void fastForward (MNode model)
        {
            if (inner != null) inner.fastForward (model);
            double current = model.getDouble ((Object[]) keyPath);
            index = (int) Math.round ((current - lo) / step);
            if (index < 0  ||  index >= count) index = 0;
        }

        public void restart ()
        {
            index = 0;
        }

        public boolean step ()
        {
            index++;
            return index < count;
        }

        public void assign (MNode model)
        {
            if (inner != null) inner.assign (model);
            model.set (lo + step * index, keyPath);
        }
    }
}

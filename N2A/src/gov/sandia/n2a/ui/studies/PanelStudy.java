/*
Copyright 2020-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.studies;

import gov.sandia.n2a.backend.internal.Simulator;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MNode.Visitor;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.MainFrame;
import gov.sandia.n2a.ui.MainTabbedPane;
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
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
                PanelRun pr = PanelRun.instance;
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
                        synchronized (pr.jobNodes) {jobNode = pr.jobNodes.get (jobKey);}
                        if (jobNode != null) paths.add (new TreePath (jobNode.getPath ()));
                    }
                    pr.delete (paths.toArray (new TreePath[paths.size ()]));
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
        Study study = new Study (node);
        study.togglePause ();  // Study is constructed in paused state, so this will start it.

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

    public class Study
    {
        protected MNode               source;
        protected StudyThread         thread;
        protected StudyIterator       iterator;
        protected int                 count;                    // Total number of samples that will be generated
        protected int                 index;                    // Of next sample that should be created. Always 1 greater than last completed sample. When 0, study is about to start. When equal to count, study has completed.
        protected List<String>        incomplete;
        protected boolean             usesRandom;               // At least one iterator in the chain generates random values. It will need an instance of the internal Simulator to create a convenient environment for generating values.
        protected Random              random;                   // random number generator used by iterator
        protected long                startTime;                // Of main loop in thread. Used to estimate time remaining.
        protected Map<String,Integer> jobMap;

        public Study (MNode source)
        {
            this.source = source;
        }

        public void buildIterator ()
        {
            // Assumes iterator is null

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
                    else if (value.startsWith ("uniform")  ||  value.startsWith ("gaussian"))
                    {
                        it = new StudyIteratorRandom (keys, value, n);
                        usesRandom = true;
                    }
                    else if (value.contains (","))
                    {
                        it = new StudyIteratorList (keys, value);
                    }
                    else return false;  // Ignore unrecognized study type. TODO: should we throw an error instead?

                    if (iterator != null)
                    {
                        iterator.next ();  // Move to first item in sequence. At least one must exist.
                        it.inner = iterator;
                    }
                    iterator = it;
                    return false;
                }
            });
        }

        public synchronized void togglePause ()
        {
            if (thread == null)
            {
                if (! source.get ("finished").isEmpty ()) return;  // Don't restart a study after it completes or is killed.

                thread = new StudyThread ();
                thread.setDaemon (true);
                thread.start ();
            }
            else
            {
                thread.stop = true;
            }
        }

        public synchronized void stop ()
        {
            source.set (System.currentTimeMillis () / 1000, "finished");
            if (thread != null) thread.stop = true;
        }

        public float complete ()
        {
            if (! source.get ("finished").isEmpty ()) return 1;
            if (count == 0) return 0;
            float complete = getJobCount () - (incomplete == null ? 0 : incomplete.size ());
            return complete / count;
        }

        public Icon getIcon ()
        {
            if (! source.get ("finished").isEmpty ()) return iconComplete;
            if (thread == null) return iconPause;
            if (count == 0) return NodeJob.iconUnknown;
            return Utility.makeProgressIcon (complete ());
        }

        public String toString ()
        {
            return source.get ("$inherit");
        }

        /**
            @return Total number of samples that have been selected or created. May include partially-created or failed jobs.
        **/
        public int getJobCount ()
        {
            return source.getInt ("jobs");
        }

        public String getJobKey (int index)
        {
            String result = source.get ("jobs", index);
            if (result.isEmpty ()) result = source.key () + "-" + index;
            return result;
        }

        public int getIndex (String jobKey)
        {
            // Try to pull out the index from the key itself.
            String[] pieces = jobKey.split ("-");
            if (pieces.length > 1)
            {
                try
                {
                    if (jobKey.startsWith (source.key ())) return Integer.valueOf (pieces[pieces.length - 1]);
                }
                catch (NumberFormatException e) {}
            }

            // Check if it is a pre-existing job we collected.
            if (jobMap == null)
            {
                jobMap = new HashMap<String,Integer> ();
                for (MNode job : source.childOrEmpty ("jobs")) jobMap.put (job.get (), Integer.valueOf (job.key ()));
            }
            Integer index = jobMap.get (jobKey);
            if (index != null) return index;

            return -1;
        }

        public class StudyThread extends Thread
        {
            public boolean stop;

            public StudyThread ()
            {
                super ("Study " + source.get ("$inherit") + " " + source.key ());
            }

            public void run ()
            {
                index = source.getOrDefault (0, "barrier");  // The sample immediately after the most recent barrier that was passed, if any. Implies that everything up through index-1 was fully completed.
                if (iterator == null)
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

                        thread = null;
                        return;
                    }

                    if (usesRandom)
                    {
                        MNode seed = source.child ("config", "seed");
                        if (seed == null) seed = source.set (System.currentTimeMillis (), "config", "seed");
                        random = new Random (seed.getLong ());
                    }

                    if (index == 0) saveIterators ();  // Snapshot initial state. This is like a barrier.
                }
                count = iterator.count ();

                if (usesRandom) new Simulator (random);  // Since this is a new thread, we always have to instantiate a new thread-local object.

                String inherit = source.get ("$inherit");
                MNode model = AppData.models.childOrEmpty (inherit);
                MNode modelCopy = new MVolatile ();
                modelCopy.merge (model);  // "model" is never touched. We only use "modelCopy".

                // Gather list of incomplete jobs.
                PanelRun pr = PanelRun.instance;
                int jobCount = getJobCount ();
                if (incomplete == null)
                {
                    incomplete = new LinkedList<String> ();
                    while (index < jobCount)
                    {
                        String jobKey = getJobKey (index++);
                        NodeJob node;
                        synchronized (pr.jobNodes) {node = pr.jobNodes.get (jobKey);}
                        if (node == null  ||  node.complete != 1) incomplete.add (jobKey);
                    }
                }

                // Outer loop handles failed jobs.
                startTime = System.currentTimeMillis ();
                boolean done = false;  // Indicates that iterator has completed. This is different than stop.
                int retry = source.getOrDefault (3, "config", "retry");
                for (int retries = 0; ! stop  &&  retries <= retry; retries++)
                {
                    restoreIterators ();
                    // Inner loop does the entire study, breaking only when done or if jobs failed.
                    while (! stop)
                    {
                        // Update list of running jobs.
                        int notStarted = 0;
                        int failed     = 0;
                        Iterator<String> it = incomplete.iterator ();
                        while (it.hasNext ())
                        {
                            String jobKey = it.next ();
                            NodeJob node;
                            synchronized (pr.jobNodes) {node = pr.jobNodes.get (jobKey);}
                            if      (node == null  ||  node.complete < 0) notStarted++;
                            else if (node.complete == 1)                  it.remove ();
                            else if (node.complete > 1)                   failed++;
                        }

                        showProgress ();

                        if (notStarted > 10)  // Throttle generation of new samples.
                        {
                            try {sleep (1000);}
                            catch (InterruptedException e) {}
                            continue;
                        }
                        else if (iterator.barrier ()  ||  done)
                        {
                            if (incomplete.isEmpty ())  // Pass the barrier
                            {
                                saveIterators ();
                                if (done)
                                {
                                    stop = true;
                                    break;
                                }
                                retries = 0;  // Passing a barrier means we have resolved all errors, so don't hold them against any future retries.
                            }
                            else  // Wait at barrier
                            {
                                try {sleep (1000);}
                                catch (InterruptedException e) {}
                                if (failed == 0) continue;
                                // Some jobs failed, so try again.
                                break;
                            }
                        }

                        // Get next sample.
                        if (! iterator.next ())
                        {
                            done = true;
                            continue;
                        }

                        // Verify that work needs to be done.
                        String jobKey = source.key () + "-" + index++;  // source key is generated the same way regular job keys. Unless the user launches a study and a regular job in the same second, they will never overlap.
                        NodeJob node;
                        synchronized (pr.jobNodes) {node = pr.jobNodes.get (jobKey);}
                        if (node != null)
                        {
                            if (node.complete <= 1) continue;  // Job already exists and is in good condition.
                            node.reset ();
                        }

                        // Expand model
                        // This allows iteration on model structure itself, for example by changing $inherit.
                        iterator.assign (modelCopy);
                        MNode collated = new MPart (modelCopy);

                        // Launch job and maintain all records
                        // See PanelEquations.listenerRun for similar code.
                        final MDoc job = (MDoc) AppData.runs.childOrCreate (jobKey);
                        NodeJob.collectJobParameters (collated, inherit, job);
                        job.save ();
                        NodeJob.saveCollatedModel (collated, job);

                        // Update job count.
                        // It is important to do this after the collated model is saved, so that the UI thread will see complete information.
                        // Notice that index was incremented above, so it now gives the count of jobs rather than the job number.
                        if (index > jobCount)
                        {
                            source.set (index, "jobs");
                            incomplete.add (jobKey);
                        }

                        EventQueue.invokeLater (new Runnable ()
                        {
                            public void run ()
                            {
                                NodeJob node;
                                synchronized (pr.jobNodes) {node = pr.jobNodes.get (jobKey);}
                                if (node == null)
                                {
                                    node = PanelRun.instance.addNewRun (job, false);
                                    if (displayStudy == Study.this) tableSamples.addJobs ();
                                }
                                Host.waitForHost (node);
                            }
                        });
                    }
                }

                long now = System.currentTimeMillis ();
                source.set (source.getLong ("time") + now - startTime, "time");
                if (done) source.set (now, "finished");
                showProgress ();

                thread = null;
            }
        }

        public void saveIterators ()
        {
            source.set (index, "barrier");
            iterator.save (source);

            if (! usesRandom) return;
            try
            {
                ByteArrayOutputStream baos = new ByteArrayOutputStream ();
                ObjectOutputStream oos = new ObjectOutputStream (baos);
                oos.writeObject (random);
                String base64 = Base64.getEncoder ().encodeToString (baos.toByteArray ());
                source.set (base64, "rng");
            }
            catch (IOException e) {}
        }

        public void restoreIterators ()
        {
            index = source.getOrDefault (0, "barrier");
            iterator.load (source);

            if (! usesRandom) return;
            try
            {
                String rng = source.get ("rng");  // Should always exist when restoreIterators() is called
                byte[] bytes = Base64.getDecoder ().decode (rng);
                ByteArrayInputStream bais = new ByteArrayInputStream (bytes);
                ObjectInputStream ois = new ObjectInputStream (bais);
                random = (Random) ois.readObject ();
                Simulator.instance.get ().random = random;
            }
            catch (Exception e) {}
        }

        public void showProgress ()
        {
            String status;
            if (! source.get ("finished").isEmpty ())
            {
                status = "Finished in " + scaleTime (source.getLong ("time") / 1000.0);
            }
            else if (thread == null)
            {
                status = "Paused sample generation. Existing jobs will continue to run.";
            }
            else
            {
                int complete = getJobCount () - (incomplete == null ? 0 :incomplete.size ());
                status = "" + complete + "/" + count + " samples; ";
                if (complete == 0)
                {
                    status += "Unknonw time remaining";
                }
                else
                {
                    long totalTime = source.getLong ("time") + System.currentTimeMillis () - startTime;
                    double averageTime = totalTime / (complete + 1);
                    double ETA = averageTime * (count - complete) / 1000;  // ETA is in seconds rather than milliseconds. It is only precise to 1/10th of a second.
                    if      (ETA > 4.3425e17) status += "This will take longer than the age of the universe.";  // 13.77 billion years, give or take a few
                    else if (ETA > 2.3652e14) status += "Deep Thought got done sooner.";                        // 7.5 million years
                    else                      status += scaleTime (ETA) + " remaining";
                }
            }
            showStatus (Study.this, status);

            EventQueue.invokeLater (new Runnable ()
            {
                public void run ()
                {
                    int row = model.indexOf (Study.this);
                    if (row < 0) return;  // Could be negative if row no longer exists, such as during delete.
                    list.repaint (list.getCellBounds (row, row));
                }
            });
        }

        public String scaleTime (double t)
        {
            if (t >  31536000) return formatTime (t / 31536000) + " years";
            if (t >   2592000) return formatTime (t /  2592000) + " months";
            if (t >    604800) return formatTime (t /   604800) + " weeks";
            if (t >     86400) return formatTime (t /    86400) + " days";
            if (t >      3600) return formatTime (t /     3600) + " hours";
            if (t >        60) return formatTime (t /       60) + " minutes";
            return                    formatTime (t           ) + " seconds";
        }

        public String formatTime (double t)
        {
            return String.valueOf (Math.round (t * 10) / 10.0);
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
            modelSamples.update ();
            modelSamples.fireTableStructureChanged ();
            updateColumnWidths ();
        }

        public void addJobs ()
        {
            int oldRowCount = modelSamples.getRowCount ();
            modelSamples.update ();
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
            String jobKey = displayStudy.getJobKey (index);

            PanelRun pr = PanelRun.instance;
            NodeJob node;
            synchronized (pr.jobNodes) {node = pr.jobNodes.get (jobKey);}
            if (node == null) return;
            TreePath path = new TreePath (node.getPath ());
            pr.tree.setSelectionPath (path);
            pr.tree.scrollPathToVisible (path);

            MainTabbedPane mtp = (MainTabbedPane) MainFrame.instance.tabs;
            mtp.setPreferredFocus (PanelStudy.instance, this);
            mtp.selectTab ("Runs");
        }
    }

    public class SampleTableModel extends AbstractTableModel
    {
        protected Study          currentStudy;
        protected List<String[]> variablePaths = new ArrayList<String[]> ();
        protected List<String[]> rowValues     = new ArrayList<String[]> ();

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
            String jobKey = displayStudy.getJobKey (index);

            if (column == 0)
            {
                // Determine status icon
                PanelRun pr = PanelRun.instance;
                NodeJob node;
                synchronized (pr.jobNodes) {node = pr.jobNodes.get (jobKey);}
                if (node == null) return NodeJob.iconUnknown;
                if (node.deleted) return NodeJob.iconFailed;
                return node.getIcon (false);
            }

            String[] values = rowValues.get (index);
            if (values == null)  // Load model and extract relevant values
            {
                // A more memory-efficient approach would be to scan the model file for keys without fully loading it.
                MNode model = NodeJob.getModel (jobKey);
                if (model == null) return null;  // This could happen if the job was deleted outside the study.
                values = new String[variableCount];
                float blanks = 0;
                for (int i = 0; i < variableCount; i++)
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
                int threshold = (int) Math.floor (variableCount * 0.25);  // 75% non-blank
                if (variableCount > 1) threshold = Math.max (threshold, 1);
                if (blanks <= threshold) rowValues.set (index, values);
            }
            return values[column-1];
        }

        public synchronized void update ()
        {
            if (currentStudy != displayStudy)
            {
                currentStudy = displayStudy;

                variablePaths.clear ();
                rowValues.clear ();

                if (displayStudy != null)
                {
                    MNode variables = displayStudy.source.childOrEmpty ("variables");
                    variables.visit (new Visitor ()
                    {
                        public boolean visit (MNode n)
                        {
                            if (! n.data ()) return true;  // The first non-null node along a branch is the study variable. Everything under that is extra metadata.
                            variablePaths.add (n.keyPath (variables));
                            return false;
                        }
                    });
                }
            }
            if (displayStudy != null)
            {
                int rows = displayStudy.getJobCount ();
                while (rowValues.size () < rows) rowValues.add (null);  // We will lazy-load the actual row data, because not all rows will necessarily be displayed.
            }
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

        public abstract int     count ();             // Returns the total number of samples that will be generated by one complete sequence.
        public abstract void    restart ();           // Moves this iterator to start of sequence, without regard to inner iterators. An immediate call to assign() will hand out the first item. This is similar to next() but unlike the initial state after construction.
        public abstract boolean step ();              // Moves this iterator to next item in sequence, without regard to inner iterator. Returns false if no more items are available.
        public abstract void    assign (MNode model); // Applies current value to model. Also calls inner.assign().
        public abstract void    save (MNode study);   // Store the state of this iterator. Also calls inner.save().
        public abstract void    load (MNode study);   // Retrieve the saved state of this iterator so it can resume exactly where it left off. Also calls inner.load().

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

        // Indicates that the caller should wait until all previously-generated jobs finish before calling next again.
        public boolean barrier ()
        {
            return false;
        }

        /**
            Utility function to locate the node within the study tree the contains the parameters for this iterator.
            This can be used to further save/load state.
        **/
        public MNode node (MNode study)
        {
            List<String> keyList = new ArrayList<String> ();
            keyList.add ("variables");
            keyList.addAll (Arrays.asList (keyPath));
            return study.child (keyList.toArray (new String[keyList.size ()]));  // This node will always exist, since it was used to create the iterator.
        }
    }

    /**
        An iterator that steps through a discrete set of items.
    **/
    public static abstract class StudyIteratorIndexed extends StudyIterator
    {
        protected int index = -1;
        protected int count;  // Must be set by concrete class constructor.

        public StudyIteratorIndexed (String[] keys)
        {
            super (keys);
        }

        public int count ()
        {
            int result = count;
            if (inner != null) result *= inner.count ();
            return result;
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

        public void save (MNode study)
        {
            if (inner != null) inner.save (study);
            MNode n = node (study);
            n.set (index, "index");
        }

        public void load (MNode study)
        {
            if (inner != null) inner.load (study);
            MNode n = node (study);
            index = n.getInt ("index");
        }
    }

    public static class StudyIteratorList extends StudyIteratorIndexed
    {
        protected List<String> items;

        public StudyIteratorList (String[] keys, String items)
        {
            super (keys);
            this.items = Arrays.asList (items.split (","));
            count = this.items.size ();
        }

        public void assign (MNode model)
        {
            if (inner != null) inner.assign (model);
            model.set (items.get (index), keyPath);
        }
    }

    public static class StudyIteratorRange extends StudyIteratorIndexed
    {
        protected double lo;
        protected double hi;
        protected double step;

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

        public void assign (MNode model)
        {
            if (inner != null) inner.assign (model);
            model.set (lo + step * index, keyPath);
        }
    }

    public static class StudyIteratorRandom extends StudyIteratorIndexed
    {
        protected Operator expression;
        protected Type     nextValue;

        public StudyIteratorRandom (String[] keys, String value, MNode n)
        {
            super (keys);
            count = n.getOrDefault (1, "count");

            try
            {
                expression = Operator.parse (value);
            }
            catch (Exception e)
            {
                // TODO: some form of error reporting for Study.
            }
        }

        public boolean step ()
        {
            index++;
            nextValue = expression.eval (null);
            return index < count;
        }

        public void assign (MNode model)
        {
            if (inner != null) inner.assign (model);
            model.set (nextValue, keyPath);
        }
    }
}

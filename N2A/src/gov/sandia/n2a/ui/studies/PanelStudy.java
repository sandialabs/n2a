/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.studies;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MDoc;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MNode.Visitor;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.execenvs.Host;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.ui.Lay;
import gov.sandia.n2a.ui.Utility;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.images.ImageUtil;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
import javax.swing.table.TableColumnModel;

@SuppressWarnings("serial")
public class PanelStudy extends JPanel
{
    public static PanelStudy instance;  ///< Technically, this class is a singleton, because only one would normally be created.

    protected DefaultListModel<Study> model = new DefaultListModel<Study> ();
    public    JList<Study>            list  = new JList<Study> (model);
    protected JScrollPane             listPane;

    protected Study       displayStudy;                // The study currently in focus.
    protected JTabbedPane displayPane;
    protected JPanel      panelSamples;
    protected JButton     buttonPause;
    protected JLabel      labelStatus = new JLabel (); // Gives brief summary of remaining work and time.
    protected JTable      tableSamples;

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
                tableSamples.repaint ();
                buttonPause.setEnabled (study.complete () < 1);
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

                // Start worker thread for each study that needs it.
                for (int i = reverse.size () - 1; i >= 0; i--) reverse.get (i).start ();
            }
        };
        startThread.setDaemon (true);
        startThread.start ();

        buttonPause = new JButton (iconPause);
        buttonPause.setMargin (new Insets (2, 2, 2, 2));
        buttonPause.setFocusable (false);
        buttonPause.setToolTipText ("Pause");
        buttonPause.addActionListener (new ActionListener ()
        {
            public void actionPerformed (ActionEvent e)
            {
                if (displayStudy != null) displayStudy.togglePause ();
            }
        });

        tableSamples = new SampleTable ();

        panelSamples = Lay.BL
        (
            "N", Lay.BL ("W", Lay.FL ("H", buttonPause, labelStatus)),
            "C", Lay.sp (tableSamples)
        );

        JSplitPane split;
        Lay.BLtg
        (
            this,
            split = Lay.SPL
            (
                listPane = Lay.sp (list),
                displayPane
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
        if (displayStudy != study) return;
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                if (displayStudy == study) labelStatus.setText (status);
            }
        });
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

        displayPane.removeAll ();
        int count = model.getSize ();
        if (nextSelection < 0) nextSelection = 0;
        if (nextSelection >= count) nextSelection = count - 1;
        if (nextSelection >= 0)  // make new selection and load display pane
        {
            list.setSelectedIndex (nextSelection);
            displayStudy = list.getSelectedValue ();
            tableSamples.repaint ();
        }
    }

    /**
        Add a newly-created study to the list. This must be called on the EDT.
    **/
    public void addNewStudy (MNode node)
    {
        Study study = new Study (node);
        study.start ();

        model.add (0, study);  // Since this always executes on event dispatch thread, it will not conflict with other code that accesses model.
        list.setSelectedValue (study, true);  // Should trigger call of view() via selection listener.
        //list.requestFocusInWindow ();
    }

    public class Study
    {
        protected MNode         source;
        protected StudyThread   thread;
        protected StudyIterator iterator;
        protected int           count;      // total number of samples that will be generated
        protected int           index;      // of next sample that should be created

        public Study (MNode source)
        {
            this.source = source;
        }

        public String makePath (String[] keyPath)
        {
            String result = keyPath[0];
            for (int i = 1; i < keyPath.length; i++) result += "." + keyPath[i];
            return result;
        }

        public void buildVariables ()
        {
            if (source.child ("variables") != null) return;  // already have cached values

            Path dir = Paths.get (source.get ()).getParent ();
            MNode model = new MDoc (dir.resolve ("model"));
            model.visit (new Visitor ()
            {
                public boolean visit (MNode n)
                {
                    if (! n.key ().equals ("study")) return true;
                    String[] keyPath = n.keyPath ();
                    int i = keyPath.length - 1;  // Search backwards because "$metadata" is more likely to be immediate parent of "study".
                    for (; i >= 0; i--) if (keyPath[i].equals ("$metadata")) break;
                    if (i < 0) return true;  // move along, nothing to see here
                    if (i == keyPath.length - 2)  // immediate parent
                    {
                        if (keyPath.length < 3) return true;  // This is the top-level metadata block, so ignore study. It contains general parameters, rather than tagging a variable.
                        source.set (n.get (), Arrays.copyOf (keyPath, keyPath.length - 2));
                        return false;
                    }
                    else  // more distant parent, so a metadata key is the item to be iterated, rather than a variable
                    {
                        source.set (n.get (), Arrays.copyOf (keyPath, keyPath.length - 1));
                        return false;
                    }
                }
            });
        }

        public void buildIterator ()
        {
            if (iterator != null) return;
            buildVariables ();
            for (MNode v : source.childOrEmpty ("variables"))
            {
                String value = v.get ().trim ();
                StudyIterator it = null;
                if (value.startsWith ("["))
                {
                    value = value.substring (1);
                    value = value.split ("]", 2)[0];
                    it = new StudyIteratorRange (v.key (), value);
                }
                else if (value.contains (","))
                {
                    it = new StudyIteratorList (v.key (), value);
                }
                // TODO: how to handle unrecognized study type?
                it.inner = iterator;
                iterator = it;
            }
            // TODO: what if no study variables were found?
            count = iterator.count ();
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
            if (source.getFlag ("pause")) return;

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

            public StudyThread ()
            {
                super ("Study " + source.get ("$inherit") + " " + source.key ());
            }

            public void run ()
            {
                Path studyDir = Paths.get (source.get ()).getParent ();
                MNode model = new MDoc (studyDir.resolve ("model"));
                MNode modelCopy = new MVolatile ();
                modelCopy.merge (model);

                // Initialize iterator and bring up to present.
                // The M order of keys in "jobs" matches their creation order.
                // By actually executing the iterator again, we ensure that any random
                // draws repeat exactly the same sequence as before.
                buildIterator ();
                iterator.setModel (modelCopy);
                int lastIndex = source.childOrEmpty ("jobs").size () - 1;
                if (index <= lastIndex) showStatus (Study.this, "Recapitulating samples");
                while (! stop  &&  index <= lastIndex)
                {
                    iterator.next ();
                    index++;
                }

                // Get next sample, but don't advance index until it is actually launched.
                // This ensures that we can restart at the right sample if interrupted.
                long startTime = System.currentTimeMillis ();
                while (! stop  &&  iterator.next ())  // Puts the next sample in modelCopy
                {
                    // Show status
                    // TODO: this should be base on jobs completed rather than merely started.
                    String status = "" + index + "/" + count + " samples; ";
                    if (index == 0)
                    {
                        status += "Unknonw time remaining";
                    }
                    else
                    {
                        long totalTime = source.getLong ("time") + System.currentTimeMillis () - startTime;
                        double averageTime = totalTime / (index + 1);
                        double ETA = averageTime * (count - index) / 1000;  // ETA is in seconds rather than milliseconds
                        if      (ETA > 4.3425e17) status += "This will take longer than the age of the universe.";  // 13.77 billion years, give or take a few
                        else if (ETA > 2.3652e14) status += "Deep Thought got done sooner.";                        // 7.5 million years
                        else if (ETA >  31536000) status += String.format ("%f.1", ETA / 31536000) + " years remaining";
                        else if (ETA >   2592000) status += String.format ("%f.1", ETA /  2592000) + " months remaining";
                        else if (ETA >    604800) status += String.format ("%f.1", ETA /   604800) + " weeks remaining";
                        else if (ETA >     86400) status += String.format ("%f.1", ETA /    86400) + " days remaining";
                        else if (ETA >      3600) status += String.format ("%f.1", ETA /     3600) + " hours remaining";
                        else if (ETA >        60) status += String.format ("%f.1", ETA /       60) + " minutes remaining";
                        else                      status += ETA                                    + " seconds remaining";
                    }
                    showStatus (Study.this, status);

                    // Use the model to guide host selection.
                    // This allows host itself to be a study variable.
                    List<Host> hosts = new ArrayList<Host> ();
                    for (String hostname : modelCopy.get ("$metadata", "host").split (","))
                    {
                        Host h = Host.get (hostname.trim ());
                        if (h != null) hosts.add (h);
                    }
                    if (hosts.isEmpty ()) hosts.add (Host.get ("localhost"));

                    // Likewise for backend
                    Backend backend = Backend.getBackend (modelCopy.get ("$metada", "backend"));

                    // Wait until a host becomes available.
                    while (! stop)
                    {
                        Host chosenHost = null;
                        for (Host h : hosts)
                        {
                            if (backend.canRunNow (h, modelCopy))
                            {
                                chosenHost = h;
                                break;
                            }
                        }
                        if (chosenHost != null)
                        {
                            // Launch job and maintain all records
                            String jobKey = new SimpleDateFormat ("yyyy-MM-dd-HHmmss", Locale.ROOT).format (new Date ()) + "-" + Host.jobCount++;
                            final MNode job = AppData.runs.childOrCreate (jobKey);  // Create the dir and model doc
                            job.merge (modelCopy);
                            ((MDoc) job).save ();  // Force directory (and job file) to exist, so Backends can work with the dir.

                            Thread thread = new Thread ()
                            {
                                public void run ()
                                {
                                    // Simulator should record all errors/warnings to a file in the job dir.
                                    // If this thread throws an untrapped exception, then there is something wrong with the implementation.
                                    backend.start (job);
                                }
                            };
                            thread.setDaemon (true);
                            thread.start ();

                            PanelRun.instance.addNewRun (job);  // TODO: make sure this doesn't pull focus to Runs tab

                            source.set ("", "jobs", jobKey);
                            index++;  // If everything is done right, index should equal source "jobs" size - 1.
                            EventQueue.invokeLater (new Runnable ()
                            {
                                public void run ()
                                {
                                    tableSamples.repaint ();  // Doesn't matter whether this study is currently showing or not.
                                }
                            });
                            break;
                        }
                        try {sleep (1000);}
                        catch (InterruptedException e) {}
                    }

                    try {sleep (1000);}
                    catch (InterruptedException e) {}
                }

                long elapsed = System.currentTimeMillis () - startTime;
                source.set (source.getLong ("time") + elapsed, "time");
            }
        }
    }

    public class SampleTable extends JTable
    {
        public SampleTable ()
        {
            super (new SampleTableModel ());

            setAutoResizeMode (JTable.AUTO_RESIZE_OFF);
            ((DefaultTableCellRenderer) getTableHeader ().getDefaultRenderer ()).setHorizontalAlignment (JLabel.LEFT);
        }

        public void updateUI ()
        {
            super.updateUI ();

            FontMetrics fm = getFontMetrics (getFont ());

            setRowHeight (fm.getHeight () + getRowMargin ());

            // Base column width solely on headers, rather than contents.
            // Scanning through all the contents could be very expensive.
            TableColumnModel cols = getColumnModel ();
            List<String> columns = ((SampleTableModel) getModel ()).variablePaths;
            int digitWidth = fm.charWidth ('0');
            int em         = fm.charWidth ('M');
            int minWidth = 10 * digitWidth;
            int maxWidth = 20 * em;
            for (int i = 0; i < columns.size (); i++)
            {
                int width = Math.min (maxWidth, Math.max (minWidth, fm.stringWidth (columns.get (i))));
                cols.getColumn (i).setPreferredWidth (width);
            }
        }
    }

    public class SampleTableModel extends AbstractTableModel
    {
        // Because MNode doesn't have indexed access, we need to capture the keys.
        // We cache this work, so it is only updated when displayStudy changes.
        protected Study        currentStudy;
        protected List<String> variablePaths;
        protected List<String> jobKeys;

        public int getRowCount ()
        {
            if (displayStudy == null) return 0;
            return displayStudy.source.childOrEmpty ("jobs").size ();
        }

        public int getColumnCount ()
        {
            if (displayStudy == null) return 0;
            displayStudy.buildVariables ();
            return displayStudy.source.childOrEmpty ("variables").size ();
        }

        public String getColumnName (int column)
        {
            checkIndices ();
            if (column < 0  ||  column >= variablePaths.size ()) return "";
            return variablePaths.get (column);
        }

        public Object getValueAt (int row, int column)
        {
            checkIndices ();
            if (row < 0  ||  row >= jobKeys.size ()) return "";
            if (column < 0  ||  column >= variablePaths.size ()) return "";

            String[] keyPath = variablePaths.get (column).split ("\\.");
            String jobKey = jobKeys.get (row);
            MNode job = AppData.runs.child (jobKey);
            if (job == null) return "";
            return job.get (keyPath);
        }

        public void checkIndices ()
        {
            if (currentStudy == displayStudy) return;
            variablePaths = new ArrayList<String> ();
            jobKeys       = new ArrayList<String> ();
            if (displayStudy == null) return;

            displayStudy.buildVariables ();
            for (MNode v : displayStudy.source.childOrEmpty ("variables")) variablePaths.add (v.key ());
            for (MNode j : displayStudy.source.childOrEmpty ("jobs")) jobKeys.add (j.key ());
        }
    }

    public static abstract class StudyIterator
    {
        protected StudyIterator inner;   // sub-iterator. If null, then this is the last iterator in the chain.
        protected MNode         model;   // write the variable value directly into this structure
        protected String        key;
        protected String[]      keyPath; // full set of keys that address the location of the variable
        // concrete classes will also include information on the specific values to iterate over

        public StudyIterator (String key)
        {
            this.key = key;
            keyPath = key.split ("\\.");
        }

        public void setModel (MNode model)
        {
            this.model = model;
            if (inner != null) inner.setModel (model);
        }

        public abstract int     count ();  // total number of samples that will be generated by one complete sequence
        public abstract void    fastForward (MNode value);  // given a set of flattened key-value pairs, set this and each sub-iterator to the state as if it had just handed out this item in the sequence
        public abstract void    reset ();  // Restart this iterator at the beginning of its sequence. Also resets any sub-iterators.
        public abstract boolean next ();  // Steps to next value of deepest contained iterator, possibly advancing any iterator up to and including this one. Returns false if no more items are available.
    }

    public static class StudyIteratorList extends StudyIterator
    {
        protected List<String> items;
        protected int          index;

        public StudyIteratorList (String key, String items)
        {
            super (key);
            this.items = Arrays.asList (items.split (","));
        }

        public int count ()
        {
            int result = items.size ();
            if (inner != null) result *= inner.count ();
            return result;
        }

        public void fastForward (MNode value)
        {
            String current = value.get (key);
            index = items.indexOf (current);
            if (index < 0) index = 0;  // not found, so start at beginning
        }

        public void reset ()
        {
            index = 0;
            if (inner != null) inner.reset ();
        }

        public boolean next ()
        {
            if (index >= items.size ()) return false;
            model.set (items.get (index++), keyPath);
            return true;
        }
    }

    public static class StudyIteratorRange extends StudyIterator
    {
        protected double lo;
        protected double hi;
        protected double step;
        protected int index;
        protected int count;

        public StudyIteratorRange (String key, String range)
        {
            super (key);
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
            int result = count;
            if (inner != null) result *= inner.count ();
            return result;
        }

        public void fastForward (MNode value)
        {
            double current = value.getDouble (key);
            index = (int) Math.floor ((current - lo) / step);
            if (index < 0) index = 0;
            if (index >= count) index = count - 1;
        }

        public void reset ()
        {
            index = 0;
            if (inner != null) inner.reset ();
        }

        public boolean next ()
        {
            if (index >= count) return false;
            model.set (lo + step * index++, keyPath);
            return true;
        }
    }
}

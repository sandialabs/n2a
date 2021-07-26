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
import gov.sandia.n2a.ui.Utility;
import gov.sandia.n2a.ui.eq.PanelEquations;
import gov.sandia.n2a.ui.eq.PanelModel;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.jobs.NodeJob;
import gov.sandia.n2a.ui.jobs.PanelRun;
import java.awt.EventQueue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.swing.Icon;
import javax.swing.ImageIcon;

public class Study
{
    protected MNode               source;
    protected StudyThread         thread;
    protected StudyIterator       iterator;
    protected int                 count;                    // Total number of samples that will be generated
    protected int                 index;                    // Of next sample that should be created. Always 1 greater than last completed sample. When 0, study is about to start. When equal to count, study has completed.
    protected List<String>        incomplete;
    protected int                 lastComplete;             // Used to throttle status messages when running headless.
    protected boolean             usesRandom;               // At least one iterator in the chain generates random values. It will need an instance of the internal Simulator to create a convenient environment for generating values.
    protected Random              random;                   // random number generator used by iterator
    protected long                startTime;                // Of main loop in thread. Used to estimate time remaining.
    protected Map<String,Integer> jobMap;

    public static final ImageIcon iconPause    = ImageUtil.getImage ("pause-16.png");
    public static final ImageIcon iconComplete = ImageUtil.getImage ("complete.gif");

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

    public void waitForCompletion ()
    {
        while (thread != null)
        {
            try {thread.join (1000);}
            catch (Exception e) {}  // both InterruptedException and NullPointerException
        }
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

    public NodeJob getJob (int index)
    {
        String jobKey = getJobKey (index);
        NodeJob result;
        synchronized (PanelRun.jobNodes) {result = PanelRun.jobNodes.get (jobKey);}
        return result;
    }

    public Path getDir ()
    {
        return Host.getLocalResourceDir ().resolve ("studies").resolve (source.key ());
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
            PanelStudy ps = PanelStudy.instance;
            index = source.getOrDefault (0, "barrier");  // The sample immediately after the most recent barrier that was passed, if any. Implies that everything up through index-1 was fully completed.
            if (iterator == null)
            {
                buildIterator ();
                if (iterator == null)  // Failed to find any study variables.
                {
                    // Convert this to a single run.
                    if (ps != null)  // only if showing GUI (not headless)
                    {
                        EventQueue.invokeLater (new Runnable ()
                        {
                            public void run ()
                            {
                                ps.list.setSelectedValue (Study.this, false); // Make ourself the current selection.
                                ps.delete ();                                 // Delete current selection.

                                PanelEquations pe = PanelModel.instance.panelEquations;
                                MNode doc = AppData.models.child (source.get ("$inherit"));
                                pe.load (doc);  // Usually this record will already be loaded, since the study was launched from there.
                                pe.launchJob ();
                            }
                        });
                    }

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
            int jobCount = getJobCount ();
            if (incomplete == null)
            {
                incomplete = new LinkedList<String> ();
                while (index < jobCount)
                {
                    String jobKey = getJobKey (index++);
                    NodeJob node;
                    synchronized (PanelRun.jobNodes) {node = PanelRun.jobNodes.get (jobKey);}
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
                        synchronized (PanelRun.jobNodes) {node = PanelRun.jobNodes.get (jobKey);}
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
                    synchronized (PanelRun.jobNodes) {node = PanelRun.jobNodes.get (jobKey);}
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

                    if (ps == null)  // headless
                    {
                        if (node == null)
                        {
                            node = new NodeJob (job, true);
                            synchronized (PanelRun.jobNodes) {PanelRun.jobNodes.put (jobKey, node);}
                        }
                        Host.waitForHost (node);
                    }
                    else  // with GUI
                    {
                        EventQueue.invokeLater (new Runnable ()
                        {
                            public void run ()
                            {
                                NodeJob node;
                                synchronized (PanelRun.jobNodes) {node = PanelRun.jobNodes.get (jobKey);}
                                if (node == null)
                                {
                                    node = PanelRun.instance.addNewRun (job, false);
                                    if (ps.displayStudy == Study.this) ps.tableSamples.addJobs ();
                                }
                                Host.waitForHost (node);
                            }
                        });
                    }
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
        int complete = 0;
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
            complete = getJobCount () - (incomplete == null ? 0 :incomplete.size ());
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
        PanelStudy ps = PanelStudy.instance;
        if (ps == null)  // headless
        {
            if (complete != lastComplete)
            {
                lastComplete = complete;
                System.err.println (status);
            }
            return;
        }
        ps.showStatus (this, status);

        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                int row = ps.model.indexOf (Study.this);
                if (row < 0) return;  // Could be negative if row no longer exists, such as during delete.
                ps.list.repaint (ps.list.getCellBounds (row, row));
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

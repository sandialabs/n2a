package gov.sandia.umf.platform.db;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import gov.sandia.umf.platform.UMF;
import gov.sandia.umf.platform.ui.AboutDialog;

/**
    Manages all user data associated with the application.
    This singleton contains several MDir objects which wrap various categories
    of records. We are responsible for loading all data, providing it to the
    rest of the app, and saving it. Saves occur at shutdown, and also on a
    regular interval (every 30 seconds) during operation.
**/
public class AppData
{
    protected static AppData instance;
    public static AppData getInstance ()
    {
        if (instance == null) instance = new AppData ();
        return instance;
    }

    public MDir models;
    public MDir references;
    public MDir runs;

    protected boolean stop;
    protected Thread saveThread;

    public AppData ()
    {
        File root = UMF.getAppResourceDir ();
        models     = new MDir (new File (root, "models"));
        references = new MDir (new File (root, "references"));
        runs       = new MDir (new File (root, "models"), "model");

        stop = false;
        saveThread = new Thread ("Save Thread")
        {
            public void run ()
            {
                while (! stop)
                {
                    try
                    {
                        sleep (30000);
                        instance.save ();
                    }
                    catch (InterruptedException e)
                    {
                    }
                }
            };
        };
        saveThread.start ();
    }

    public void checkInitialDB ()
    {
        if (models.length () > 0) return;

        // Unfortunately, this list must be maintained to match the initial set of models.
        // It is possible to scan the directory, but it requires sifting through the whole jar file.
        // The method used here is more time efficient.
        String[] sourceFiles = new String []
        {
            "Channel",
            "Channel K",
            "Channel Na",
            "Compartment Passive",
            "Hodgkin-Huxley Cable",
            "Hodgkin-Huxley Coupling",
            "Neuron Brette",
            "Neuron Firing-Rate",
            "Neuron Hodgkin-Huxley",
            "Neuron Izhikevich",
            "Neuron LIF",
            "STPU Connector",
            "STPU Connector Input",
            "STPU Input",
            "Synapse Exponential"
        };
        try
        {
            for (String s : sourceFiles)
            {
                MDoc doc = (MDoc) models.set ("", s);
                BufferedReader reader = new BufferedReader (new InputStreamReader ((AppData.class.getResource ("init/" + s).openStream ())));
                reader.readLine ();  // dispose of schema line
                doc.read (reader);
                reader.close ();
            }
        }
        catch (IOException e)
        {
            System.err.println ("Unable to load some or all of initial DB");
        }
    }

    public void save ()
    {
        models.save ();
        references.save ();
        runs.save ();  // The reason to save runs is if we record data in them about process status. If no data is changed, could get rid of this save.
    }

    public void quit ()
    {
        stop = true;
        saveThread.interrupt ();
        save ();
    }
}

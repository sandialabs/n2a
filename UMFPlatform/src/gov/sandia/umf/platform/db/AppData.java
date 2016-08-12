package gov.sandia.umf.platform.db;

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import gov.sandia.umf.platform.UMF;

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

    public void backup (File destination)
    {
        save ();

        // Assemble file list
        String stem = UMF.getAppResourceDir ().getAbsolutePath ();
        List<String> paths = new LinkedList<String> ();
        for (String f : models    .root.list ()) paths.add (new File ("models",     f).getPath ());
        for (String f : references.root.list ()) paths.add (new File ("references", f).getPath ());

        // Dump to zip
        try
        {
            FileOutputStream fos = new FileOutputStream (destination);
            ZipOutputStream zos = new ZipOutputStream (fos);
            try
            {
                for (String path : paths)
                {
                    zos.putNextEntry (new ZipEntry (path));
                    Files.copy (Paths.get (stem, path), zos);
                }
            }
            finally
            {
                zos.closeEntry ();
                zos.close ();
                fos.close ();
            }
        }
        catch (IOException error)
        {
            System.err.println (error.toString ());
        }
    }

    public void restore (File source, boolean removeAdded)
    {
        // Purge existing files
        if (removeAdded)
        {
            models.clear ();
            references.clear ();
        }

        // Read the zip file
        try
        {
            String stem = UMF.getAppResourceDir ().getAbsolutePath ();
            ZipFile zipFile = new ZipFile (source);
            Enumeration<? extends ZipEntry> entries = zipFile.entries ();
            while (entries.hasMoreElements ())
            {
                ZipEntry entry = entries.nextElement ();
                InputStream stream = zipFile.getInputStream (entry);
                Files.copy (stream, Paths.get (stem, entry.getName ()), StandardCopyOption.REPLACE_EXISTING);
            }
            zipFile.close ();
        }
        catch (IOException error)
        {
            System.err.println (error.toString ());
        }

        models    .fireChanged ();
        references.fireChanged ();
    }

    public void quit ()
    {
        stop = true;
        saveThread.interrupt ();
        save ();
    }
}

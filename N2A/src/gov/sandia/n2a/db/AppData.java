/*
Copyright 2016-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
    Manages all user data associated with the application.
    This singleton contains several MDir objects which wrap various categories
    of records. We are responsible for loading all data, providing it to the
    rest of the app, and saving it. Saves occur at shutdown, and also on a
    regular interval (every 30 seconds) during operation.
**/
public class AppData
{
    public static MNode  properties;
    public static MDoc   state;
    public static MDir   runs;
    public static MCombo models;
    public static MCombo references;

    protected static boolean stop;
    protected static Thread saveThread;

    protected static Map<String,String> indexID;  ///< Maps IDs to model names. Model names are required to be unique, so they function as the direct key into the models database.

    static
    {
        Path root = Paths.get (System.getProperty ("user.home"), "n2a").toAbsolutePath ();
        properties = new MVolatile ();
        properties.set ("resourceDir", root);

        state = new MDoc (root.resolve ("client.state").toString ());
        runs  = new MDir (root.resolve ("jobs"), "model");  // "model" is our internal housekeeping data, in MNode serialization form. Backend output generally goes into a simulator-specific file.

        List<MNode> modelContainers     = new ArrayList<MNode> ();
        List<MNode> referenceContainers = new ArrayList<MNode> ();
        Path reposDir = root.resolve ("repos");
        for (MNode repo : state.childOrCreate ("Repos"))
        {
            Path repoDir = reposDir.resolve (repo.get ());  // key() is index number, which establishes precedence order
            modelContainers    .add (new MDir (repoDir.resolve ("models")));
            referenceContainers.add (new MDir (repoDir.resolve ("references")));
        }
        models     = new MCombo ("models",     modelContainers);
        references = new MCombo ("references", referenceContainers);

        stop = false;
        saveThread = new Thread ("Save AppData")
        {
            public void run ()
            {
                while (! stop)
                {
                    try
                    {
                        sleep (30000);
                        AppData.save ();
                    }
                    catch (InterruptedException e)
                    {
                    }
                }
            };
        };
        saveThread.setDaemon (true);  // This thread should be killed gracefully by a call to quit() before the app shuts down. But if not, we don't want it to keep the VM alive.
        saveThread.start ();
    }

    public static void checkInitialDB ()
    {
        if (state.childOrCreate ("Repos").size () > 0) return;
        state.set ("Repos", "0", "local");
        state.set ("Repos", "1", "base");

        Path root = Paths.get (properties.get ("resourceDir")).toAbsolutePath ();
        Path reposDir = root.resolve ("repos");
        Path baseDir  = reposDir.resolve ("base");
        Path localDir = reposDir.resolve ("local");

        MDir baseModels      = new MDir (baseDir .resolve ("models"));
        MDir baseReferences  = new MDir (baseDir .resolve ("references"));
        MDir localModels     = new MDir (localDir.resolve ("models"));
        MDir localReferences = new MDir (localDir.resolve ("references"));

        List<MNode> modelContainers     = new ArrayList<MNode> ();
        List<MNode> referenceContainers = new ArrayList<MNode> ();
        modelContainers.add (localModels);
        modelContainers.add (baseModels);
        referenceContainers.add (localReferences);
        referenceContainers.add (baseReferences);
        models    .init (modelContainers);
        references.init (referenceContainers);

        try (ZipInputStream zip = new ZipInputStream (AppData.class.getResource ("initialDB").openStream ()))
        {
            ZipEntry entry;
            while ((entry = zip.getNextEntry ()) != null)
            {
                if (entry.isDirectory ()) continue;
                String name = entry.getName ();
                String[] pieces = name.split ("/");
                if (pieces.length != 3) continue;
                MDir dir = null;
                if (pieces[0].equals ("local"))
                {
                    if (pieces[1].equals ("references")) dir = localReferences;
                    else                                 dir = localModels;
                }
                else
                {
                    if (pieces[1].equals ("references")) dir = baseReferences;
                    else                                 dir = baseModels;
                }
                MDoc doc = (MDoc) dir.set (pieces[2], "");
                BufferedReader reader = new BufferedReader (new InputStreamReader (zip, "UTF-8"));
                reader.readLine ();  // dispose of schema line
                doc.read (reader);
            }
        }
        catch (Exception e)
        {
            System.err.println ("Unable to load some or all of initial DB");
            e.printStackTrace ();
        }
    }

    public static void set (String id, MNode model)
    {
        if (indexID == null) return;  // When get(ID) is called, this new entry will get indexed along with everything else.
        if (model == null) indexID.remove (id);
        else               indexID.put (id, model.key ());
    }

    public static MDoc getModel (String id)
    {
        if (indexID == null)
        {
            indexID = new HashMap<String,String> ();
            for (MNode n : models)
            {
                String nid = n.get ("$metadata", "id");
                if (! nid.isEmpty ()) indexID.put (nid, n.key ());
            }
        }
        String name = indexID.get (id);
        if (name == null) return null;
        return (MDoc) models.child (name);
    }

    public synchronized static void save ()
    {
        models.save ();
        references.save ();
        runs.save ();  // The reason to save runs is if we record data in them about process status. If no data is changed, could get rid of this save.
        state.save ();
    }

    public static void quit ()
    {
        stop = true;
        saveThread.interrupt ();
        save ();
    }
}

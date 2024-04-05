/*
Copyright 2016-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.Cleaner;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import gov.sandia.n2a.ui.settings.SettingsRepo;

/**
    Manages all user data associated with the application.
    This singleton contains several MDir objects which wrap various categories
    of records. We are responsible for loading all data, providing it to the
    rest of the app, and saving it. Saves occur at shutdown, and also on a
    regular interval (every 30 seconds) during operation.
**/
public class AppData
{
    public static MNode     properties;
    public static MDoc      state;
    public static MDir      runs;
    public static MDir      studies;
    public static MDir      repos;
    public static MVolatile docs;     // Collection of MCombos. Key is the document type, aka folder. Prime examples include "models" and "references".
    public static MVolatile existing; // Collection of all MDirs under repos, including those not currently active in docs. Organized by folder name first, then repo name. Purpose is to ensure object identity of every repo and document.

    protected static boolean stop;

    public static final Cleaner cleaner = Cleaner.create ();

    protected static Map<String,String> indexID;  ///< Maps IDs to model names. Model names are required to be unique, so they function as the direct key into the models database.

    static
    {
        Path root = Paths.get (System.getProperty ("user.home"), "n2a").toAbsolutePath ();
        properties = new MVolatile ();
        properties.set (root, "resourceDir");

        state   = new MDoc (root.resolve ("state"));
        runs    = new MDir (root.resolve ("jobs"), "job");  // "job" is our internal housekeeping data, in MNode serialization form. "model" is a fully-collated archival copy of the model being simulated. Backend output generally goes into files named "model" with specific suffixes.
        studies = new MDir (root.resolve ("studies"), "study");  // "study" contains general metadata. A separate MDoc called "model" holds a snapshot of the model being studied.
        Path reposDir = root.resolve ("repos");
        repos   = new MDir (reposDir, "state");

        String reposOrderString = state.get ("Repos", "order");
        List<String> reposOrder = new ArrayList<String> ();
        for (String repoName : reposOrderString.split (",")) reposOrder.add (repoName);
        for (MNode repo : repos)
        {
            String repoName = repo.key ();
            if (! reposOrder.contains (repoName))
            {
                reposOrder.add (repoName);
                reposOrderString += "," + repoName;
            }
        }
        if (reposOrderString.startsWith (",")) reposOrderString = reposOrderString.substring (1);
        state.set (reposOrderString, "Repos", "order");

        String primary = state.get ("Repos", "primary");
        if (! primary.isEmpty ()  &&  reposOrder.indexOf (primary) != 0)  // Also covers the case where primary is not in the list at all.
        {
            reposOrder.remove (primary);
            reposOrder.add (0, primary);
        }
        if (reposOrder.size () > 0)
        {
            primary = reposOrder.get (0);
            state.set (primary, "Repos", "primary");
        }

        existing = new MVolatile ();
        for (String repoName : reposOrder)
        {
            Path repoDir = reposDir.resolve (repoName);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream (repoDir))
            {
                for (Path path : stream)
                {
                    if (! Files.isDirectory (path)) continue;
                    String folderName = path.getFileName ().toString ();
                    if (folderName.startsWith (".")) continue;

                    MVolatile folder = (MVolatile) existing.child (folderName);
                    if (folder == null)
                    {
                        folder = new MVolatile (folderName);
                        existing.link (folder);
                    }
                    folder.link (new MDir (repoName, path));
                }
            }
            catch (IOException e) {}
        }

        docs = new MVolatile ()
        {
            public MNode set (String value, String key)
            {
                // In theory, set() will only be called when we need to create a new folder.
                // However, nothing stops client code from calling it directly, so we guard against existing value.
                MNode result = child (key);
                if (result == null)
                {
                    result = new MFolder (key, new ArrayList<MNode> ());
                    link (result);
                }
                return result;
            }
        };
        buildDocs ();

        Thread saveThread = new Thread ("Save AppData")
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

    public static class MFolder extends MCombo
    {
        public MFolder (String name, List<MNode> containers)
        {
            super (name, containers);
        }

        public synchronized MNode set (String value, String key)
        {
            load ();
            MNode container = children.get (key);
            if (containerIsWritable (container)) return container.set (value, key);
            // Here is where we deviate from MCombo.set().
            // We need to ensure that "primary" is actually the primary repo, rather
            // than just the first repo containing an instance of the folder.
            String primaryName = state.get ("Repos", "primary");  // After initial startup, this should always be non-empty.
            if (! primary.key ().equals (primaryName))
            {
                // SettingRepo.instance should be non-null by the time any code calls MFolder.set().
                SettingsRepo s = SettingsRepo.instance;
                String folderName = key ();  // Key of the MCombo, which is not the same as the parameter "key".
                boolean needRebuild = s.needRebuild;  // Avoid changing this flag, since we directly update ourselves.
                primary = s.getOrCreateContainer (primaryName, folderName);
                s.needRebuild = needRebuild;
                containers.add (0, primary);
                primary.addListener (this);
            }
            return primary.set (value, key);
        }
    }

    public static void buildDocs ()
    {
        String primary = state.get ("Repos", "primary");

        String reposOrderString = state.get ("Repos", "order");
        String[] reposOrder = reposOrderString.split (",");

        for (MNode e : existing)
        {
            String folderName = e.key ();

            List<MNode> containers = new ArrayList<MNode> ();
            for (String repoName : reposOrder)
            {
                MNode container = e.child (repoName);
                if (container == null) continue;

                boolean isPrimary = repoName.equals (primary);
                MNode repo = repos.child (repoName);
                if (! isPrimary  &&  ! repo.getBoolean ("editable")  &&  ! repo.getBoolean ("visible")) continue;

                if (isPrimary) containers.add (0, container); 
                else           containers.add (container);
            }

            MCombo d = (MCombo) docs.child (folderName);
            if (d == null) docs.link (new MFolder (folderName, containers));
            else           d.init (containers);  // Triggers changed() callback, which in turn triggers PanelSearch.search().
            // Notice that the above code won't remove a folder from docs.
            // However, it will update the MCombo's container list to be empty.
        }
    }

    public static void checkInitialDB ()
    {
        if (repos.size () > 0) return;

        repos.set (1,         "local", "visible");
        repos.set (1,         "local", "editable");
        repos.set (1,         "base",  "visible");
        repos.set ("#0000FF", "base",  "color");
        state.set ("local,base", "Repos", "order");
        state.set ("local",      "Repos", "primary");
        state.set ("",           "Repos", "needUpstream");

        Path root = Paths.get (properties.get ("resourceDir")).toAbsolutePath ();
        Path reposDir = root.resolve ("repos");
        Path baseDir  = reposDir.resolve ("base");
        Path localDir = reposDir.resolve ("local");

        MDir baseModels      = new MDir ("base",  baseDir .resolve ("models"));
        MDir baseReferences  = new MDir ("base",  baseDir .resolve ("references"));
        MDir localModels     = new MDir ("local", localDir.resolve ("models"));
        MDir localReferences = new MDir ("local", localDir.resolve ("references"));

        MVolatile existingModels     = new MVolatile ("models");
        MVolatile existingReferences = new MVolatile ("references");
        existingModels.link (baseModels);
        existingModels.link (localModels);
        existingReferences.link (baseReferences);
        existingReferences.link (localReferences);
        existing.link (existingModels);
        existing.link (existingReferences);

        List<MNode> modelContainers     = new ArrayList<MNode> ();
        List<MNode> referenceContainers = new ArrayList<MNode> ();
        modelContainers.add (localModels);
        modelContainers.add (baseModels);
        referenceContainers.add (localReferences);
        referenceContainers.add (baseReferences);
        docs.link (new MFolder ("models",     modelContainers));
        docs.link (new MFolder ("references", referenceContainers));

        try (ZipInputStream zip = new ZipInputStream (AppData.class.getResource ("initialDB.zip").openStream ()))
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
                MDoc doc = (MDoc) dir.childOrCreate (pieces[2]);
                BufferedReader reader = new BufferedReader (new InputStreamReader (zip, "UTF-8"));
                Schema.readAll (doc, reader);
            }
        }
        catch (Exception e)
        {
            System.err.println ("Unable to load some or all of initial DB");
            e.printStackTrace ();
        }
    }

    // Utility for converting documents to latest schema.
    // This simply tags them as needing to be saved. MDir always saves in the latest format.
    protected static void convert (List<MNode> containers)
    {
        for (MNode c : containers)
        {
            for (MNode d : c)
            {
                MDoc doc = (MDoc) d;
                doc.load ();
                doc.markChanged ();
            }
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
            for (MNode n : docs.childOrEmpty ("models"))
            {
                String nid = n.get ("$meta", "id");
                if (! nid.isEmpty ()) indexID.put (nid, n.key ());
            }
        }
        String name = indexID.get (id);
        if (name == null) return null;
        return (MDoc) docs.child ("models", name);
    }

    public synchronized static void save ()
    {
        // Sorted from most critical to least, in terms of how damaging a loss of information would be.
        docs.forEach (c -> ((MCombo) c).save ());
        studies.save ();
        runs.save ();
        repos.save ();
        state.save ();
    }

    public static void quit ()
    {
        stop = true;
        save ();
    }
}

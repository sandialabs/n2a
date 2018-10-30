/*
Copyright 2016-2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
    Manages all user data associated with the application.
    This singleton contains several MDir objects which wrap various categories
    of records. We are responsible for loading all data, providing it to the
    rest of the app, and saving it. Saves occur at shutdown, and also on a
    regular interval (every 30 seconds) during operation.
**/
public class AppData
{
    public static MDir  models;
    public static MDir  references;
    public static MDir  runs;
    public static MDoc  state;
    public static MNode properties;

    protected static boolean stop;
    protected static Thread saveThread;

    protected static Map<String,String> indexID;  ///< Maps IDs to model names. Model names are required to be unique, so they function as the direct key into the models database.

    static
    {
        File root = new File (System.getProperty ("user.home"), "n2a").getAbsoluteFile ();
        properties = new MVolatile ();
        properties.set ("resourceDir", root.getAbsolutePath ());

        models     = new MDir (new File (root, "models"));
        references = new MDir (new File (root, "references"));
        runs       = new MDir (new File (root, "jobs"), "model");  // "model" is our internal housekeeping data, in MNode serialization form. Backend output generally goes into a simulator-specific file.
        state      = new MDoc (new File (root, "client.state").getAbsolutePath ());

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
        if (models.size () > 0) return;

        try (ZipInputStream zip = new ZipInputStream (AppData.class.getResource ("models").openStream ()))
        {
            ZipEntry entry;
            while ((entry = zip.getNextEntry ()) != null)
            {
                MDoc doc = (MDoc) models.set (entry.getName (), "");
                BufferedReader reader = new BufferedReader (new InputStreamReader (zip, "UTF-8"));
                reader.readLine ();  // dispose of schema line
                doc.read (reader);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace ();
            System.err.println ("Unable to load some or all of initial DB");
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

    public static void backup (File destination)
    {
        save ();

        // Assemble file list
        String stem = properties.get ("resourceDir");
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

    public static void restore (File source, boolean removeAdded)
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
            String stem = properties.get ("resourceDir");
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

    public static void quit ()
    {
        stop = true;
        saveThread.interrupt ();
        save ();
    }
}

/*
Copyright 2016-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import gov.sandia.n2a.host.Host;

import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
    Holds a collection of MDocs and ensures that any changes get written out to disk.
**/
public abstract class MDocGroup extends MNode
{
    protected String                                   name;  // We could be held in an even higher-level node.
    protected NavigableMap<String,SoftReference<MDoc>> children   = new TreeMap<String,SoftReference<MDoc>> ();
    protected Set<MDoc>                                writeQueue = new HashSet<MDoc> ();  // By storing strong references to docs that need to be saved, we prevent them from being garbage collected until that is done.
    protected List<MNodeListener>                      listeners  = new ArrayList<MNodeListener> ();

    public MDocGroup ()
    {
    }

    public MDocGroup (String name)
    {
        this.name = name;
    }

    public String key ()
    {
        return name;  // Don't bother guarding against null. If the caller puts us under a higher-level node, it is also responsible to construct us with a proper key.
    }

    public String getOrDefault (String defaultValue)
    {
        return defaultValue;
    }

    public static String validFilenameFrom (String name)
    {
        String[] forbiddenChars = new String[] {"\\", "/", ":", "*", "\"", "<", ">", "|"};
        for (String c : forbiddenChars) name = name.replace (c, "-");

        // For Windows, certain file names are forbidden due to its archaic roots in DOS.
        String upperName = name.toUpperCase ();
        HashSet<String> forbidden = new HashSet<String> (Arrays.asList ("CON", "PRN", "AUX", "NUL"));
        if (forbidden.contains (upperName)  ||  upperName.matches ("(LPT|COM)\\d")) name += "_";

        return name;
    }

    /**
        Generates absolute path for the MDoc, based only on the key.
    **/
    public abstract Path pathForDoc (String key);

    /**
        Similar to pathForDoc(), but gives path to the file for the purposes of moving or deleting.
        This is potentially a directory rather than a specific file.
    **/
    public Path pathForFile (String key)
    {
        return pathForDoc (key);
    }

    protected synchronized MNode getChild (String key)
    {
        if (key.isEmpty ()) return null;  // The file-existence code below can be fooled by an empty string, so explicitly guard against it.
        if (! children.containsKey (key)) return null;
        MDoc result = null;
        SoftReference<MDoc> reference = children.get (key);
        if (reference != null) result = reference.get ();
        if (result == null)  // Doc has been garbage collected.
        {
            Path path = pathForDoc (key);
            if (! Files.isReadable (path)) return null;
            result = new MDoc (this, pathForDoc (key).toString (), key);
            children.put (key, new SoftReference<MDoc> (result));
        }
        return result;
    }

    /**
        Empty this group of all files.
        File themselves will not be deleted.
        However, subclass MDir does delete the entire directory from disk.
    **/
    public synchronized void clear ()
    {
        children.clear ();
        writeQueue.clear ();
        fireChanged ();
    }

    protected synchronized void clearChild (String key)
    {
        SoftReference<MDoc> ref = children.remove (key);
        if (ref != null) writeQueue.remove (ref.get ());
        Path path = pathForFile (key);
        Host.get (path).deleteTree (path);
        fireChildDeleted (key);
    }

    public synchronized int size ()
	{
        return children.size ();
	}

    /**
        Creates a new MDoc.
        @param key Simple name for document.
        @param value The path to the document. Depending on subclass, this may
        be ignored. IE: if the subclass knows how to construct path from just
        the key.
    **/
    public synchronized MNode set (String value, String key)
    {
        MDoc result = (MDoc) getChild (key);
        if (result == null)  // new document, or at least new to us
        {
            Path path = pathForDoc (key);
            result = new MDoc (this, path.toString (), key);
            children.put (key, new SoftReference<MDoc> (result));
            if (! Files.exists (path)) result.markChanged ();  // Set the new document to save. Adds to writeQueue.

            fireChildAdded (key);
        }
        return result;
    }

    /**
        Renames an MDoc on disk.
        If you already hold a reference to the MDoc named by fromKey, then that reference remains valid
        after the move.
    **/
    public synchronized void move (String fromKey, String toKey)
    {
        if (toKey.equals (fromKey)) return;
        save ();  // If this turns out to be too much work, then scan the write queue for fromKey and save it directly.

        // This operation is independent of bookkeeping in "children" collection.
        Path fromPath = pathForFile (fromKey);
        Path toPath   = pathForFile (toKey);
        try
        {
            if (Files.exists (toPath)) Host.get (toPath).deleteTree (toPath);
            Files.move (fromPath, toPath, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e)
        {
            // This can happen if a new doc has not yet been flushed to disk.
        }

        SoftReference<MDoc> fromReference = children.get (fromKey);
        boolean fromExists = fromReference != null;
        boolean toExists   = children.containsKey (toKey);
        children.remove (fromKey);
        children.remove (toKey);
        if (fromExists)
        {
            MDoc from = fromReference.get ();
            if (from != null) from.name = toKey;
            children.put (toKey, fromReference);
            fireChildChanged (fromKey, toKey);
        }
        else  // from does not exist
        {
            if (toExists) fireChildDeleted (toKey);  // Because we overwrote an existing node with a non-existing node, causing the destination to cease to exist.
        }
    }

    public synchronized void addListener (MNodeListener listener)
    {
        listeners.add (listener);
    }

    public synchronized void removeListener (MNodeListener listener)
    {
        // Don't call listeners.remove() directly, because ArrayList calls equals().
        // If the listener is an MNode, this could result in a deep comparison
        // of entire trees, when all that is really needed is object identity.
        for (int i = listeners.size () - 1; i >= 0; i--)
        {
            if (listeners.get (i) != listener) continue;
            listeners.remove (i);
            break;
        }
    }

    public synchronized void fireChanged ()
    {
        for (MNodeListener l : listeners) l.changed ();
    }

    public synchronized void fireChildAdded (String key)
    {
        for (MNodeListener l : listeners) l.childAdded (key);
    }

    public synchronized void fireChildDeleted (String key)
    {
        for (MNodeListener l : listeners) l.childDeleted (key);
    }

    public synchronized void fireChildChanged (String oldKey, String newKey)
    {
        for (MNodeListener l : listeners) l.childChanged (oldKey, newKey);
    }

    public synchronized Iterator<MNode> iterator ()
    {
        return new IteratorWrapper (new ArrayList<String> (children.keySet ()));  // Duplicate the keys, to avoid concurrent modification
    }

    public synchronized void save ()
    {
        for (MDoc doc: writeQueue) doc.save ();
        writeQueue.clear ();  // This releases the strong references, so these docs can be garbage collected if needed.
    }
}

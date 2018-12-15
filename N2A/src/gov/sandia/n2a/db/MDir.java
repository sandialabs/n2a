/*
Copyright 2016-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
    A top-level node which maps to a directory on the file system.
    Each child node maps to a file under this directory. However, the document file need not
    be a direct child of this directory. Instead, some additional pathing may be added.
    This allows the direct children of this directory to be subdirectories, and each document
    file may be a specifically-named entry in a subdirectory.
**/
public class MDir extends MNode
{
    protected String  name;    // MDirs could be held in a collection, so this provides a way to reference them.
    protected Path    root;    // The directory containing the files or subdirs that constitute the children of this node
    protected String  suffix;  // Relative path to document file, or null if documents are directly under root
    protected boolean loaded;  // Indicates that an initial read of the dir has been done. After that, it is not necessary to monitor the dir, only keep track of documents internally.

    protected NavigableMap<String,SoftReference<MDoc>> children   = new TreeMap<String,SoftReference<MDoc>> ();
    protected Set<MDoc>                                writeQueue = new HashSet<MDoc> ();  // By storing strong references to docs that need to be saved, we prevent them from being garbage collected until that is done.
    protected List<MNodeListener>                      listeners  = new ArrayList<MNodeListener> ();

    public MDir (Path root)
    {
        this (null, root, null);
    }

    public MDir (String name, Path root)
    {
        this (name, root, null);
    }

    public MDir (Path root, String suffix)
    {
        this (null, root, suffix);
    }

    public MDir (String name, Path root, String suffix)
    {
        this.name = name;
        this.root = root;
        if (suffix != null  &&  suffix.isEmpty ()) suffix = null;
        this.suffix = suffix;
        try {Files.createDirectories (root);}  // We take the liberty of forcing the dir to exist.
        catch (IOException e) {}
    }

    public String key ()
    {
        if (name == null) return root.toString ();
        return name;
    }

    public String getOrDefault (String defaultValue)
    {
        if (root == null) return defaultValue;  // This should never happen.
        return root.toAbsolutePath ().toString ();
    }

    public Path pathForChild (String index)
    {
        Path result = root.resolve (index);
        if (suffix != null) result = result.resolve (suffix);
        return result;
    }

    public static String validFilenameFrom (String name)
    {
        // TODO: This is only sufficient for Linux. What else is needed for Windows?
        name = name.replace ("\\", "-");
        name = name.replace ("/", "-");
        return name;
    }

    protected synchronized MNode getChild (String index)
    {
        if (index.isEmpty ()) return null;  // The file-existence code below can be fooled by an empty string, so explicitly guard against it.
        MDoc result = null;
        SoftReference<MDoc> reference = children.get (index);
        if (reference != null) result = reference.get ();
        if (result == null)  // We have never loaded this document, or it has been garbage collected.
        {
            Path childPath = pathForChild (index);
            if (! Files.isReadable (childPath))
            {
                if (suffix == null) return null;
                // We allow the possibility that the dir exists but lacks its special file.
                Path parentPath = childPath.getParent ();
                if (! Files.isReadable (parentPath)) return null;
            }
            result = new MDoc (this, index);
            children.put (index, new SoftReference<MDoc> (result));
        }
        return result;
    }

    /**
        Empty this directory of all files.
        This is an extremely dangerous function! It destroys all data in the directory on disk and all data pending in memory.
    **/
    public synchronized void clear ()
    {
        children.clear ();
        writeQueue.clear ();
        deleteTree (root.toAbsolutePath (), false);
        fireChanged ();
    }

    public static void deleteTree (Path start, boolean includeStartDir)
    {
        try
        {
            Files.walkFileTree (start, new SimpleFileVisitor<Path> ()
            {
                public FileVisitResult visitFile (final Path file, final BasicFileAttributes attrs) throws IOException
                {
                    Files.delete (file);
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult postVisitDirectory (final Path dir, final IOException e) throws IOException
                {
                    if (includeStartDir  ||  ! dir.equals (start)) Files.delete (dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e)
        {
        }
    }

    /**
        When suffix is defined, the entire subdirectory that contains the document will be deleted,
        including any auxiliary files.
    **/
    protected synchronized void clearChild (String index)
    {
        SoftReference<MDoc> ref = children.remove (index);
        if (ref != null) writeQueue.remove (ref.get ());
        deleteTree (root.resolve (index).toAbsolutePath (), true);
        fireChildDeleted (index);
    }

    public synchronized int size ()
	{
        String[] list = root.toFile ().list ();
        if (writeQueue.isEmpty ()) return list.length;

        Set<String> files = new HashSet<String> (Arrays.asList (list));
        for (MDoc doc : writeQueue) files.add (doc.get ());
        return files.size ();
	}

    /**
        Point to a new location on disk.
        Must be called before actually moving the dir, since we need to flush the write queue.
    **/
    public synchronized void set (Path value)
    {
        save ();
        root = value;
    }

    /**
        Creates a new MDoc in this directory if it does not already exist.
        MDocs that are children of an MDir ignore value, so it doesn't matter what is passed for that field.
    **/
    public synchronized MNode set (String index, String value)
    {
        MDoc result = (MDoc) getChild (index);
        if (result == null)  // new document
        {
            result = new MDoc (this, index);
            children.put (index, new SoftReference<MDoc> (result));

            // Set the new document to save.
            // Due to subtle interactions with MDoc.save() and load(), this will not force an empty MDoc to exist.
            // Specifically, load() on a non-existent file will clear the needsWrite flag. save() forces load()
            // in order to iterate children. OTOH, if children are added and removed before save(),
            // an empty MDoc will still be flagged to write. Whether this is a bug or a feature is debatable. The
            // net effect for the N2A application is that new models which are not actually filled will evaporate.
            result.markChanged ();

            fireChildAdded (index);
        }
        return result;
    }

    /**
        Renames an MDoc on disk.
        If you already hold a reference to the MDoc named by fromIndex, then that reference remains valid
        after the move.
    **/
    public synchronized void move (String fromIndex, String toIndex)
    {
        if (toIndex.equals (fromIndex)) return;
        save ();  // If this turns out to be too much work, then scan the write queue for fromIndex and save it directly.

        // This operation is independent of bookkeeping in children
        Path fromPath = root.resolve (fromIndex).toAbsolutePath ();
        Path toPath   = root.resolve (toIndex  ).toAbsolutePath ();
        try
        {
            if (Files.exists (toPath)) deleteTree (toPath, true);
            Files.move (fromPath, toPath, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e)
        {
            // This can happen if a new doc has not yet been flushed to disk.
        }

        SoftReference<MDoc> fromReference = children.get (fromIndex);
        SoftReference<MDoc> toReference   = children.get (toIndex);
        children.remove (fromIndex);
        children.remove (toIndex);
        if (fromReference == null)
        {
            if (toReference != null) fireChildDeleted (toIndex);  // Because we overwrote an existing node with a non-existing node, causing the destination to cease to exist.
        }
        else
        {
            MDoc from = fromReference.get ();
            if (from != null) from.name = toIndex;
            children.put (toIndex, fromReference);
            fireChildChanged (fromIndex, toIndex);
        }
    }

    public synchronized void addListener (MNodeListener listener)
    {
        listeners.add (listener);
    }

    public synchronized void removeListener (MNodeListener listener)
    {
        listeners.remove (listener);
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

    /**
        Used by repository manager to notify us about changes made directly to disk.
    **/
    public synchronized void nodeChanged (String key)
    {
        // Check if it exists on disk. If not, then this is a delete.
        if (key.isEmpty ()) return;
        Path childPath = pathForChild (key);
        if (! Files.isReadable (childPath))
        {
            children.remove (key);
            fireChildDeleted (key);
            return;
        }

        // Synchronize with updated/restored doc on disk.
        SoftReference<MDoc> reference = children.get (key);
        if (reference == null)  // added back into db
        {
            MDoc child = new MDoc (this, key);
            reference = new SoftReference<MDoc> (child);
            children.put (key, reference);
            fireChildAdded (key);
        }
        else  // reverted to previous state
        {
            MDoc child = reference.get ();
            if (child != null)
            {
                // Put doc into same state as newly-read directory entry.
                // All old children are invalid, and listeners should completely reload data.
                child.needsWrite = false;
                child.children = null;
            }
            fireChildChanged (key, key);
        }
    }

    public class IteratorWrapperSoft implements Iterator<MNode>
    {
        List<String>     keys;
        Iterator<String> iterator;
        String           key;  // of the most recent node returned by next()

        public IteratorWrapperSoft (List<String> keys)
        {
            this.keys = keys;
            iterator = keys.iterator ();
        }

        public boolean hasNext ()
        {
            return iterator.hasNext ();
        }

        /**
            If a document is deleted while the iterator is running, this could return null.
            If a document is added, it will not be included.
        **/
        public MNode next ()
        {
            key = iterator.next ();
            return getChild (key);
        }

        public void remove ()
        {
            clearChild (key);
            iterator.remove ();
        }
    }

    public synchronized Iterator<MNode> iterator ()
    {
        load ();
        return new IteratorWrapperSoft (new ArrayList<String> (children.keySet ()));  // Duplicate the keys, to avoid concurrent modification
    }

    public synchronized void load ()
    {
        if (loaded) return;

        NavigableMap<String,SoftReference<MDoc>> newChildren = new TreeMap<String,SoftReference<MDoc>> ();
        for (String index : root.toFile ().list ())  // This may cost a lot of time in some cases. However, N2A should never have more than about 10,000 models in a dir.
        {
            if (index.startsWith (".")) continue; // Filter out special files. This allows, for example, a git repo to share the models dir.
            newChildren.put (index, null);
        }
        for (Entry<String,SoftReference<MDoc>> c : children.entrySet ())
        {
            newChildren.put (c.getKey (), c.getValue ());
        }
        children = newChildren;

        loaded = true;
    }

    public synchronized void save ()
    {
        for (MDoc doc: writeQueue) doc.save ();
        writeQueue.clear ();  // This releases the strong references, so these docs can be garbage collected if needed.
    }
}

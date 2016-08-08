/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.db;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
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
	protected File    root;    // The directory containing the files or subdirs that constitute the children of this node
	protected String  suffix;  // Relative path to document file, or null if documents are directly under root
	protected boolean loaded;  // Indicates that an initial read of the dir has been done. After that, it is not necessary to monitor the dir, only keep track of documents internally.

	protected NavigableMap<String,SoftReference<MDoc>> children = new TreeMap<String,SoftReference<MDoc>> ();
	protected Set<MDoc> writeQueue = new HashSet<MDoc> ();  // By storing strong references to docs that need to be saved, we prevent them from being garbage collected until that is done.

    public MDir (File root)
    {
        this (null, root, null);
    }

	public MDir (File root, String suffix)
	{
	    this (null, root, suffix);
	}

    public MDir (String name, File root, String suffix)
    {
        this.name = name;
        this.root = root;
        this.suffix = suffix;
        root.mkdirs ();  // We take the liberty of forcing the dir to exist.
    }

	public String key ()
	{
	    if (name == null) return root.toString ();
	    return name;
	}

	public String getOrDefault (String defaultValue)
	{
	    return root.toString ();  // We crash and burn if root is null, but that should never happen.
	}

	public File pathForChild (String index)
	{
        File result = new File (root, index);
        if (suffix != null  &&  ! suffix.isEmpty ()) result = new File (result, suffix);
        return result;
	}

	public synchronized MNode child (String index)
    {
	    MDoc result = null;
	    SoftReference<MDoc> reference = children.get (index);
	    if (reference != null) result = reference.get ();
	    if (result == null)  // We have never loaded this document, or it has been garbage collected.
	    {
	        if (! pathForChild (index).canRead ()) return null;
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
        try
        {
            Files.walkFileTree (Paths.get (root.getAbsolutePath ()), new SimpleFileVisitor<Path> ()
            {
                public FileVisitResult visitFile (final Path file, final BasicFileAttributes attrs) throws IOException
                {
                    Files.delete (file);
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult postVisitDirectory (final Path dir, final IOException e) throws IOException
                {
                    if (! dir.equals (Paths.get (root.getAbsolutePath ()))) Files.delete (dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e)
        {
        }
    }

    public synchronized void clear (String index)
    {
        SoftReference<MDoc> ref = children.remove (index);
        MDoc doc = ref.get ();
        if (ref != null) writeQueue.remove (doc);
        pathForChild (index).delete ();
    }

    public synchronized int length ()
	{
        if (writeQueue.isEmpty ()) return root.list ().length;

        Set<String> files = new HashSet<String> (Arrays.asList (root.list ()));
        for (MDoc doc : writeQueue) files.add (doc.get ());
        return files.size ();
	}

    /**
        Creates a new MDoc in this directory, or renames it if it already exists.
        The reason setting the value moves an MDoc is that MDoc treats its value as its file name (in addition
        to the index, which is also its file name). These semantics are arbitrary, but they create a 
        convenient way to specify a file rename without adding another function. We simply force everything to
        be consistent with the newly-set value. In the case of a new file, the index takes precedence, so the
        value is ignored. It is sufficient to pass value="" to create a new file.
        
        @param value The destination file name for a move. Ignored if a file named by the given index does not exist.
        @param index The file name for a new document, or the source file name for a move.
     */
    public synchronized MNode set (String value, String index)
    {
        MDoc result = (MDoc) child (index);
        if (result == null)  // new document
        {
            result = new MDoc (this, index);
            children.put (index, new SoftReference<MDoc> (result));
            result.markChanged ();  // Set the new document to save
        }
        else  // existing document; move if needed
        {
            if (value == null  ||  value.isEmpty ()  ||  value.equals (index)) return result;
            result.set (value);  // MDoc does all the low-level work, including updating our children collection with the new index
            // Note: We don't need to mark this document to be saved, since we are moving an existing file on disk.
            // However, if the document currently has unsaved changes, they will eventually get written to the new location.
        }
        return result;
    }

    public synchronized void move (String fromIndex, String toIndex)
    {
        save ();  // If this turns out to be too much work, then scan the write queue for fromIndex and save it directly.

        // This operation is independent of bookkeeping in children
        Path fromPath = Paths.get (pathForChild (fromIndex).getAbsolutePath ());
        Path toPath   = Paths.get (pathForChild (toIndex  ).getAbsolutePath ());
        try
        {
            Files.deleteIfExists (toPath);
            Files.move (fromPath, toPath, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e)
        {
            System.err.println ("Failed to move file: " + fromIndex + " --> " + toIndex);
        }

        MDoc source = null;
        SoftReference<MDoc> ref = children.get (fromIndex);
        if (ref != null) source = ref.get ();
        children.remove (toIndex);
        children.remove (fromIndex);
        if (source != null)
        {
            source.name = toIndex;
            children.put (toIndex, new SoftReference<MDoc> (source));
        }
    }

    public class IteratorWrapperSoft implements Iterator<MNode>
    {
        Iterator<Entry<String,SoftReference<MDoc>>> iterator;

        public IteratorWrapperSoft (Iterator<Entry<String,SoftReference<MDoc>>> iterator)
        {
            this.iterator = iterator;
        }

        public boolean hasNext ()
        {
            return iterator.hasNext ();
        }

        public MNode next ()
        {
            Entry<String,SoftReference<MDoc>> e = iterator.next ();
            MDoc doc = e.getValue ().get ();
            if (doc == null)
            {
                doc = new MDoc (MDir.this, e.getKey ());
                e.setValue (new SoftReference<MDoc> (doc));
            }
            return doc;
        }

        public void remove ()
        {
            iterator.remove ();
        }
    }

    public synchronized Iterator<MNode> iterator ()
    {
        if (! loaded)
        {
            String[] fileNames = root.list ();  // This may cost a lot of time in some cases. However, N2A should never have more than about 10,000 models in a dir.
            for (String index : fileNames)
            {
                // This is a slightly more compact version of child(index)
                MDoc doc = null;
                SoftReference<MDoc> ref = children.get (index);
                if (ref != null) doc = ref.get ();
                if (doc == null)
                {
                    doc = new MDoc (this, index);
                    children.put (index, new SoftReference<MDoc> (doc));
                }
            }
            loaded = true;
        }
        return new IteratorWrapperSoft (children.entrySet ().iterator ());
    }

    public synchronized void save ()
    {
        for (MDoc doc: writeQueue) doc.save ();
        writeQueue.clear ();  // This releases the strong references, so these docs can be garbage collected if needed.
    }
}

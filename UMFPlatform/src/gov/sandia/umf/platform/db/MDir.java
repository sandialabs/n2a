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
	public File   root;    // The directory containing the files or subdirs that constitute the children of this node
	public String suffix;  // Relative path to document file, or null if documents are directly under root

	public NavigableMap<String,SoftReference<MDoc>> children = new TreeMap<String,SoftReference<MDoc>> ();

    public MDir (File root)
    {
        this (root, null);
    }

	public MDir (File root, String suffix)
	{
	    this.root = root;
	    this.suffix = suffix;
	    root.mkdirs ();  // We take the liberty of forcing the dir to exist.
	}

	public MNode child (String index)
    {
	    MDoc result = null;
	    SoftReference<MDoc> reference = children.get (index);
	    if (reference != null) result = reference.get ();
	    if (result == null)  // We have never loaded this document, or it has been garbage collected.
	    {
            File path = new File (index);
            if (suffix != null) path = new File (path, suffix);
	        if (! new File (root, path.getPath ()).canRead ()) return null;
	        result = new MDoc (this, path.toString ());
	        children.put (index, new SoftReference<MDoc> (result));
	    }
        return result;
    }

    public void clear ()
    {
        // Empty this directory of all files!
        children.clear ();
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

    public void clear (String index)
    {
        children.remove (index);
        Path path;
        if (suffix.isEmpty ()) path = Paths.get (root.getAbsolutePath (), index);
        else                   path = Paths.get (root.getAbsolutePath (), index, suffix);
        try
        {
            Files.delete (path);
        }
        catch (IOException e)
        {
        }
    }

    public int length ()
	{
	    return root.list ().length;
	}

	public String getDefault (String defaultValue)
    {
        return defaultValue;
    }

    public void set (String value)
    {
        // ignore
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
    public MNode set (String value, String index)
    {
        MDoc result = (MDoc) child (index);
        if (result == null)  // new document
        {
            File path = new File (index);
            if (suffix != null) path = new File (path, suffix);
            result = new MDoc (this, path.toString ());
            children.put (index, new SoftReference<MDoc> (result));
        }
        else  // existing document; move if needed
        {
            if (value.isEmpty ()  ||  value.equals (index)) return result;

            // Move the document in our internal collection.
            children.remove (index);
            children.put (value, new SoftReference<MDoc> (result));

            // Move the document on disk.
            // Don't use suffix even if it is non-empty, because in that case we want to move the whole directory named by index.
            Path oldPath = Paths.get (root.getAbsolutePath (), index);
            Path newPath = Paths.get (root.getAbsolutePath (), index);
            try
            {
                Files.move (oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException e)
            {
            }
        }
        return result;
    }

    public Iterator<Entry<String,MNode>> iterator ()
    {
        TreeMap<String,MNode> dir = new TreeMap<String,MNode> ();
        String[] fileNames = root.list ();  // This may cost a lot of time in some cases. However, N2A should never have more than about 10,000 models in a dir.
        for (String index : fileNames)
        {
            MDoc doc = new MDoc (this, index);
            children.put (index, new SoftReference<MDoc> (doc));
            dir.put (index, doc);
        }
        return dir.entrySet ().iterator ();
    }

    public void save ()
    {
        if (children == null) return;
        Set<Entry<String,SoftReference<MDoc>>> entries = children.entrySet ();
        for (Entry<String,SoftReference<MDoc>> e : entries)
        {
            MDoc doc = e.getValue ().get ();
            if (doc != null) doc.save ();
        }
    }
}

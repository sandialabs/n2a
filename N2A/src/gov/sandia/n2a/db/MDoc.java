/*
Copyright 2016,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.TreeMap;

/**
    Stores a document in memory and coordinates with its persistent form on disk.
    We assume that only one instance of this class exists for a given disk document
    at any given moment, and that no other process in the system modifies the file on disk.

    We inherit the value field from MVolatile. Since we don't really have a direct value,
    we store a copy of the file name there. This is used to implement several functions, such
    as renaming. It also allows an instance to stand alone, without being a child of an MDir.

    We inherit the children collection from MVolatile. This field is left null until we first
    read in the associated file on disk. If the file is empty or non-existent, children becomes
    non-null but empty. Thus, whether children is null or not safely indicates the need to load.
**/
public class MDoc extends MPersistent
{
    Schema schema = new Schema (1, "");

    /**
        Constructs a document as a child of an MDir.
        In this case, the key contains the file name in the dir, and the full path is constructed
        when needed using information from the parent.
    **/
    public MDoc (MDir parent, String index)
    {
        this (parent, index, null);
    }

    /**
        Constructs a stand-alone document.
        In this case, the value contains the full path to the file on disk.
    **/
    public MDoc (String path)
    {
        this (null, null, path);
    }

    public MDoc (MDir parent, String name, String path)
    {
        super (null, name, path);  // We cheat a little here, since MDir is not an MPersistent.
        this.parent = parent;
    }

    /**
        The value of an MDoc is defined to be its full path on disk.
        Note that the key for an MDoc depends on what kind of collection contains it.
        In an MDir, the key is the primary file name (without path prefix and suffix).
        For a stand-alone document the key is arbitrary, and the document may be stored
        in another MNode with arbitrary other objects.
    **/
    public synchronized String getOrDefault (String defaultValue)
    {
        if (parent == null)
        {
            if (value == null) return defaultValue;
            return value;
        }

        return ((MDir) parent).pathForChild (name).getAbsolutePath ();
    }

    public synchronized void markChanged ()
    {
        if (! needsWrite)
        {
            if (parent != null)
            {
                synchronized (parent)
                {
                    ((MDir) parent).writeQueue.add (this);
                }
            }
            needsWrite = true;
        }
    }

    /**
        Removes this document from persistent storage, but retains its contents in memory.
    **/
    public void delete ()
    {
        if (parent == null)
        {
            try
            {
                Files.delete (Paths.get (value));
            }
            catch (IOException e)
            {
                System.err.println ("Failed to delete file: " + value);
            }
        }
        else parent.clear (name);
    }

    public synchronized MNode child (String index)
    {
        if (children == null) load ();  // redundant with the guard in load(), but should save time in the common case that file is already loaded
        return children.get (index);
    }

    public synchronized void clear (String index)
    {
        if (children == null) load ();
        super.clear (index);
    }

    public synchronized int length ()
    {
        if (children == null) load ();
        return children.size ();
    }

    /**
        If this is a stand-alone document, then moves the file on disk.
        Otherwise, does nothing.
    **/
    public synchronized void set (String value)
    {
        if (parent != null) return;  // Alternately, we could parse out the "index" portion of the file name, but this use-case will never occur.
        try
        {
            Files.move (Paths.get (this.value), Paths.get (value), StandardCopyOption.REPLACE_EXISTING);
            this.value = value;
        }
        catch (IOException e)
        {
            System.err.println ("Failed to move file: " + this.value + " --> " + value);
        }
    }

    public synchronized MNode set (String index, String value)
    {
        if (children == null) load ();
        return super.set (index, value);
    }

    public synchronized void move (String fromIndex, String toIndex)
    {
        if (children == null) load ();
        super.move (fromIndex, toIndex);
    }

    public synchronized Iterator<MNode> iterator ()
    {
        if (children == null) load ();
        return super.iterator ();
    }

    public synchronized File path ()
    {
        if (parent == null) return new File (value);
        return ((MDir) parent).pathForChild (name);
    }

	/**
        We only load once. We assume no other process is modifying the files, so once loaded, we know its exact state.
	**/
	public synchronized void load ()
	{
	    if (children != null) return;  // already loaded
	    children = new TreeMap<String,MNode> (comparator);
        File file = path ();
        try
        {
            needsWrite = true;  // lie to ourselves, to prevent being put onto the MDir write queue
            schema.readAll (new FileReader (file), this);
        }
        catch (IOException e)
        {
            // This exception is common for a newly created doc that has not yet been flushed to disk.
        }
        clearChanged ();  // After load(), clear the slate so we can detect any changes and save the document.
	}

	public synchronized void save ()
	{
	    if (! needsWrite) return;
        File file = path ();
	    try
	    {
	        file.getParentFile ().mkdirs ();
	        FileWriter writer = new FileWriter (file);
	        schema.writeAll (writer, this);
	        writer.close ();
	        clearChanged ();
	    }
	    catch (IOException e)
	    {
            System.err.println ("Failed to write file: " + file);
	    }
	}
}

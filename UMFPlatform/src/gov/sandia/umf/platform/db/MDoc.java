/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.db;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.TreeMap;

/**
    Stores a document in memory and coordinates with its persistent form on disk.
    We assume that only one instance of this class exists for a given disk document
    at any given moment, and that no other process in the system modifies the file on disk.
**/
public class MDoc extends MPersistent
{
    // Note: MNodeRAM.value stores the path, since we don't allow a document node to have a top-level value.

    boolean needsWrite; // indicates that this node has changed since it was last read from disk (and therefore should be written out again)

    // Note: "needRead" is indicated by whether the MNodeRAM.children collection is null. If it is non-null, the read has happened.
    // If children exists but is empty, then either the file was actually empty or it does not yet exist.

	public MDoc (MDir parent, File path)
	{
	    super (null, path.toString ());
	    this.parent = parent;
	}

    public void markChanged ()
    {
        needsWrite = true;
    }

    public MNode child (String index)
    {
        if (children == null) load ();  // redundant with the guard in load(), but should save time in the common case that file is already loaded
        return children.get (index);
    }

    public MNode set (String value, String index)
    {
        if (children == null) load ();  // see comment in child(String)
        return super.set (value, index);
    }

	/**
        We only load once. We assume no other process is modifying the files, so once loaded, we know its exact state.
	**/
	public void load ()
	{
	    if (children != null) return;  // already loaded
	    if (value == null) return;  // we have no file name, so can't load
	    children = new TreeMap<String,MNode> ();
        try
        {
            BufferedReader reader = new BufferedReader (new FileReader (new File (value)));
            String line = reader.readLine ().trim ();
            String[] pieces = line.split ("=", 2);
            if (pieces.length < 2  ||  ! pieces[0].equals ("N2A.schema")  ||  ! pieces[1].equals ("1"))
            {
                System.err.println ("WARNING: schema version not recognized. Proceeding as if it were.");
                // Note that we may have just destroyed an important line of input in the process.
            }
            read (reader);
            reader.close ();
            needsWrite = false;  // needsWrite will get set true during normal load process
        }
        catch (IOException e)
        {
            System.err.println ("Failed to read file: " + value);
        }
	}

	public void save ()
	{
	    if (! needsWrite) return;
	    try
	    {
	        BufferedWriter writer = new BufferedWriter (new FileWriter (new File (value)));
	        writer.write (String.format ("N2A.schema=1%n"));
	        write (writer, "");
	        writer.close ();
	        needsWrite = false;
	    }
	    catch (IOException e)
	    {
            System.err.println ("Failed to write file: " + value);
	    }
	}

	protected void finalize () throws Throwable
    {
	    // If we get garbage collected, due to the soft reference in MNodeDir.children, then push out any unsaved changes.
	    // The normal path for saving is via an explicit shutdown procedure or a background save thread.
	    // In those two cases, the reference has not been cleared.
	    save ();
    }
}

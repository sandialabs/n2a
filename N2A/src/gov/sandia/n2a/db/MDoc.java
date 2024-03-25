/*
Copyright 2016-2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.TreeMap;

import gov.sandia.n2a.ui.settings.SettingsLookAndFeel;

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
    /**
        Constructs a stand-alone document with blank key.
        In this case, the value contains the full path to the file on disk.
    **/
    public MDoc (Path path)
    {
        this (null, path.toAbsolutePath ().toString (), null);
    }

    /**
        Constructs a stand-alone document with specified key.
        In this case, the value contains the full path to the file on disk.
    **/
    public MDoc (Path path, String key)
    {
        this (null, path.toAbsolutePath ().toString (), key);
    }

    protected MDoc (MDocGroup parent, String path, String key)
    {
        super (parent, path, key);
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
        if (parent instanceof MDocGroup) return ((MDocGroup) parent).pathForDoc (name).toAbsolutePath ().toString ();
        if (value == null) return defaultValue;
        return value.toString ();
    }

    public synchronized void markChanged ()
    {
        if (needsWrite) return;

        // If this is a new document, then treat it as if it were already loaded.
        // If there is content on disk, it will be blown away.
        if (children == null) children = new TreeMap<String,MNode> (comparator);
        needsWrite = true;
        if (parent instanceof MDocGroup)
        {
            synchronized (parent)
            {
                ((MDocGroup) parent).writeQueue.add (this);
            }
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
                Files.delete (Paths.get (value.toString ()));
            }
            catch (IOException e)
            {
                System.err.println ("Failed to delete file: " + value);
            }
        }
        else parent.clear (name);
    }

    protected synchronized MNode getChild (String key)
    {
        if (children == null) load ();  // redundant with the guard in load(), but should save time in the common case that file is already loaded
        return children.get (key);
    }

    protected synchronized void clearChild (String key)
    {
        if (children == null) load ();
        super.clearChild (key);
    }

    public synchronized int size ()
    {
        if (children == null) load ();
        return children.size ();
    }

    /**
        If this is a stand-alone document, then move the file on disk.
        Otherwise, do nothing. DeleteDoc.undo() relies on this class to do nothing for a regular doc,
        because it uses merge() to restore the data, and merge() will touch the root value.
    **/
    public synchronized void set (String value)
    {
        if (parent != null) return;  // Not stand-alone, so ignore.
        if (value.equals (this.value)) return;  // Don't call file move if location on disk has not changed.
        try
        {
            Files.move (Paths.get (this.value.toString ()), Paths.get (value), StandardCopyOption.REPLACE_EXISTING);
            this.value = value;
        }
        catch (IOException e)
        {
            System.err.println ("Failed to move file: " + this.value + " --> " + value);
        }
    }

    public synchronized MNode set (String value, String key)
    {
        if (children == null) load ();
        return super.set (value, key);
    }

    public synchronized void move (String fromKey, String toKey)
    {
        if (toKey.equals (fromKey)) return;
        if (children == null) load ();
        super.move (fromKey, toKey);
    }

    public synchronized Iterator<MNode> iterator ()
    {
        if (children == null) load ();
        return super.iterator ();
    }

    public synchronized Path path ()
    {
        if (parent instanceof MDocGroup) return ((MDocGroup) parent).pathForDoc (name);
        return Paths.get (value.toString ());  // for stand-alone document
    }

	/**
        We only load once. We assume no other process is modifying the files, so once loaded, we know its exact state.
	**/
	public synchronized void load ()
	{
	    if (children != null) return;  // already loaded
	    children = new TreeMap<String,MNode> (comparator);
        Path file = path ();
        needsWrite = true;  // lie to ourselves, to prevent being put onto the MDir write queue
        int version = -1;
        try (BufferedReader br = Files.newBufferedReader (file))
        {
            version = Schema.readAll (this, br).version;
        }
        catch (IOException e) {}  // This exception is common for a newly created doc that has not yet been flushed to disk.
        clearChanged ();  // After load(), clear the slate so we can detect any changes and save the document.
        if (version == 2)
        {
            System.out.println ("converting: " + file);
            visit (new ConvertSchema2 ());  // Positioned after clearChanged() so that updates get written back to db.
            markChanged ();  // Force a save in the new schema, even if doc is otherwise unmodified. This stops us from re-checking the doc each time it is loaded.
        }
	}

	public synchronized void save ()
	{
	    if (! needsWrite) return;
        Path file = path ();
	    try
	    {
	        Files.createDirectories (file.getParent ());
	        try (BufferedWriter writer = Files.newBufferedWriter (file))
	        {
	            Schema.latest ().writeAll (this, writer);
	            clearChanged ();
	        }
	    }
	    catch (IOException e)
	    {
            System.err.println ("Failed to write file: " + file);
            e.printStackTrace ();
	    }
	}

    /**
        Upgrade this document from schema 2.
        Specifically, the semantics/names of some entries have changed.
        This function will be called on all documents as they are opened, so
        it does not distinguish between parts/models and other kinds of docs.
        Instead, we assume that certain patterns are so unique that they only
        appear in parts/models.
    **/
	protected class ConvertSchema2 implements Visitor
	{
	    public double em = SettingsLookAndFeel.em;

        public void convert (MNode parent, String key)
	    {
	        MNode c = parent.child (key);
	        if (c == null) return;
	        double value = c.getDouble () / em;
	        c.setTruncated (value, 2);
	    }

	    public boolean visit (MNode node)
        {
            String key   = node.key ();
            String value = node.get ();

            // Models
            if (value.equals ("$kill")  ||  key.startsWith ("@")  &&  value.isBlank ()  &&  node.data ())  // revoked variable or equation
            {
                node.set (null);
                node.set ("", "$kill");
                return false;  // A revocation should always be a leaf node.
            }
            if (key.equals ("$metadata"))
            {
                MNode gui = node.child ("gui");
                if (gui != null)
                {
                    MNode bounds = gui.child ("bounds");
                    if (bounds != null)
                    {
                        convert (bounds, "x");
                        convert (bounds, "y");
                        convert (bounds, "width");
                        convert (bounds, "height");

                        MNode open = bounds.child ("open");
                        if (open != null)
                        {
                            convert (open, "width");
                            convert (open, "height");
                        }

                        MNode parent = bounds.child ("parent");
                        if (parent != null)
                        {
                            parent.clear ("x");
                            parent.clear ("y");
                            if (parent.isEmpty ())
                            {
                                if (! parent.getBoolean ()) bounds.clear ("parent");
                            }
                            else
                            {
                                convert (parent, "width");
                                convert (parent, "height");
                            }
                        }
                    }

                    MNode pinBounds = gui.child ("pin", "bounds");
                    if (pinBounds != null)
                    {
                        MNode in = pinBounds.child ("in");
                        if (in != null)
                        {
                            convert (in, "x");
                            convert (in, "y");
                        }

                        MNode out = pinBounds.child ("out");
                        if (out != null)
                        {
                            convert (out, "x");
                            convert (out, "y");
                        }
                    }
                }

                node.parent ().move ("$metadata", "$meta");
                return false;
            }
            if (key.equals ("$reference"))
            {
                node.parent ().move ("$reference", "$ref");
                return false;
            }
            if (value.startsWith (":"))
            {
                node.set (";" + value.substring (1));
                return false;
            }

            // Application state
            if (key.equals ("WinLayout"))
            {
                convert (node, "x");
                convert (node, "y");
                convert (node, "width");
                convert (node, "height");
                return false;
            }
            if (key.equals ("PanelModel"))
            {
                convert (node, "divider");
                convert (node, "dividerMRU");
                MNode view = node.child ("view");
                if (view != null)
                {
                    convert (view, "1"); // SIDE
                    convert (view, "2"); // BOTTOM
                }
                return false;
            }
            if (key.equals ("PanelReference"))
            {
                convert (node, "divider");
                return false;
            }
            if (key.equals ("PanelRun"))
            {
                convert (node, "divider");
                return false;
            }

            return true;
        }
	}
}

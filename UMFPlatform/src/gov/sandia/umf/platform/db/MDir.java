/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.db;

import java.io.File;
import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NavigableMap;
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
	File   root;    // The directory containing the files or subdirs that constitute the children of this node
	String suffix;  // Relative path to document file, or null if documents are directly under root

	NavigableMap<String,SoftReference<MDoc>> children;

    public MDir (File root)
    {
        this (root, null);
    }

	public MDir (File root, String suffix)
	{
	    this.root = root;
	    this.suffix = suffix;
	}

	public MNode child (String index)
    {
	    MDoc result = null;
	    if (children != null) result = children.get (index).get ();
	    if (result == null)  // We have never loaded this document, or it has been garbage collected.
	    {
	        File path = new File (root, index);
	        if (suffix != null) path = new File (path, suffix);
	        if (! path.canRead ()) return null;
	        result = new MDoc (this, path);

	        // Now create and store a new doc
	        if (children == null) children = new TreeMap<String,SoftReference<MDoc>> ();
	        children.put (index, new SoftReference<MDoc> (result));
	    }
        return result;
    }

	public int length ()
	{
	    return root.list ().length;
	}

	public String get (String defaultValue)
    {
        return defaultValue;
    }

    public void set (String value)
    {
        // ignore
    }

    public MNode set (String value, String index)
    {
        if (children == null) children = new TreeMap<String,SoftReference<MDoc>> ();

        File path = new File (root, index);
        if (suffix != null) path = new File (path, suffix);
        MDoc result = new MDoc (this, path);

        if (children == null) children = new TreeMap<String,SoftReference<MDoc>> ();
        children.put (index, new SoftReference<MDoc> (result));
        return result;
    }

    public Iterator<Entry<String,MNode>> iterator ()
    {
        TreeMap<String,MNode> dir = new TreeMap<String,MNode> ();
        File[] files = root.listFiles ();  // This may cost a lot of time in some cases. However, N2A should never have more than about 10,000 models in a dir.
        if (files.length > 0  &&  children == null) children = new TreeMap<String,SoftReference<MDoc>> ();
        for (File f : files)
        {
            MDoc doc = new MDoc (this, f);
            String index = f.getName ();
            children.put (index, new SoftReference<MDoc> (doc));
            dir.put (index, doc);
        }
        return dir.entrySet ().iterator ();
    }
}

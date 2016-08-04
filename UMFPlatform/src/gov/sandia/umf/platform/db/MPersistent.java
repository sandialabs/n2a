/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.db;

import java.util.TreeMap;

public class MPersistent extends MVolatile
{
    protected MNode parent;
    protected boolean needsWrite; // indicates that this node is new or has changed since it was last read from disk (and therefore should be written out)

    public MPersistent (MPersistent parent)
	{
        this.parent = parent;
	}

	public MPersistent (MPersistent parent, String value)
	{
	    super (value);
	    this.parent = parent;
	}

    public MPersistent (MPersistent parent, String name, String value)
    {
        super (name, value);
        this.parent = parent;
    }

    /**
        Return the highest-level MPersistent that contains us.
        Generally, this will be an MDoc.
    **/
	public MNode getRoot ()
	{
	    MPersistent result = this;
	    while (result.parent instanceof MPersistent) result = (MPersistent) result.parent;
	    // Since MDir is not an MPersistent, the loop should stop on the top-level MDoc, which is what we really want.
	    return result;
	}

	public synchronized void markChanged ()
	{
	    if (! needsWrite)
	    {
	        ((MPersistent) parent).markChanged ();
	        needsWrite = true;
	    }
	}

	public synchronized void clearChanged ()
	{
	    needsWrite = false;
        for (MNode i : this)
        {
            ((MPersistent) i).clearChanged ();
        }
	}

	public synchronized void clear ()
    {
        super.clear ();
        markChanged ();
    }

	public synchronized void set (String value)
    {
        if (value.isEmpty ())
        {
            if (this.value != null)
            {
                this.value = null;
                markChanged ();
            }
        }
        else
        {
            if (! this.value.equals (value))
            {
                this.value = value;
                markChanged ();
            }
        }
    }

    public synchronized MNode set (String value, String index)
    {
        if (children == null)
        {
            markChanged ();
            children = new TreeMap<String,MNode> (comparator);
            MNode result = new MPersistent (this, index, value);
            children.put (index, result);
            return result;
        }
        MNode result = children.get (index);
        if (result == null)
        {
            markChanged ();
            result = new MPersistent (this, index, value);
            children.put (index, result);
            return result;
        }
        result.set (value);
        return result;
    }
}

/*
Copyright 2016-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.util.TreeMap;

public class MPersistent extends MVolatile
{
    protected boolean needsWrite; // indicates that this node is new or has changed since it was last read from disk (and therefore should be written out)

    public MPersistent (MNode parent)
	{
        this.parent = parent;
	}

	public MPersistent (MNode parent, String value)
	{
	    super (value);
	    this.parent = parent;
	}

    public MPersistent (MNode parent, String name, String value)
    {
        super (value, name);
        this.parent = parent;
    }

	public synchronized void markChanged ()
	{
	    if (! needsWrite)
	    {
	        if (parent instanceof MPersistent) ((MPersistent) parent).markChanged ();
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

    protected synchronized void clearChild (String index)
    {
        super.clearChild (index);
        markChanged ();
    }

	public synchronized void set (String value)
    {
        if (value == null)
        {
            if (this.value != null)
            {
                this.value = null;
                markChanged ();
            }
        }
        else
        {
            if (this.value == null  ||  ! this.value.equals (value))
            {
                this.value = value;
                markChanged ();
            }
        }
    }

    public synchronized MNode set (String value, String index)
    {
        if (children == null) children = new TreeMap<String,MNode> (comparator);
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

    public synchronized void move (String fromIndex, String toIndex)
    {
        if (toIndex.equals (fromIndex)) return;
        if (children == null) return;  // Nothing to move
        MNode source = children.get (fromIndex);
        children.remove (toIndex);
        children.remove (fromIndex);
        if (source != null)
        {
            children.put (toIndex, source);
            MPersistent p = (MPersistent) source;  // We can safely assume any child is MPersistent.
            p.name = toIndex;
            p.markChanged ();
        }
        markChanged ();
    }
}

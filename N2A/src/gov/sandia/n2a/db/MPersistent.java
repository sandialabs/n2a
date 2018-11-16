/*
Copyright 2016 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

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

    public MNode parent ()
    {
        return parent;
    }

    public MNode getRoot ()
    {
        MPersistent result = this;
        while (result.parent instanceof MPersistent) result = (MPersistent) result.parent;
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

    public synchronized void clear (String index)
    {
        super.clear (index);
        markChanged ();
    }

	public synchronized void set (String value)
    {
        if (value == null  ||  value.isEmpty ())
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

    public synchronized MNode set (String index, String value)
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
            MPersistent p = (MPersistent) source;
            p.name = toIndex;
            p.markChanged ();  // Cast should be a safe assumption
        }
        markChanged ();
    }
}

/*
Copyright 2016-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.TreeMap;

public class MVolatile extends MNode
{
    protected String                     name;
	protected String                     value;
    protected MNode                      parent;
	protected NavigableMap<String,MNode> children;

	public MVolatile ()
	{
	}

	public MVolatile (String value)
	{
	    if (value != null  &&  ! value.isEmpty ()) this.value = value;
	}

    public MVolatile (String name, String value)
    {
        if (name  != null  &&  ! name .isEmpty ()) this.name  = name;
        if (value != null  &&  ! value.isEmpty ()) this.value = value;
    }

    public MVolatile (String name, String value, MNode parent)
    {
        if (name  != null  &&  ! name .isEmpty ()) this.name  = name;
        if (value != null  &&  ! value.isEmpty ()) this.value = value;
        this.parent = parent;
    }

    public String key ()
    {
        if (name == null) return "";
        return name;
    }

    public MNode parent ()
    {
        return parent;
    }

    public MNode getRoot ()
    {
        MVolatile result = this;
        while (result.parent instanceof MVolatile) result = (MVolatile) result.parent;
        return result;
    }

	protected synchronized MNode getChild (String index)
    {
        if (children == null) return null;
        return children.get (index);
    }

	public synchronized void clear ()
	{
	    if (children != null) children.clear ();
	}

    protected synchronized void clearChild (String index)
    {
        if (children == null) return;
        children.remove (index);
    }

	public synchronized int size ()
	{
	    if (children == null) return 0;
	    return children.size ();
	}

	public synchronized String getOrDefault (String defaultValue)
    {
        if (value == null) return defaultValue;
        return value;
    }

    public synchronized void set (String value)
    {
        if (value.isEmpty ()) this.value = null;
        else                  this.value = value;
    }

    public synchronized MNode set (String value, String index)
    {
        if (children == null) children = new TreeMap<String,MNode> (comparator);
        MNode result = children.get (index);
        if (result == null)
        {
            result = new MVolatile (index, value, this);
            children.put (index, result);
            return result;
        }
        result.set (value);
        return result;
    }

    public synchronized void move (String fromIndex, String toIndex)
    {
        if (children == null) children = new TreeMap<String,MNode> (comparator);
        MNode source = children.get (fromIndex);
        children.remove (toIndex);
        children.remove (fromIndex);
        if (source != null)
        {
            ((MVolatile) source).name = toIndex;  // If this cast ceases to be a safe assumption, then the M hierarchy needs re-design.
            children.put (toIndex, source);
        }
    }

    public synchronized Iterator<MNode> iterator ()
    {
        if (children == null) return super.iterator ();
        return new IteratorWrapper (new ArrayList<String> (children.keySet ()));  // Duplicate the keys, to avoid concurrent modification
    }
}

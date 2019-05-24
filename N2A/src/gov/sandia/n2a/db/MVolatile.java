/*
Copyright 2016-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
	protected Object                     value;
    protected MNode                      parent;
	protected NavigableMap<String,MNode> children;

	public MVolatile ()
	{
	}

	public MVolatile (String value)
	{
	    this.value = value;
	}

    public MVolatile (String value, String name)
    {
        this.name  = name;
        this.value = value;
    }

    public MVolatile (String value, String name, MNode parent)
    {
        this.name   = name;
        this.value  = value;
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

    /**
        @return The most distant ancestor that is still an MVolatile.
    **/
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

	public boolean data ()
	{
	    return value != null;
	}

	public synchronized String getOrDefault (String defaultValue)
    {
        if (value == null) return defaultValue;
        return value.toString ();
    }

    public synchronized Object getOrDefaultObject (Object defaultValue, Object... indices)
    {
        String[] stringIndices = new String[indices.length];
        for (int i = 0; i < indices.length; i++) stringIndices[i] = indices[i].toString ();
        MVolatile c = (MVolatile) child (stringIndices);
        if (c == null  ||  c.value == null) return defaultValue;
        return c.value;
    }

    public synchronized Object getObject (Object... indices)
    {
        return getOrDefaultObject (null, indices);
    }

    public synchronized void set (String value)
    {
        this.value = value;
    }

    public synchronized MNode set (String value, String index)
    {
        if (children == null) children = new TreeMap<String,MNode> (comparator);
        MNode result = children.get (index);
        if (result == null)
        {
            result = new MVolatile (value, index, this);
            children.put (index, result);
            return result;
        }
        result.set (value);
        return result;
    }

    /**
        Store the value as an Object, rather than converting to String.
    **/
    public synchronized MNode setObject (Object value, Object... indices)
    {
        String[] stringIndices = new String[indices.length];
        for (int i = 0; i < indices.length; i++) stringIndices[i] = indices[i].toString ();
        MVolatile result = (MVolatile) childOrCreate (stringIndices);
        result.value = value;
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

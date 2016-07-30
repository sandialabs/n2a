/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.db;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

public class MVolatile extends MNode
{
	protected String value;
	protected NavigableMap<String,MNode> children;

	public MVolatile ()
	{
	}

	public MVolatile (String value)
	{
	    if (! value.isEmpty ()) this.value = value;
	}

	public synchronized MNode child (String index)
    {
        if (children == null) return null;
        return children.get (index);
    }

	public synchronized void clear ()
	{
	    if (children != null) children.clear ();
	}

    public synchronized void clear (String index)
    {
        if (children == null) return;
        children.remove (index);
    }

	public synchronized int length ()
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
        if (children == null)
        {
            children = new TreeMap<String,MNode> (comparator);
            MNode result = new MVolatile (value);
            children.put (index, result);
            return result;
        }
        MNode result = children.get (index);
        if (result == null)
        {
            result = new MVolatile (value);
            children.put (index, result);
            return result;
        }
        result.set (value);
        return result;
    }

    public synchronized Iterator<Entry<String,MNode>> iterator ()
    {
        if (children == null) return new MNode.IteratorEmpty ();
        return children.entrySet ().iterator ();
    }
}

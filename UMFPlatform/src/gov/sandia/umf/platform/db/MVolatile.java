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
    protected String name;
	protected String value;
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

	public String key ()
	{
	    if (name == null) return "";
	    return name;
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
            MNode result = new MVolatile (index, value);
            children.put (index, result);
            return result;
        }
        MNode result = children.get (index);
        if (result == null)
        {
            result = new MVolatile (index, value);
            children.put (index, result);
            return result;
        }
        result.set (value);
        return result;
    }

    public static class IteratorWrapper implements Iterator<MNode>
    {
        Iterator<Entry<String,MNode>> iterator;

        public IteratorWrapper (Iterator<Entry<String,MNode>> iterator)
        {
            this.iterator = iterator;
        }

        public boolean hasNext ()
        {
            return iterator.hasNext ();
        }

        public MNode next ()
        {
            return iterator.next ().getValue ();
        }

        public void remove ()
        {
            iterator.remove ();
        }
    }

    public synchronized Iterator<MNode> iterator ()
    {
        if (children == null) return new MNode.IteratorEmpty ();
        return new IteratorWrapper (children.entrySet ().iterator ());
    }
}

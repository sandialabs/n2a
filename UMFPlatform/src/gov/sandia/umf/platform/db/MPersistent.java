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
    public MNode parent;

    public MPersistent (MPersistent parent)
	{
        this.parent = parent;
	}

	public MPersistent (MPersistent parent, String value)
	{
	    super (value);
	    this.parent = parent;
	}

	public void markChanged ()
	{
	    ((MPersistent) parent).markChanged ();
	}

    public void clear ()
    {
        super.clear ();
        markChanged ();
    }

	public void set (String value)
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

    public MNode set (String value, String index)
    {
        if (children == null)
        {
            markChanged ();
            children = new TreeMap<String,MNode> (comparator);
            MNode result = new MPersistent (this, value);
            children.put (index, result);
            return result;
        }
        MNode result = children.get (index);
        if (result == null)
        {
            markChanged ();
            result = new MPersistent (this, value);
            children.put (index, result);
            return result;
        }
        result.set (value);
        return result;
    }
}

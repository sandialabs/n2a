/*
Copyright 2016-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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

    public MVolatile (String name)
    {
        this.name = name;
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

    protected synchronized MNode getChild (String key)
    {
        if (children == null) return null;
        return children.get (key);
    }

    public synchronized void clear ()
    {
        if (children != null) children.clear ();
    }

    protected synchronized void clearChild (String key)
    {
        if (children == null) return;
        children.remove (key);
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
        if (value instanceof Boolean) return ((Boolean) value) ? "1" : "0";
        String result = value.toString ();
        if (result.isEmpty ()) return defaultValue;
        return result;
    }

    public synchronized Object getOrDefaultObject (Object defaultValue, Object... keys)
    {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) stringKeys[i] = keys[i].toString ();
        MVolatile c = (MVolatile) child (stringKeys);
        if (c == null  ||  c.value == null) return defaultValue;
        return c.value;
    }

    public synchronized Object getObject (Object... keys)
    {
        return getOrDefaultObject (null, keys);
    }

    public synchronized void set (String value)
    {
        this.value = value;
    }

    public synchronized MNode set (String value, String key)
    {
        if (children == null) children = new TreeMap<String,MNode> (comparator);
        MNode result = children.get (key);
        if (result == null)
        {
            result = new MVolatile (value, key, this);
            children.put (key, result);
            return result;
        }
        result.set (value);
        return result;
    }

    /**
        Store the value as an Object, rather than converting to String.
    **/
    public synchronized MNode setObject (Object value, Object... keys)
    {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) stringKeys[i] = keys[i].toString ();
        MVolatile result = (MVolatile) childOrCreate (stringKeys);
        result.value = value;
        return result;
    }

    /**
        Adds the given node to our list of children.
        This can be thought of as a symbolic link.
        The node establishes no special relationship with this parent.
        In particular, it can still be the child of another parent, including
        one that it has a special connection with, such as an MDir.
    **/
    public synchronized void link (MNode node)
    {
        if (children == null) children = new TreeMap<String,MNode> (comparator);
        children.put (node.key (), node);
    }

    /**
        If you already hold a reference to the node named by fromKey, then that reference remains
        valid and its key is updated.
    **/
    public synchronized void move (String fromKey, String toKey)
    {
        if (toKey.equals (fromKey)) return;
        if (children == null) return;  // Nothing to move
        children.remove (toKey);
        MNode source = children.get (fromKey);
        if (source != null)
        {
            children.remove (fromKey);
            if      (source instanceof MVolatile) ((MVolatile) source).name = toKey;
            else if (source instanceof MDocGroup) ((MDocGroup) source).name = toKey;
            else if (source instanceof MCombo)    ((MCombo)    source).name = toKey;
            children.put (toKey, source);
        }
    }

    public synchronized Iterator<MNode> iterator ()
    {
        if (children == null) return super.iterator ();
        return new IteratorWrapper (new ArrayList<String> (children.keySet ()));  // Duplicate the keys, to avoid concurrent modification
    }
}

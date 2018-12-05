/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
    Presents several different sets of MPersistent children as a single set.
    The children continue to point to their original parent, so they are stored properly.
**/
public class MCombo extends MNode
{
    protected String      name;
    protected List<MNode> containers;
	protected boolean     loaded;
	protected MNode       primary;  // The one source we are allowed to modify. Always set to first object in sources.

	protected NavigableMap<String,MNode> children = new TreeMap<String,MNode> ();  // from key to source; requires second lookup to retrieve actual child

    public MCombo (String name, List<MNode> containers)
    {
        this.name = name;
        init (containers);
    }

    public synchronized void init (List<MNode> containers)
    {
        this.containers = containers;
        if (containers.size () > 0) primary = containers.get (0);
        else                        primary = new MVolatile ();
        children.clear ();
        loaded = false;
    }

    public String key ()
    {
        if (name == null) return "";
        return name;
    }

    public synchronized Map<String,MNode> getContainerMap ()
    {
        Map<String,MNode> result = new TreeMap<String,MNode> ();
        for (MNode c : containers) result.put (c.key (), c);
        return result;
    }

    public boolean isWriteable (MNode doc)
    {
        return doc.parent () == primary;
    }

    public synchronized boolean isVisible (MNode doc)
    {
        if (doc == null) return false;
        String key = doc.key ();
        for (MNode c : containers)
        {
            MNode child = c.child (key);
            if (child != null) return doc == child;
        }
        return false;
    }

    protected synchronized MNode getChild (String index)
    {
        load ();
        MNode container = children.get (index);
        if (container == null) return null;
        return container.child (index);
    }

    public synchronized void clear ()
    {
        // This does not remove the original objects, only our links to them.
        containers.clear ();
        children  .clear ();
    }

    protected synchronized void clearChild (String index)
    {
        // This actually removes the original object.
        load ();
        MNode container = children.get (index);
        if (container == primary)
        {
            children.remove (index);
            container.clear (index);
        }
    }

    public synchronized int size ()
	{
        load ();
        return children.size ();
	}

    public synchronized MNode set (String index, String value)
    {
        load ();
        children.put (index, primary);
        return primary.set (index, value);
    }

    /**
        Renames an MDoc on disk.
        If you already hold a reference to the MDoc named by fromIndex, then that reference remains valid
        after the move.
    **/
    public synchronized void move (String fromIndex, String toIndex)
    {
        load ();
        MNode container = children.get (fromIndex);
        if (container != primary) return;

        primary.move (fromIndex, toIndex);
        children.remove (fromIndex);
        children.put (toIndex, primary);

        // Check if the move exposed a model of the same name from another repo.
        // It shouldn't hurt anything to do this check on the primary repo, since the old model should be gone from there.
        for (MNode c : containers)
        {
            MNode child = c.child (fromIndex);
            if (child != null)
            {
                children.put (fromIndex, child);
                break;
            }
        }
    }

    public class IteratorCombo implements Iterator<MNode>
    {
        List<String>     keys;
        Iterator<String> iterator;
        String           key;  // of most recent node returned by next()

        public IteratorCombo (List<String> keys)
        {
            this.keys = keys;
            iterator = keys.iterator ();
        }

        public boolean hasNext ()
        {
            return iterator.hasNext ();
        }

        /**
            If a document is deleted while the iterator is running, this could return null.
            If a document is added, it will not be included.
        **/
        public MNode next ()
        {
            key = iterator.next ();
            return getChild (key);
        }

        public void remove ()
        {
            clearChild (key);
            iterator.remove ();
        }
    }

    public synchronized Iterator<MNode> iterator ()
    {
        load ();
        return new IteratorCombo (new ArrayList<String> (children.keySet ()));  // Duplicate the keys, to avoid concurrent modification
    }

    public synchronized void save ()
    {
        for (MNode c : containers) if (c instanceof MDir) ((MDir) c).save ();
    }

    public synchronized void load ()
    {
        if (loaded) return;
        for (int i = containers.size () - 1; i >= 0; i--)
        {
            MNode container = containers.get (i);
            if (container instanceof MDir)  // Avoid forcing the load of every file!
            {
                MDir dir = (MDir) container;
                dir.load ();
                for (String key : dir.children.keySet ()) children.put (key, container);
            }
            else  // General case
            {
                for (MNode c : container) children.put (c.key (), container);
            }
        }
        loaded = true;
    }
}

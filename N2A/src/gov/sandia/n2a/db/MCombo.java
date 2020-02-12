/*
Copyright 2018-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
public class MCombo extends MNode implements MNodeListener
{
    protected String  name;
	protected boolean loaded;
	protected MNode   primary;  // The one source we are allowed to modify. Always set to first object in sources.

    protected List<MNode>                containers = new ArrayList<MNode> ();
	protected NavigableMap<String,MNode> children   = new TreeMap<String,MNode> ();  // from key to source; requires second lookup to retrieve actual child
    protected List<MNodeListener>        listeners  = new ArrayList<MNodeListener> ();

    public MCombo (String name, List<MNode> containers)
    {
        this.name = name;
        init (containers);
    }

    public synchronized void init (List<MNode> containers)
    {
        for (MNode c : this.containers) c.removeListener (this);
        for (MNode c :      containers) c.addListener (this);

        this.containers = containers;
        if (containers.size () > 0) primary = containers.get (0);
        else                        primary = new MVolatile ();
        children.clear ();
        loaded = false;
        fireChanged ();
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

    public synchronized boolean isWriteable (String key)
    {
        load ();
        MNode container = children.get (key);
        if (container == null) return false;
        return container == primary;
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

    public synchronized MNode containerFor (String key)
    {
        return children.get (key);
    }

    protected synchronized MNode getChild (String key)
    {
        load ();
        MNode container = children.get (key);
        if (container == null) return null;
        return container.child (key);
    }

    public synchronized void clear ()
    {
        // This does not remove the original objects, only our links to them.
        containers.clear ();
        children  .clear ();
        fireChanged ();
    }

    protected synchronized void clearChild (String key)
    {
        // This actually removes the original object.
        load ();
        MNode container = children.get (key);
        if (container == primary) container.clear (key);  // Triggers childDeleted() call from MDir, which updates our children collection.
    }

    public synchronized int size ()
	{
        load ();
        return children.size ();
	}

    public synchronized MNode set (String value, String key)
    {
        load ();
        return primary.set (value, key);  // Triggers childAdded() call from MDir, which updates our children collection.
    }

    /**
        Renames an MDoc on disk.
        If you already hold a reference to the MDoc named by fromIndex, then that reference remains valid
        after the move.
    **/
    public synchronized void move (String fromKey, String toKey)
    {
        load ();
        MNode container = children.get (fromKey);
        if (container != primary) return;
        primary.move (fromKey, toKey);  // Triggers childChanged() call from MDir, which updates our children collection.
    }

    public synchronized void addListener (MNodeListener listener)
    {
        listeners.add (listener);
    }

    public synchronized void removeListener (MNodeListener listener)
    {
        listeners.remove (listener);
    }

    public synchronized void fireChanged ()
    {
        for (MNodeListener l : listeners) l.changed ();
    }

    public synchronized void fireChildAdded (String key)
    {
        for (MNodeListener l : listeners) l.childAdded (key);
    }

    public synchronized void fireChildDeleted (String key)
    {
        for (MNodeListener l : listeners) l.childDeleted (key);
    }

    public synchronized void fireChildChanged (String oldKey, String newKey)
    {
        for (MNodeListener l : listeners) l.childChanged (oldKey, newKey);
    }

    public void changed ()
    {
        // Force a rebuild of children.
        children.clear ();
        loaded = false;
        fireChanged ();
    }

    /**
        Similar to containerFor(), but does a fresh search for child rather than using cached information.
        This is a subroutine for childAdded(), childDeleted() and childChanged().
    **/
    protected synchronized MNode rescanContainer (String key)
    {
        for (MNode c : containers) if (c.child (key) != null) return c;
        return null;
    }

    public void childAdded (String key)
    {
        MNode oldChild = getChild (key);
        MNode newContainer = rescanContainer (key);
        MNode newChild = newContainer.child (key);
        if (oldChild == newChild) return;  // Change is hidden by higher-precedence dataset.
        children.put (key, newContainer);
        if (oldChild == null) fireChildAdded (key);
        fireChildChanged (key, key);
    }

    public void childDeleted (String key)
    {
        MNode newContainer = rescanContainer (key);
        if (newContainer == null)
        {
            children.remove (key);
            fireChildDeleted (key);
            return;
        }
        // A hidden node was exposed by the delete.
        // It's also possible that the deleted node was hidden so no effective change occurred.
        // It's not worth the extra work to detect the "still hidden" case.
        children.put (key, newContainer);
        fireChildChanged (key, key);
    }

    public void childChanged (String oldKey, String newKey)
    {
        if (! oldKey.equals (newKey))  // Not a simple change of content, but rather a move of some sort.
        {
            // Update container mapping at both oldKey and newKey
            MNode container = rescanContainer (oldKey);
            if (container == null) children.remove (oldKey);
            else                   children.put (oldKey, container);
            container = rescanContainer (newKey);
            if (container == null) children.remove (newKey);
            else                   children.put (newKey, container);
        }

        // It's possible that both oldKey and newKey are hidden by higher-precedent datasets,
        // producing no effective change. We should avoid forwarding the message in that case,
        // but it's not worth the effort to detect.
        fireChildChanged (oldKey, newKey);
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

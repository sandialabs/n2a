/*
Copyright 2016-2019 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
    A hierarchical key-value storage system, with subclasses that provide persistence.
    The "M" in MNode refers to the MUMPS language, in which variables have this hierarchical structure.

    This class and all its descendants are thread-safe.
    Note: Each class that extends this one must make its own choices about which methods to synchronize.
    This base implementation only synchronizes those methods that clearly need it in this context.
    For example, if an operation is implemented in terms of several other operations, and the state
    of the tree should not be modified between those operations, then the method is synchronized.
    If the method is naturally atomic, then it is not synchronized. Such choices may not hold for derived implementations.
**/
public class MNode implements Iterable<MNode>, Comparable<MNode>
{
    public String key ()
    {
        return "";
    }

    public MNode parent ()
    {
        return null;
    }

    /**
        Returns the child indicated by the given index, or null if it doesn't exist.
        This function is separate from child(String...) for ease of implementing subclasses.
    **/
    protected MNode getChild (String index)
    {
        return null;
    }

    /**
        Returns a child node from arbitrary depth, or null if any part of the path doesn't exist.
    **/
    public synchronized MNode child (String... indices)
    {
        MNode result = this;
        for (int i = 0; i < indices.length; i++)
        {
            MNode c = result.getChild (indices[i]);
            if (c == null) return null;
            result = c;
        }
        return result;  // If no indices are specified, we return this node.
    }

    /**
        Retrieves a child node from arbitrary depth, or creates it if nonexistent.
        Like a combination of child() and set().
        The benefit of getting back a node rather than a value is ease of access
        to a list stored as children of the node.
    **/
    public synchronized MNode childOrCreate (String... indices)
    {
        MNode result = this;
        for (int i = 0; i < indices.length; i++)
        {
            MNode c = result.getChild (indices[i]);
            if (c == null) c = result.set ("", indices[i]);
            result = c;
        }
        return result;
    }

    /**
        Remove all children.
    **/
    public synchronized void clear ()
    {
        for (MNode n : this) clearChild (n.key ());
    }

    /**
        Removes child with the given index, if it exists.
        This function is separate from clear(String...) for ease of implementing subclasses.
    **/
    protected void clearChild (String index)
    {
    }

    /**
        Removes child with arbitrary depth.
        If no index is specified, then removes all children of this node.
    **/
    public synchronized void clear (String... indices)
    {
        if (indices.length == 0)
        {
            clear ();
            return;
        }

        MNode c = this;
        int last = indices.length - 1;
        for (int i = 0; i < last; i++)
        {
            c = c.getChild (indices[i]);
            if (c == null) return;  // Nothing to clear
        }
        c.clearChild (indices[last]);
    }

    /**
        @return The number of children we have.
    **/
    public int size ()
    {
        return 0;
    }

    /**
        @return This node's value, with "" as default
    **/
    public String get ()
    {
        return getOrDefault ("");
    }

    /**
        Digs down tree as far as possible to retrieve value; returns "" if node does not exist.
    **/
    public String get (String... indices)
    {
        MNode c = child (indices);
        if (c == null) return "";
        return c.get ();
    }

    /**
        Digs down tree as far as possible to retrieve value; returns "" if node does not exist.
    **/
    public String get (Object... indices)
    {
        String[] stringIndices = new String[indices.length];
        for (int i = 0; i < indices.length; i++) stringIndices[i] = indices[i].toString ();
        return get (stringIndices);
    }

    /**
        Retrieve our own value, or the given default if we are set to "".
        This is the only get*() function that needs to be overridden by subclasses.
    **/
    public String getOrDefault (String defaultValue)
    {
        return defaultValue;
    }

    /**
        Digs down tree as far as possible to retrieve value; returns given defaultValue if node does not exist or is set to "".
    **/
    public String getOrDefault (String defaultValue, String... indices)
    {
        String value = get (indices);
        if (value.isEmpty ()) return defaultValue;
        return value;
    }

    public String getOrDefault (String defaultValue, Object... indices)
    {
        String value = get (indices);
        if (value.isEmpty ()) return defaultValue;
        return value;
    }

    public boolean getOrDefault (boolean defaultValue, Object... indices)
    {
        String value = get (indices);
        if (value.isEmpty ()) return defaultValue;
        if (value.trim ().equals ("1")) return true;
        return Boolean.parseBoolean (value);
    }

    public int getOrDefault (int defaultValue, Object... indices)
    {
        String value = get (indices);
        if (value.isEmpty ()) return defaultValue;
        try
        {
            return Integer.parseInt (value);
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    public long getOrDefault (long defaultValue, Object... indices)
    {
        String value = get (indices);
        if (value.isEmpty ()) return defaultValue;
        try
        {
            return Long.parseLong (value);
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    public double getOrDefault (double defaultValue, Object... indices)
    {
        String value = get (indices);
        if (value.isEmpty ()) return defaultValue;
        try
        {
            return Double.parseDouble (value);
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    public boolean getBoolean (Object... indices)
    {
        String value = get (indices);
        if (value.trim ().equals ("1")) return true;
        return Boolean.parseBoolean (value);
    }

    public int getInt (Object... indices)
    {
        try
        {
            return Integer.parseInt (get (indices));
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    public long getLong (Object... indices)
    {
        try
        {
            return Long.parseLong (get (indices));
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    public double getDouble (Object... indices)
    {
        try
        {
            return Double.parseDouble (get (indices));
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    /**
        Sets this node's own value.
        Should be overridden by a subclass.
    **/
    public void set (String value)
    {
    }

    /**
        Sets value of child node specified by index, effectively with a call to child.set(String).
        Creates child node if it doesn't already exist.
        Should be overridden by a subclass.
        @return The child node on which the value was set.
    **/
    public MNode set (String value, String index)
    {
        return new MNode ();  // A completely useless object.
    }

    /**
        Creates all children necessary to set value
    **/
    public synchronized MNode set (String value, String... indices)
    {
        MNode result = this;
        for (int i = 0; i < indices.length; i++)
        {
            MNode c = result.getChild (indices[i]);
            if (c == null) c = result.set ("", indices[i]);
            result = c;
        }
        result.set (value);
        return result;
    }

    public synchronized MNode set (Object value, String... indices)
    {
        if (value instanceof MNode)
        {
            MNode c = childOrCreate (indices);
            c.clear ();
            c.merge ((MNode) value);
            return c;
        }
        return set (value.toString (), indices);
    }

    public synchronized MNode set (Object value, Object... indices)
    {
        String[] stringIndices = new String[indices.length];
        for (int i = 0; i < indices.length; i++) stringIndices[i] = indices[i].toString ();
        return set (value, stringIndices);
    }

    /**
        Deep copies the source node into this node, while leaving any non-overlapping values in
        this node unchanged. The value of this node is replaced, even if the source node is the
        empty string. Children of the source node are then merged with this node's children.
    **/
    public synchronized void merge (MNode that)
    {
        set (that.get ());
        for (MNode thatChild : that)
        {
            String index = thatChild.key ();
            MNode c = getChild (index);
            if (c == null) c = set ("", index);  // ensure a target child node exists
            c.merge (thatChild);
        }
    }

    /**
        Deep copies the source node into this node, while leaving all values in this node unchanged.
        This method could be called "underride", but that already has a special meaning in MPart.
    **/
    public synchronized void mergeUnder (MNode that)
    {
        for (MNode thatChild : that)
        {
            String index = thatChild.key ();
            MNode c = getChild (index);
            if (c == null) set (thatChild, index);
            else           c.mergeUnder (thatChild);
        }
    }

    /**
        Removes empty children from this node which have the same key as children in that node.
        The process works bottom-up, so children of this node may get emptied before they are tested,
        and thus get deleted.
    **/
    public synchronized void uniqueNodes (MNode that)
    {
        for (MNode c : this)
        {
            String key = c.key ();
            MNode d = that.getChild (key);
            if (d == null) continue;
            c.uniqueNodes (d);
            if (c.size () == 0) clearChild (key);
        }
    }

    /**
        Removes empty children from this node which match both key and value of children in that node.
        The process works bottom-up, so children of this node may get emptied before they are tested.
    **/
    public synchronized void uniqueValues (MNode that)
    {
        for (MNode c : this)
        {
            String key = c.key ();
            MNode d = that.getChild (key);
            if (d == null) continue;
            c.uniqueValues (d);
            if (c.size () == 0  &&  c.get ().equals (d.get ())) clearChild (key);
        }
    }

    /**
        Changes the key of a child.
        A move only happens if the given keys are different (same key is no-op).
        Any previously existing node at the destination key will be completely erased and replaced.
        An entry will no longer exist at the source key.
        If the source does not exist before the move, then neither node will exist afterward.
    **/
    public synchronized void move (String fromIndex, String toIndex)
    {
        if (toIndex.equals (fromIndex)) return;
        clearChild (toIndex);
        MNode source = getChild (fromIndex);
        if (source != null)
        {
            MNode destination = set ("", toIndex);
            destination.merge (source);
            clearChild (fromIndex);
        }
    }

    public void addListener (MNodeListener listener)
    {
    }

    public void removeListener (MNodeListener listener)
    {
    }

    public class IteratorWrapper implements Iterator<MNode>
    {
        List<String>     keys;
        Iterator<String> iterator;
        String           key;  // of the most recent node returned by next()

        public IteratorWrapper (List<String> keys)
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

    public Iterator<MNode> iterator ()
    {
        return new MNode.IteratorWrapper (new ArrayList<String> ());
    }

    public static class Visitor
    {
        /**
            @return true to recurse below current node. false if further recursion below this node is not needed.
        **/
        public boolean visit (MNode node)
        {
            return false;  // Since this default implementation doesn't do anything, might as well stop.
        }
    }

    /**
        Execute some operation on each node in the tree. Traversal is depth-first.
    **/
    public synchronized void visit (Visitor v)
    {
        v.visit (this);
        for (MNode c : this) c.visit (v);  // We are an Iterable. In particular, we iterate over the children nodes.
    }

    // It might be possible to write a more efficient M collation routine by sifting through
    // each string, character by character. It would do the usual string comparison while
    // simultaneously converting each string into a number. As long as a string still
    // appears to be a number, conversion continues.
    // This current version is easier to write and understand, but less efficient.
    public static int compare (String A, String B)
    {
        if (A.equals (B)) return 0;  // If strings follow M collation rules, then compare for equals works for numbers.

        Double Avalue = null;
        try {Avalue = Double.valueOf (A);}
        catch (NumberFormatException e) {}

        Double Bvalue = null;
        try {Bvalue = Double.valueOf (B);}
        catch (NumberFormatException e) {}

        if (Avalue == null)  // A is a string
        {
            if (Bvalue == null) return A.compareTo (B);  // Both A and B are strings
            return 1;  // string > number
        }
        else  // A is a number
        {
            if (Bvalue == null) return -1;  // number < string
            return (int) Math.signum (Avalue - Bvalue);
        }
    }

    public static class MOrder implements Comparator<String>
    {
        public int compare (String A, String B)
        {
            return MNode.compare (A, B);
        }
    }
    public static MOrder comparator = new MOrder ();

    @Override
    public int compareTo (MNode that)
    {
        return compare (key (), that.key ());
    }

    /**
        Deep comparison of two nodes. All structure, keys and values must match exactly.
    **/
    @Override
    public boolean equals (Object o)
    {
        if (this == o) return true;
        if (! (o instanceof MNode)) return false;
        MNode that = (MNode) o;
        if (! key ().equals (that.key ())) return false;
        return equalsRecursive (that);
    }

    public boolean equalsRecursive (MNode that)
    {
        if (! get ().equals (that.get ())) return false;
        if (size () != that.size ()) return false;
        for (MNode a : this)
        {
            MNode b = that.getChild (a.key ());
            if (b == null) return false;
            if (! a.equalsRecursive (b)) return false;
        }
        return true;
    }

    public String toString ()
    {
        StringWriter writer = new StringWriter ();
        Schema.latest ().write (this, writer);
        return writer.toString ();
    }
}

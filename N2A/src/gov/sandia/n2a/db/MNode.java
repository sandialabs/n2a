/*
Copyright 2016-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.io.StringWriter;
import java.util.Comparator;
import java.util.Iterator;

/**
    A hierarchical key-value storage system, with subclasses that provide persistence.
    The "M" in MNode refers to the MUMPS language, in which variables have this hierarchical structure.

    This class and all its descendants are thread-safe.
    Note: Each class that extends this one must make its own choices about which methods to synchronize.
    This base implementation only synchronizes those methods that clearly need it in this context.
    For example, if an operation is implemented in terms of several other operations, and the state
    of the tree should not be modified between those operations, then the method is synchronized.
    If the method is naturally atomic, then it is not. Such choices may not hold for derived implementations.
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
        MNode c = getChild (indices[0]);
        for (int i = 1; i < indices.length; i++)
        {
            if (c == null) return null;
            c = c.getChild (indices[i]);
        }
        return c;
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
            if (c == null) c = result.set (indices[i], "");
            result = c;
        }
        return result;  // If no indices are specified, we actually return this node.
    }

    /**
        Remove all children.
    **/
    public synchronized void clear ()
    {
        for (MNode n : this) clear (n.key ());
    }

    /**
        Removes child with the given index, if it exists.
        Outside this package, use clear(String...) for most purposes.
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
        int length = indices.length;
        String[] shiftedIndices = new String[length];
        String index0 = indices[0];
        for (int i = 1; i < length; i++) shiftedIndices[i-1] = indices[i];
        shiftedIndices[length-1] = "";
        return getOrDefault (index0, shiftedIndices);
    }

    /**
        Digs down tree as far as possible to retrieve value; returns "" if node does not exist.
    **/
    public String get (Object... indices)
    {
        int length = indices.length;
        String[] shiftedIndices = new String[length];
        String index0 = indices[0].toString ();
        for (int i = 1; i < length; i++) shiftedIndices[i-1] = indices[i].toString ();
        shiftedIndices[length-1] = "";
        return getOrDefault (index0, shiftedIndices);
    }

    /**
        Retrieve our own value, or the given default if we are set to "".
        Note that this is the only get function that needs to be overridden by subclasses,
        and even this is optional.
    **/
    public String getOrDefault (String defaultValue)
    {
        return defaultValue;
    }

    /**
        Digs down tree as far as possible to retrieve value; returns last arg if necessary.
    **/
    public synchronized String getOrDefault (String parm0, String... parms)
    {
        if (parms.length == 0) return getOrDefault (parm0);  // This could happen indirectly through getOrDefaultType(), where Type is a specific basic type.

        int last = parms.length - 1;
        String defaultValue = parms[last];
        MNode c = getChild (parm0);
        if (c == null) return defaultValue;
        for (int i = 0; i < last; i++)
        {
            c = c.getChild (parms[i]);
            if (c == null) return defaultValue;
        }
        return c.getOrDefault (defaultValue);
    }

    public boolean getBoolean ()
    {
        String value = get ();
        if (value.trim ().equals ("1")) return true;
        return Boolean.parseBoolean (value);
    }

    public int getInt ()
    {
        try
        {
            return Integer.parseInt (get ());
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    public long getLong ()
    {
        try
        {
            return Long.parseLong (get ());
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    public double getDouble ()
    {
        try
        {
            return Double.parseDouble (get ());
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

    public boolean getOrDefaultBoolean (String index0, String... parms)
    {
        String value = getOrDefault (index0, parms);
        if (value.trim ().equals ("1")) return true;
        return Boolean.parseBoolean (value);
    }

    public int getOrDefaultInt (String index0, String... parms)
    {
        try
        {
            return Integer.parseInt (getOrDefault (index0, parms));
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    public long getOrDefaultLong (String index0, String... parms)
    {
        try
        {
            return Long.parseLong (getOrDefault (index0, parms));
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    public double getOrDefaultDouble (String index0, String... parms)
    {
        try
        {
            return Double.parseDouble (getOrDefault (index0, parms));
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    /**
        Sets this node's own value.
    **/
    public void set (String value)
    {
    }

    public synchronized void set (MNode value)
    {
        clear ();
        merge (value);
    }

    /**
        Sets value of child node specified by index (effectively with a call to
        child.set(String)). Creates child node if it doesn't already exist.
        @return The node on which the value was set, for use by set(String,String,String...)
    **/
    public MNode set (String index, String value)
    {
        return new MNode ();  // A completely useless object.
    }

    public synchronized MNode set (String index, MNode value)
    {
        clearChild (index);
        MNode result = set (index, "");
        result.merge (value);
        return result;
    }

    /**
        Creates all children necessary to set value
    **/
    public synchronized void set (String index0, String index1, String... deeperIndices)
    {
        MNode c = getChild (index0);
        if (c == null) c = set (index0, "");

        MNode d = c.getChild (index1);
        if (d == null) d = c.set (index1, "");
        c = d;

        int last = deeperIndices.length - 1;
        for (int i = 0; i < last; i++)
        {
            d = c.getChild (deeperIndices[i]);
            if (d == null) d = c.set (deeperIndices[i], "");
            c = d;
        }

        c.set (deeperIndices[last]);
    }

    public synchronized void set (Object parm0, Object... parms)  // set() with no parameters is not allowed, so parm0 must be a specific Object, not an array.
    {
        String string0 = parm0.toString ();
        int length = parms.length;
        if (length == 0)
        {
            set (string0);
            return;
        }

        int last = length - 1;
        if (parms[last] instanceof MNode)
        {
            String[] names = new String[length];
            names[0] = string0;
            for (int i = 0; i < last; i++) names[i+1] = parms[i].toString ();
            childOrCreate (names).set ((MNode) parms[last]);
            return;
        }

        String string1 = parms[0].toString ();
        if (length == 1)
        {
            set (string0, string1);
        }
        else  // length > 1
        {
            String[] strings = new String[length-1];
            for (int i = 1; i < length; i++) strings[i-1] = parms[i].toString ();
            set (string0, string1, strings);
        }
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
            if (c == null) c = set (index, "");  // ensure a target child node exists
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
            if (c == null) set (index, thatChild);
            else           c.mergeUnder (thatChild);
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
            MNode destination = set (toIndex, "");
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

    public static class IteratorEmpty implements Iterator<MNode>
    {
        public boolean hasNext ()
        {
            return false;
        }

        public MNode next ()
        {
            return null;
        }

        public void remove ()
        {
            // Do nothing, since the list is empty.
        }
    }

    public Iterator<MNode> iterator ()
    {
        return new MNode.IteratorEmpty ();
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
        try
        {
            Avalue = Double.valueOf (A);
        }
        catch (NumberFormatException e)
        {
        }

        Double Bvalue = null;
        try
        {
            Bvalue = Double.valueOf (B);
        }
        catch (NumberFormatException e)
        {
        }

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

/*
Copyright 2016-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
    A hierarchical key-value storage system, with subclasses that provide persistence.
    The "M" in MNode refers to the MUMPS language, in which variables have this hierarchical structure.
    MUMPS is one of the earliest hierarchical key-value system, designed in 1966.

    This class and all its descendants are thread-safe.
    Note: Each class that extends this one must make its own choices about which methods to synchronize.
    This base implementation only synchronizes those methods that clearly need it in this context.
    For example, if an operation is implemented in terms of several other operations, and the state
    of the tree should not be modified between those operations, then the method is synchronized.
    If the method is naturally atomic, then it is not synchronized. Such choices may not hold for derived implementations.

    A node can be "undefined". For the most part, this behaves as having a value of "".
    Undefined nodes have a special role in tree differencing, where they act as structural placeholders.
    The tree-differencing and merge functions in this class assume that leaf nodes are always defined.
**/
public class MNode implements Iterable<MNode>, Comparable<MNode>
{
    public String key ()
    {
        return "";
    }

    /**
        Returns an array of keys suitable for locating this node relative to its root node.
        Does not include the key of the root node itself.
    **/
    public String[] keyPath ()
    {
        return keyPath (null);
    }

    /**
        Returns an array of keys suitable for locating this node relative to the given root node.
        Does not include the key of the root node itself.
        If the given root node is not on the path to the actual root, then it is ignored and
        the full path to actual root is returned.
    **/
    public String[] keyPath (MNode root)
    {
        int index = depth (root);
        String[] result = new String[index];
        MNode parent = this;
        while (index > 0)
        {
            result[--index] = parent.key ();
            parent = parent.parent ();
        }
        return result;
    }

    /**
        Utility function for printing node's key path.
    **/
    public String keyPathString ()
    {
        return keyPathString (null);
    }

    /**
        Utility function for printing node's key path relative to given root node.
        The key path is a dot-separated list of keys.
    **/
    public String keyPathString (MNode root)
    {
        String[] keyPath = keyPath (root);
        if (keyPath.length == 0) return "";
        String result = "";
        for (String s : keyPath) result += s + ".";
        return result.substring (0, result.length () - 1);  // Get rid of final dot.
    }

    public int depth ()
    {
        return depth (null);
    }

    public int depth (MNode root)
    {
        if (this == root) return 0;
        MNode parent = parent ();
        if (parent == null) return 0;
        return parent.depth (root) + 1;
    }

    public MNode parent ()
    {
        return null;
    }

    public MNode root ()
    {
        MNode result = this;
        while (true)
        {
            MNode parent = result.parent ();
            if (parent == null) break;
            result = parent;
        }
        return result;
    }

    /**
        Find the last common ancestor between this node and the given node.
        If the nodes do not share a common ancestor, the result is null.
    **/
    public MNode lca (MNode that)
    {
        // Strategy: Place the ancestry of one node in a set. Then walk up the ancestry
        // of the other node. The first ancestor found in the set is the LCA.

        Set<MNode> thisAncestors = new HashSet<MNode> ();
        MNode A = this;
        while (A != null)
        {
            thisAncestors.add (A);
            A = A.parent ();
        }

        MNode B = that;
        while (B != null)
        {
            if (thisAncestors.contains (B)) return B;
            B = B.parent ();
        }
        return null;
    }

    /**
        Returns the child indicated by the given key, or null if it doesn't exist.
        This function is separate from child(String...) for ease of implementing subclasses.
    **/
    protected MNode getChild (String key)
    {
        return null;
    }

    /**
        Returns a child node from arbitrary depth, or null if any part of the path doesn't exist.
    **/
    public synchronized MNode child (String... keys)
    {
        MNode result = this;
        for (String key : keys)
        {
            MNode c = result.getChild (key);
            if (c == null) return null;
            result = c;
        }
        return result;  // If no keys are specified, we return this node.
    }

    public MNode child (Object... keys)
    {
        return child (toStrings (keys));
    }

    /**
        Retrieves a child node from arbitrary depth, or creates it if nonexistent.
        Like a combination of child() and set().
        The benefit of getting back a node rather than a value is ease of access
        to a list stored as children of the node.
    **/
    public synchronized MNode childOrCreate (String... keys)
    {
        MNode result = this;
        for (String key : keys)
        {
            MNode c = result.getChild (key);
            if (c == null) c = result.set (null, key);
            result = c;
        }
        return result;
    }

    public MNode childOrCreate (Object... keys)
    {
        return childOrCreate (toStrings (keys));
    }

    /**
        Convenience method for iterating over an arbitrary sub-node.
        If the node doesn't exist, returns a temporary value with no children.
        The returned value is not attached to any tree, and should not be used for anything except iteration.
    **/
    public MNode childOrEmpty (String... keys)
    {
        MNode result = child (keys);
        if (result == null) return new MNode ();
        return result;
    }

    public MNode childOrEmpty (Object... keys)
    {
        return childOrEmpty (toStrings (keys));
    }

    /**
        Convenience method for retrieving node at an ordinal position without knowing the specific key.
        Children are traversed in the usual key order.
        If index is out of range, then result is null.
    **/
    public synchronized MNode childAt (int index)
    {
        for (MNode c : this) if (index-- == 0) return c;
        return null;
    }

    /**
        Remove all children.
    **/
    public synchronized void clear ()
    {
        for (MNode n : this) clearChild (n.key ());
    }

    /**
        Removes child with the given key, if it exists.
        This function is separate from clear(String...) for ease of implementing subclasses.
    **/
    protected void clearChild (String key)
    {
    }

    /**
        Removes child with arbitrary depth.
        If no key is specified, then removes all children of this node.
    **/
    public synchronized void clear (String... keys)
    {
        if (keys.length == 0)
        {
            clear ();
            return;
        }

        MNode c = this;
        int last = keys.length - 1;
        for (int i = 0; i < last; i++)
        {
            c = c.getChild (keys[i]);
            if (c == null) return;  // Nothing to clear
        }
        c.clearChild (keys[last]);
    }

    public void clear (Object... keys)
    {
        clear (toStrings (keys));
    }

    /**
        @return The number of children we have.
    **/
    public int size ()
    {
        return 0;
    }

    public boolean isEmpty ()
    {
        return size () == 0;
    }

    /**
        Indicates whether this node is defined.
        Works in conjunction with isEmpty() to provide information similar to the MUMPS function "DATA".
        Since get() returns "" for undefined nodes, this is the only way to determine whether a node
        is actually defined to "" or is undefined. "Undefined" is not the same as non-existent,
        because and undefined node can have children. A child() call on the parent can confirm the
        complete non-existence of a node. Alternately, if the node does not exist, then data()
        returns false and isEmpty() returns true.
    **/
    public boolean data ()
    {
        return false;
    }

    public boolean data (String... keys)
    {
        MNode c = child (keys);
        if (c == null) return false;
        return c.data ();
    }

    public boolean data (Object... keys)
    {
        return data (toStrings (keys));
    }

    /**
        Determines if the given key exists anywhere in the hierarchy.
        This is a deep query. For a shallow (one-level) query, use child() instead.
    **/
    public boolean containsKey (String key)
    {
        if (child (key) != null) return true;
        for (MNode c : this) if (c.containsKey (key)) return true;
        return false;
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
    public String get (String... keys)
    {
        MNode c = child (keys);
        if (c == null) return "";
        return c.get ();
    }

    public static String[] toStrings (Object... keys)
    {
        String[] result = new String[keys.length];
        for (int i = 0; i < keys.length; i++) result[i] = keys[i].toString ();
        return result;
    }

    /**
        Digs down tree as far as possible to retrieve value; returns "" if node does not exist.
    **/
    public String get (Object... keys)
    {
        return get (toStrings (keys));
    }

    /**
        Returns this node's value, or the given default if node is undefined or set to "".
        This is the only get*() function that needs to be overridden by subclasses.
    **/
    public String getOrDefault (String defaultValue)
    {
        return defaultValue;
    }

    /**
        Digs down tree as far as possible to retrieve value; returns given defaultValue if node does not exist or is set to "".
    **/
    public String getOrDefault (String defaultValue, String... keys)
    {
        String value = get (keys);
        if (value.isEmpty ()) return defaultValue;
        return value;
    }

    public String getOrDefault (String defaultValue, Object... keys)
    {
        return getOrDefault (defaultValue, toStrings (keys));
    }

    public boolean getOrDefault (boolean defaultValue, String... keys)
    {
        String value = get (keys);
        if (value.isEmpty ()) return defaultValue;
        if (value.trim ().equals ("1")) return true;
        return Boolean.parseBoolean (value);
    }

    public boolean getOrDefault (boolean defaultValue, Object... keys)
    {
        return getOrDefault (defaultValue, toStrings (keys));
    }

    public int getOrDefault (int defaultValue, String... keys)
    {
        String value = get (keys);
        if (value.isEmpty ()) return defaultValue;
        try
        {
            return Integer.parseInt (value);
        }
        catch (NumberFormatException e) {}

        // A number formatted as a float (containing a decimal point) will fail to parse as an integer.
        // Attempt to parse as float and round. If that fails, then it is truly hopeless.
        try
        {
            return (int) Math.round (Double.parseDouble (value));
        }
        catch (NumberFormatException e) {}
        {
            return defaultValue;
        }
    }

    public int getOrDefault (int defaultValue, Object... keys)
    {
        return getOrDefault (defaultValue, toStrings (keys));
    }

    public long getOrDefault (long defaultValue, String... keys)
    {
        String value = get (keys);
        if (value.isEmpty ()) return defaultValue;
        try
        {
            return Long.parseLong (value);
        }
        catch (NumberFormatException e) {}

        try
        {
            return Math.round (Double.parseDouble (value));
        }
        catch (NumberFormatException e) {}
        {
            return defaultValue;
        }
    }

    public long getOrDefault (long defaultValue, Object... keys)
    {
        return getOrDefault (defaultValue, toStrings (keys));
    }

    public double getOrDefault (double defaultValue, String... keys)
    {
        String value = get (keys);
        if (value.isEmpty ()) return defaultValue;
        try
        {
            return Double.parseDouble (value);
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    public double getOrDefault (double defaultValue, Object... keys)
    {
        return getOrDefault (defaultValue, toStrings (keys));
    }

    /**
        Interprets value as boolean, with a small extension to Java's string parser:
        true <-- "1" or "true";
        false <-- everything else, including empty and undefined.
        See getFlag() for a different way to interpret booleans. The key difference is
        that a boolean defaults to false.
    **/
    public boolean getBoolean (String... keys)
    {
        return getOrDefault (false, keys);
    }

    public boolean getBoolean (Object... keys)
    {
        return getBoolean (toStrings (keys));
    }

    /**
        Interprets value as flag, which may contain extended information when set:
        false <-- "0" or non-existent;
        true <-- everything else, including empty and undefined.
        See getBoolean() for a different way to interpret booleans. The key difference is
        that a flag defaults to true, so it can indicate something by merely existing, without a value.
        It also tolerates arbitrary content, so a flag can carry extra data (either its value
        or children) and still be interpreted as true.
    **/
    public boolean getFlag (String... keys)
    {
        MNode c = child (keys);
        if (c == null  ||  c.get ().equals ("0")) return false;
        return true;
    }

    public boolean getFlag (Object... keys)
    {
        return getFlag (toStrings (keys));
    }

    public int getInt (String... keys)
    {
        return getOrDefault (0, keys);
    }

    public int getInt (Object... keys)
    {
        return getInt (toStrings (keys));
    }

    public long getLong (String... keys)
    {
        return getOrDefault (0l, keys);
    }

    public long getLong (Object... keys)
    {
        return getLong (toStrings (keys));
    }

    public double getDouble (String... keys)
    {
        return getOrDefault (0.0, keys);
    }

    public double getDouble (Object... keys)
    {
        return getDouble (toStrings (keys));
    }

    /**
        Sets this node's own value.
        Passing null makes future calls to data() returns false, that is, makes the value of this node undefined.
        Should be overridden by a subclass.
    **/
    public void set (String value)
    {
    }

    /**
        Sets value of child node specified by key, effectively with a call to child.set(String).
        Creates child node if it doesn't already exist.
        Should be overridden by a subclass.
        @return The child node on which the value was set.
    **/
    public MNode set (String value, String key)
    {
        // A lie that allows this base class to be minimally useful.
        // Could also return null, but that might needlessly break some simple use cases.
        return new MNode ();
    }

    /**
        Creates all children necessary to set value
    **/
    public synchronized MNode set (String value, String... keys)
    {
        MNode result = childOrCreate (keys);
        result.set (value);
        return result;
    }

    /**
        Formats a number by truncating it to the given number of decimal places.
        Drops any trailing zeroes to produce the most compact form.
        The purpose of this function is to efficiently store numbers when the
        desired/reasonable precision is known. Otherwise, might get a bunch of junk
        precision at the end of the string.
        @param value Must have a relatively small exponent, because this routine
        relies on converting to integer then back again.
        @param precision Of the digits after (to the right of) the decimal point.
        Cannot be negative.
    **/
    public MNode setTruncated (double value, int precision, String... keys)
    {
        double shift = Math.pow (10, precision);
        String converted = String.valueOf (Math.round (value * shift) / shift);
        int pos = converted.lastIndexOf ('.');
        if (pos >= 0)
        {
            int end = Math.min (pos + precision, converted.length () - 1);
            for (; end >= pos; end--)
            {
                char c = converted.charAt (end);
                if (c != '0'  &&  c != '.') break;
            }
            converted = converted.substring (0, end + 1);
        }
        return set (converted, keys);
    }

    public MNode setTruncated (double value, int precision, Object... keys)
    {
        return setTruncated (value, precision, toStrings (keys));
    }

    public synchronized MNode set (Object value, String... keys)
    {
        MNode result = childOrCreate (keys);
        if (value instanceof MNode)
        {
            result.clear ();   // get rid of all children
            result.set (null); // ensure that if value node is undefined, result node will also be undefined
            result.merge ((MNode) value);
        }
        else
        {
            String stringValue = null;
            if (value instanceof Boolean) stringValue = (Boolean) value ? "1" : "0";
            else if (value != null)       stringValue = value.toString ();
            result.set (stringValue);
        }
        return result;
    }

    public synchronized MNode set (Object value, Object... keys)
    {
        return set (value, toStrings (keys));
    }

    /**
        Deep copies the source node into this node, while leaving any non-overlapping values in
        this node unchanged. The value of this node is only replaced if the source value is defined.
        Children of the source node are then merged with this node's children.
    **/
    public synchronized void merge (MNode that)
    {
        if (that.data ()) set (that.get ());
        for (MNode thatChild : that)
        {
            String key = thatChild.key ();
            MNode c = getChild (key);
            if (c == null) c = set (null, key);  // ensure a target child node exists
            c.merge (thatChild);
        }
    }

    /**
        Deep copies the source node into this node, while leaving all values in this node unchanged.
        This method could be called "underride", but that already has a special meaning in MPart.
    **/
    public synchronized void mergeUnder (MNode that)
    {
        if (! data ()  &&  that.data ()) set (that.get ());
        for (MNode thatChild : that)
        {
            String key = thatChild.key ();
            MNode c = getChild (key);
            if (c == null) set (thatChild, key);
            else           c.mergeUnder (thatChild);
        }
    }

    /**
        Modifies this tree so it contains only nodes which are not defined in the given tree ("that").
        Implicitly, it also contains parents of such nodes, but a parent will be undefined if it was
        defined in the given tree. This function is used for tree differencing,
        where it is necessary to represent nodes that will be subtracted as a separate tree from nodes
        that will be added. This function computes the subtraction tree. To apply the subtraction,
        this function can be called again, passing the result of the previous call. Specifically, let:
        <pre>
        A = one tree
        B = another tree
        C = clone of A, then run C.uniqueNodes(B)
        D = clone of B, then run D.uniqueValues(A)
        To transform A into B, run
        A.uniqueNodes(C) to remove/undefine nodes that are only in A
        A.merge(D) to add/change values which are different in B
        </pre>
    **/
    public synchronized void uniqueNodes (MNode that)
    {
        if (that.data ()) set (null);
        for (MNode c : this)
        {
            String key = c.key ();
            MNode d = that.getChild (key);
            if (d == null) continue;
            c.uniqueNodes (d);
            if (c.size () == 0  &&  ! c.data ()) clearChild (key);
        }
    }

    /**
        Modifies this tree so it contains only nodes which differ from the given tree ("that")
        in either key or value. Any parent nodes which are not also differences will be undefined.
        See uniqueNodes(MNode) for an explanation of tree differencing.
    **/
    public synchronized void uniqueValues (MNode that)
    {
        if (data ()  &&  that.data ()  &&  get ().equals (that.get ())) set (null);
        for (MNode c : this)
        {
            String key = c.key ();
            MNode d = that.getChild (key);
            if (d == null) continue;
            c.uniqueValues (d);
            if (c.size () == 0  &&  ! c.data ()) clearChild (key);
        }
    }

    /**
        Assuming "that" will be the target of a merge, saves any values this node would change.
        The resulting tree can be used to revert a merge. Specifically, let:
        <pre>
        A = this tree (merge source) before any operations
        B = that tree (merge target) before any operations
        C = clone of A, then run C.uniqueNodes(B)
        D = clone of A, then run D.changes(B)
        Suppose you run B.merge(A). To revert B to its original state, run
        B.uniqueNodes(C) to remove/undefine nodes not originally in B
        B.merge(D) to restore original values
        </pre>
        See uniqueNodes(MNode) for more explanation of tree differencing.
    **/
    public synchronized void changes (MNode that)
    {
        if (data ())
        {
            if (that.data ())
            {
                String value = that.get ();
                if (get ().equals (value)) set (null);
                else                       set (value);
            }
            else
            {
                set (null);
            }
        }
        for (MNode c : this)
        {
            String key = c.key ();
            MNode d = that.child (key);
            if (d == null) clearChild (key);
            else           c.changes (d);
        }
    }

    /**
        Changes the key of a child.
        A move only happens if the given keys are different (same key is no-op).
        Any previously existing node at the destination key will be completely erased and replaced.
        An entry will no longer exist at the source key.
        If the source does not exist before the move, then neither node will exist afterward.
        Many subclasses guarantee object identity, but that is not a requirement.
        The safest approach is to call child(toKey) to get a reference to the renamed node.
    **/
    public synchronized void move (String fromKey, String toKey)
    {
        if (toKey.equals (fromKey)) return;
        clearChild (toKey);
        MNode source = getChild (fromKey);
        if (source != null)
        {
            MNode destination = set (null, toKey);
            destination.merge (source);
            clearChild (fromKey);
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
            If a child is deleted while the iterator is running, this could return null.
            If a child is added, it will not be included.
            Implicitly, if the current child is moved (renamed), then it won't appear again in the iteration.
            In general, the caller is free to modify the returned value without any harm to the iteration.
            There will not be a concurrent modification exception, and the iteration order won't be impacted.
            Derived MNode classes should use IteratorWrapper or equivalent to maintain these guarantees to the caller.
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

    public interface Visitor
    {
        /**
            @return true to recurse below current node. false if further recursion below this node is not needed.
        **/
        public boolean visit (MNode node);
    }

    /**
        Execute some operation on each node in the tree. Traversal is depth-first.
    **/
    public synchronized void visit (Visitor v)
    {
        if (! v.visit (this)) return;
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
        if (data () != that.data ()) return false;
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

    /**
        Compares only key structure, not values.
    **/
    public boolean structureEquals (MNode that)
    {
        if (size () != that.size ()) return false;
        for (MNode a : this)
        {
            MNode b = that.getChild (a.key ());
            if (b == null) return false;
            if (! a.structureEquals (b)) return false;
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

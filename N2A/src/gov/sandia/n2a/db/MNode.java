/*
Copyright 2016 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
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
    If the method is naturally atomic, then it is not. Such choices may not hold for derived implementations.
**/
public class MNode implements Iterable<MNode>, Comparable<MNode>
{
    public String key ()
    {
        return "";
    }

    /**
        Returns the child indicated by the given index, or null if it doesn't exist.
    **/
    public MNode child (String index)
    {
        return null;
    }

    /**
        Returns a child node from arbitrary depth, or null if any part of the path doesn't exist.
    **/
    public synchronized MNode child (String index0, String... deeperIndices)
    {
        MNode c = child (index0);
        for (int i = 0; i < deeperIndices.length; i++)
        {
            if (c == null) return null;
            c = c.child (deeperIndices[i]);
        }
        return c;
    }

    /**
        Returns a child node from arbitrary depth, or null if any part of the path doesn't exist.
    **/
    public synchronized MNode child (List<String> indices)
    {
        MNode result = this;
        for (String index : indices)
        {
            result = result.child (index);
            if (result == null) break;
        }
        return result;
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
            MNode c = result.child (indices[i]);
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
        Remove child with the given index, if it exists.
    **/
    public void clear (String index)
    {
    }

    public synchronized void clear (String index0, String... deeperIndices)
    {
        MNode c = child (index0);
        int last = deeperIndices.length - 1;
        for (int i = 0; i < last; i++)
        {
            if (c == null) return;  // Nothing to clear
            c = c.child (deeperIndices[i]);
        }
        if (c != null) c.clear (deeperIndices[last]);
    }

    /**
        @return The number of children we have.
    **/
    public int length ()
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
        if (parms.length == 0) return getOrDefault (parm0);  // This could happen indirectly through getOfDefaultType(), where Type is a specific basic type.

        int last = parms.length - 1;
        String defaultValue = parms[last];
        MNode c = child (parm0);
        if (c == null) return defaultValue;
        for (int i = 0; i < last; i++)
        {
            c = c.child (parms[i]);
            if (c == null) return defaultValue;
        }
        return c.getOrDefault (defaultValue);
    }

    public boolean getBoolean ()
    {
        return Boolean.parseBoolean (get ());
    }

    public int getInt ()
    {
        return Integer.parseInt (get ());
    }

    public long getLong ()
    {
        return Long.parseLong (get ());
    }

    public double getDouble ()
    {
        return Double.parseDouble (get ());
    }

    public boolean getOrDefaultBoolean (String index0, String... parms)
    {
        return Boolean.parseBoolean (getOrDefault (index0, parms));
    }

    public int getOrDefaultInt (String index0, String... parms)
    {
        return Integer.parseInt (getOrDefault (index0, parms));
    }

    public long getOrDefaultLong (String index0, String... parms)
    {
        return Long.parseLong (getOrDefault (index0, parms));
    }

    public double getOrDefaultDouble (String index0, String... parms)
    {
        return Double.parseDouble (getOrDefault (index0, parms));
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
        clear (index);
        MNode result = set (index, "");
        result.merge (value);
        return result;
    }

    /**
        Creates all children necessary to set value
    **/
    public synchronized void set (String index0, String index1, String... deeperIndices)
    {
        MNode c = child (index0);
        if (c == null) c = set (index0, "");

        MNode d = c.child (index1);
        if (d == null) d = c.set (index1, "");
        c = d;

        int last = deeperIndices.length - 1;
        for (int i = 0; i < last; i++)
        {
            d = c.child (deeperIndices[i]);
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
            MNode c = child (string0);
            if (c == null) c = set (string0, "");
            for (int i = 0; i < last; i++)
            {
                String s = parms[i].toString ();
                MNode d = c.child (s);
                if (d == null) d = c.set (s, "");
                c = d;
            }
            c.set ((MNode) parms[last]);
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
        empty string. Any matching children are then merged. Any children in the source with no
        match in this node are deep-copied. Any children in this node with no match in the source
        remain unchanged.
    **/
    public synchronized void merge (MNode that)
    {
        set (that.get ());
        for (MNode thatChild : that)
        {
            String index = thatChild.key ();
            MNode c = child (index);
            if (c == null) c = set (index, "");  // ensure a target child node exists
            c.merge (thatChild);
        }
    }

    /**
        Changes the key of a child.
        Any previously existing node at the destination key will be completely erased and replaced.
        An entry will no longer exist at the source key.
        If the source does not exist before the move, then neither node will exist afterward.
    **/
    public synchronized void move (String fromIndex, String toIndex)
    {
        clear (toIndex);
        MNode source = child (fromIndex);
        if (source != null)
        {
            MNode destination = set (toIndex, "");
            destination.merge (source);
            clear (fromIndex);
        }
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

    /**
        Brings in data from a stream. See write(Writer,String) for format.
        This method only processes children. The direct value of this node
        must be set by the caller.

        Wraps the given Reader in a BufferedReader, unless it is already a
        BufferedReader (see the inner class LineReader). If you wish to continue
        using the Reader after this function returns, for example if the
        stream contains a YAML terminator (---) followed by another serialized
        object, then you must pass in a BufferedReader to begin with. Otherwise,
        the position in the original Reader is not well defined.
    **/
    public synchronized void read (Reader reader)
    {
        clear ();
        try
        {
            LineReader lineReader = new LineReader (reader);
            read (lineReader, 0);
            lineReader.close ();
        }
        catch (IOException e)
        {
        }
    }

    /**
        Recursive version of read(Reader) for loading children.
        We assume LineReader always holds the next unprocessed line.
    **/
    public synchronized void read (LineReader reader, int whitespaces) throws IOException
    {
        while (true)
        {
            if (reader.line == null) return;  // stop at end of file
            // At this point, reader.whitespaces == whitespaces

            // Parse the line into key=value. Must pay special attention when multiple "=" are present
            String line = reader.line.trim ();
            String[] pieces = line.split ("=", -1);  // Negative limit means to create empty string in resulting array for any zero-length pieces (as opposed to ignoring them). The following algorithm depends on this.
            String index = pieces[0];
            int last = pieces.length - 1;
            int i = 1;
            for (; i < last; i++)  // Note that we never include the last piece in the index, unless there is only one piece (see initial assignment above).
            {
                if (pieces[i].isEmpty ()  ||  index.endsWith ("=")  ||  index.endsWith ("<")  ||  index.endsWith (">")  ||  index.endsWith ("!"))
                {
                    index = index + "=" + pieces[i];
                    continue;
                }
                break;
            }
            String value = "";
            if (i <= last)
            {
                value = pieces[i++];
                while (i <= last) value = value + "=" + pieces[i++];
            }
            index = index.trim ();
            value = value.trim ();

            if (value.startsWith ("|"))  // go into string reading mode
            {
                StringBuilder block = new StringBuilder ();
                reader.getNextLine ();
                if (reader.whitespaces > whitespaces)
                {
                    int blockIndent = reader.whitespaces;
                    while (true)
                    {
                        block.append (reader.line.substring (blockIndent));
                        reader.getNextLine ();
                        if (reader.whitespaces < blockIndent) break;
                        block.append (String.format ("%n"));
                    }
                }
                value = block.toString ();
            }
            else
            {
                reader.getNextLine ();
            }
            MNode child = set (index, value);  // Create a child with the given value
            if (reader.whitespaces > whitespaces) child.read (reader, reader.whitespaces);  // Recursively populate child. When this call returns, reader.whitespaces <= whitespaces in this function, because that is what ends the recursion.
            if (reader.whitespaces < whitespaces) return;  // end recursion
        }
    }

    public static class LineReader
    {
        public Reader         originalReader;
        public BufferedReader reader;
        public String         line;
        public int            whitespaces;

        public LineReader (Reader reader) throws IOException
        {
            originalReader = reader;
            if (reader instanceof BufferedReader) this.reader = (BufferedReader) reader;
            else                                  this.reader = new BufferedReader (reader);
            getNextLine ();
        }

        public void getNextLine () throws IOException
        {
            // Scan for non-empty line
            // Only ignore lines which start with a comment character, or which are perfectly empty.
            while (true)
            {
                line = reader.readLine ();
                if (line == null)  // end of file
                {
                    whitespaces = -1;
                    return;
                }
                if (line.isEmpty ()) continue;
                if (line.startsWith ("#")) continue;
                break;
            }

            // Count leading whitespace
            int length = line.length ();
            whitespaces = 0;
            while (whitespaces < length)
            {
                char c = line.charAt (whitespaces);
                if (c != ' '  &&  c != '\t') break;
                whitespaces++;
            }
        }

        public void close ()
        {
            if (reader != originalReader)
            {
                try
                {
                    reader.close ();
                    reader = null;  // Poison it
                }
                catch (IOException e)
                {
                }
            }
        }
    }

    /**
        Convenience function for calling write(Writer,String) with no initial indent.
    **/
    public synchronized void write (Writer writer)
    {
        try
        {
            write (writer, "");
        }
        catch (IOException e)
        {
        }
    }

    /**
        Produces an indented hierarchical list of indices and values from entire tree.
        Note that this function is not exactly idempotent with read(). Rather, this function
        writes the node, then its children. read() only reads children. To use write() in
        a way that is exactly idempotent with read(), use a line like:
        <code>for (MNode c : NodeWhoseChildrenYouWantToWrite) c.write (writer, indent);</code>
    **/
    public synchronized void write (Writer writer, String space) throws IOException
    {
        String index = key ();
        String value = get ();
        if (value.isEmpty ())
        {
            writer.write (String.format ("%s%s%n", space, index));
        }
        else
        {
            String newLine = String.format ("%n");
            if (value.contains (newLine))  // go into extended text write mode
            {
                value = value.replace (newLine, newLine + space + "  ");
                value = "|" + newLine + space + "  " + value;
            }
            writer.write (String.format ("%s%s=%s%n", space, index, value));
        }

        String space2 = space + " ";
        for (MNode c : this) c.write (writer, space2);  // if this node has no children, nothing at all is written
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

    @Override
    public boolean equals (Object that)
    {
        if (that instanceof MNode) return compareTo ((MNode) that) == 0;
        return false;
    }

    public String toString ()
    {
        StringWriter writer = new StringWriter ();
        write (writer);
        return writer.toString ();
    }
}

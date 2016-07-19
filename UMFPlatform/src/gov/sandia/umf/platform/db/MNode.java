/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
    A hierarchical key-value storage system, with subclasses that provide persistence.
    The "M" in MNode refers to the MUMPS language, in which variables have this hierarchical structure.
**/
public abstract class MNode implements Iterable<Map.Entry<String,MNode>>
{
    /**
        @return The child indicated by the given index, or null if it doesn't exist.
    **/
    public MNode child (String index)
    {
        return null;
    }

    public MNode child (String index0, String... deeperIndices)
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
        Remove all children.
    **/
    public void clear ()
    {
    }

    /**
        @return This node's value, with "" as default
    **/
    public String get ()
    {
        return get ("");
    }

    /**
        @return Our value. If our value is "", then return the defaultValue.
    **/
    public String get (String defaultValue)
    {
        return defaultValue;
    }

    /**
        @return Child node value, with first arg as default
    **/
    public String get (String defaultValue, String index)
    {
        MNode c = child (index);
        if (c == null) return defaultValue;
        return c.get (defaultValue);
    }

    /**
        Digs down tree as far as possible to retrieve value; returns first arg if necessary.
    **/
    public String get (String defaultValue, String index0, String... deeperIndices)
    {
        MNode c = child (index0);
        if (c == null) return defaultValue;
        for (int i = 0; i < deeperIndices.length; i++)
        {
            c = c.child (deeperIndices[i]);
            if (c == null) return defaultValue;
        }
        return c.get (defaultValue);
    }

    public int getInt (String... args)
    {
        if (args.length == 0) return Integer.parseInt (get (""));
        if (args.length == 1) return Integer.parseInt (get (args[0]));
        if (args.length == 2) return Integer.parseInt (get (args[0], args[1]));
        return Integer.parseInt (get (args[0], args[1], Arrays.copyOfRange (args, 2, args.length - 1)));
    }

    public double getDouble (String... args)
    {
        if (args.length == 0) return Double.parseDouble (get (""));
        if (args.length == 1) return Double.parseDouble (get (args[0]));
        if (args.length == 2) return Double.parseDouble (get (args[0], args[1]));
        return Double.parseDouble (get (args[0], args[1], Arrays.copyOfRange (args, 2, args.length - 1)));
    }

    /**
        Sets this node's own value.
    **/
    public void set (String value)
    {
    }

    /**
        Sets value of child node specified by index (effectively with a call to
        child.set(String)). Creates child node if it doesn't already exist.
        @return The node on which the value was set, for use by set(String,String,String...)
    **/
    public abstract MNode set (String value, String index);

    /**
        Creates all children necessary to set value
    **/
    public void set (String value, String index0, String... deeperIndices)
    {
        MNode c = child (index0);
        if (c == null) c = set ("", index0);
        int last = deeperIndices.length - 1;
        for (int i = 0; i < last; i++)
        {
            MNode d = c.child (deeperIndices[i]);
            if (d == null) d = c.set ("", deeperIndices[i]);
            c = d;
        }
        c.set (value, deeperIndices[last]);
    }

    public<T extends Object> void set (T value, String... indices)
    {
        if (indices.length == 0)
        {
            set (value.toString ());
        }
        else if (indices.length == 1)
        {
            set (value.toString (), indices[0]);
        }
        else  // indices.length >= 2
        {
            set (value.toString (), indices[0], Arrays.copyOfRange (indices, 1, indices.length - 1));
        }
    }

    public static class IteratorEmpty implements Iterator<Entry<String,MNode>>
    {
        public boolean hasNext ()
        {
            return false;
        }

        public Entry<String,MNode> next ()
        {
            return null;
        }

        public void remove ()
        {
            // Do nothing, since the list is empty.
        }
    }

    public static class Visitor
    {
        /**
            @return true to recurse below current node. false if further recursion below this node is not needed.
        **/
        public boolean visit (MNode node, String index)
        {
            return false;  // Since this default implementation doesn't do anything, might as well stop.
        }
    }

    public void visit (Visitor v)
    {
        visit (v, "");
    }

    /**
        Execute some operation on each node in the tree. Traversal is depth-first.
    **/
    public void visit (Visitor v, String index)
    {
        v.visit (this, index);
        for (Entry<String,MNode> e : this)  // We are an Iterable. In particular, we iterate over the children nodes.
        {
            e.getValue ().visit (v, e.getKey ());
        }
    }

    /**
        Deep copies the source node into this node, while leaving any non-overlapping values in
        this node unchanged. The value of this node will only be replaced if the value of the source
        node is not empty. In either case, any matching children are then merged. Any children in the
        source with no match in this node are deep-copied. Any children in this node with no match in
        the source remain unchanged.
    **/
    public void merge (MNode that)
    {
        String value = that.get ();
        if (! value.isEmpty ()) set (value);
        for (Entry<String,MNode> e : that)
        {
            String index = e.getKey ();
            MNode c = child (index);
            if (c == null) c = set ("", index);  // ensure a target child node exists
            c.merge (e.getValue ());
        }
    }

    /**
        Brings in data from a stream. See write(Writer,String) for format.
        This method only processes children. It assumes that our value was processed
        at the caller's level, as its child.
    **/
    public void read (BufferedReader reader)
    {
        clear ();
        try
        {
            LineReader lineReader = new LineReader (reader);
            read (lineReader, 0);
        }
        catch (IOException e)
        {
        }
    }

    /**
        Recursive version of read(Reader) for loading children.
        We assume LineReader always holds the next unprocessed line.
    **/
    public void read (LineReader reader, int whitespaces) throws IOException
    {
        while (true)
        {
            // At this point, reader.whitespaces == whitespaces
            String line = reader.line.trim ();
            String[] pieces = line.split ("=", 2);
            String index = pieces[0].trim ();
            String value;
            if (pieces.length > 1) value = pieces[1].trim ();
            else                   value = "";
            MNode child = set (value, index);  // Create a child with the given value

            reader.getNextLine ();
            if (reader.whitespaces > whitespaces) child.read (reader, reader.whitespaces);  // Recursively populate child. When this call returns, reader.whitespaces <= whitespaces in this function, because that is what ends the recursion.
            if (reader.whitespaces < whitespaces) break;
        }
    }

    public static class LineReader
    {
        public BufferedReader reader;
        public String         line;
        public int            whitespaces;

        public LineReader (BufferedReader reader) throws IOException
        {
            this.reader = reader;
            getNextLine ();
        }

        public void getNextLine () throws IOException
        {
            // Scan for non-empty line
            // A pure comment line will be thrown away, but a line that contains a comment and other data will be passed on in full.
            while (true)
            {
                line = reader.readLine ();
                int comment = line.indexOf ('#');  // find comment character, if it exists
                if (comment == -1)  // no comment
                {
                    if (!                        line.trim ().isEmpty ()) break;  // got a non-empty line
                }
                else
                {
                    if (! line.substring (0, comment).trim ().isEmpty ()) break;  // ditto
                }
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
    }

    /**
        Produces an indented hierarchical list of indices and values from entire tree.
        Note that an empty value simply produces no characters rather than two quote marks.
    **/
    public void write (Writer writer)
    {
        try
        {
            writer.write (String.format ("%s%n", get ()));  // At the top level, we don't know any index referring to us, so simply dump the value.
            write (writer, "");
        }
        catch (IOException e)
        {
        }
    }

    /**
        Produces an indented hierarchical list of indices and values from entire tree.
        Also note that an empty value simply produces no characters rather than two quote marks.
    **/
    public void write (Writer writer, String space) throws IOException
    {
        String space2 = space + " ";
        for (Entry<String,MNode> e : this)  // if this node has no children, nothing at all is written
        {
            MNode child = e.getValue ();
            writer.write (String.format ("%s%s=%s%n", space, e.getKey (), child.get ()));
            child.write (writer, space2);
        }
    }

    public String toString ()
    {
        StringWriter writer = new StringWriter ();
        write (writer);
        return writer.toString ();
    }
}

/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public class Schema1 extends Schema
{
    public Schema1 (int version, String type)
    {
        super (version, type);
    }

    /**
        Brings in data from a stream. See write(MNode,Writer,String) for format.
        This method only processes children. The direct value of the node
        must be set by the caller.

        Wraps the given Reader in a BufferedReader, unless it is already a
        BufferedReader (see the inner class LineReader). If you wish to continue
        using the Reader after this function returns, for example if the
        stream contains a YAML terminator (---) followed by another serialized
        object, then you must pass in a BufferedReader to begin with. Otherwise,
        the position in the original Reader is not well defined.
    **/
    public void read (MNode node, Reader reader)
    {
        node.clear ();
        try
        {
            LineReader lineReader = new LineReader (reader);
            read (node, lineReader, 0);
            lineReader.close ();
        }
        catch (IOException e) {}
    }

    /**
        Recursive version of read(Reader,MNode) for loading children.
        We assume LineReader always holds the next unprocessed line.
    **/
    public void read (MNode node, LineReader reader, int whitespaces) throws IOException
    {
        while (true)
        {
            if (reader.line == null) return;  // stop at end of file
            // At this point, reader.whitespaces == whitespaces

            // Parse the line into key=value. Must pay special attention when multiple "=" are present
            String line = reader.line.trim ();
            String[] pieces = line.split ("=", -1);  // Negative limit means to create empty string in resulting array for any zero-length pieces (as opposed to ignoring them). The following algorithm depends on this.
            String key = pieces[0];
            int i = 1;
            for (; i < pieces.length; i++)  // Note: It is OK if everything goes in the index, leaving an empty value.
            {
                if (pieces[i].isEmpty ()  ||  key.endsWith ("=")  ||  key.endsWith ("<")  ||  key.endsWith (">")  ||  key.endsWith ("!"))
                {
                    key = key + "=" + pieces[i];
                    continue;
                }
                break;
            }
            String value = "";
            if (i < pieces.length)
            {
                value = pieces[i++];
                while (i < pieces.length) value = value + "=" + pieces[i++];
            }
            key   = key  .trim ();
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
            MNode child = node.set (value, key);  // Create a child with the given value
            if (reader.whitespaces > whitespaces) read (child, reader, reader.whitespaces);  // Recursively populate child. When this call returns, reader.whitespaces <= whitespaces in this function, because that is what ends the recursion.
            if (reader.whitespaces < whitespaces) return;  // end recursion
        }
    }

    /**
        Produces an indented hierarchical list of indices and values from entire tree.
        Note that this function is not exactly idempotent with read(). Rather, this function
        writes the node, then its children. read() only reads children. To use write() in
        a way that is exactly idempotent with read(), use a line like:
        <code>for (MNode c : NodeWhoseChildrenYouWantToWrite) write (c, writer, indent);</code>
    **/
    public void write (MNode node, Writer writer, String indent) throws IOException
    {
        String key   = node.key ();
        String value = node.get ();
        if (value.isEmpty ())
        {
            writer.write (String.format ("%s%s%n", indent, key));
        }
        else
        {
            String newLine = String.format ("%n");
            if (value.contains (newLine))  // go into extended text write mode
            {
                value = value.replace (newLine, newLine + indent + "  ");
                value = "|" + newLine + indent + "  " + value;
            }
            writer.write (String.format ("%s%s=%s%n", indent, key, value));
        }

        String space2 = indent + " ";
        for (MNode c : node) write (c, writer, space2);  // if this node has no children, nothing at all is written
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
            while (true)
            {
                line = reader.readLine ();
                if (line == null)  // end of file
                {
                    whitespaces = -1;
                    return;
                }
                if (line.isEmpty ()) continue;
                break;
            }

            // Count leading whitespace
            int length = line.length ();
            whitespaces = 0;
            while (whitespaces < length  &&  line.charAt (whitespaces) == ' ') whitespaces++;
        }

        public void close ()
        {
            if (reader == originalReader) return;
            try
            {
                reader.close ();
                reader = null;  // Poison it
            }
            catch (IOException e) {}
        }
    }
}

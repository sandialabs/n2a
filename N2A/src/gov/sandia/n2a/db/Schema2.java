/*
Copyright 2018-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.io.IOException;
import java.io.Writer;

public class Schema2 extends Schema1
{
    public Schema2 (int version, String type)
    {
        super (version, type);
    }

    public void read (MNode node, LineReader reader, int whitespaces) throws IOException
    {
        while (true)
        {
            if (reader.line == null) return;  // stop at end of file
            // At this point, reader.whitespaces == whitespaces
            // LineReader guarantees that line contains at least one character.

            // Parse the line into key=value.
            String line = reader.line.trim ();
            StringBuilder prefix = new StringBuilder ();
            String value = null;
            boolean escape =  ! line.isEmpty ()  &&  line.charAt (0) == '"';
            int i = escape ? 1 : 0;
            int last = line.length () - 1;
            for (; i <= last; i++)
            {
                char c = line.charAt (i);
                if (escape)
                {
                    if (c == '"')
                    {
                        // Look ahead for second quote
                        if (i < last  &&  line.charAt (i+1) == '"')
                        {
                            i++;
                        }
                        else
                        {
                            escape = false;
                            continue;
                        }
                    }
                }
                else
                {
                    if (c == ':')
                    {
                        value = line.substring (i+1).trim ();
                        break;
                    }
                }
                prefix.append (c);
            }
            String key = prefix.toString ().trim ();

            if (value != null  &&  value.startsWith ("|"))  // go into string reading mode
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
                        block.append ("\n");
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

    public void write (MNode node, Writer writer, String indent) throws IOException
    {
        String key = node.key ();
        if (key.startsWith ("\"")  ||  key.contains (":")  ||  key.isEmpty ())  // Could also trap if key starts with white space, but such cases are usually normalized by the UI.
        {
            key = "\"" + key.replace ("\"", "\"\"") + "\"";  // Using quote as its own escape, we avoid the need to escape a second code (such as both quote and backslash). This follows the example of YAML.
        }

        if (! node.data ())
        {
            writer.write (String.format ("%s%s%n", indent, key));
        }
        else
        {
            String value = node.get ();
            String newLine = String.format ("%n");
            if (value.contains ("\n")  ||  value.startsWith ("|"))  // go into extended text write mode
            {
                value = value.replace ("\n", newLine + indent + " ");
                value = "|" + newLine + indent + " " + value;
            }
            writer.write (String.format ("%s%s:%s%n", indent, key, value));
        }

        String space2 = indent + " ";
        for (MNode c : node) write (c, writer, space2);  // if this node has no children, nothing at all is written
    }
}

/*
Copyright 2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
    Simple JSON input/output for MNodes.
    This class has one minor limitation with respect to MNode: JSON nodes
    can have a value or children, but not both. To work around this, we treat
    a child key of "" (empty string) as holding the value of the node (but
    only when it has both a value and children). This means that "" can never
    be a proper child. This is such a rare case that it is a reasonable tradeoff
    to get richer representation of MNode.
**/
public class JSON
{
    public String tab = "  ";

    public void read (MNode node, Reader reader) throws IOException
    {
        BufferedReader br;
        boolean alreadyBuffered = reader instanceof BufferedReader;
        if (alreadyBuffered) br =    (BufferedReader) reader;
        else                 br = new BufferedReader (reader);
        read (node, br);
        if (! alreadyBuffered) br.close ();
    }

    /**
        Obtain either the value or children of the current node.
        This is the start point for reading a JSON file.
    **/
    public void read (MNode node, BufferedReader reader) throws IOException
    {
        StringBuilder number = null;
        while (true)
        {
            reader.mark (1);
            int i = reader.read ();
            if (i < 0) break;
            char c = (char) i;

            if (number != null)
            {
                // We don't check if these characters actually satisfy the grammar for number.
                // JSON doesn't support infinity or nan.
                if (c >= '0'  &&  c <= '9'  ||  c == '-'  ||  c == '+'  ||  c == '.'  ||  c == 'E'  ||  c == 'e')
                {
                    number.append (c);
                    continue;
                }
                else
                {
                    reader.reset ();
                    node.set (Double.valueOf (number.toString ()));
                    break;
                }
            }

            if (c == ' '  ||  c == '\t'  ||  c == '\r'  ||  c == '\n') continue;  // consume white space
            if (c == '{')
            {
                readChildren (node, reader);
            }
            else if (c == '[')  // start of array
            {
                readArray (node, reader);
            }
            else if (c == '"')  // string value
            {
                node.set (extractString (reader));
            }
            else if (c == 't')  // true
            {
                char[] buffer = new char[3];
                int count = reader.read (buffer);
                if (count < 3) throw new IOException ("Incomplete token");
                node.set (true);
            }
            else if (c == 'f')  // false
            {
                char[] buffer = new char[4];
                int count = reader.read (buffer);
                if (count < 4) throw new IOException ("Incomplete token");
                node.set (false);
            }
            else if (c == 'n')  // null
            {
                char[] buffer = new char[3];
                int count = reader.read (buffer);
                if (count < 3) throw new IOException ("Incomplete token");
                node.set (null);
            }
            else  // Only remaining type is number
            {
                number = new StringBuilder ();
                number.append (c);
                continue;
            }
            break;
        }
    }

    /**
        Starting with reader just after the curly brace, consume the key-values in this JSON object.
        Ends with the reader just after the closing curly brace.
    **/
    public void readChildren (MNode node, BufferedReader reader) throws IOException
    {
        int state = 0;  // looking for: 0=key, 1=colon, 3=comma
        String key = "";
        while (true)
        {
            int i = reader.read ();
            if (i < 0) break;
            char c = (char) i;
            if (c == ' '  ||  c == '\t'  ||  c == '\r'  ||  c == '\n') continue;  // consume white space
            if (c == '}') break;  // This could appear prematurely. We exit anyway.

            switch (state)
            {
                case 0:
                    if (c != '"') throw new IOException ("Expected string");
                    key = extractString (reader);
                    state = 1;
                    break;
                case 1:
                    if (c != ':') throw new IOException ("Expected colon");
                    MNode child = node.childOrCreate (key);
                    read (child, reader);
                    state = 2;
                    break;
                case 2:
                    if (c != ',') throw new IOException ("Expected comma or closing brace");  // The case of reaching the closing brace is covered above.
                    key = "";
                    state = 0;
                    break;
            }
        }

        // If there is a child with key "", convert it into node value.
        MNode child = node.child ("");
        if (child != null)
        {
            node.set (child.get ());
            node.clear ("");
        }
    }

    /**
        Starting with reader just after the square brace, consume array values.
        Ends with the reader just after the closing square brace.
        In this case, we only read children values, not keys. Keys will be
        created automatically as integers 0, 1, 2, ...
    **/
    public void readArray (MNode node, BufferedReader reader) throws IOException
    {
        int key = 0;
        while (true)
        {
            reader.mark (1);
            int i = reader.read ();
            if (i < 0) break;
            char c = (char) i;
            if (c == ' '  ||  c == '\t'  ||  c == '\r'  ||  c == '\n') continue;  // consume white space
            if (c == ']') break;  // This could appear prematurely. We exit anyway.

            if (c == ',')
            {
                key++;
                continue;
            }

            // We just read the first character of an element.
            reader.reset ();
            MNode child = node.childOrCreate (String.valueOf (key));
            read (child, reader);
        }
    }

    /**
        This is the start point for writing a JSON file.
        It can write either the value or children of node, depending on what is present.
        The children can either be a list or object.
    **/
    public void write (MNode node, Writer writer) throws IOException
    {
        writeValue (node, writer, "");
    }

    public void write (MNode node, Writer writer, String indent) throws IOException
    {
        writer.append (indent + escape (node.key ()));
        writer.append (": ");
        writeValue (node, writer, indent);
    }

    /**
        Picking up just after key and colon, this writes the value for a node.
        @param indent Leading space in front of the key for which we are writing the value.
        We will calculate further indent for children if needed.
    **/
    public void writeValue (MNode node, Writer writer, String indent) throws IOException
    {
        if (node.isEmpty ())  // No children
        {
            if (node.data ()) writer.append (convertValue (node));
            else              writer.append ("null");
        }
        else  // children
        {
            // Determine if this is an object or an array
            // An array has keys 0, 1, 2, ...
            // with no breaks in the sequence and no other kind of key.
            // A node that contains an array must not have a value.
            boolean isArray = ! node.data ();
            if (isArray)
            {
                int i = 0;
                for (MNode c : node)
                {
                    if (c.key ().equals (String.valueOf (i++))) continue;
                    isArray = false;
                    break;
                }
            }

            String indent2 = indent + tab;
            if (isArray)
            {
                writer.append ("[\n");
                boolean first = true;
                for (MNode c : node)  // if this node has no children, nothing at all is written
                {
                    if (! first) writer.append (",\n");   // Just use Unix line endings. Most text editors will work
                    writer.append (indent2);
                    writeValue (c, writer, indent2);
                    first = false;
                }
                writer.append ("\n" + indent + "]");
            }
            else  // object
            {
                writer.append ("{\n");
                if (node.data ()) writer.append (indent2 + "\"\": " + convertValue (node) + ",\n");  // Save node value as a fake child.
                writeChildren (node, writer, indent2);
                writer.append ("\n" + indent + "}");
            }
        }
    }

    public String convertValue (MNode node)
    {
        if (node instanceof MVolatile)
        {
            Object value = ((MVolatile) node).getObject ();
            if      (value instanceof Boolean) return (Boolean) value ? "true" : "false";   // No string conversion, so quote marks will be absent in output stream.
            else if (value instanceof Number)  return ((Number) value).toString ();
            return escape (value.toString ());
        }
        return escape (node.get ());
    }

    public void writeChildren (MNode node, Writer writer, String indent) throws IOException
    {
        boolean first = true;
        for (MNode c : node)  // if this node has no children, nothing at all is written
        {
            if (! first) writer.append (",\n");   // Just use Unix line endings. Most text editors will work
            write (c, writer, indent);
            first = false;
        }
    }

    /**
        Given an arbitrary string, convert to a JSON string, complete with opening and closing quote marks.
    **/
    public static String escape (String value)
    {
        StringBuilder result = new StringBuilder ();
        result.append ('"');
        int count = value.length ();
        for (int i = 0; i < count; i++)
        {
            char c = value.charAt (i);
            switch (c)
            {
                case '/':  result.append ("\\/");  break;
                case '\\': result.append ("\\\\"); break;
                case '"':  result.append ("\\\""); break;
                case '\b': result.append ("\\b");  break;
                case '\f': result.append ("\\f");  break;
                case '\n': result.append ("\\n");  break;
                case '\r': result.append ("\\r");  break;
                case '\t': result.append ("\\t");  break;
                default:   result.append (c);
            }
        }
        result.append ('"');
        return result.toString ();
    }

    /**
        Starting just after a quote mark has been extracted from reader, consumes
        characters until the quote closes. Leaves reader positioned just after the
        closing quote. Returns the extracted string with escapes converted back into
        regular characters.
    **/
    public static String extractString (Reader reader) throws IOException
    {
        StringBuilder result = new StringBuilder ();
        boolean inEscape = false;
        while (true)
        {
            int i = reader.read ();
            if (i < 0) break;
            char c = (char) i;
            if (inEscape)
            {
                switch (c)
                {
                    case 'b': result.append ("\b"); break;
                    case 'f': result.append ("\f"); break;
                    case 'n': result.append ("\n"); break;
                    case 'r': result.append ("\r"); break;
                    case 't': result.append ("\t"); break;
                    case 'u':
                        char[] buffer = new char[4];
                        int count = reader.read (buffer);
                        if (count < 4) throw new IOException ("Short read on hex string");
                        int codepoint = Integer.valueOf (new String (buffer), 16);
                        result.append ((char) codepoint);
                        break;
                    default:  result.append (c);  // Quote mark and both slashes. Could also allow malformed strings.
                }
                inEscape = false;
            }
            else if (c == '\\')
            {
                inEscape = true;
            }
            else if (c == '"')
            {
                break;
            }
            else
            {
                result.append (c);
            }
        }
        return result.toString ();
    }
}

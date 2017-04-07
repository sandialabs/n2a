/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.ref;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;

/**
    A simple recursive-descent parser for BibTeX files/strings.
**/
public class ParserBibtex
{
    public static Set<String> forms = new TreeSet<String> (Arrays.asList ("article", "book", "booklet", "conference", "inbook", "incollection", "inproceedings", "manual", "mastersthesis", "misc", "phdthesis", "proceedings", "techreport", "unpublished"));
    public static Set<String> ignore = new TreeSet<String> (Arrays.asList ("comment", "preamble"));

    public Map<String,String> strings = new TreeMap<String,String> ();

    public void parse (Reader input, MNode output)
    {
        BufferedReader reader;
        if (input instanceof BufferedReader) reader =    (BufferedReader) input;
        else                                 reader = new BufferedReader (input);

        try
        {
            while (parseEntry (reader, output));
            if (! (input instanceof BufferedReader)) reader.close ();
        }
        catch (IOException e)
        {
        }
    }

    public boolean parseEntry (BufferedReader reader, MNode output) throws IOException
    {
        // find next @ that's not inside a comment
        boolean inComment = false;
        while (true)
        {
            int c = reader.read ();
            if (c < 0) return false;
            if (inComment)
            {
                if (c == '\r'  ||  c == '\n') inComment = false;
                continue;
            }
            if (c == '@') break;
            if (c == '%') inComment = true;
        }

        // read string until opening brace
        String form = "";
        while (true)
        {
            int c = reader.read ();
            if (c < 0) return false;
            if (c == '{') break;
            form += (char) c;
        }
        form = form.trim ().toLowerCase ();

        if (form.equals ("string"))
        {
            parseString (reader);
        }
        else if (form.equals ("preamble"))
        {
            parseContent (reader);  // and ignore
        }
        else if (form.equals ("comment"))
        {
            parseBracedContent (reader, '}');  // and ignore
        }
        else
        {
            MNode tags = parseTags (reader);
            if (! ignore.contains (form))
            {
                tags.set ("form", form);
                output.set (tags.get (), tags);
            }
        }
        return true;  // Whether it's true or not, we will find out in the next parse cycle.
    }

    public void parseString (BufferedReader reader) throws IOException
    {
        // find =
        String name = "";
        while (true)
        {
            int c = reader.read ();
            if (c < 0) return;
            if (c == '=') break;
            name += (char) c;
        }

        // find "
        while (true)
        {
            int c = reader.read ();
            if (c < 0) return;
            if (c == '"') break;
        }

        String value = parseBracedContent (reader, '"');
        strings.put (name.trim (), value);

        // consume closing }
        while (true)
        {
            int c = reader.read ();
            if (c < 0  ||  c == '}') break;
        }
    }

    public MNode parseTags (BufferedReader reader) throws IOException
    {
        MNode result = new MVolatile ();

        // read to first ,
        String id = "";
        while (true)
        {
            int c = reader.read ();
            if (c < 0  ||  c == '}')
            {
                result.set (id.trim ());
                return result;
            }
            if (c == ',') break;
            id += (char) c;
        }
        result.set (id.trim ());  // TODO: should this be lower-case as well?

        while (parseTag (reader, result));
        return result;
    }

    public boolean parseTag (BufferedReader reader, MNode result) throws IOException
    {
        // read to =
        String name = "";
        while (true)
        {
            int c = reader.read ();
            if (c < 0) return false;
            if (c == '=') break;
            name += (char) c;
        }

        result.set (name.trim ().toLowerCase (), parseContent (reader));
        return true;
    }

    public String parseContent (BufferedReader reader) throws IOException
    {
        String result = "";
        String name = "";
        while (true)
        {
            reader.mark (1);
            int c = reader.read ();
            boolean done =  c < 0  ||  c == ','  ||  c == '}'; 
            if (done  ||  c == '#')
            {
                name = name.trim ();
                if (strings.containsKey (name)) result += strings.get (name);
                else                            result += name;
                if (done) return result;
                name = "";
                continue;
            }
            if (c == '{'  ||  c == '"') name += parseBracedContent (reader, c);
            else                        name += (char) c;
        }
    }

    public String parseBracedContent (BufferedReader reader, int endChar) throws IOException
    {
        String result = "";
        boolean inEscape = false;
        while (true)
        {
            int c = reader.read ();
            if (c < 0) return result;
            if (inEscape)
            {
                inEscape = false;
                result += (char) c;
                continue;
            }
            if (c == endChar  ||  c == '}') break;
            if (c == '{') result += parseBracedContent (reader, '}');
            else
            {
                result += (char) c;
                if (c == '\\') inEscape = true;
            }
        }
        return result;
    }
}

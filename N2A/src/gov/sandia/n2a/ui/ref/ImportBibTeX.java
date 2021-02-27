/*
Copyright 2017-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
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
public class ImportBibTeX extends ImportBibliography
{
    public static Set<String> forms = new TreeSet<String> (Arrays.asList ("article", "book", "booklet", "conference", "inbook", "incollection", "inproceedings", "manual", "mastersthesis", "misc", "phdthesis", "proceedings", "techreport", "unpublished"));

    @Override
    public String getName ()
    {
        return "BibTeX";
    }

    @Override
    public float matches (BufferedReader reader) throws IOException
    {
        for (int i = 0; i < 1000; i++)  // Limit how many lines to check before giving up. A very long-winded comment could prevent import.
        {
            String line = reader.readLine ();
            if (line == null) break;
            line = line.trim ();
            if (line.isEmpty ()) continue;
            if (line.startsWith ("%")) continue;  // comments
            if (! line.startsWith ("@"))  return 0;  // Every section of a BibTeX file must start with @, so anything else is malformed.
            line = line.substring (1).split ("\\{", 2)[0].trim ();
            if (line.equals ("string"))   return 1;
            if (line.equals ("preamble")) return 1;
            if (line.equals ("comment"))  return 1;
            if (forms.contains (line))    return 1;
        }
        return 0;
    }

    @Override
    public boolean accept (Path source)
    {
        if (Files.isDirectory (source)) return true;
        String name = source.getFileName ().toString ();
        int lastDot = name.lastIndexOf ('.');
        if (lastDot >= 0)
        {
            String suffix = name.substring (lastDot);
            if (suffix.equalsIgnoreCase (".bib"))    return true;
            if (suffix.equalsIgnoreCase (".bibtex")) return true;
        }
        return false;
    }

    @Override
    public void parse (BufferedReader input, MNode output) throws IOException
    {
        Parser parser = new Parser ();
        while (parser.parseEntry (input, output));
    }

    public class Parser
    {
        public Map<String,String> strings = new TreeMap<String,String> ();

        public boolean parseEntry (Reader reader, MNode output) throws IOException
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
            else  // entry
            {
                MNode tags = parseTags (reader);
                tags.set (form, "form");
                String key = tags.get ();
                tags.set (null);
                output.set (tags, key);
            }
            return true;  // Whether it's true or not, we will find out in the next parse cycle.
        }

        public void parseString (Reader reader) throws IOException
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

        public MNode parseTags (Reader reader) throws IOException
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

        public boolean parseTag (Reader reader, MNode result) throws IOException
        {
            // read to =
            String name = "";
            while (true)
            {
                int c = reader.read ();
                if (c < 0  ||  c == '}') return false;
                if (c == '=') break;
                name += (char) c;
            }

            result.set (parseContent (reader), name.trim ().toLowerCase ());
            return true;
        }

        public String parseContent (Reader reader) throws IOException
        {
            String result = "";
            String name = "";
            while (true)
            {
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
                if      (c == '{') name += parseBracedContent (reader, '}');
                else if (c == '"') name += parseBracedContent (reader, '"');
                else               name += (char) c;
            }
        }

        public String parseBracedContent (Reader reader, int endChar) throws IOException
        {
            String result = "";
            boolean inEscape = false;
            while (true)
            {
                int c = reader.read ();
                if (c < 0) return result;
                if (inEscape)
                {
                    // TODO: read ahead and process LaTeX escapes properly.
                    inEscape = false;
                    result += (char) c;
                    continue;
                }
                if (c == endChar  ||  c == '}') break;  // If '}' is encountered when we are looking for a different endChar, then there is a problem with the file. This is a defensive check.
                if (c == '{')
                {
                    result += parseBracedContent (reader, '}');
                }
                else
                {
                    result += (char) c;
                    if (c == '\\') inEscape = true;  // Yes, we really do copy in that slash in the previous line. For now, we aren't really handling escapes, just copying them verbatim in the import.
                }
            }
            return result;
        }
    }
}

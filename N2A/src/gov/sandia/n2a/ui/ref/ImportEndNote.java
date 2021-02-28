/*
Copyright 2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import gov.sandia.n2a.db.MNode;

public class ImportEndNote extends ImportBibliography
{
    protected static HashMap<String,String> forms = new HashMap<String,String> ();
    static
    {
        // All types not explicitly mapped will go to "misc"
        forms.put ("Book",                   "book");
        forms.put ("Book Section",           "inbook");
        forms.put ("Conference Paper",       "inproceedings");
        forms.put ("Conference Proceedings", "conference");
        forms.put ("Dictionary",             "book");
        forms.put ("Edited Book",            "book");
        forms.put ("Electronic Article",     "article");
        forms.put ("Electronic Book",        "book");
        forms.put ("Encyclopedia",           "book");
        forms.put ("Journal Article",        "article");
        forms.put ("Magazine Article",       "article");
        forms.put ("Manuscript",             "unpublished");
        forms.put ("Newspaper Article",      "article");
        forms.put ("Report",                 "techreport");
        forms.put ("Thesis",                 "phdthesis");  // could also be mastersthesis, but no way to distinguish
        forms.put ("Unpublished Work",       "unpublished");
    }

    @Override
    public String getName ()
    {
        return "EndNote";
    }

    @Override
    public float matches (BufferedReader reader) throws IOException
    {
        for (int i = 0; i < 100; i++)  // Only check the first 100 lines of the file.
        {
            String line = reader.readLine ();
            if (line == null) break;
            if (line.startsWith ("%0")) return 1;
            if (! line.trim ().isEmpty ()) return 0;  // File must start with %0. Anything else is malformed.
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
            if (suffix.equalsIgnoreCase (".enw")) return true;
        }
        return false;
    }

    @Override
    public void parse (BufferedReader input, MNode output) throws IOException
    {
        Parser parser = new Parser (output);
        parser.parse (input);
    }

    public class Parser
    {
        public MNode  output;
        public MNode  entry;  // current bibliographic entry being constructed
        public String key   = "";
        public String value = "";

        public Parser (MNode output)
        {
            this.output = output;
        }

        public void parse (BufferedReader input) throws IOException
        {
            while (true)
            {
                String line = input.readLine ();
                if (line == null) break;
                if (line.trim ().isEmpty ()) continue;

                if (! line.startsWith ("%"))  // add to existing tag
                {
                    value += line;  // not clear what to do with multi-line fields
                    continue;
                }
                else  // new tag
                {
                    if (! key.isEmpty ()) processField ();
                    key = line.substring (0, 2);
                    value = line.substring (3);
                }
            }
            if (! key.isEmpty ()) processField ();
        }

        public void processField ()
        {
            switch (key)
            {
                case "%0":  // Start a record
                    entry = output.childOrCreate (String.valueOf (output.size ()));
                    String form = forms.get (value);
                    if (form == null) form = "misc";
                    entry.set (form, "form");
                    break;
                case "%A":
                    String author = entry.get ("author");
                    author = mergeAuthor (author, value);
                    entry.set (author, "author");
                    break;
                case "%B":
                    switch (entry.get ("form"))
                    {
                        case "inproceedings":
                        case "incollection":
                            entry.set (value, "booktitle");
                            break;
                        case "article":
                            entry.set (value, "journal");
                            break;
                        case "book":
                        case "unpublished":
                            entry.set (value, "title");
                            break;
                        default:
                            entry.set (value, key);
                    }
                    break;
                case "%C":
                    entry.set (value, "address");
                    break;
                case "%D":
                    parseDate (entry, value);
                    break;
                case "%E":
                    author = entry.get ("editor");
                    author = mergeAuthor (author, value);
                    entry.set (author, "editor");
                    break;
                case "%F":  // not sure if "Label" field should really be treated as ID
                    output.move (entry.key (), value);
                    break;
                case "%I":
                    entry.set (value, "publisher");
                    break;
                case "%J":
                    entry.set (value, "journal");
                    break;
                case "%K":
                    String keywords = entry.get (key);
                    if (! keywords.isEmpty ()) keywords += "; ";
                    keywords += value;
                    entry.set (keywords, key);
                case "%N":
                    entry.set (value, "number");
                    break;
                case "%P":
                    entry.set (value, "pages");
                    break;
                case "%R":
                    entry.set (value, "doi");
                    break;
                case "%S":
                    entry.set (value, "series");
                    break;
                case "%T":
                    entry.set (value, "title");
                    break;
                case "%U":
                    entry.set (value, "url");
                    break;
                case "%V":
                    entry.set (value, "volume");
                    break;
                case "%Y":
                case "%?":
                    author = entry.get (key);
                    author = mergeAuthor (author, value);
                    entry.set (author, key);
                    break;
                case "%Z":
                case "%<":
                    String notes = entry.get ("note");
                    if (! notes.isEmpty ()) notes += "\n";
                    notes += value;
                    entry.set (notes, "note");
                    break;
                case "%7":
                    entry.set (value, "edition");
                    break;
                case "%8":
                    if (value.contains ("/")) parseDate (entry, value);
                    else                      entry.set (value, "month");
                    break;
                default:
                    entry.set (value, key);
            }
        }

        public String mergeAuthor (String A1, String A2)
        {
            if (A1.isEmpty ()) return A2;
            if (A2.isEmpty ()) return A1;
            return A1 + " and " + A2;
        }

        public void parseDate (MNode entry, String value)
        {
            // official format is YYYY/MM/DD/other info
            String pieces[] = value.split ("/");
            entry.set (pieces[0], "year");
            if (pieces.length > 1) entry.set (pieces[1], "month");
            if (pieces.length > 2) entry.set (pieces[2], "day");
            // Don't know what to do with "other info", so just ignore it.
        }
    }
}

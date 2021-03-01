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

public class ImportPubMed extends ImportBibliography
{
    protected static HashMap<String,String> forms = new HashMap<String,String> ();
    static
    {
        // If no type is given in record, it maps to "article".
        // If "PT" is given but there is no mapping here, it is ignored (and form will remain "article" or whatever it was before).
        // An entry can have multiple "PT" fields, not all of them relevant for selecting form.
        forms.put ("Autobiography",                         "book");
        forms.put ("Biography",                             "book");
        forms.put ("Clinical Conference",                   "conference");
        forms.put ("Collected Works",                       "book");
        forms.put ("Congress",                              "conference");
        forms.put ("Consensus Development Conference",      "conference");
        forms.put ("Consensus Development Conference, NIH", "conference");
        forms.put ("Dictionary",                            "book");
        forms.put ("Preprint",                              "unpublished");
        forms.put ("Technical Report",                      "techreport");
    }

    @Override
    public String getName ()
    {
        return "PubMed";
    }

    @Override
    public float matches (BufferedReader reader) throws IOException
    {
        for (int i = 0; i < 100; i++)  // Only check the first 100 lines of the file.
        {
            String line = reader.readLine ();
            if (line == null) break;
            if (line.startsWith ("PMID-")) return 1;
            if (! line.trim ().isEmpty ()) return 0;  // File must start with "PMID-". Anything else is malformed.
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
            if (suffix.equalsIgnoreCase (".txt")) return true;
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

                if (line.startsWith ("      "))  // add to existing tag
                {
                    value += line.substring (6);  // lines appear to end with space character when needed, so simply appending is OK.
                    continue;
                }
                else if (line.substring (4, 6).equals ("- "))  // new tag
                {
                    if (! key.isEmpty ()) processField ();
                    key = line.substring (0, 4).trim ();
                    value = line.substring (6);
                }
                else throw new IOException ("malformed file");
            }
            if (! key.isEmpty ()) processField ();
        }

        public void processField ()
        {
            switch (key)
            {
                case "PMID":  // Start a record
                    entry = output.childOrCreate (String.valueOf (value));
                    entry.set ("article", "form");
                    break;
                case "PT":
                    String form = forms.get (value);
                    if (form != null) entry.set (form, "form");
                    break;
                case "AID":
                    entry.set (value, "doi");
                    break;
                case "AU":
                case "CN":
                case "ED":
                case "FIR":
                case "FPS":
                case "IR":
                    String temp = entry.get (key);
                    temp = mergeAuthor (temp, value);
                    entry.set (temp, key);
                    break;
                case "FAU":
                    temp = entry.get ("author");
                    temp = mergeAuthor (temp, value);
                    entry.set (temp, "author");
                    break;
                case "BTI":
                    entry.set (value, "booktitle");
                    break;
                case "CP":
                    entry.set (value, "chapter");
                    break;
                case "CTI":
                    entry.set (value, "series");
                    break;
                case "DEP":
                    if (entry.get ("year").isEmpty ()) parseDate (entry, value);
                    else                               entry.set (value, key);
                    break;
                case "DP":
                    parseDate (entry, value);
                    break;
                case "EN":
                    entry.set (value, "edition");
                    break;
                case "FED":
                    temp = entry.get ("editor");
                    temp = mergeAuthor (temp, value);
                    entry.set (temp, "editor");
                    break;
                case "GN":
                    temp = entry.get ("note");
                    if (! temp.isEmpty ()) temp += "\n";
                    temp += value;
                    entry.set (temp, "note");
                    break;
                case "IP":
                    entry.set (value, "number");
                    break;
                case "JT":
                    entry.set (value, "journal");
                    break;
                case "MH":
                case "OT":
                    temp = entry.get (key);
                    if (! temp.isEmpty ()) temp += "; ";
                    temp += value;
                    entry.set (temp, key);
                    break;
                case "PB":
                    entry.set (value, "publisher");
                    break;
                case "PG":
                    entry.set (value, "pages");
                    break;
                case "PL":
                    entry.set (value, "address");
                    break;
                case "TI":
                    entry.set (value, "title");
                    break;
                case "VI":
                    entry.set (value, "volume");
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

/*
Copyright 2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.ref;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;

import gov.sandia.n2a.db.MNode;

public class ParserRIS implements Parser
{
    protected static HashMap<String,String> forms  = new HashMap<String,String> ();
    static
    {
        // All types not explicitly mapped will go to "misc"
        forms.put ("BOOK",    "book");
        forms.put ("CHAP",    "inbook");
        forms.put ("CLSWK",   "book");
        forms.put ("CONF",    "conference");
        forms.put ("CPAPER",  "inproceedings");
        forms.put ("DICT",    "book");
        forms.put ("EBOOK",   "book");
        forms.put ("ECHAP",   "inbook");
        forms.put ("EDBOOK",  "book");
        forms.put ("EJOUR",   "article");
        forms.put ("ENCYC",   "book");
        forms.put ("INPR",    "unpublished");
        forms.put ("JFULL",   "article");
        forms.put ("JOUR",    "article");
        forms.put ("MANSCPT", "unpublished");
        forms.put ("MGZN",    "article");
        forms.put ("RPRT",    "techreport");
        forms.put ("THES",    "phdthesis");
        forms.put ("UNPB",    "unpublished");
    }

    protected MNode entry;  // current bibliographic entry being constructed

    public void parse (BufferedReader input, MNode output) throws IOException
    {
        String key   = "";
        String value = "";
        boolean done = false;
        while (! done)
        {
            String line = input.readLine ();
            if (line == null)
            {
                line = "ER  - ";  // not really used; just flushes out the last proper field
                done = true;
            }
            else
            {
                if (line.trim ().isEmpty ()) continue;
            }

            if (line.length () < 6  ||  ! line.substring (2, 6).equals ("  - "))  // add to existing tag
            {
                value += line;  // not clear what to do with multi-line fields
                continue;
            }
            else  // new tag
            {
                if (! key.isEmpty ()) processField (output, key, value);
                key = line.substring (0, 2);
                value = line.substring (6);
            }
        }
    }

    public void processField (MNode output, String key, String value)
    {
        switch (key)
        {
            case "TY":  // Start a record
                entry = output.childOrCreate (String.valueOf (output.size ()));
                String form = forms.get (value);
                if (form == null) form = "misc";
                entry.set (form, "form");
                break;
            case "ER":  // Finish a record
                // Merge page number fields
                String SP = entry.get ("SP");
                String EP = entry.get ("EP");
                if (! SP.isEmpty ()  ||  ! EP.isEmpty ()) entry.set (SP + "--" + EP, "pages");
                entry.clear ("SP");
                entry.clear ("EP");
                break;
            case "ID":
                output.move (entry.key (), value);
                break;
            case "A1":
            case "AU":
                String author = entry.get ("author");
                author = mergeAuthor (author, value);
                entry.set (author, "author");
                break;
            case "A2":
            case "ED":
                author = entry.get ("editor");
                author = mergeAuthor (author, value);
                entry.set (author, "editor");
                break;
            case "A3":
            case "A4":
                author = entry.get (key);
                author = mergeAuthor (author, value);
                entry.set (author, key);
                break;
            case "SP":
            case "EP":
                entry.set (value, key);
                break;
            case "DA":
                if (value.contains ("/")) parseDate (entry, value);
                else                      entry.set (value, "month");
                break;
            case "Y1":
            case "PY":
                parseDate (entry, value);
                break;
            case "CY":
            case "PP":
            case "AD":
                entry.set (value, "address");
                break;
            case "DO":
                entry.set (value, "doi");
                break;
            case "ET":
                entry.set (value, "edition");
                break;
            case "CP":
            case "IS":
            case "M1":
                entry.set (value, "number");
                break;
            case "J1":
            case "J2":
            case "JA":
            case "JF":
            case "JO":
                entry.set (value, "journal");
                break;
            case "PB":
                entry.set (value, "publisher");
                break;
            case "T1":
            case "TI":
            case "CT":
                entry.set (value, "title");
                break;
            case "BT":
            case "T2":
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
            case "T3":
                entry.set (value, "series");
                break;
            case "VL":
                entry.set (value, "volume");
                break;
            case "LK":
            case "UR":
                entry.set (value, "url");
                break;
            case "KW":
                String keywords = entry.get (key);
                if (! keywords.isEmpty ()) keywords += "; ";
                keywords += value;
                entry.set (keywords, key);
            case "N1":
                entry.set (value, "note");
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

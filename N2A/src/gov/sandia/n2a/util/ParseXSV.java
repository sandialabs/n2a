/*
Copyright 2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
    See OutputParser for redundant implementations of the XSV parser.
**/
public abstract class ParseXSV
{
    public char delimiter = ' ';  // space char, initially
    public int  columns   = 1;

    public void parse (BufferedReader reader) throws IOException
    {
        boolean delimiterSet = false;
        while (true)
        {
            String line = reader.readLine ();
            if (line == null) break;  // indicates end of stream
            if (line.length () == 0) continue;

            char chars[] = line.toCharArray ();
            if (! delimiterSet)
            {
                // Scan for first delimiter character that is not inside a quote.
                boolean inQuote = false;
                for (char c : chars)
                {
                    if (c == '\"')
                    {
                        inQuote = ! inQuote;
                        continue;
                    }
                    if (inQuote) continue;
                    if (c == '\t')
                    {
                        delimiter = c;
                        break;
                    }
                    if (c == ',') delimiter = c;
                    // space character is lowest precedence
                }
                delimiterSet =  delimiter != ' '  ||  ! line.isBlank ();
            }

            // Break line into delimited strings, possibly quoted.
            List<String> parts = new ArrayList<String> (columns);
            boolean inQuote = false;
            StringBuilder token = new StringBuilder ();
            for (int i = 0; i < chars.length; i++)
            {
                char c = chars[i];
                if (c == '\"')
                {
                    if (inQuote  &&  i < chars.length - 1  &&  chars[i+1] == '\"')
                    {
                        token.append (c);
                        i++;
                        continue;
                    }
                    inQuote = ! inQuote;
                    continue;
                }
                if (c == delimiter  &&  ! inQuote)
                {
                    parts.add (token.toString ());
                    token.setLength (0);
                    continue;
                }
                token.append (c);
            }
            if (! token.isEmpty ()) parts.add (token.toString ());
            columns = Math.max (parts.size (), columns);

            // Process line.
            boolean keepGoing = processLine (parts);
            if (! keepGoing) break;
        }
    }

    /**
        @param parts The columns found on the current row.
        @return true to continue parsing the file. false to stop early.
    **/
    public abstract boolean processLine (List<String> parts);
}

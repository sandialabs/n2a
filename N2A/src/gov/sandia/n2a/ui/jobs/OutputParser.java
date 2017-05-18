/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OutputParser
{
    public class Column
    {
    	public String header = "";
    	public List<Double> values = new ArrayList<Double> ();
    	public int startRow;
    	public int textWidth;
    }
    public List<Column> columns = new ArrayList<Column> ();
    public boolean      isXycePRN;
    public Column       time;

    public void parse (File f)
    {
        parse (f, 0.0);
    }

    public void parse (File f, double defaultValue)
    {
        columns = new ArrayList<Column> ();
        isXycePRN = false;
        time = null;

        try
        {
            int row = 0;
            BufferedReader br = new BufferedReader (new FileReader (f));
            while (true)
            {
                String line = br.readLine ();
                if (line == null) break;  // indicates end of stream

                line = line.trim ();
            	if (line.length () == 0) continue;
            	if (line.startsWith ("End of")) continue;

                String[] parts = line.split ("\\s");
                int lastSize = columns.size ();
                while (columns.size () < parts.length)
                {
                	Column c = new Column ();
                	c.startRow = row;
                	columns.add (c);
                }

                char firstCharacter = parts[0].charAt (0);
                if (firstCharacter < '0'  ||  firstCharacter > '9')  // column header
                {
            		isXycePRN = parts[0].equals ("Index");
                    for (int p = lastSize; p < parts.length; p++)
                    {
                    	columns.get (p).header = parts[p];
                    }
                }
                else
                {
                	int p = isXycePRN ? 1 : 0;  // skip parsing Index column, since we don't use it
                    for (; p < parts.length; p++)
                    {
                        Column c = columns.get (p);
                        double value = defaultValue;
                        if (! parts[p].isEmpty ())
                        {
                            value = Double.parseDouble (parts[p]);
                            c.textWidth = Math.max (c.textWidth, parts[p].length ());
                        }
                        c.values.add (value);
                    }
                    row++;
                }
            }
            br.close ();
        }
        catch (IOException e)
        {
		}

        if (columns.size () == 0) return;
        time = columns.get (0);  // fallback, in case we don't find it by name
        int timeMatch = 0;
        for (Column c : columns)
        {
            int potentialMatch = 0;
            if      (c.header.equals ("t"   )) potentialMatch = 1;
            else if (c.header.equals ("TIME")) potentialMatch = 2;
            else if (c.header.equals ("$t"  )) potentialMatch = 3;
            if (potentialMatch > timeMatch)
            {
                timeMatch = potentialMatch;
                time = c;
            }
        }
    }
}

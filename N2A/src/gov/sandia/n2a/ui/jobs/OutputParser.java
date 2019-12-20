/*
Copyright 2017-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class OutputParser
{
    public List<Column> columns = new ArrayList<Column> ();
    public boolean      raw;        // Indicates that all column names are empty, likely the result of output() in raw mode.
    public boolean      isXycePRN;
    public Column       time;
    public boolean      timeFound;  // Indicates that time is a properly-labeled column, rather than a fallback.

    public void parse (Path f)
    {
        parse (f, 0.0f);
    }

    public void parse (Path f, float defaultValue)
    {
        columns = new ArrayList<Column> ();
        raw = true;  // Will be negated if any non-empty column name is found.
        isXycePRN = false;
        time = null;

        try (BufferedReader br = Files.newBufferedReader (f))
        {
            int row = 0;
            while (true)
            {
                String line = br.readLine ();
                if (line == null) break;  // indicates end of stream
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
                    raw = false;
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
                        float value = defaultValue;
                        if (! parts[p].isEmpty ())
                        {
                            value = Float.parseFloat (parts[p]);
                            c.textWidth = Math.max (c.textWidth, parts[p].length ());
                        }
                        c.values.add (value);
                    }
                    for (; p < columns.size (); p++) columns.get (p).values.add (defaultValue);  // Because the structure is not sparse, we must fill out every row.
                    row++;
                }
            }
        }
        catch (IOException e)
        {
		}
        if (columns.size () == 0) return;

        // If there is a separate columns file, open and parse it.
        Path jobDir = f.getParent ();
        Path columnFile = jobDir.resolve (f.getFileName ().toString () + ".columns");
        try (BufferedReader br = Files.newBufferedReader (columnFile))
        {
            int columnIndex = 0;
            String line;
            while (columnIndex < columns.size ()  &&  (line = br.readLine ()) != null)
            {
                Column c = columns.get (columnIndex++);
                if (c.header.isEmpty ()) c.header = line;
            }
        }
        catch (IOException e)
        {
        }

        // Determine time column
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
                timeFound = true;
            }
        }

        // Get rid of Index column. No subclass uses it.
        if (isXycePRN) columns.remove (0);
    }

    public boolean hasData ()
    {
        for (Column c : columns) if (! c.values.isEmpty ()) return true;
        return false;
    }

    public static class Column
    {
        public String      header = "";
        public int         index;  // If this is a spike raster, then header should convert to an integer.
        public List<Float> values = new ArrayList<Float> ();
        public int         startRow;
        public int         textWidth;
        public double      min    = Double.POSITIVE_INFINITY;
        public double      max    = Double.NEGATIVE_INFINITY;
        public double      range;

        public void computeStats ()
        {
            for (Float f : values)
            {
                if (f.isInfinite ()  ||  f.isNaN ()) continue;
                min = Math.min (min, f);
                max = Math.max (max, f);
            }
            if (Double.isInfinite (max))  // There was no good data. If max is infinite, then so is min.
            {
                // Set defensive values, so plot doesn't explode
                range = 0;
                min   = 0;
                max   = 0;
            }
            else
            {
                range = max - min;
            }
        }
    }

    public static class ColumnComparator implements Comparator<Column>
    {
        public int compare (Column a, Column b)
        {
            if (a.range > b.range) return  1;
            if (a.range < b.range) return -1;
            return 0;
        }
    }
}

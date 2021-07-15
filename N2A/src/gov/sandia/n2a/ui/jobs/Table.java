/*
Copyright 2017-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.Component;
import java.awt.FontMetrics;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MVolatile;
import gov.sandia.n2a.language.type.Scalar;

public class Table extends OutputParser
{
    int rows;

    public Table (Path path, boolean sorted)
    {
    	parse (path, Float.NaN);
    	for (Column c : columns) rows = Math.max (rows, c.startRow + c.values.size ());

    	int t = columns.indexOf (time);
    	if (t > 0)
    	{
    	    columns.remove (t);
    	    columns.add (0, time);
    	}

    	int count = columns.size ();
    	if (sorted  &&  count > 1  &&  ! raw)
    	{
    	    // By using an MNode to sort, we get the columns in M order (numbers first, in truly numerical order, followed alphabetical order)
    	    MVolatile mapping = new MVolatile ();
    	    for (int i = 1; i < count; i++) mapping.set (i, columns.get (i).header);
    	    ArrayList<Column> sortedColumns = new ArrayList<Column> (count);
    	    sortedColumns.add (columns.get (0));  // time always goes first
    	    for (MNode m : mapping) sortedColumns.add (columns.get (m.getInt ()));
    	    columns = sortedColumns;
    	}
    }

    public void dump (Path destination) throws IOException
    {
        dump (destination, "\t");
    }

    public void dumpCSV (Path destination) throws IOException
    {
        dump (destination, ",");
    }

    public void dump (Path destination, String separator) throws IOException
    {
        if (columns.isEmpty ()) return;
        try (BufferedWriter writer = Files.newBufferedWriter (destination))
        {
            Column last = columns.get (columns.size () - 1);
            String eol = String.format ("%n");
            if (hasHeaders ())
            {
                for (Column c : columns)
                {
                    writer.write (c.header);
                    if (c == last) writer.write (eol);
                    else           writer.write (separator);
                }
            }
            if (hasData ())
            {
                int rows = 0;
                for (Column c : columns) rows = Math.max (rows, c.startRow + c.values.size ());

                for (int r = 0; r < rows; r++)
                {
                    for (Column c : columns)
                    {
                        writer.write (String.valueOf (c.get (r)));
                        if (c == last) writer.write (eol);
                        else           writer.write (separator);
                    }
                }
            }
        }
    }

    public Component createVisualization ()
    {
        return new OutputTable ();
    }

    @SuppressWarnings("serial")
    public class OutputTable extends JTable
    {
        public OutputTable ()
        {
            super (new OutputTableModel ());

            setAutoResizeMode (JTable.AUTO_RESIZE_OFF);
            ((DefaultTableCellRenderer) getTableHeader ().getDefaultRenderer ()).setHorizontalAlignment (JLabel.LEFT);
        }

        public void updateUI ()
        {
            super.updateUI ();

            FontMetrics fm = getFontMetrics (getFont ());

            setRowHeight (fm.getHeight () + getRowMargin ());

            int digitWidth = fm.charWidth ('0');
            int maxTextWidth = 10;
            for (int i = 0; i < columns.size (); i++) maxTextWidth = Math.max (maxTextWidth, columns.get (i).textWidth);
            maxTextWidth *= digitWidth * 2;

            TableColumnModel cols = getColumnModel ();
            for (int i = 0; i < columns.size (); i++)
            {
                Column c = columns.get (i);
                int width = Math.max (digitWidth * c.textWidth, fm.stringWidth (c.header) + digitWidth);
                width = Math.min (width, maxTextWidth);  // Avoid absurdly wide columns, because it's hard to read table.
                cols.getColumn (i).setPreferredWidth (width);
            }
        }
    }

    @SuppressWarnings("serial")
    public class OutputTableModel extends AbstractTableModel
    {
        public int getRowCount ()
        {
            return rows;
        }

        public int getColumnCount ()
        {
            return columns.size ();
        }

        public String getColumnName (int column)
        {
            return columns.get (column).header;
        }

        public Object getValueAt (int row, int column)
        {
            Column c = columns.get (column);
            row -= c.startRow;
            if (row < 0  ||  row >= c.values.size ()) return "";
            float result = c.values.get (row);
            if (Float.isNaN (result)) return "";
            return Scalar.print (result);
        }
    }
}

/*
Copyright 2017-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.Component;
import java.awt.FontMetrics;
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
    	parse (path, Double.NaN);
    	for (Column c : columns) rows = Math.max (rows, c.startRow + c.values.size ());

    	if (isXycePRN) columns.remove (0);  // get rid of Index column
    	int t = columns.indexOf (time);
    	if (t > 0)
    	{
    	    columns.remove (t);
    	    columns.add (0, time);
    	}

    	int count = columns.size ();
    	if (sorted  &&  count > 1)
    	{
    	    // By using an MNode to sort, we get the columns in M order (numbers first, in truly numerical order, followed alphabetical order)
    	    MVolatile mapping = new MVolatile ();
    	    for (int i = 1; i < count; i++) mapping.set (columns.get (i).header, i);
    	    ArrayList<Column> sortedColumns = new ArrayList<Column> (count);
    	    sortedColumns.add (columns.get (0));
    	    for (MNode m : mapping) sortedColumns.add (columns.get (m.getInt ()));
    	    columns = sortedColumns;
    	}
    }

    public Component createVisualization ()
    {
        JTable result = new JTable (new OutputTableModel ());
        result.setAutoResizeMode (JTable.AUTO_RESIZE_OFF);
        ((DefaultTableCellRenderer) result.getTableHeader ().getDefaultRenderer ()).setHorizontalAlignment (JLabel.LEFT);

        FontMetrics fm = result.getFontMetrics (result.getFont ());
        int digitWidth = fm.charWidth ('0');

        int maxTextWidth = 10;
        for (int i = 0; i < columns.size (); i++) maxTextWidth = Math.max (maxTextWidth, columns.get (i).textWidth);
        maxTextWidth *= digitWidth * 2;

        TableColumnModel cols = result.getColumnModel ();
        for (int i = 0; i < columns.size (); i++)
        {
            Column c = columns.get (i);
            int width = Math.max (digitWidth * c.textWidth, fm.stringWidth (c.header) + digitWidth);
            width = Math.min (width, maxTextWidth);  // Avoid absurdly wide columns, because it's hard to read table.
            cols.getColumn (i).setPreferredWidth (width);
        }

        return result;
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
            double result = c.values.get (row);
            if (Double.isNaN (result)) return "";
            return Scalar.print (result);
        }
    }
}

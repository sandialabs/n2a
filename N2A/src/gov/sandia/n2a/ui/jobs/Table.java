/*
Copyright 2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.jobs;

import java.awt.Component;
import java.awt.FontMetrics;
import java.io.File;

import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;

public class Table extends OutputParser
{
    int rows;

    public Table (String path)
    {
    	parse (new File (path), Double.NaN);
    	for (Column c : columns) rows = Math.max (rows, c.startRow + c.values.size ());
    	if (isXycePRN) columns.remove (0);  // get rid of Index column
    	int t = columns.indexOf (time);
    	if (t > 0)
    	{
    	    columns.remove (t);
    	    columns.add (0, time);
    	}
    }

    public Component createVisualization ()
    {
        JTable result = new JTable (new OutputTableModel ());
        result.setAutoResizeMode (JTable.AUTO_RESIZE_OFF);

        FontMetrics fm = result.getFontMetrics (result.getFont ());
        int digitWidth = fm.charWidth ('0');
        TableColumnModel cols = result.getColumnModel ();
        for (int i = 0; i < columns.size (); i++)
        {
            Column c = columns.get (i);
            cols.getColumn (i).setPreferredWidth (Math.max (digitWidth * c.textWidth, fm.stringWidth (c.header) + digitWidth));
        }

        return result;
    }

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
            return result;
        }
    }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.search;

import gov.sandia.umf.platform.AppState;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;

public class SearchTable extends JTable {


    ////////////
    // FIELDS //
    ////////////

    private DefaultTableCellRenderer regularRenderer = new DefaultTableCellRenderer();
    private DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public SearchTable(TableModel dm, int sel) {
        super(dm);
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        setSelectionMode(sel);
        setFillsViewportHeight(true);
        setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        setShowUids (AppState.getState ().getBoolean (false, "ShowUids"));
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // Mutators

    public void setShowUids(boolean show) {
        if(show) {
            int[][] widths = new int[][] {{35, 35, 35}, {30, 140, 300},{110, 110, 110},{110, 110, 110}, {-1, -1, -1}};
            redoColumns(widths);
            getColumnModel().getColumn(0).setCellRenderer( regularRenderer );
            getColumnModel().getColumn(1).setCellRenderer( regularRenderer );
            getColumnModel().getColumn(2).setCellRenderer( centerRenderer );
        } else {
            int[][] widths = new int[][] {{30, 140, 300},{110, 110, 110},{110, 110, 110}, {-1, -1, -1}};
            redoColumns(widths);
            getColumnModel().getColumn(0).setCellRenderer( regularRenderer );
            getColumnModel().getColumn(1).setCellRenderer( centerRenderer );
            getColumnModel().getColumn(2).setCellRenderer( regularRenderer );
        }
    }


    //////////
    // MISC //
    //////////

    private void redoColumns(int[][] widths) {
        for(int r = 0; r < widths.length; r++) {
            int[] ww = widths[r];
            if(ww[0] != -1) {
                getColumnModel().getColumn(r).setMinWidth(ww[0]);
            }
            if(ww[1] != -1) {
                getColumnModel().getColumn(r).setPreferredWidth(ww[1]);
            }
            if(ww[2] != -1) {
                getColumnModel().getColumn(r).setMaxWidth(ww[2]);
            }

            int cw = getColumnModel().getColumn(r).getWidth();
            if(cw < ww[0]) {
                getColumnModel().getColumn(r).setWidth(ww[0]);
            }
            if(cw > ww[2]) {
                getColumnModel().getColumn(r).setWidth(ww[2]);
            }
        }
    }
}

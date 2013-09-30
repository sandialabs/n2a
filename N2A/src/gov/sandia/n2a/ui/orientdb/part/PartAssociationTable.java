/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.part;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableCellRenderer;

import replete.event.ChangeNotifier;

public class PartAssociationTable extends JTable {


    //////////////
    // NOTIFIER //
    //////////////

    protected ChangeNotifier emptyClickNotifier = new ChangeNotifier(this);
    public void addEmptyClickListener(ChangeListener listener) {
        emptyClickNotifier.addListener(listener);
    }
    protected void fireEmptyClickNotifier() {
        emptyClickNotifier.fireStateChanged();
    }


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public PartAssociationTable(PartAssociationTableModel model) {
        super(model);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setFillsViewportHeight(true);

        setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        int[][] widths = new int[][] {{-1, 80, 80}, {-1, 140, 140},{110, 110, 110},{110, 110, 110}, {-1, -1, -1}};
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
        }

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        getColumnModel().getColumn(1).setCellRenderer( centerRenderer );
        getColumnModel().getColumn(2).setCellRenderer( centerRenderer );

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if(SwingUtilities.isLeftMouseButton(e) &&
                        e.getClickCount() == 2 &&
                        rowAtPoint(e.getPoint()) == -1 /* && bdoesn't matter if selected or not */) {
                    fireEmptyClickNotifier();
                }
            }
        });
    }
}

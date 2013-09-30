/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.partadv;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;

import javax.swing.DefaultCellEditor;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableCellRenderer;

import replete.gui.table.SimpleTable;

public class AdvancedEquationTable extends SimpleTable {


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public AdvancedEquationTable(final AdvancedEquationTableModel model) {
        super(model);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setFillsViewportHeight(true);

        setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        int[][] widths = new int[][] {{-1, -1, -1}, {120, 120, 120}};
        setColumnWidths(widths);

        DefaultTableCellRenderer valueRenderer = new DefaultTableCellRenderer();
        valueRenderer.setHorizontalAlignment(JLabel.LEFT);
        getColumnModel().getColumn(0).setCellRenderer( valueRenderer );
        DefaultTableCellRenderer sourceRenderer = new DefaultTableCellRenderer();
        sourceRenderer.setHorizontalAlignment(JLabel.CENTER);
        getColumnModel().getColumn(1).setCellRenderer( sourceRenderer );

        addEmptyClickListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                NDoc dEq = new NDoc("gov.sandia.umf.n2a$Equation");
                dEq.set("value", "VAR = VALUE");
                model.addEquation(dEq);

                AdvancedEquationTable.this.editCellAt(model.getRowCount() - 1, 0);
                JTextField txt = (JTextField) ((DefaultCellEditor) AdvancedEquationTable.this.getCellEditor()).getComponent();
                txt.requestFocusInWindow();
                txt.selectAll();
            }
        });
    }
}

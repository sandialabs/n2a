/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.ensemble.specs;

import gov.sandia.n2a.parms.ParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.ListParameterSpecification;
import gov.sandia.umf.platform.ui.ensemble.images.ImageUtil;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.table.DefaultTableModel;

import replete.gui.controls.IconButton;
import replete.gui.table.SimpleTable;
import replete.util.Lay;
import replete.util.NumUtil;

public class ListParamSpecDefPanel extends ParamSpecDefEditPanel {


    ///////////
    // FIELD //
    ///////////

    private SimpleTable tblValues;
    private DefaultTableModel mdlValues;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ListParamSpecDefPanel() {
        // TODO: This is really more of a class-level thing, but
        // how to get polymorphism to work at that level?
        ListParameterSpecification spec = new ListParameterSpecification();

        tblValues = new SimpleTable(mdlValues = new DefaultTableModel(new Object[0][0], new Object[] {"Values"}));
        tblValues.setTableHeader(null);
        tblValues.setRowHeight(30);
        tblValues.setFont(tblValues.getFont().deriveFont(16F));
        tblValues.setBackground(Color.white);
        tblValues.setFillsViewportHeight(true);
        tblValues.setShowHorizontalLines(false);
        tblValues.setShowVerticalLines(false);
        tblValues.setUseStandardDeleteBehavior(true);

        IconButton btnAdd = new IconButton(ImageUtil.getImage("add.gif"), "Add Value...", 2);
        IconButton btnRemove = new IconButton(ImageUtil.getImage("remove.gif"), "Remove Values", 2);

        btnAdd.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                mdlValues.addRow(new Object[] {"<VALUE>"});
                int size = mdlValues.getRowCount();
                tblValues.getSelectionModel().setSelectionInterval(size - 1, size - 1);
            }
        });
        btnRemove.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                tblValues.removeSelected();
            }
        });

        Lay.BLtg(this,
            "N", Lay.BL(
                "W", Lay.lb(ImageUtil.getImage("inf.gif"), "valign=top,eb=5r"),
                "C", Lay.lb("<html>" + spec.getDescription() + "</html>")
            ),
            "C", Lay.BL(
                "W", Lay.BL(
                    "N", Lay.lb("Values:"),
                    "C", Lay.sp(tblValues, "prefw=150"),
                    "E", Lay.BxL("Y",
                        Lay.BL(btnAdd, "eb=5bl,maxH=20"),
                        Lay.BL(btnRemove, "eb=5bl,maxH=20"),
                        Box.createVerticalGlue()
                    )
                ),
                /*"C", /*
                "N", Lay.FL("L",
                    Lay.lb("Values:", "eb=10r,dim=[60,30]"),
                    Lay.hn(txtValues = new SelectAllTextField(), "dim=[100,30],size=16"),
                ),*/
                "eb=10t"
            )
        );
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    @Override
    public ParameterSpecification getSpecification() {
        List<Object> ds = new ArrayList<Object>();
        for(int r = 0; r < mdlValues.getRowCount(); r++) {
            Object value = mdlValues.getValueAt(r, 0);
            if(NumUtil.isDouble((String) value)) {
                value = Double.parseDouble((String) value);
            }
            ds.add(value);
        }
        return new ListParameterSpecification(ds.toArray());
    }

    @Override
    public String getValidationMsg() {
//        if(!NumUtil.isDouble(txtValues.getText().trim())) {
//            txtValues.requestFocusInWindow();
//            return "Invalid value.  List elements must be a number.";
//        }
        return null;
    }

    @Override
    public void setSpecification(ParameterSpecification spec) {
        ListParameterSpecification xspec = (ListParameterSpecification) spec;
        for(Object value : xspec.getValues()) {
            mdlValues.addRow(new Object[] {value + ""});
        }
    }
}

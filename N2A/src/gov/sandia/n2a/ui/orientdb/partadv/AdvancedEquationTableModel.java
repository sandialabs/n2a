/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.partadv;

import gov.sandia.n2a.eqset.EquationAssembler;
import gov.sandia.n2a.eqset.PartEquationMap;
import gov.sandia.n2a.parsing.ParsedEquation;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;

import java.util.ArrayList;
import java.util.List;

import replete.gui.table.MinimalAbstractTableModel;

public class AdvancedEquationTableModel extends MinimalAbstractTableModel {


    ////////////
    // FIELDS //
    ////////////

    // Const

    private static final String[] COLUMNS = {"Equation", "Source"};
    private boolean includeInherited;
    private boolean includeOverridden;
    private boolean includeChildren;

    // State

    private NDoc source;
    private List<NDoc> eqs = new ArrayList<NDoc>();


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public AdvancedEquationTableModel() {
    }


    ////////////
    // CUSTOM //
    ////////////

    // Accessors

    public List<NDoc> getEquations() {
        return eqs;
    }
    public NDoc getEquation(int index) {
        return eqs.get(index);
    }

    // Mutators

    public void addEquation(NDoc newEq) {
        eqs.add(newEq);
        // TODO: Edit SOURCE?
        source.set("eqs", eqs);
        fireTableDataChanged();
    }
//    public void removeEquation(int index) {
//        eqs.remove(index);
//        fireTableDataChanged();
//    }

    public void setEquations(NDoc source) {
        this.source = source;
        rebuildFromSource();
    }

    private void rebuildFromSource() {
        eqs.clear();
        PartEquationMap pem = EquationAssembler.getAssembledPartEquations(source);
        for(String s : pem.keySet()) {
            List<ParsedEquation> peqs = pem.get(s);
            for(ParsedEquation peq : peqs) {
                eqs.add((NDoc) peq.getMetadata("eq"));
            }
        }
        fireTableDataChanged();
    }
    public void setIncludeInherited(boolean includeInherited) {
        this.includeInherited = includeInherited;
        rebuildFromSource();
    }
    public void setIncludeOverridden(boolean includeOverridden) {
        this.includeOverridden = includeOverridden;
        rebuildFromSource();
    }
    public void setIncludeChildren(boolean includeChildren) {
        this.includeChildren = includeChildren;
        rebuildFromSource();
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////


    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }
    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }
    @Override
    public String getColumnName(int columnIndex) {
        return COLUMNS[columnIndex];
    }
    @Override
    public int getRowCount() {
        if(eqs == null) {
            return 0;
        }
        return eqs.size();
    }
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if(rowIndex == -1) {
            return null;
        }

        NDoc eq = eqs.get(rowIndex);

        switch(columnIndex) {
            case 0: return eq.get("value");
            case 1: return "Local";
        }

        return null;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 0 && rowIndex != -1;
    };

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        NDoc eq = eqs.get(rowIndex);
        String newValue = ((String) value).trim();
        System.out.println("EQ=" + eq + ", name=" + newValue);
        eq.set("value", newValue);
        fireTableDataChanged();
    }
}

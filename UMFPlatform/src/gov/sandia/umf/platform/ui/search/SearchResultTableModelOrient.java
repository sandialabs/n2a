/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.search;

import gov.sandia.umf.platform.AppState;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;

import java.util.ArrayList;
import java.util.List;

import replete.gui.table.MinimalAbstractTableModel;
import replete.util.StringUtil;

public class SearchResultTableModelOrient extends MinimalAbstractTableModel {


    ////////////
    // FIELDS //
    ////////////

    // Const

    private static final String[] COLUMNS = {"ID", "Name", "Type", "Owner", "Notes"};

    // State

    public List<NDoc> results = new ArrayList<NDoc>();


    ////////////
    // CUSTOM //
    ////////////

    // Accessor

    public NDoc getResult(int index) {
        return results.get(index);
    }
    public List<NDoc> getResults() {
        return results;
    }
    public void remove(int index) {
        results.remove(index);
        fireTableDataChanged();
    }

    // Mutator

    public void setResults(List<NDoc> newResults) {
        results = newResults;
        fireTableDataChanged();
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
        if(AppState.getState().isShowUids()) {
            return COLUMNS.length;
        }
        return COLUMNS.length - 1;
    }
    @Override
    public String getColumnName(int columnIndex) {
        if(AppState.getState().isShowUids()) {
            return COLUMNS[columnIndex];
        }
        return COLUMNS[columnIndex + 1];
    }
    @Override
    public int getRowCount() {
        if(results == null) {
            return 0;
        }
        return results.size();
    }
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if(rowIndex == -1) {
            return null;
        }

        NDoc result = results.get(rowIndex);

        if(AppState.getState().isShowUids()) {
            switch(columnIndex) {
                case 0: return result.getId();
                case 1: return result.get("name", "");
                case 2: return StringUtil.toTitleCase(result.get("type", "").toString());
                case 3: return result.get("$owner");
                case 4: return result.get("notes", "");
            }
        }
        else {
            switch(columnIndex) {
                case 0: return result.get("name", "");
                case 1: return StringUtil.toTitleCase(result.get("type", "").toString());
                case 2: return result.get("$owner");
                case 3: return result.get("notes", "");
            }
        }

        return null;
    }
}

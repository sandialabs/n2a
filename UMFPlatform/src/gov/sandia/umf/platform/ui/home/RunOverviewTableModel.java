/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.home;

import replete.gui.table.MinimalAbstractTableModel;

public class RunOverviewTableModel extends MinimalAbstractTableModel {
/*

    ////////////
    // FIELDS //
    ////////////

    // Const

    private static final String[] COLUMNS = {"ID", "Name", "Submitted", "Status"};

    // State

    public List<Run> results = new ArrayList<Run>();


    ////////////
    // CUSTOM //
    ////////////

    // Accessor

    public Run getResult(int index) {
        return results.get(index);
    }
    public List<Run> getResults() {
        return results;
    }

    // Mutator

    public void setResults(List<Run> newResults) {
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

        Run run = results.get(rowIndex);

        if(AppState.getState().isShowUids()) {
            switch(columnIndex) {
                case 0: return run.getId();
                case 1: return run.getName();
                case 2: return "2012/8/9";
                case 3: return run.getStatus();
            }
        } else {
            switch(columnIndex) {
                case 0: return run.getName();
                case 1: return "2012/8/9";
                case 2: return run.getStatus();
            }
        }

        return null;
    }
    */
}

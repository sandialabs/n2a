/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ui.ensemble;

import gov.sandia.n2a.parms.ParameterKeyPath;
import gov.sandia.umf.platform.ensemble.params.ParameterSetList;
import gov.sandia.umf.platform.ensemble.params.ParameterSetMap;
import replete.gui.table.MinimalAbstractTableModel;

public class ParameterSetTableModel extends MinimalAbstractTableModel {
    private ParameterSetList paramsList;
    private ParameterSetMap paramsMap;

    public void setParameterSetList(ParameterSetList list) {
        paramsList = list;
        paramsMap = list.transform();

        fireTableStructureChanged();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }
    @Override
    public int getColumnCount() {
        if(paramsList == null) {
            return 1;
        }
        return paramsMap.getNumParams() + 1;
    }
    @Override
    public String getColumnName(int columnIndex) {
        if(columnIndex == 0) {
            return "Run #";
        }
        Object paramKey = paramsMap.getParamByIndex(columnIndex - 1);
        return ((ParameterKeyPath) paramKey).toString(true);
    }
    public Object getParam(int columnIndex) {
        return paramsMap.getParamByIndex(columnIndex - 1);
    }
    @Override
    public int getRowCount() {
        if(paramsList == null || paramsList.getNumSets() == 0) {
            return 1;
        }
        return paramsList.getNumSets();
    }
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if(columnIndex == 0) {
            return rowIndex + 1;
        }
        return paramsMap.getByIndex(columnIndex - 1).get(rowIndex);
    }
}

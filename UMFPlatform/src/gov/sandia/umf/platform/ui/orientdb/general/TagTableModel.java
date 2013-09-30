/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.orientdb.general;

import gov.sandia.umf.platform.ui.orientdb.general.TermValue;

import java.util.ArrayList;
import java.util.List;

import replete.gui.table.MinimalAbstractTableModel;
import replete.gui.windows.Dialogs;

public class TagTableModel extends MinimalAbstractTableModel {

    private List<TermValue> tags = new ArrayList<TermValue>();
    private String[] columns = {"Name", "Value"};

    public TagTableModel() {
    }

    public List<TermValue> getTags() {
        return tags;
    }
    public void setTags(List<TermValue> newTags) {
        tags = newTags;
        fireTableDataChanged();
    }
    public TermValue getTag(int index) {
        return tags.get(index);
    }

    public void addTag(TermValue newTag) {
        tags.add(newTag);
        fireTableDataChanged();
    }

    public void removeTag(int index) {
        tags.remove(index);
        fireTableDataChanged();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }
    @Override
    public int getColumnCount() {
        return columns.length;
    }
    @Override
    public String getColumnName(int columnIndex) {
        return columns[columnIndex];
    }
    @Override
    public int getRowCount() {
        if(tags == null) {
            return 0;
        }
        return tags.size();
    }
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if(rowIndex == -1) {
            return null;
        }
        if(columnIndex == 0) {
            return tags.get(rowIndex).getTerm();
        }
        return tags.get(rowIndex).getValue();
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return true;
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        String valStr = ((String) value).trim();
        if(columnIndex == 0 && valStr.trim().equals("")) {
            Dialogs.showWarning("Tag names cannot be blank.");
            return;
        }
        if(columnIndex == 0) {
            tags.get(rowIndex).setTerm(valStr);
        } else {
            tags.get(rowIndex).setValue(valStr);
        }
        fireTableDataChanged();
    }
}

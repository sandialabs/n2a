/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.part;

import gov.sandia.n2a.language.parse.ASTVarNode;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ui.CommonWarningMessage;

import java.util.ArrayList;
import java.util.List;

import replete.gui.table.MinimalAbstractTableModel;
import replete.gui.windows.Dialogs;
import replete.util.StringUtil;

public class PartAssociationTableModel extends MinimalAbstractTableModel {


    //////////
    // ENUM //
    //////////

    public enum Direction {
        SOURCE,
        DEST
    }


    ////////////
    // FIELDS //
    ////////////

    // Const

    private static final String[] COLUMNS = {"Alias", "Compartment", "Type", "Owner", "Notes"};

    // State

    private List<NDoc> assocs = new ArrayList<NDoc>();
//    started to change code to use map of includes/connects instead of list of part associations
//    but it would probably break more things than it would fix
//    private Map<String, NDoc> associations = new HashMap<String, NDoc>();
    private Direction dir;
    private boolean aliasEditable;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public PartAssociationTableModel(Direction d, boolean ae) {
        dir = d;
        aliasEditable = ae;
    }


    ////////////
    // CUSTOM //
    ////////////

    // Accessors

    public List<NDoc> getPartAssociations() {
        return assocs;
    }
    public NDoc getPartAssociation(int index) {
        return assocs.get(index);
    }
    
//    public Map<String, NDoc> getAssociatedParts() {
//        return associations;
//    }
//    public NDoc getAssociatedPart(String alias) {
//        return associations.get(alias);
//    }

    // Mutators

    public void setPartAssociations(List<NDoc> newAssocs) {
        assocs = newAssocs;
        fireTableDataChanged();
    }
    public void addPartAssociation(NDoc newAssoc) {
        assocs.add(newAssoc);
        fireTableDataChanged();
    }
    public void removePartAssociation(int index) {
        assocs.remove(index);
        fireTableDataChanged();
    }
    
//    public void setAssociatedParts(Map<String, NDoc> newAssocs) {
//        associations = newAssocs;
//        fireTableDataChanged();
//    }
//    public void addAssociatedPart(String name, NDoc part) {
//        associations.put(name, part);
//    }
//    public void removeAssociatedPart(String name) {
//        associations.remove(name);
//    }

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
        if(assocs == null) {
            return 0;
        }
        return assocs.size();
    }
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if(rowIndex == -1) {
            return null;
        }
        NDoc assoc = assocs.get(rowIndex);

        NDoc srcPart = null;//assoc.ge???????????????????
        NDoc dstPart = assoc.get("dest");

        NDoc part;
        if(dir == Direction.SOURCE) {
            part = srcPart;
        } else {
            part = dstPart;
        }

        switch(columnIndex) {
            case 0: return assoc.get("name", null) == null ? dstPart.get("name") : assoc.get("name");
            case 1: return part.get("name");
            case 2: return StringUtil.toTitleCase((String) part.get("type"));
            case 3: return part.getOwner();
            case 4: return part.get("notes");
        }
        return null;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return aliasEditable && columnIndex == 0;
    };

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        NDoc assoc = assocs.get(rowIndex);
        String name = ((String) value).trim();
        if(name.equals("")) {
            Dialogs.showWarning("Cannot have a blank alias.");
        } else {
            boolean exists = false;
            for(NDoc xa : assocs) {
                if(xa != assoc && xa.get("name") != null && xa.get("name").equals(name)) {
                    exists = true;
                }
            }
            if(exists) {
                Dialogs.showWarning("Must have unique aliases.");
            } else {
                if(!ASTVarNode.isValidVariableName(name)) {
                    CommonWarningMessage.showInvalidVariable("Include alias");
                } else {
                    if(assoc.get("name") == null || !assoc.get("name").equals(name)) {
                        assoc.set("name", name);
                        fireTableDataChanged();
                    }
                }
            }
        }
    }
}

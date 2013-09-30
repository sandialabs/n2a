/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.expl.menus;

import gov.sandia.umf.platform.connect.orientdb.expl.OrientDbExplorerPanelUIController;


// This is static so we don't have to pass an instance
// around to all the components that need it.

public class PopupMenuManager {
    private static AllDatabasesPopupMenu mnuAllDatabases;
    private static DatabasePopupMenu mnuDatabase;
    private static ClassPopupMenu mnuClass;
    private static RecordPopupMenu mnuRecord;
    private static FieldPopupMenu mnuField;

    public static void init(OrientDbExplorerPanelUIController uiController) {
        mnuAllDatabases = new AllDatabasesPopupMenu(uiController);
        mnuDatabase = new DatabasePopupMenu(uiController);
        mnuClass = new ClassPopupMenu(uiController);
        mnuRecord = new RecordPopupMenu(uiController);
        mnuField = new FieldPopupMenu(uiController);
    }

    public static AllDatabasesPopupMenu getAllDatabasesMenu() {
        return mnuAllDatabases;
    }
    public static DatabasePopupMenu getDatabaseMenu() {
        return mnuDatabase;
    }
    public static ClassPopupMenu getClassMenu() {
        return mnuClass;
    }
    public static RecordPopupMenu getRecordMenu() {
        return mnuRecord;
    }
    public static FieldPopupMenu getFieldMenu() {
        return mnuField;
    }
}
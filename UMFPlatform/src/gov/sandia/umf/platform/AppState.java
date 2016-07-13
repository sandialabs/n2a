/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform;

import gov.sandia.umf.platform.connect.orientdb.ui.ConnectionModel;
import gov.sandia.umf.platform.plugins.extpoints.ProductCustomization;
import gov.sandia.umf.platform.ui.TabState;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import replete.xstream.XStreamWrapper;

/**
 * @author Derek Trumbo
 */

public class AppState {

    private static final File stateFile = new File(UMF.getAppResourceDir(), "client.state");


    ///////////////
    // SINGLETON //
    ///////////////

    private static AppState state;
    public static AppState getState() {
        return state;
    }
    public static void setState(AppState newState) {
        state = newState;
    }
    public static void load() {
        try {
            state = (AppState) XStreamWrapper.loadTargetFromFile(stateFile);
        } catch(Exception e) {
            e.printStackTrace();
            state = new AppState();
        }
    }
    public static void save() {
        try {
            if(!stateFile.exists()) {
                stateFile.getParentFile().mkdirs();
            }
            XStreamWrapper.writeToFile(state, stateFile);
        } catch(IOException e) {
            e.printStackTrace();
        }
    }


    ////////////
    // FIELDS //
    ////////////

    private String lookAndFeel;
    private String theme;
    private Map<String, Object> winLayout;
    private boolean showUids;
    private boolean eqnFormat;
    private boolean showTestRecords;
    private transient ProductCustomization prodCustomization;
    private ConnectionModel connectModel;
    private List<String> orientQueries;
    private List<TabState> tabStates;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public AppState() {
        lookAndFeel = null;
        theme = null;
        winLayout = new HashMap<String, Object>();
        showUids = false;
        eqnFormat = false;
        connectModel = new ConnectionModel();
        orientQueries = new ArrayList<String>();
        tabStates = new ArrayList<TabState>();
    }

    public Object readResolve() {
        if(winLayout == null) {
            winLayout = new HashMap<String, Object>();
        }
        if(connectModel == null) {
            connectModel = new ConnectionModel();
        }
        if(orientQueries == null) {
            orientQueries = new ArrayList<String>();
        }
        if(tabStates == null) {
            tabStates = new ArrayList<TabState>();
        }
        return this;
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // Accessors

    public String getLookAndFeel() {
        return lookAndFeel;
    }
    public String getTheme() {
        return theme;
    }
    public Map<String, Object> getWinLayout() {
        return winLayout;
    }
    public boolean isShowUids() {
        return showUids;
    }
    public boolean isEqnFormat() {
        return eqnFormat;
    }
    public boolean isShowTestRecords() {
        return showTestRecords;
    }
    public ProductCustomization getProductCustomization() {
        return prodCustomization;
    }
    public ConnectionModel getConnectModel() {
        return connectModel;
    }
    public List<String> getOrientQueries() {
        return orientQueries;
    }
    public List<TabState> getTabStates() {
        return tabStates;
    }

    // Mutators

    public void setLookAndFeel(String lookAndFeel) {
        this.lookAndFeel = lookAndFeel;
    }
    public void setTheme(String theme) {
        this.theme = theme;
    }
    public void setWinLayout(Map<String, Object> winLayout) {
        this.winLayout = winLayout;
    }
    public void setShowUids(boolean showUids) {
        this.showUids = showUids;
    }
    public void setEqnFormat(boolean eqnFormat) {
        this.eqnFormat = eqnFormat;
    }
    public void setShowTestRecords(boolean showTestRecords) {
        this.showTestRecords = showTestRecords;
    }
    public void setProductCustomization(ProductCustomization pc) {
        prodCustomization = pc;
    }
}

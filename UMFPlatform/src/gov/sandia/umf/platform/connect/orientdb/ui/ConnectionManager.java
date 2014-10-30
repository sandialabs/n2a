/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.ui;


import javax.swing.event.ChangeListener;

import replete.event.ChangeNotifier;

public class ConnectionManager {


    ////////////
    // FIELDS //
    ////////////

    private OrientDatasource dataModel;
    private OrientConnectDetails connectDetails;


    ///////////////
    // SINGLETON //
    ///////////////

    private static ConnectionManager instance;
    public static ConnectionManager getInstance() {
        if(instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    private ConnectionManager() {}


    //////////////
    // NOTIFIER //
    //////////////

    protected ChangeNotifier connectNotifier = new ChangeNotifier(this);
    public void addConnectListener(ChangeListener listener) {
        connectNotifier.addListener(listener);
    }
    protected void fireConnectNotifier() {
        connectNotifier.fireStateChanged();
    }


    /////////////
    // CONNECT //
    /////////////

    public void connect() {
        connectInternal(connectDetails);
    }
    public void connect(OrientConnectDetails details) {
        connectInternal(details);
    }
    private void connectInternal(OrientConnectDetails details) {
        if(isConnected()) {
            disconnect();
        }
        connectDetails = details;
        dataModel = new OrientDatasource(details);
        fireConnectNotifier();
    }
    public void disconnect() {
        if(dataModel != null) {
            //dataModel.disconnect();
            dataModel = null;
            fireConnectNotifier();
        }
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // Accessors

    public OrientDatasource getDataModel() {
        return dataModel;
    }
    public OrientConnectDetails getConnectDetails() {
        return connectDetails;
    }
    public boolean isConnected() {
        return dataModel != null && dataModel.isConnected();
    }
    public boolean hasDetails() {
        return connectDetails != null;
    }

    // Mutators

    public void setConnectDetails(OrientConnectDetails details) {
        connectDetails = details;
    }
}

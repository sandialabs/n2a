/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.ui;

import java.util.ArrayList;
import java.util.List;

public class ConnectionModel {


    ////////////
    // FIELDS //
    ////////////

    private List<OrientConnectDetails> all = new ArrayList<OrientConnectDetails>();
    private OrientConnectDetails selected = null;


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // Accessors

    public OrientConnectDetails getSelected() {
        return selected;
    }
    public List<OrientConnectDetails> getList() {
        return all;
    }

    // Mutators

    public void setSelected(OrientConnectDetails sel) {
        selected = sel;
    }
    public void setList(List<OrientConnectDetails> a) {
        all = a;
    }
}

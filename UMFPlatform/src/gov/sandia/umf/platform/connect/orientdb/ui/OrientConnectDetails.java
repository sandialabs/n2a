/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.ui;

public class OrientConnectDetails {
    public String name;
    public String location;
    public String user;
    public String password;

    public OrientConnectDetails(OrientConnectDetails connect) {
        this(connect.name, connect.location, connect.user, connect.password);
    }

    public OrientConnectDetails(String name, String location, String user, String password) {
        super();
        this.name = name;
        this.location = location;
        this.user = user;
        this.password = password;
    }

    public OrientConnectDetails(String loc) {
        this(null, loc, "admin", "admin");
    }

    public String getName() {
        return name;
    }
    public String getLocation() {
        return location;
    }
    public String getUser() {
        return user;
    }
    public String getPassword() {
        return password;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setLocation(String location) {
        this.location = location;
    }
    public void setUser(String user) {
        this.user = user;
    }
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return name;
    }
    public String toLongString() {
        return "ConnectDetails [name=" + name + ", location=" + location + ", user=" + user +
               ", password=" + password + "]";
    }

    public boolean equalsLimited(OrientConnectDetails checkedDetails) {
        return
            location.equals(checkedDetails.location) &&
            user.equals(checkedDetails.user) &&
            password.equals(checkedDetails.password);
    }
}

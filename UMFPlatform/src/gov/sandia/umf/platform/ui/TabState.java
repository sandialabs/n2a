/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui;

public class TabState {


    ////////////
    // FIELDS //
    ////////////

    private TabStateType type;
    private TabStateDbType dbType;
    private String beanClass;
    private int sqlId;
    private String orientId;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public TabState(TabStateType t) {
        type = t;
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // Accessors

    public TabStateType getType() {
        return type;
    }
    public TabStateDbType getDbType() {
        return dbType;
    }
    public String getBeanClass() {
        return beanClass;
    }
    public int getSqlId() {
        return sqlId;
    }
    public String getOrientId() {
        return orientId;
    }

    // Mutators

    public void setType(TabStateType type) {
        this.type = type;
    }
    public void setDbType(TabStateDbType dbType) {
        this.dbType = dbType;
    }
    public void setBeanClass(String beanClass) {
        this.beanClass = beanClass;
    }
    public void setSqlId(int sqlId) {
        this.sqlId = sqlId;
    }
    public void setOrientId(String orientId) {
        this.orientId = orientId;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public String toString() {
        String ret = type.toString();
        if(dbType != null) {
            ret += "(" + dbType + "," + beanClass + ",";
            if(dbType == TabStateDbType.SQL) {
                ret += sqlId;
            } else {
                ret += orientId;
            }
            ret += ")";
        }
        return ret;
    }
}

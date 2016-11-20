/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.parms;

import javax.swing.ImageIcon;

public class Parameter {


    ////////////
    // FIELDS //
    ////////////

    private Object key;
    private Object defaultValue;
    private String desc;
    private ImageIcon icon;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public Parameter(Object key) {
        this(key, null, null, null);
    }
    public Parameter(Object key, Object defaultValue) {
        this(key, defaultValue, null, null);
    }
    public Parameter(Object key, Object defaultValue, String desc) {
        this(key, defaultValue, desc, null);
    }
    public Parameter(Object key, Object defaultValue, String desc, ImageIcon icon) {
        super();
        this.key = key;
        this.defaultValue = defaultValue;
        this.desc = desc;
        this.icon = icon;
    }


    ///////////////
    // ACCESSORS //
    ///////////////

    public Object getKey() {
        return key;
    }
    public Object getDefaultValue() {
        return defaultValue;
    }
    public String getDescription() {
        return desc;
    }
    public ImageIcon getIcon() {
        return icon;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public String toString() {
        return key + " = " + defaultValue + " [" + desc + "]";
    }
}

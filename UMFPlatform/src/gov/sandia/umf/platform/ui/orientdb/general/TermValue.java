/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.orientdb.general;

public class TermValue {

    private String term;
    private String value;
    
    public TermValue(String key, String val) {
        term = key;
        value = val;
    }

    public String getTerm() {
        return term;
    }
    
    public String getValue() {
        return value;
    }
    
    public void setTerm(String s) {
        term = s;
    }
    
    public void setValue(String s) {
        value = s;
    }
}

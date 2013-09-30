/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.ensemble;

import java.util.ArrayList;
import java.util.Collection;

import replete.util.StringUtil;

public class ParameterKeyPath extends ArrayList<Object> {

    public boolean useColon;

    public ParameterKeyPath() {
        super();
    }
    public ParameterKeyPath(Object S) {
        super();
        add(S);
    }
    public ParameterKeyPath(Collection<? extends Object> c) {
        super(c);
    }
    public ParameterKeyPath(int initialCapacity) {
        super(initialCapacity);
    }

    public ParameterKeyPath plus(Object S) {
        ParameterKeyPath P = new ParameterKeyPath(this);
        P.add(S);
        return P;
    }

    public String toString(boolean colonFirstParam) {
        String rest = "";
        int start = 0;
        if(colonFirstParam) {
            rest += get(0) + ": ";
            start++;
        }
        for(int i = start; i < size(); i++) {
            rest += get(i) + ".";
        }
        if(!rest.equals("")) {
            rest = StringUtil.cut(rest, 1);
        }
        return rest;
    }

    public String toHtml() {
        return toHtml(false);
    }
    public String toHtml(boolean useBlue) {
        String html = "<i>" + (""+get(0)).replaceAll("<", "&lt;") + "</i> : ";
        for(int i = 1; i < size() - 1; i++) {
            html += "<u>" + (""+get(i)).replaceAll("<", "&lt;") + "</u> . ";
        }
        if(useBlue) {
            html += "<font color='blue'>" + (""+get(size() - 1)).replaceAll("<", "&lt;") + "</font></html>";
        } else {
            html += (""+get(size() - 1)).replaceAll("<", "&lt;");
        }
        return html;
    }

    @Override
    public String toString() {
        return toString(useColon);
    }
}

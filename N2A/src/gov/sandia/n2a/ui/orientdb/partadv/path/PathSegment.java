/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.partadv.path;

import javax.swing.JPanel;


public class PathSegment {
    public Object key;
    public String text;
    public JPanel panel;
    public PathSegment(Object key2, String text2, JPanel panel2) {
        key = key2;
        text = text2;
        panel = panel2;
    }
}

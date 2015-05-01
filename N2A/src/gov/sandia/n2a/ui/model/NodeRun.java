/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.model;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.plugins.Run;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.Icon;

import replete.gui.controls.simpletree.NodeBase;

public class NodeRun extends NodeBase {

    private static final Icon icon = ImageUtil.getImage("run.gif");

    private Run run;

    public NodeRun(Run run) {
        this.run = run;
    }

    @Override
    public Icon getIcon(boolean expanded) {
        return icon;
    }

    @Override
    public String toString() {
        return "Run";
    }

    public NDoc getRun() {
        return run.getSource();
    }
}

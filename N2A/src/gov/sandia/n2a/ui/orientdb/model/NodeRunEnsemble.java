/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.model;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.plugins.Run;
import gov.sandia.umf.platform.plugins.RunEnsemble;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.Icon;

import replete.gui.controls.simpletree.NodeBase;

public class NodeRunEnsemble extends NodeBase {

    private static final Icon icon = ImageUtil.getImage("runens.gif");

    private RunEnsemble ensemble;

    public NodeRunEnsemble(RunEnsemble ensemble) {
        this.ensemble = ensemble;
    }

    public NDoc getEnsemble() {
        return ensemble.getSource();
    }

    @Override
    public Icon getIcon(boolean expanded) {
        return icon;
    }

    @Override
    public String toString() {
        int comp = 0;
        int prog = 0;
        for(Run r : ensemble.getRuns()) {
            comp++;
        }
        String counts = comp + " completed / " +
            prog + " in progress / " +
            ensemble.getTotalRunCount() + " Total";
        String label = (ensemble.getLabel() != null ? " [" + ensemble.getLabel() + "]" : "");
        return "Run Ensemble" + label + " (" + counts + ")";
    }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ui.ensemble.tree;

import java.awt.Component;

import javax.swing.JTree;

import replete.gui.controls.simpletree.NodeBaseTreeRenderer;
import replete.gui.controls.simpletree.TNode;

public class ParamTreeRenderer extends NodeBaseTreeRenderer {

    private boolean showDefaultValues;

    public void setShowDefaultValues(boolean show) {
        showDefaultValues = show;
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                  boolean expanded, boolean leaf, int row,
                                                  boolean hasFocus1) {
        super.getTreeCellRendererComponent(
            tree, value, sel,
            expanded, leaf, row,
            hasFocus1);

        TNode nCur = (TNode) value;
        if(nCur.getObject() instanceof NodeParameter && showDefaultValues) {
            NodeParameter uParam = (NodeParameter) nCur.getObject();
            setText("<html>" + uParam.toString() + " <font color='blue'>= " +
                uParam.getParameter().getDefaultValue() + "</font></html>");
        }

        return this;
    }
}

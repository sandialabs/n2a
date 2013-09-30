/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.partadv;

import com.mxgraph.view.mxGraph;

public class N2AGraph extends mxGraph {
    public N2AGraph() {
        setCellsDisconnectable(false);  // Can select the end of an edge and drag it off of a vertex
        setDisconnectOnMove(false);     // These allow you to just randomly remove edges from the vertices they connect.
        setAllowDanglingEdges(false);   // Whether or not edges can not connect to something.
        setCellsCloneable(false);       // Hold control key while dragging to create copy. (+ still shows up but doesn't do anything on mouse up)
//            setBorder(30); //??
//            setCellsSelectable(false);  // Still shows mouse + icon, but can't select
//            setCellsMovable(false);  // Removes + icon and can't drag to move. (combine with cells selectable for purely static cells)
//            setCellsDeletable(true);   // ??
        setGridEnabled(true);           // Snap to grid
        setGridSize(10);
        setHtmlLabels(true);
        setCellsEditable(false);        // Can't double-click labels to change text.
        setVertexLabelsMovable(false);  // Can't move labels
        setEdgeLabelsMovable(false);    // Can't move labels
        setResetEdgesOnMove(true);
        setDropEnabled(false);

        // Debug
//        AdvancedGraphDetailPanel.listenTo(this);
//        AdvancedGraphDetailPanel.listenTo(getSelectionModel());
//        AdvancedGraphDetailPanel.listenTo(getView());
//        AdvancedGraphDetailPanel.listenTo(getModel());
    }
}

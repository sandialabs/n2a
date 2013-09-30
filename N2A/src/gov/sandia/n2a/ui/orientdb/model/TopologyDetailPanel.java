/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.model;

import gov.sandia.n2a.data.Layer;
import gov.sandia.n2a.data.ModelOrient;
import gov.sandia.umf.platform.ui.UIController;

import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.util.Lay;

public class TopologyDetailPanel extends ModelEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private LayerTreePanel pnlLayers;
    private BridgeTreePanel pnlBridges;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public TopologyDetailPanel(UIController uic, ModelOrient m) {
        super(uic, m);

        Lay.GLtg(this, 2, 1,
            Lay.BL(
                "N", Lay.hn(createLabelPanel("Layers", "notes"), "pref=[10,25]"),
                "C", pnlLayers = new LayerTreePanel(uic, m)
            ),
            Lay.BL(
                "N", Lay.hn(createLabelPanel("Bridges", "notes"), "pref=[10,25]"),
                "C", pnlBridges = new BridgeTreePanel(uic, m, topContext)
            ),
            "eb=10"
        );

        pnlLayers.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                model.setLayers(pnlLayers.getLayers());
                fireContentChangedNotifier();
            }
        });
        pnlBridges.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                model.setBridges(pnlBridges.getBridges());
                fireContentChangedNotifier();
            }
        });
    }

    private TopologyContext topContext = new TopologyContext() {
        public List<Layer> getSelectedLayers() {
            return pnlLayers.getSelectedLayers();
        }
    };

    @Override
    public void reload() {
        pnlLayers.setLayers(model.getLayers());
        pnlBridges.setBridges(model.getBridges());
    }
}

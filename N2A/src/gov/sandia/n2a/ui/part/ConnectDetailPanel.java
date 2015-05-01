/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.part;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditDetailPanel;
import gov.sandia.umf.platform.ui.UIController;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.util.Lay;

public class ConnectDetailPanel extends RecordEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // UI

    private PartAssociationTablePanel pnlConnects;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ConnectDetailPanel(UIController uic, NDoc p) {
        super(uic, p);

        Lay.BLtg(this,
            "N", Lay.hn(createLabelPanel("Connected Compartments", "part-connect"), "pref=[10,25]"),
            "C", pnlConnects = new PartAssociationTablePanel(
                uiController, record, PartAssociationTablePanel.Type.CONNECT),
            "eb=10"
        );

        pnlConnects.addContentChangedListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                List<NDoc> cAssocs = pnlConnects.getPartAssociations();
                for(NDoc assoc : cAssocs) {
                    assoc.set("source", record);
                }
                List<NDoc> newAssocs = new ArrayList<NDoc>();
                newAssocs.addAll(cAssocs);
                List<NDoc> oldAssocs = record.getValid("associations", new ArrayList<NDoc>(), List.class);
                for (NDoc doc : oldAssocs) {
                    if(!((String) doc.get("type")).equalsIgnoreCase("connect")) {
                        newAssocs.add(doc);
                    }
                }
                record.set("associations", newAssocs);
                fireContentChangedNotifier();
            }
        });
    }


    //////////
    // MISC //
    //////////

    @Override
    public void reload() {
        List<NDoc> assocs = record.getValid("associations", new ArrayList<NDoc>(), List.class);
        List<NDoc> cAssocs = new ArrayList<NDoc>();
        for(NDoc assoc : assocs) {
            if(((String) assoc.get("type")).equalsIgnoreCase("connect")) {
                cAssocs.add(assoc);
            }
        }
        pnlConnects.setPartAssociations(cAssocs);
    }
}

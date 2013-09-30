/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.model;

import gov.sandia.n2a.data.ModelOrient;
import gov.sandia.umf.platform.connect.orientdb.ui.RecordEditDetailPanel;
import gov.sandia.umf.platform.ui.UIController;

public abstract class ModelEditDetailPanel extends RecordEditDetailPanel {


    ////////////
    // FIELDS //
    ////////////

    // Core

    protected ModelOrient model;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ModelEditDetailPanel(UIController uic, ModelOrient m) {
        super(uic, m.getSource());
        model = m;
    }

    public void setModel(ModelOrient m) {
        model = m;
        reload();
    }
}

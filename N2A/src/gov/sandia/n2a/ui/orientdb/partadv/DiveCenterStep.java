/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.ui.orientdb.partadv;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;

public class DiveCenterStep {
    public DiveCenterStepType type;
    public NDoc part;
    public NDoc parent;

    public DiveCenterStep(DiveCenterStepType t, NDoc p, NDoc pr) {
        type = t;
        part = p;
        parent = pr;
    }

    @Override
    public String toString() {
        String parStr = parent == null ? "<no parent>" : (String) parent.get("name", parent.get("alias", "UNK"));
        return type + "," + part.get("name", part.get("alias", "UNK")) + "," + parStr;
    }
}

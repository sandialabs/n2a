/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.network;

import gov.sandia.n2a.data.Model;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ModelInstanceOrient {
    private Model model;
    private long seed;
    private Random rng;
    private NetworkOrient net;

    public ModelInstanceOrient(Model m, long s)
            throws Exception
    {
        model = m;

        seed = s;
        if (s==0) {
            seed = System.currentTimeMillis(); }
        rng = new Random(seed);

        net = new NetworkOrient(model, rng);
    }

    public Model getModel() {
        return model;
    }

    public NetworkOrient getNetwork() {
        return net;
    }

    public List<EquationEntry> getOutputEqs() throws Exception {
        // Fred's reworking this, but try the following for now...
        ArrayList<EquationEntry> result = new ArrayList<EquationEntry>();
        for (NDoc eqNDoc : model.getOutputEqs()) {
            result.add(new EquationEntry(eqNDoc));
        }
        return result;
    }

    public long getSeed() {
        return seed;
    }

    public String getAdjMatrix() {
        return net.getAdjacencyMatrix();
    }
}

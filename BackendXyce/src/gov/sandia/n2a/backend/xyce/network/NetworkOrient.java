/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.network;

import gov.sandia.n2a.backend.xyce.XyceTranslationException;
import gov.sandia.n2a.data.Bridge;
import gov.sandia.n2a.data.Layer;
import gov.sandia.n2a.data.Model;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class NetworkOrient {
    Map<String,CompartmentLayerOrient> compMap;
    Map<String, ConnectionSetOrient> connMap;
    private Random rng;
    public EquationSet eqSet;
    private Map<String, CompartmentLayerOrient> aliasMap;

    public NetworkOrient(Model model, Random rng)
            throws Exception
    {
        compMap = new HashMap<String,CompartmentLayerOrient>();
        connMap = new HashMap<String,ConnectionSetOrient>();
        aliasMap = new HashMap<String, CompartmentLayerOrient>();
        this.rng = rng;
        eqSet = createEquationSet(model.getSource());
        createParts();
    }

    private EquationSet createEquationSet(NDoc model) throws Exception
    {
        EquationSet e = new EquationSet (model);
        e.name = "Model";  // because the default is for top-level equation set to be anonymous
//        e.flatten ();
        // TODO - which of the rest of these are actually useful for me?
//        e.addSpecials ();  // $index, $init - also adds $dt and $t, but no equations; messes me up
        e.findConstants ();
        e.findIntegrated ();
        e.addInit ();
        e.resolveLHS ();
        e.resolveRHS ();
        e.findTemporary ();
//        e.determineOrder ();	// I don't really use/need this
        e.findDerivative ();
        e.addAttribute    ("global",       0, false, new String[] {"$max", "$min", "$k", "$n", "$radius", "$ref"});
        e.addAttribute    ("transient",    1, true,  new String[] {"$p", "$ref", "$xyz"});
        e.addAttribute    ("preexistent",  0, true,  new String[] {"$dt", "$index"});
        e.removeAttribute ("constant",     0, true,  new String[] {"$dt"});
        e.setInit (false);
        System.out.println (e.flatList (true));
        return e;
    }

    private void createParts() throws NetworkGenerationException, XyceTranslationException
    {
        PartInstanceCounter counter = new PartInstanceCounter();
        // sneaking up on recursive hierarchical composition, but not there yet.
        // assume for now that there's one top-level 'model' equation set that has 
        // 'layer' and 'bridge' children - no descendants beyond those
        // OR, there might be a single EquationSet with no children; treat as lone layer/compartment
        if (eqSet.parts== null || eqSet.parts.isEmpty()) {
           createCompartment(eqSet, counter);
           return;
        }
        for (EquationSet childSet : eqSet.parts) {
            if (childSet.connectionBindings == null) {
                createCompartment(childSet, counter);
            } else {
                createConnection(childSet, counter);
            }
        }
    }

    private void createCompartment(EquationSet eqs, PartInstanceCounter counter)
            throws NetworkGenerationException, XyceTranslationException
    {
        String name = eqs.name;
        if (compMap.containsKey(name)) {
            throw new NetworkGenerationException("duplicate layer name " + name);
        }
        compMap.put(name, new CompartmentLayerOrient(eqs, rng, counter));
    }

    private void createConnection(EquationSet eqs, PartInstanceCounter counter)
            throws NetworkGenerationException, XyceTranslationException
    {
        String name = eqs.name;
        if (connMap.containsKey(name)) {
            throw new NetworkGenerationException("duplicate bridge name " + name);
        }
        for (String alias : eqs.connectionBindings.keySet()) {
            String layerName = eqs.connectionBindings.get(alias).name;
            aliasMap.put(alias, compMap.get(layerName));
        }
        connMap.put(name, new ConnectionSetOrient(eqs, rng, aliasMap, counter));
    }

    public CompartmentLayerOrient getLayer(String layerName) {
        return compMap.containsKey(layerName) ? compMap.get(layerName) : null;
    }

    public PartSetInterface getPartSet(String partName) {
        // partName could be a compartment name, a connection name, or an alias for a compartment name; try each
        PartSetInterface result = null;
        if (compMap.containsKey(partName)) {
            result = compMap.get(partName);
        }
        if (connMap.containsKey(partName)) {
            result = connMap.get(partName);
        }
        if (aliasMap.containsKey(partName)) {
            result = aliasMap.get(partName);
        }
        return result;
    }

    public Collection<PartSetAbstract> getParts()
    {
        Collection<PartSetAbstract> partList = new ArrayList<PartSetAbstract>();
        partList.addAll(getLayers());
        partList.addAll(getBridges());
        return partList;
    }

    public Collection<CompartmentLayerOrient> getLayers()
    {
        return compMap.values();
    }

    public Collection<ConnectionSetOrient> getBridges()
    {
        return connMap.values();
    }

    public String getAdjacencyMatrix() {
        StringBuilder sb = new StringBuilder();
        for (ConnectionSetOrient set : connMap.values()) {
            sb.append(set.name + ":\n" + set.getAdjacencyMatrix());
        }
        return sb.toString();
    }

}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.network;

import gov.sandia.n2a.backend.xyce.XyceTranslationException;
import gov.sandia.n2a.backend.xyce.parsing.LanguageUtil;
import gov.sandia.n2a.backend.xyce.parsing.XyceASTUtil;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.type.Instance;
import gov.sandia.n2a.language.type.Scalar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public class ConnectionSetOrient extends PartSetOrient {
    // TODO - do I really need pre/post layers, or can I just use refLayer and otherLayer?
    private CompartmentLayerOrient preLayer;
    private CompartmentLayerOrient postLayer;
    private Map<String, CompartmentLayerOrient> layerMap;

    private TreeMap<Integer, TreeMap<Integer, ArrayList<Integer>>> adjMatrix;
    private EquationEntry connEq;
    private Instance connContext;
    
    // default values; overwritten if the corresponding equations have been specified
    private double connProb = -1.0;

    public ConnectionSetOrient(EquationSet eqs, Random rng, Map<String, CompartmentLayerOrient> layerMap,
            PartInstanceCounter counter)
            throws NetworkGenerationException, XyceTranslationException
    {
        super(eqs, rng, counter);
        this.layerMap = layerMap;
        adjMatrix = new TreeMap<Integer, TreeMap<Integer, ArrayList<Integer>>>();
        setLayers();
        setConnectivityInfo();
        createInstances();
        findInitEqs();
    }

    private void setLayers() throws NetworkGenerationException
    {
        // TODO - need to get away from hard-coded aliases...
        preLayer = layerMap.get("A");
        postLayer = layerMap.get("B");
        addRelatedPart(preLayer);
        addRelatedPart(postLayer);
    }

    private void setConnectivityInfo() 
    {
        // connection probability
        try {
            connEq = LanguageUtil.getConnectionEq(eqns); 
            connContext = XyceASTUtil.getEvalContext(connEq, eqns);
            
        } catch (Exception e) {
            // treat like $p=1
            connProb = 1.0;
            System.out.println("Cannot identify connection equation; treating as fixed probability of 1");
        }
        if (connEq != null && !LanguageUtil.isInstanceDependent(connEq)) {
            // should be a fixed probability
            setConnectedContext(connContext, (CompartmentInstance)preLayer.getInstances().get(0), 
                    (CompartmentInstance)postLayer.getInstances().get(0));
            Type evalResult = connEq.expression.eval (connContext);
            if (evalResult instanceof Scalar) connProb = ((Scalar) evalResult).value;
        }
    }

    private void createInstances()
            throws NetworkGenerationException, XyceTranslationException
    {
        // TODO need to determine whether pre or post layer is the 'reference' layer
        // for each compartment instance in ref layer
        //     (later) apply mask/determine candidate subset of instances in other layer
        //     for each compartment instance in candidate set
        //         evaluate $p equation to determine whether to connect
//        CompartmentLayer refLayer = getRefLayer();
//        if (refLayer==preLayer) {
//            connectRefPre();
//        }
//        else {
//            connectRefPost();
//        }
        connectRefPost();
        if (instances.size() > 0) {
            firstSN = instances.get(0).serialNumber;
        }
    }

    // TODO: handle $min and $max (replaces earlier notion of $card)
    private void connectRefPost() throws NetworkGenerationException, XyceTranslationException
    {
        for (PartInstance part : postLayer.instances )
        {
            CompartmentInstance postInstance = (CompartmentInstance) part;
            Object[] candidates = getCandidates(preLayer).toArray();
            // randomize order of iterating through candidates, ensuring each only visited once
            int[] perm = new int[candidates.length];
            for (int i=0; i<candidates.length; i++) {
                perm[i] = i;
            }
            for (int i=0; i<candidates.length; i++)
            {
                int r = i + (int) (rng.nextDouble()*(candidates.length-i));
                int t = perm[r];
                perm[r] = perm[i];
                perm[i]=t;
                CompartmentInstance candidate = (CompartmentInstance) candidates[t]; 
                if (shouldConnect(candidate, postInstance)) {
                    createConnection(candidate, postInstance);
                }
            }
        }
    }

    private void createConnection(CompartmentInstance source, CompartmentInstance target)
    {
        addInstance(new ConnectionInstance(this, counter.getNextSerialNumber(), source, target));
    }

    private boolean shouldConnect(CompartmentInstance preInstance, CompartmentInstance postInstance)
            throws NetworkGenerationException, XyceTranslationException
    {
        if (connProb == 1.0) return true;
        if (connProb == 0  ) return false;
        if (connProb >= 0  ) return connProb > rng.nextDouble ();

        setConnectedContext (connContext, preInstance, postInstance);
        Type evalResult = connEq.expression.eval (connContext);
        if (evalResult == null) throw new NetworkGenerationException ("Cannot evaluate " + connEq);
        if (! (evalResult instanceof Scalar)) throw new NetworkGenerationException ("connection equation does not evaluate to number");
        return ((Scalar) evalResult).value > rng.nextDouble ();
    }

    private Collection<PartInstance> getCandidates(CompartmentLayerOrient otherLayer)
    {
        // TODO eventually should use this to evaluate 'mask' equation, and/or apply any 'hints'
        // about downselecting the population to reduce computation.
        // but for now...
        return otherLayer.instances;
    }

    private void addInstance(ConnectionInstance instance) 
    {
        instances.add(instance);
        allSNs.add(instance.serialNumber);

        // add to adjacency matrix
        int preSN = instance.A.serialNumber;
        int postSN = instance.B.serialNumber;
        if (!adjMatrix.containsKey(preSN)) {
            adjMatrix.put(preSN, new TreeMap<Integer, ArrayList<Integer>>());
        }
        TreeMap<Integer, ArrayList<Integer>> row = adjMatrix.get(preSN);
        if (!row.containsKey(postSN)) {
            row.put(postSN, new ArrayList<Integer>());
        }
        List<Integer> cell = row.get(postSN);
        cell.add(instance.serialNumber);
    }

    public String getAdjacencyMatrix() {
        StringBuilder sb = new StringBuilder();
        // format:
        // matrix with compartment 1 as rows and compartment 2 as columns
        // entries are 0 for no connection, serial number of connection instance if there is a connection
        // so that values printed out from connection can be related to network structure
        int firstRow = preLayer.firstSN;
        int firstCol = postLayer.firstSN;
        // headers
        for (int col=firstCol; col<firstCol+preLayer.allSNs.size(); col++) {
            sb.append("\t" + col);
        }
        sb.append("\n");
        for (int row=firstRow; row<firstRow+postLayer.allSNs.size(); row++) {
            sb.append(row + "\t");
            for (int col=firstCol; col<firstCol+preLayer.allSNs.size(); col++) {
                int SN = 0;     // connection SN; 0 by default
                if (adjMatrix.containsKey(row)) {
                    if (adjMatrix.get(row).containsKey(col)) {
                        // TODO:  This currently prints just one connection instance
                        // if there are more (which adjMatrix allows, but we don't expect yet),
                        // how do we distinguish them?
                        SN = adjMatrix.get(row).get(col).get(0);
                    }
                }
                sb.append(SN + "\t");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}

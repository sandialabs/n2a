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
import gov.sandia.n2a.language.EvaluationContext;

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
    private EvaluationContext connContext;
    
    // default values; overwritten if the corresponding equations have been specified
    private double connProb = -1.0;
    private int cardA = -1;    // TODO - can I get away from A/B?  do these relate to refLayer and other?
    private int cardB = -1;
    private int targetNum = -1;

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
            Object evalResult = XyceASTUtil.evaluateEq(connEq, connContext);
            if (evalResult instanceof Number) {
                connProb = ((Number)evalResult).doubleValue();
            }
        }
        // connection cardinality
        cardA = evalCardinality("A.");
        cardB = evalCardinality("B.");
        // target total number of connections
        EquationEntry nEq = null;
        EvaluationContext nContext = null;
        try {
            nEq = LanguageUtil.getNEq(eqns);
            nContext = XyceASTUtil.getEvalContext(nEq, eqns);
        } catch (Exception e) {
            // no $n equation, but that's fine; nothing to do
        }
        if (nEq!=null) {
            setConnectedContext(nContext, (CompartmentInstance)preLayer.getInstances().get(0), 
                    (CompartmentInstance)postLayer.getInstances().get(0));
            Object evalResult = XyceASTUtil.evaluateEq(nEq, nContext);
            if (evalResult instanceof Number) {
                targetNum = ((Number)evalResult).intValue();
            }
        }
    }

    private int evalCardinality(String prefix)
    {
        int result = -1;
        try {
            EquationEntry cardEq = LanguageUtil.getSinglePE(eqns, prefix+LanguageUtil.$CARD, false);
            EvaluationContext context = XyceASTUtil.getEvalContext(cardEq, eqns);
            Object evalResult = XyceASTUtil.evaluateEq(cardEq, context);
            if (evalResult instanceof Number) {
                result = ((Number)evalResult).intValue();
            } else {
                System.out.println("cardinality value " + cardEq + " not understood; ignoring");
            }
        } catch (Exception e) {
//            System.out.println("could not find cardinality equation for " + prefix + "; ignoring");
       }
        return result;
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

    private void connectRefPre() throws NetworkGenerationException, XyceTranslationException
    {
        for (PartInstance part : preLayer.instances )
        {
            CompartmentInstance preInstance = (CompartmentInstance) part;
            Collection<PartInstance> candidates = getCandidates(postLayer);
            for (PartInstance candidate : candidates)
            {
                if (shouldConnect(preInstance, (CompartmentInstance)candidate)) {
                    createConnection(preInstance, (CompartmentInstance)candidate);
                }
            }
        }
    }

    private void connectRefPost() throws NetworkGenerationException, XyceTranslationException
    {
        for (PartInstance part : postLayer.instances )
        {
            CompartmentInstance postInstance = (CompartmentInstance) part;
            Object[] candidates = getCandidates(preLayer).toArray();
            int numConnections = 0;
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
                    numConnections++;
                }
                if (cardA>0 && numConnections==cardA) {    // TODO: check cardB also?
                    break;
                }
            }
            if (targetNum>0 && instances.size()==targetNum) {
                break;
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
        if (connProb == 1.0) {
            return true;
        }
        double prob;
        if (connProb > 0) {
            prob = connProb;
        }
        else {
            setConnectedContext(connContext, preInstance, postInstance);
            Object evalResult = XyceASTUtil.evaluateEq(connEq, connContext);
            if (evalResult == null) {
                throw new NetworkGenerationException("Cannot evaluate " + connEq);
            }
            if (evalResult instanceof Boolean) {
                return (Boolean) evalResult;
            }
            if (!(evalResult instanceof Number))
            {
                throw new NetworkGenerationException("connection equation does not evaluate to number");
            }
            prob = (((Number)evalResult).doubleValue());
        }
        return prob > rng.nextDouble();
    }

    private Collection<PartInstance> getCandidates(CompartmentLayerOrient otherLayer)
    {
        // TODO eventually should use this to evaluate 'mask' equation, and/or apply any 'hints'
        // about downselecting the population to reduce computation.
        // but for now...
        return otherLayer.instances;
    }

    private CompartmentLayerOrient getRefLayer()
    {
        // TODO: evaluate $ref to do this??
        // for now, assume postsynaptic layer is the 'reference'
        return postLayer;
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

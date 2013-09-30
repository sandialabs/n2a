/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.network;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.MetadataAssembler;
import gov.sandia.n2a.eqset.PartMetadataMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class PartSetOrient extends PartSetAbstract {
    PartInstanceCounter counter;

    public PartSetOrient(EquationSet e, Random rng, PartInstanceCounter counter)
    {
        this.name = e.name;
        this.rng = rng;
        this.counter = counter;
        eqns = e;
        relatedParts = new ArrayList<PartSetAbstract>();
        instances = new ArrayList<PartInstance>();
        allSNs = new ArrayList<Integer>();
        initEqs = new ArrayList<EquationEntry>();
        eqSNs = new HashMap<EquationEntry, Integer>();
    }
}
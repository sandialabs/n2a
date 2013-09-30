/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.symbol;

import gov.sandia.n2a.backend.xyce.network.PartInstance;
import gov.sandia.n2a.backend.xyce.network.PartSetInterface;
import gov.sandia.n2a.backend.xyce.parsing.LanguageUtil;
import gov.sandia.n2a.eqset.EquationEntry;

import java.util.List;

public abstract class DefaultSymbolDef implements SymbolDef {
    protected EquationEntry eq;
    protected String name;
    protected PartSetInterface partSet;
    protected List<Integer> SNs;
    protected boolean instanceSpecific;
    protected PartInstance firstInstance;
    protected boolean defWritten = false;

    public DefaultSymbolDef(EquationEntry eq, PartSetInterface partSet) {
        this.eq = eq;
        this.name = eq.variable.name;
        this.partSet = partSet;
        this.instanceSpecific = LanguageUtil.isInstanceDependent(eq);
    }
    
    public DefaultSymbolDef(EquationEntry eq, PartInstance firstPI)
    {
        this(eq, firstPI.getPartSet());
        this.firstInstance = firstPI;
   }
}

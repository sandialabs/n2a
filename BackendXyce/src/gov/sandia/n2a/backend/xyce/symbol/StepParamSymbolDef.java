/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.symbol;

import gov.sandia.n2a.backend.xyce.Xyceisms;
import gov.sandia.n2a.backend.xyce.network.PartInstance;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.umf.platform.ensemble.params.specs.StepParameterSpecification;


public class StepParamSymbolDef extends ParamSymbolDef 
{
    double start, stop, step;

   public StepParamSymbolDef(EquationEntry eq, PartInstance firstPI, StepParameterSpecification spec, int runCount)
    {
        super(eq, firstPI);
        start = spec.getStart().doubleValue();
        step = spec.getDelta().doubleValue();
        stop = start + (runCount-1)*step;
    }

    @Override
    public String getDefinition(SymbolManager symMgr, PartInstance pi) 
    {
        if (defWritten) {
            return "";
        }
        defWritten = true;
        return Xyceisms.stepParam(name, firstInstance.serialNumber, start, step, stop);
    }
}

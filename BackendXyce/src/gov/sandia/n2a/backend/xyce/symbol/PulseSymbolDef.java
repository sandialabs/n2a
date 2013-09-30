/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.symbol;

import gov.sandia.n2a.backend.xyce.Xyceisms;
import gov.sandia.n2a.backend.xyce.network.PartInstance;
import gov.sandia.n2a.backend.xyce.network.PartSetInterface;
import gov.sandia.n2a.eqset.EquationEntry;

public class PulseSymbolDef extends DefaultSymbolDef
{
    // a more generic pulse than XycePulseInputSymbolDef
    // shows up in equations as pulse(var, width, period, rise, fall)
    // Might be scaled or shifted:  y = scaleFactor * pulse(x + xOffset, ...) + yOffset
    // IF var represents time, this can be translated into a Xyce pulse
    // otherwise, it needs to be treated as a Xyce .func

    private double width;
    private double period;
    private double rise;
    private double fall;
    private double scaleFactor;
    private double xOffset;
    private double yOffset;

    public PulseSymbolDef(EquationEntry eq, PartSetInterface partSet)
    {
        super(eq, partSet);
        // TODO - parse out variables above  
    }

    @Override
    public String getDefinition(SymbolManager symMgr, PartInstance pi) 
    {
        // TODO - actually need translator (XyceRHSTranslator) to handle translation of 'pulse'
        // to either a Xyce pulse or a function that mimics a pulse
        // but how will it know which to use, and the Xyce pulse variables?
        // Why did I think translator would handle this?  Translator primarily used BY SymbolDefs,
        // in response to Netlist::writeDefinition call to SymbolDef::getDefinition


        StringBuilder result = new StringBuilder();
        int SN = pi.serialNumber;
        if (!instanceSpecific) { 
            SN = firstInstance.serialNumber;
        }
        // TODO - need different pulse function - V instead of I, order of nodes switched
//            result.append(Xyceisms.pulse(inputVar, stateVar, SN, newSpec));
        return result.toString();
    }

    @Override
    public String getReference(int SN) 
    {
        if (instanceSpecific) {
            return Xyceisms.referenceStateVar(name, SN);
        }
        return Xyceisms.referenceStateVar(name, firstInstance.serialNumber);
    }
}

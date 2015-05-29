/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.symbol;

import java.util.ArrayList;

import gov.sandia.n2a.backend.xyce.XyceBackendData;
import gov.sandia.n2a.backend.xyce.Xyceisms;
import gov.sandia.n2a.backend.xyce.parsing.XyceRenderer;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.type.Instance;

public class PulseSymbolDef extends InputSymbolDef
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

    public PulseSymbolDef (EquationEntry eq)
    {
        super (eq);
    }

    @Override
    public String getDefinition (XyceRenderer renderer) 
    {
        int SN = renderer.pi.hashCode ();
        ArrayList<String> params = new ArrayList<String>(7);
        for(int a = 0; a < funcNode.operands.length; a++)
        {
            Operator param = funcNode.operands[a];
            if (param instanceof Constant)
            {
                params.add (param.toString ());
            }
            else
            {
                XyceBackendData bed = (XyceBackendData) eq.variable.container.backendData;
                // param is an expression; need to get the entire string at this subtree and translate it
                params.add (XyceASTUtil.getReadableShort (param, new XyceRenderer (bed, pi, null, false)));
            }
        }

        return Xyceisms.voltagePulse (eq.variable.name, SN, params);
    }

    @Override
    public String getReference (XyceRenderer renderer) 
    {
        return Xyceisms.referenceStateVar (eq.variable.name, renderer.pi.hashCode ());
    }
}

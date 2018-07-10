/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.c;

import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.language.Operator;

public class RendererCfp extends RendererC
{
    public RendererCfp (JobC job, StringBuilder result, EquationSet part)
    {
        super (job, result, part);
    }

    public String print (double d, int exponent)
    {
        if (d == 0) return "0";

        long bits = Double.doubleToLongBits (d);
        int  e    = Math.getExponent (d);
        bits |=   0x10000000000000l;  // set implied msb of mantissa (bit 52) to 1
        bits &= 0x801FFFFFFFFFFFFFl;  // clear exponent bits
        bits >>= 52 - Operator.MSB + exponent - e;
        return Integer.toString ((int) bits);
    }
}

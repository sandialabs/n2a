/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import javax.measure.Unit;

/**
    Utility class for capturing units of numeric constants during parsing.
**/
public class UnitValue
{
    public double  value;
    public Unit<?> unit;
}

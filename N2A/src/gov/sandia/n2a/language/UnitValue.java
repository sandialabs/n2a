/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import java.util.Set;

import javax.measure.Unit;
import javax.measure.format.UnitFormat;
import javax.measure.spi.ServiceProvider;
import javax.measure.spi.SystemOfUnits;

/**
    Utility class for capturing units of numeric constants during parsing.
**/
public class UnitValue
{
    public double  value;
    public Unit<?> unit;
    public int     digits;  // number of digits in the textual form of the constant (base 10)

    public static SystemOfUnits systemOfUnits = ServiceProvider.current ().getSystemOfUnitsService ().getSystemOfUnits ("UCUM");
    public static UnitFormat    UCUM          = ServiceProvider.current ().getUnitFormatService ().getUnitFormat ("UCUM");
    public static Unit<?>       seconds       = UCUM.parse ("s");

    public static Unit<?> simplify (Unit<?> unit)
    {
        Set<? extends Unit<?>> set = systemOfUnits.getUnits (unit.getDimension ());
        if (! set.isEmpty ()) unit = set.iterator ().next ();  // Get first (arbitrary) built-in unit with matching dimensions.
        return unit.getSystemUnit ();
    }
}

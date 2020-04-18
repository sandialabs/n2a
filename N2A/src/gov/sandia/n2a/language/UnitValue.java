/*
Copyright 2017-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.language;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.measure.Unit;
import javax.measure.format.UnitFormat;
import javax.measure.spi.ServiceProvider;
import javax.measure.spi.SystemOfUnits;

import gov.sandia.n2a.language.type.Scalar;

/**
    Utility class for capturing units of numeric constants during parsing.
**/
public class UnitValue
{
    public double  value;
    public Unit<?> unit;
    public int     digits;  // number of digits in the textual form of the constant (base 10)

    public static Pattern       floatParser   = Pattern.compile ("[-+]?(NaN|Infinity|([0-9]*\\.?[0-9]*([eE][-+]?[0-9]+)?))");
    public static SystemOfUnits systemOfUnits = ServiceProvider.current ().getSystemOfUnitsService ().getSystemOfUnits ("UCUM");
    public static UnitFormat    UCUM          = ServiceProvider.current ().getFormatService ().getUnitFormat ("UCUM");
    public static Unit<?>       seconds       = UCUM.parse ("s");

    // Allow empty constructor
    public UnitValue ()
    {
    }

    /**
        Parses the given string, which must be properly-formatted number with optional unit specified at end.
        Any error in number format results in 0. A missing unit, or any error in format, result in a null unit field.
        At present there is no use for determining the digits field during direct parse, so it remains 0.
        The main language parser, OTOH, does determine digits.
    **/
    public UnitValue (String input)
    {
        input = input.trim ();
        int unitIndex = findUnits (input);
        String valueString = input.substring (0, unitIndex).trim ();
        String unitString  = input.substring (unitIndex).trim ();
        if (! valueString.isEmpty ())
        {
            try {value = Double.parseDouble (valueString);}
            catch (NumberFormatException e) {}
        }
        if (! unitString.isEmpty ())
        {
            try {unit = UCUM.parse (unitString);}
            catch (Exception e) {}
        }
    }

    /**
        Returns the value scaled according to the unit.
        For example, if the input was "1ms", then value=1, unit=milliseconds, and this function returns 0.001
    **/
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public double get ()
    {
        if (unit == null) return value;  // naked number, so assume already in SI
        Unit<?> systemUnit = unit.getSystemUnit ();
        return unit.getConverterTo ((Unit) systemUnit).convert (value);
    }

    public static Unit<?> simplify (Unit<?> unit)
    {
        Set<? extends Unit<?>> set = systemOfUnits.getUnits (unit.getDimension ());
        if (! set.isEmpty ()) unit = set.iterator ().next ();  // Get first (arbitrary) built-in unit with matching dimensions.
        return unit.getSystemUnit ();
    }

    public static int findUnits (String value)
    {
        Matcher m = floatParser.matcher (value);
        m.find ();
        return m.end ();
    }

    public String toString ()
    {
        String result = Scalar.print (value);
        if (unit != null) result += UCUM.format (unit);
        return result;
    }
}

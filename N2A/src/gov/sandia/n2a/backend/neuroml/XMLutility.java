/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.measure.Unit;
import javax.measure.format.UnitFormat;
import javax.measure.spi.ServiceProvider;
import javax.measure.spi.SystemOfUnits;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import gov.sandia.n2a.language.type.Scalar;

public class XMLutility
{
    public static Pattern       floatParser   = Pattern.compile ("[-+]?(NaN|Infinity|([0-9]*\\.?[0-9]*([eE][-+]?[0-9]+)?))");
    public static Pattern       forbiddenUCUM = Pattern.compile ("[.,;><=!&|+\\-*/%\\^~]");
    public static SystemOfUnits systemOfUnits = ServiceProvider.current ().getSystemOfUnitsService ().getSystemOfUnits ("UCUM");
    public static UnitFormat    UCUM          = ServiceProvider.current ().getUnitFormatService ().getUnitFormat ("UCUM");

    public static final double epsilon = Math.ulp (1);  // Even though this is stored as double, it is really a single-precision epsilon

    public static int findUnits (String value)
    {
        Matcher m = floatParser.matcher (value);
        m.find ();
        return m.end ();
    }

    public static String cleanupUnits (String units)
    {
        units = units.replace ("_per_", "/");
        units = units.replace ("per_",  "/");
        units = units.replace ("_",     ".");
        units = units.replace (" ",     ".");
        units = units.replace ("ohm",   "Ohm");
        units = units.replace ("KOhm",  "kOhm");
        units = units.replace ("degC",  "Cel");
        units = units.replace ("hour",  "h");
        units = units.replace ("litre", "L");
        if (units.equals ("M" )) units = "kmol/m3";
        if (units.equals ("mM")) units = "mol/m3";
        return safeUnit (units);
    }

    public static String safeUnit (String unit)
    {
        if (forbiddenUCUM.matcher (unit).find ()) return "(" + unit + ")";
        return unit;
    }

    public static String safeUnit (Unit<?> unit)
    {
        return safeUnit (UCUM.format (unit));
    }

    public static String print (double d)
    {
        return Scalar.print (d);
    }

    public static String getText (Node node)
    {
        String result = "";
        for (Node child = node.getFirstChild (); child != null; child = child.getNextSibling ())
        {
            if (child.getNodeType () == Node.TEXT_NODE) result = result + child.getNodeValue ();
        }
        return result;
    }

    public static String getAttributes (Node node, String... names)
    {
        for (String name : names)
        {
            String result = getAttribute (node, name);
            if (! result.isEmpty ()) return result;
        }
        return "";
    }

    public static String getAttribute (Node node, String name)
    {
        return getAttribute (node, name, "");
    }

    public static String getAttribute (Node node, String name, String defaultValue)
    {
        NamedNodeMap attributes = node.getAttributes ();
        Node attribute = attributes.getNamedItem (name);
        if (attribute == null) return defaultValue;
        return attribute.getNodeValue ();
    }

    public static int getAttribute (Node node, String name, int defaultValue)
    {
        try
        {
            return Integer.parseInt (getAttribute (node, name, String.valueOf (defaultValue)));
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    public static double getAttribute (Node node, String name, double defaultValue)
    {
        try
        {
            return Double.parseDouble (getAttribute (node, name, String.valueOf (defaultValue)));
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }
}

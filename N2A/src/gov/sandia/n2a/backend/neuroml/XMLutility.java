/*
Copyright 2018-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import java.util.HashMap;
import java.util.Map;
import javax.measure.Dimension;
import javax.measure.Unit;
import javax.measure.format.UnitFormat;
import javax.measure.spi.SystemOfUnits;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.language.type.Scalar;
import tech.units.indriya.AbstractUnit;

public class XMLutility
{
    public static SystemOfUnits         systemOfUnits = UnitValue.systemOfUnits;
    public static UnitFormat            UCUM          = UnitValue.UCUM;                   // To save a little typing, since it's used so much.
    public static Map<Dimension,String> dimensionsNML = new HashMap<Dimension,String> (); // Map from Dimension to NML name
    public static Map<String,Unit<?>>   nmlDimensions = new HashMap<String,Unit<?>> ();   // Map from NML dimension name to base unit
    public static Map<Unit<?>,String>   unitsNML      = new HashMap<Unit<?>,String> ();   // Map from Unit to NML name

    public static final double epsilon = Math.ulp (1);  // Even though this is stored as double, it is really a single-precision epsilon

    static
    {
        // Dimensions specified in NeuroMLCoreDimensions.
        addDimension ("s",         "time");
        addDimension ("/s",        "per_time");
        addDimension ("V",         "voltage");
        addDimension ("/V",        "per_voltage");
        addDimension ("S",         "conductance");
        addDimension ("S/m2",      "conductanceDensity");
        addDimension ("F",         "capacitance");
        addDimension ("F/m2",      "specificCapacitance");
        addDimension ("Ohm",       "resistance");
        addDimension ("Ohm.m",     "resistivity");
        addDimension ("C",         "charge");
        addDimension ("C/mol",     "charge_per_mole");
        addDimension ("A",         "current");
        addDimension ("A/m2",      "currentDensity");
        addDimension ("m",         "length");
        addDimension ("m2",        "area");
        addDimension ("m3",        "volume");
        addDimension ("mol/m3",    "concentration");
        addDimension ("mol",       "substance");
        addDimension ("m/s",       "permeability");
        addDimension ("Cel",       "temperature");
        addDimension ("J/K/mol",   "idealGasConstant");
        addDimension ("S/V",       "conductance_per_voltage");
        addDimension ("mol/m/A/s", "rho_factor");
        dimensionsNML.put (AbstractUnit.ONE.getDimension (), "none");
        nmlDimensions.put ("none", AbstractUnit.ONE);

        // Units specified in NeuroMLCoreDimensions. With a little massaging, these can be converted to UCUM.
        String[] nmlDefined =
        {
            "s", "ms",
            "per_s", "per_ms", "Hz",
            "min", "per_min", "hour", "per_hour",
            "m", "cm", "um",
            "m2", "cm2", "um2",
            "m3", "cm3", "litre", "um3",
            "V", "mV",
            "per_V", "per_mV",
            "ohm", "kohm", "Mohm",
            "S", "mS", "uS", "nS", "pS",
            "S_per_m2", "mS_per_m2", "S_per_cm2",
            "F", "uF", "nF", "pF",
            "F_per_m2", "uF_per_cm2",
            "ohm_m", "kohm_cm", "ohm_cm",
            "C",
            "C_per_mol", "nA_ms_per_mol",
            "m_per_s",
            "A", "uA", "nA", "pA",
            "A_per_m2", "uA_per_cm2", "mA_per_cm2",
            "mol_per_m3", "mol_per_cm3", "M", "mM",
            "mol",
            "m_per_s", "cm_per_s", "um_per_s", "cm_per_ms",
            "degC",
            "K",
            "J_per_K_per_mol",
            "S_per_V", "nS_per_mV",
            "mol_per_m_per_A_per_s", "mol_per_cm_per_uA_per_ms"
        };
        for (String u : nmlDefined) unitsNML.put (UCUM.parse (cleanupUnits (u)), u);
    }

    public static void addDimension (String unitName, String dimensionName)
    {
        Unit<?> unit = UCUM.parse (unitName);
        dimensionsNML.put (unit.getDimension (), dimensionName);
        nmlDimensions.put (dimensionName, unit);
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
        return UnitValue.safeUnit (units);
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

/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPersistent;
import gov.sandia.n2a.eqset.MPart;
import gov.sandia.n2a.language.Constant;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.ParseException;
import gov.sandia.n2a.language.operator.Negate;
import gov.sandia.n2a.language.type.Scalar;

public class PartMap
{
    public Map<String,NameMap> outward = new HashMap<String,NameMap> ();  // from internal part to NeuroML; one-to-one
    public Map<String,NameMap> inward  = new HashMap<String,NameMap> ();  // from NeuroML part to internal; can have multiple keys for the same internal part

    public static class NameMap
    {
        public String                        internal;
        public List<String>                  neuroml    = new ArrayList<String> ();                 // All the NeuroML names mapped to the internal part. The first entry is the preferred name for export.
        public Map<String,ArrayList<String>> outward    = new HashMap<String,ArrayList<String>> (); // from internal variable to NeuroML; First entry is the preferred name for export.
        public Map<String,String>            inward     = new HashMap<String,String> ();            // from NeuroML variable to internal; several keys can map to the same value
        public Map<String,String>            dimensions;                                            // Only non-null if this part is tagged as having dimensionless (DL) fields. In that case, this maps from internal variable name to presumptive unit.

        /**
            Use this constructor to create a neutral (non-transforming) map on the fly.
        **/
        public NameMap (String neuromlPartName)
        {
            internal = neuromlPartName;
            neuroml.add (neuromlPartName);
        }

        public NameMap (MNode part)
        {
            build (part);
        }

        public void build (MNode part)
        {
            internal = part.key ();
            String pieces[] = part.get ("$metadata", "backend.neuroml.part").split (",");
            for (String n : pieces)
            {
                n = n.replace ("\"", "");
                neuroml.add (n);
            }

            if (part.child ("$metadata", "backend.neuroml.DL") != null) dimensions = new HashMap<String,String> ();

            // Only process top-level equations (no subparts)
            for (MNode c : part)
            {
                // Add name mapping
                String param = c.get ("$metadata", "backend.neuroml.param");
                if (! param.isEmpty ())
                {
                    String key = c.key ();
                    pieces = param.split (",");
                    ArrayList<String> variables = new ArrayList<String> ();
                    for (String s : pieces)
                    {
                        variables.add (s);
                        inward.put (s, key);
                    }
                    outward.put (key, variables);
                }

                // Add default unit
                if (dimensions == null) continue;
                pieces = c.get ().split ("@");
                try
                {
                    // Only do this for simple constants
                    Operator op = Operator.parse (pieces[0]);
                    if (op instanceof Negate) op = ((Negate) op).operand;
                    if (op instanceof Constant  &&  ((Constant) op).value instanceof Scalar)
                    {
                        String value = pieces[0].trim ();
                        int unitIndex = ImportJob.findUnits (value);
                        if (unitIndex < value.length ())
                        {
                            String unit = value.substring (unitIndex);
                            dimensions.put (c.key (), unit);
                        }
                    }
                }
                catch (ParseException e)
                {
                }
            }
        }

        public String importName (String neuromlVariableName)
        {
            String result = inward.get (neuromlVariableName);
            if (result == null) return neuromlVariableName;
            return result;
        }

        public String defaultUnit (String internalVariableName)
        {
            if (dimensions == null) return "";
            String unit = dimensions.get (internalVariableName);
            if (unit != null) return unit;
            return "";
        }
    }

    public PartMap ()
    {
        build ();
    }

    /**
        Scans model database and collects parts which are tagged for neuroml.
        This mapping really ought to be updated every time a tagged part is edited.
        However, in normal use (not during library development) the parts will be read-only,
        so one-time initialization should be sufficient.
    **/
    public void build ()
    {
        for (MNode c : AppData.models)
        {
            if (c.child ("$metadata", "backend.neuroml.part") == null) continue;  // Must directly declare a NeuroML part to be included.
            NameMap map = new NameMap (new MPart ((MPersistent) c));  // Create map using fully-collated part, not just the immediate one.
            outward.put (map.internal, map);
            for (String n : map.neuroml) inward.put (n, map);
        }
    }

    public NameMap exportMap (String internalPartName)
    {
        NameMap map = outward.get (internalPartName);
        if (map != null) return map;
        return new NameMap (internalPartName);
    }

    public NameMap importMap (String neuromlPartName)
    {
        NameMap map = inward.get (neuromlPartName);
        if (map != null) return map;
        return new NameMap (neuromlPartName);
    }

    public String importName (String neuromlPartName)
    {
        return importMap (neuromlPartName).internal;
    }
}

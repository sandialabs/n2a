/*
Copyright 2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPart;
import gov.sandia.n2a.db.MPartRepo;

/**
    Maps parts and variables to/from a backend-specific naming scheme.
    These mappings are stored as metadata in the model files.
    This class just collects that data and makes it available for processing in Java.
    Organized for fast lookup.
**/
public class PartMap
{
    public String              backend;                                   // name of backend to which this map applies
    public Map<String,NameMap> outward = new HashMap<String,NameMap> ();  // from internal part to backend part; one-to-one
    public Map<String,NameMap> inward  = new HashMap<String,NameMap> ();  // from backend part to internal part; can have multiple keys for the same internal part

    public static class NameMap
    {
        public String                        internalPart;
        public List<String>                  externalParts = new ArrayList<String> ();                 // All the backend names mapped to the internal part. The first entry is the preferred name for export.
        public Map<String,ArrayList<String>> outward       = new HashMap<String,ArrayList<String>> (); // from internal variable to backend variable; First entry is the preferred name for export.
        public Map<String,String>            inward        = new HashMap<String,String> ();            // from backend variable to internal variable; several keys can map to the same value

        /**
            Use this constructor to create a neutral (non-transforming) map on the fly.
        **/
        public NameMap (String partName)
        {
            internalPart = partName;
            externalParts.add (partName);
        }

        public NameMap (String backend, MNode part)
        {
            build (backend, part);
        }

        /**
            Creates a completely empty name map.
            Allow subclasses to have full control of construction.
            Also useful to create a neutral map when the part name is unknown.
        **/
        public NameMap ()
        {
        }

        public void build (String backend, MNode part)
        {
            internalPart = part.key ();
            String externalPartNames[] = part.get ("$meta", "backend", backend, "part").split (",");
            for (String name : externalPartNames)
            {
                if (! name.isBlank ()) externalParts.add (name);
            }
            if (externalParts.isEmpty ()) externalParts.add (internalPart);  // Simply a tagged part, with no name change.

            // Add variable mappings from top-level equations.
            for (MNode c : part)
            {
                String externalVariableNames = c.get ("$meta", "backend", backend, "param");
                if (externalVariableNames.isEmpty ()) continue;

                String key = c.key ();
                ArrayList<String> variables = new ArrayList<String> ();
                for (String name : externalVariableNames.split (","))
                {
                    variables.add (name);
                    inward.put (name, key);
                }
                outward.put (key, variables);
            }
        }

        public String importName (String externalVariableName)
        {
            String result = inward.get (externalVariableName);
            if (result == null) return externalVariableName;
            return result;
        }

        public void dump ()
        {
            System.out.println (internalPart + " " + externalParts);
            for (Entry<String, ArrayList<String>> e : outward.entrySet ())
            {
                System.out.println ("  " + e.getKey () + " " + e.getValue ());
            }
        }
    }

    /**
        Create an empty map.
    **/
    public PartMap ()
    {
    }

    /**
        Build a map for the given backend name.
        Every part that has mappings of some sort for this backend will be included,
        even if the part name itself is not mapped.
    **/
    public PartMap (String backendName)
    {
        backend = backendName;
        build ();
    }

    /**
        Scans model database and collects parts which are tagged for this backend.
        This mapping really ought to be updated every time a tagged part is edited.
        However, in normal use (not during library development) the parts will be read-only,
        so one-time initialization should be sufficient.
    **/
    public void build ()
    {
        for (MNode model : AppData.docs.childOrEmpty ("models"))
        {
            if (model.child ("$meta", "backend", backend, "part") == null) continue;  // Must directly declare a backend part to be included.
            NameMap map = new NameMap (backend, new MPartRepo (model));  // Create map using fully-collated part, not just the immediate one.
            outward.put (map.internalPart, map);
            for (String n : map.externalParts) inward.put (n, map);
        }
    }

    public NameMap exportMap (String internalPartName)
    {
        // Simple lookup.
        NameMap map = outward.get (internalPartName);
        if (map != null) return map;

        // Attempt to follow inheritance hierarchy.
        MNode part = AppData.docs.child ("models", internalPartName);
        if (part != null) return exportMap (part);

        // Give up and return neutral map.
        return new NameMap (internalPartName);
    }

    /**
        Finds the closest parent of the given part (which may be the part itself) which
        has an entry in this part map, and return the associated name map.
        Assumes entire heritage resides in the main database.
    **/
    public NameMap exportMap (MNode part)
    {
        String key = part.key ();
        NameMap map = outward.get (key);
        if (map != null) return map;
        String inherit = MPart.parseInheritOne (part.get ("$inherit"));  // Assume single inheritance
        if (! inherit.isEmpty ())
        {
            MNode parent = AppData.docs.child ("models", inherit);
            if (parent != null) return exportMap (parent);
        }
        return new NameMap (key);
    }

    public String exportName (String internalPartName)
    {
        return exportMap (internalPartName).externalParts.get (0);
    }

    public NameMap importMap (String externalPartName)
    {
        NameMap map = inward.get (externalPartName);
        if (map != null) return map;
        return new NameMap (externalPartName);
    }

    public String importName (String externalPartName)
    {
        return importMap (externalPartName).internalPart;
    }

    public void dump ()
    {
        System.out.println (backend);
        for (NameMap map : outward.values ()) map.dump ();
    }
}

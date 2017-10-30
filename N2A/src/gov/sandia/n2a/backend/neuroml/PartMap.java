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

public class PartMap
{
    public Map<String,NameMap> outward = new HashMap<String,NameMap> ();  // from internal part to NeuroML; one-to-one
    public Map<String,NameMap> inward  = new HashMap<String,NameMap> ();  // from NeuroML part to internal; can have multiple keys for the same internal part

    public static class NameMap
    {
        public String                        internal;
        public List<String>                  neuroml = new ArrayList<String> ();                 // All the NeuroML names mapped to the internal part. The first entry is the preferred name for export.
        public Map<String,ArrayList<String>> outward = new HashMap<String,ArrayList<String>> (); // from internal variable to NeuroML; First entry is the preferred name for export.
        public Map<String,String>            inward  = new HashMap<String,String> ();            // from NeuroML variable to internal

        public NameMap ()
        {
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

            // Only process top-level equations (no subparts)
            for (MNode c : part)
            {
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
            }
        }

        public String importVariable (String neuromlVariable)
        {
            String result = inward.get (neuromlVariable);
            if (result == null) return neuromlVariable;
            return result;
        }
    }

    public PartMap ()
    {
        build ();
    }

    /**
        Scans model database and collects parts which are tagged for neuroml.
        TODO: This mapping should be updated every time a tagged part is edited.
        However, in normal use (not during library development) the parts will be read-only,
        so this is a minor issue.
    **/
    public void build ()
    {
        for (MNode c : AppData.models)
        {
            if (c.get ("$metadata", "backend.neuroml.part").isEmpty ()) continue;
            NameMap map = new NameMap (c);
            outward.put (map.internal, map);
            for (String n : map.neuroml) inward .put (n, map);
        }
    }

    public String importPart (String neuromlPart)
    {
        NameMap map = inward.get (neuromlPart);
        if (map == null) return neuromlPart;
        return map.internal;
    }

    public String importVariable (String neuromlPart, String neuromlVariable)
    {
        NameMap map = inward.get (neuromlPart);
        if (map == null) return neuromlVariable;
        return map.importVariable (neuromlVariable);
    }
}

/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import java.util.HashMap;
import java.util.Map;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;

public class PartMap
{
    public Map<String,NameMap> outward = new HashMap<String,NameMap> ();  // from our to neuroml part
    public Map<String,NameMap> inward  = new HashMap<String,NameMap> ();  // from neuroml to our part

    public static class NameMap
    {
        String ourPart;
        String neuromlPart;
        public Map<String,String> outward = new HashMap<String,String> ();  // from our to neuroml variable
        public Map<String,String> inward  = new HashMap<String,String> ();  // from neuroml to our variable

        public NameMap ()
        {
        }

        public NameMap (MNode part)
        {
            build (part);
        }

        public void build (MNode part)
        {
            ourPart     = part.key ();
            neuromlPart = part.get ("$metadata", "backend.neuroml.part");

            // Only process top-level equations (no subparts)
            for (MNode c : part)
            {
                String param = c.get ("$metadata", "backend.neuroml.param");
                if (! param.isEmpty ())
                {
                    String key = c.key ();
                    outward.put (key, param);
                    inward .put (param, key);
                }
            }
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
            NameMap m = new NameMap (c);
            inward .put (m.neuromlPart, m);
            outward.put (m.ourPart,     m);
        }
    }
}

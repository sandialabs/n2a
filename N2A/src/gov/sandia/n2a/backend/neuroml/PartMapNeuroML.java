/*
Copyright 2017-2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import gov.sandia.n2a.backend.PartMap;
import gov.sandia.n2a.backend.neuroml.Sequencer.SequencerElement;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPart;
import gov.sandia.n2a.db.MPartRepo;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.UnitValue;
import gov.sandia.n2a.language.operator.Negate;

public class PartMapNeuroML extends PartMap
{
    public static class NameMapNeuroML extends NameMap
    {
        public Map<String,String>  dimensions;                                  // Only non-null if this part has dimensionless (DL) fields. In that case, this maps from internal variable name to specified unit.
        public Set<String>         children   = new HashSet<String> ();         // Subparts or parts named by a metadata "children" entry. Used to determine probable containment hierarchy.
        public Set<NameMapNeuroML> containers = new HashSet<NameMapNeuroML> (); // Parts that may contain us.
        public boolean             inheritContainersDone;                       // Indicates this map has already collated all the containers from its parents (via $inherit).
        public NameMapNeuroML      visitedFrom;                                 // Name map entry which is currently doing a breadth-first scan to collect container variables.

        /**
            Use this constructor to create a neutral (non-transforming) map on the fly.
        **/
        public NameMapNeuroML (String neuromlPartName)
        {
            super (neuromlPartName);
        }

        public NameMapNeuroML (MNode part)
        {
            build ("lems", part);
        }

        public void build (String backend, MNode part)
        {
            internalPart = part.key ();
            String pieces[] = part.get ("$meta", "backend", "lems", "part").split (",");
            for (String n : pieces)
            {
                if (! n.isBlank ()) externalParts.add (n);
            }
            if (externalParts.isEmpty ()) externalParts.add (internalPart);  // Simply a tagged part, with no name change.

            MNode metadata = part.child ("$meta", "backend", "lems", "children");
            if (metadata != null)
            {
                for (MNode m : metadata)
                {
                    children.add (m.get ().split (",")[0]);
                }
            }

            // We only want mappings from top-level equations.
            for (MNode c : part)
            {
                // Add child
                String inherit = c.get ("$inherit");
                if (! inherit.isEmpty ())
                {
                    inherit = MPart.parseInheritOne (inherit);
                    children.add (inherit);
                    continue;  // This is a subpart, so we are done with it.
                }

                // Add name mapping
                String key = c.key ();
                String param = c.get ("$meta", "backend", "lems", "param");
                if (! param.isEmpty ())
                {
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
                MNode metaDL = c.child ("$meta", "backend", "lems", "DL");
                if (metaDL != null)
                {
                    if (dimensions == null) dimensions = new HashMap<String,String> ();
                    String unit = metaDL.get ();
                    if (unit.isEmpty ())
                    {
                        pieces = c.get ().split ("@");
                        try
                        {
                            // Only do this for simple constants
                            Operator op = Operator.parse (pieces[0]);
                            if (op instanceof Negate) op = ((Negate) op).operand;
                            if (op.isScalar ())
                            {
                                String value = pieces[0].trim ();
                                int unitIndex = UnitValue.findUnits (value);
                                if (unitIndex < value.length ()) unit = value.substring (unitIndex);
                            }
                        }
                        catch (Exception e) {}
                    }
                    if (! unit.isEmpty ()) dimensions.put (key, unit);
                }
            }
        }

        /**
            Assuming our "containers" collection is filled by a call to inheritContainers(PartMap),
            add all name mappings from the containers, then recurse to containers of containers
            (breadth-first search).
        **/
        public void buildContainerMappings ()
        {
            LinkedList<NameMapNeuroML> front = new LinkedList<NameMapNeuroML> ();
            front.addAll (containers);
            while (! front.isEmpty ())
            {
                NameMapNeuroML c = front.removeFirst ();
                c.visitedFrom = this;

                // Add any variables not already mapped
                for (String key : c.outward.keySet ())
                {
                    // This code is named to resemble the "Add name mapping" code in build().
                    // Note the difference between our local outward collection versus the container's c.outward
                    ArrayList<String> pieces = c.outward.get (key);
                    ArrayList<String> variables = new ArrayList<String> ();
                    for (String s : pieces)
                    {
                        if (inward.containsKey (s)) continue;  // Only add mappings if the NeuroML variable is not already mapped.
                        variables.add (s);
                        inward.put (s, key);
                    }
                    if (variables.size () > 0)
                    {
                        ArrayList<String> existing = outward.get (key);
                        if (existing == null) outward.put (key, variables);
                        else                  existing.addAll (variables);
                    }
                }

                // Breadth-first search
                for (NameMapNeuroML cc : c.containers)
                {
                    if (cc.visitedFrom != this) front.addLast (cc);
                }
            }
        }

        /**
            Fills member "containers" with maps for all parts that can directly contain this one.
            This follows $inherit, but does not deal with containers of containers.
            Why inherit? Because any part C that inherits from another part P can be contained
            in the same way as P. Thus, children inherit their parent's containers.
        **/
        public void inheritContainers (PartMapNeuroML partMap)
        {
            if (inheritContainersDone) return;
            inheritContainersDone = true;
            inheritContainers (partMap, internalPart);
        }

        /**
            Search the inheritance hierarchy in the database for parent parts which have a
            name mapping, and thus possibly some containers to contribute to our list.
            inheritContainers(PartMap) produces a map entry that has all the containers
            for a given parent part, so there is no need to continue recursion after that call.
            While it may seem that this function and inheritContainers(PartMap) are mutually redundant,
            they are not. The subtle distinction is a shift in which object is currently receiving
            containers. This function stays put at the current name mapping, while recursing up through
            the $inherit hierarchy, searching for parents.
        **/
        public void inheritContainers (PartMapNeuroML partMap, String partName)
        {
            String inherit = AppData.docs.get ("models", partName, "$inherit");
            if (inherit.isEmpty ()) return;
            String pieces[] = MPart.parseInherit (inherit);
            for (String p : pieces)
            {
                NameMapNeuroML nameMap = (NameMapNeuroML) partMap.outward.get (p);
                if (nameMap == null)
                {
                    inheritContainers (partMap, p);
                }
                else
                {
                    nameMap.inheritContainers (partMap);
                    containers.addAll (nameMap.containers);
                }
            }
        }

        public String importName (String neuromlVariableName)
        {
            String result = inward.get (neuromlVariableName);
            if (result == null) return neuromlVariableName;
            return result;
        }

        /**
            Given a more specific hint about part identity, pick the most appropriate external name.
            @param neuromlPartName If empty, then return the default name, which is the first one
            given on the backend.lems.param line.
        **/
        public String exportName (String internalVariableName, String neuromlPartName)
        {
            ArrayList<String> names = outward.get (internalVariableName);
            if (names == null) return internalVariableName;
            String result = names.get (0);

            if (names.size () > 1  &&  ! neuromlPartName.isEmpty ())  // The export name is ambiguous, so try to pick one most relevant to the given part.
            {
                if (internalPart.startsWith ("HHVariable"))  // rather inflexible hard coding
                {
                    if (neuromlPartName.contains ("Rate"))     return "r";
                    if (neuromlPartName.contains ("Time"))     return "t";
                    if (neuromlPartName.contains ("Variable")) return "x";
                }
                else if (neuromlPartName.equals ("izhikevichCell"))  // not izhikevich2007Cell, which works correctly with default mapping
                {
                    if (internalVariableName.equals ("u" )) return "U";
                    if (internalVariableName.equals ("u'")) return "U'";
                }
                else if (neuromlPartName.endsWith ("GeneratorDL"))
                {
                    if (internalVariableName.equals ("I")) return "I";  // The default is to translate to lowercase i, but "DL" parts use uppercase I instead.
                }

                // Try sequencer
                SequencerElement se = PluginNeuroML.sequencer.getSequencerElement (neuromlPartName);
                if (se != null)
                {
                    int bestRank = Integer.MAX_VALUE;
                    for (String f : names)
                    {
                        int rank = se.attributes.indexOf (f);
                        if (rank >= 0  &&  rank < bestRank)
                        {
                            bestRank = rank;
                            result = f;
                        }
                    }
                }
            }

            return result;
        }

        public String defaultUnit (String internalVariableName)
        {
            if (dimensions == null) return "";
            String unit = dimensions.get (internalVariableName);
            if (unit == null) return "";
            return unit;
        }

        public void dump ()
        {
            System.out.println (internalPart + " " + externalParts);
            System.out.print ("  containers = [");
            for (NameMap c : containers) System.out.print (c.internalPart + ", ");  // leaves one bogus comma at end, but we don't really care
            System.out.println ("]");
            for (Entry<String, ArrayList<String>> e : outward.entrySet ())
            {
                System.out.println ("  " + e.getKey () + " " + e.getValue ());
            }
        }
    }

    public PartMapNeuroML ()
    {
        super ("lems");
    }

    /**
        Scans model database and collects parts which are tagged for neuroml.
        This mapping really ought to be updated every time a tagged part is edited.
        However, in normal use (not during library development) the parts will be read-only,
        so one-time initialization should be sufficient.
    **/
    public void build ()
    {
        for (MNode c : AppData.docs.childOrEmpty ("models"))
        {
            if (c.child ("$meta", "backend", "lems", "part") == null) continue;  // Must directly declare a NeuroML part to be included.
            NameMapNeuroML map = new NameMapNeuroML (new MPartRepo (c));  // Create map using fully-collated part, not just the immediate one.
            outward.put (map.internalPart, map);
            for (String n : map.externalParts) inward.put (n, map);
        }

        // Determine which parts can be contained by other parts.
        for (NameMap map : outward.values ())
        {
            NameMapNeuroML mapNML = (NameMapNeuroML) map;
            for (String childName : mapNML.children)
            {
                NameMapNeuroML childMap = (NameMapNeuroML) outward.get (childName);
                if (childMap != null) childMap.containers.add (mapNML);
            }
        }
        // Child parts add name mappings for variables visible from their containers.
        for (NameMap map : outward.values ()) ((NameMapNeuroML) map).inheritContainers (this);
        for (NameMap map : outward.values ()) ((NameMapNeuroML) map).buildContainerMappings ();
    }

    public NameMapNeuroML exportMap (String internalPartName)
    {
        // Simple lookup.
        NameMapNeuroML map = (NameMapNeuroML) outward.get (internalPartName);
        if (map != null) return map;

        // Attempt to follow inheritance hierarchy.
        MNode part = AppData.docs.child ("models", internalPartName);
        if (part != null) return exportMap (part);

        // Give up and return neutral map.
        return new NameMapNeuroML (internalPartName);
    }

    /**
        Finds the closest parent of the given part (which may be the part itself) which
        has an entry in this part map, and return the associated name map.
        Assumes entire heritage resides in the main database.
    **/
    public NameMapNeuroML exportMap (MNode part)
    {
        String key = part.key ();
        NameMapNeuroML map = (NameMapNeuroML) outward.get (key);
        if (map != null) return map;
        String inherit = MPart.parseInheritOne (part.get ("$inherit"));  // Assume single inheritance
        if (! inherit.isEmpty ())
        {
            MNode parent = AppData.docs.child ("models", inherit);
            if (parent != null) return exportMap (parent);
        }
        return new NameMapNeuroML (key);
    }

    public NameMapNeuroML importMap (String neuromlPartName)
    {
        NameMapNeuroML map = (NameMapNeuroML) inward.get (neuromlPartName);
        if (map != null) return map;
        return new NameMapNeuroML (neuromlPartName);
    }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.eqset;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.util.NDocList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import replete.util.ReflectionUtil;

public class MetadataAssembler {

    public static PartMetadataMap getAssembledPartMetadata(NDoc target) {
        return getAssembledPartMetadata(target, false);
    }
    public static PartMetadataMap getAssembledPartMetadata(NDoc target, boolean recursiveCheckOnly) {
        try {
            NDocList parts = new NDocList();
            List<String> assembleReasons = new ArrayList<String>();
            PartMetadataMap map = getAssembledPartMetadata(target, parts, assembleReasons, "(target)", recursiveCheckOnly);
            return map;
        } catch(DataModelLoopException e) {
            String cna = "Could not assemble " + target.get("name").toString() + " metadata.  ";
            String msg = (!recursiveCheckOnly ? cna : "") + "A loop exists in the parent and/or include hierarchy:";
            ReflectionUtil.set("detailMessage", e, msg + "\n" + e.getMessage());
            throw e;
        }
    }

    private static PartMetadataMap getAssembledPartMetadata(NDoc target,
            NDocList parts, List<String> assembleReasons, String assembleR, boolean recursiveCheckOnly)
            throws DataModelLoopException {

        // Perform recursion check.  Make sure that the part being processed
        // has not yet been seen during this recursive assembly process.
        if(parts.contains(target)) {
            parts.add(target);
            assembleReasons.add(assembleR);

            DataModelLoopException dmle = new DataModelLoopException(target, parts, assembleReasons);
            String msg = "";
            for(int i = 0; i < parts.size(); i++) {
                msg += DataModelLoopException.getErrorLine(dmle, i);
                if(i != parts.size() - 1) {
                    msg += " --> ";
                }
            }
            ReflectionUtil.set("detailMessage", dmle, msg);
            throw dmle;
        }

        // Push tracking information for this part onto some lists.
        // This enables recursion detection.
        parts.add(target);
        assembleReasons.add(assembleR);

        // Start off with a blank equation map for this part.
        PartMetadataMap map = recursiveCheckOnly ? null : new PartMetadataMap();

        // Inheritance: Add all the parent's equations to the map.
        NDoc parent = target.get("parent");
        if(parent != null) {
            PartMetadataMap parMap = getAssembledPartMetadata(parent, parts, assembleReasons, "(parent)", recursiveCheckOnly);
            if(!recursiveCheckOnly) {
                map.putAll(parMap);
            }
        }

        if(!recursiveCheckOnly) {
            Map<String, String> terms = target.getAndSetValid("$metadata", new HashMap<String, String>(), Map.class);
            for(String term : terms.keySet()) {
                map.put(term, terms.get(term));
            }
        }

        // Remove tracking information for this part.
        parts.remove(parts.size() - 1);
        assembleReasons.remove(assembleReasons.size() - 1);

        return map;
    }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.data;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BridgeOrient implements Bridge {
    private NDoc source;

    public BridgeOrient(List<Layer> layers, String name, Model model,
            Part derivedPart) {
        source = new NDoc("gov.sandia.umf.n2a$Bridge");
        source.set("name", name);
        source.set("model", model.getSource());// Necessary?
        source.set("derivedPart", derivedPart.getSource());
        List<NDoc> layerDocs = new ArrayList<NDoc>();
        for (Layer layer : layers) {
            layerDocs.add(layer.getSource());
        }
        source.set("layers", layerDocs);
    }

    public BridgeOrient(NDoc doc) {
        source = doc;
    }

    @Override
    public String getName() {
        return source.get("name");
    }

    @Override
    public void setName(String newBridgeName) {
        source.set("name", newBridgeName);
    }

   @Override
    public String getConnectionName() {
        return getDerivedPart().getName();
    }

    @Override
    public List<Layer> getLayers() {
        List<NDoc> layerDocs = source.get("layers");
        List<Layer> layers = new ArrayList<Layer>();
        for(NDoc doc : layerDocs) {
            layers.add(new LayerOrient(doc));
        }
        return layers;
    }

    @Override
    public void setLayers(List<Layer> layers) {
        List<NDoc> layerDocs = new ArrayList<NDoc>();
        for(Layer layer : layers) {
            layerDocs.add(layer.getSource());
        }
        source.set("layers", layerDocs);
    }

    @Override
    public Map<String, Layer> getAliasLayerMap() {
        Map<String, Layer> aliases = new LinkedHashMap<String, Layer>();
        NDoc dPdoc = source.get("derivedPart");
        NDoc connDoc = dPdoc.get("parent");
        List<NDoc> assocs = connDoc.getValid("associations", new ArrayList<NDoc>(), List.class);
        List<NDoc> cAssocs = new ArrayList<NDoc>();
        for(NDoc assoc : assocs) {
            if(((String) assoc.get("type")).equalsIgnoreCase("connect")) {
                cAssocs.add(assoc);
            }
        }
        Set<String> aliasedLayers = new HashSet<String>();
        for(NDoc con : cAssocs) {
            NDoc destDoc = con.get("dest");
            List<NDoc> layerDocs = source.get("layers");
            for (NDoc layerDoc : layerDocs) {
                if(aliasedLayers.contains(layerDoc.getId())) {
                    continue;
                }
                Layer layer = new LayerOrient(layerDoc);
                NDoc compDoc = ((NDoc) layerDoc.get("derivedPart")).get("parent");
                if(layerDocs.size() == 1) {
                    if(destDoc.getId().equals(compDoc.getId())) {
                        aliases.put(con.get("name").toString(), layer);
                        break;
                    }
                } else {
                    System.out.println(destDoc.getId());
                    System.out.println(compDoc.getId());
                    System.out.println(aliases);
                    System.out.println(con.get("name").toString ());
                    if(destDoc.getId().equals(compDoc.getId()) && !aliases.containsKey(con.get("name"))) {
                        aliases.put(con.get("name").toString(), layer);
                        aliasedLayers.add(layerDoc.getId());
                        break;
                    }
                }
            }
        }
        if(aliases.size() != cAssocs.size()) {
            throw new RuntimeException("Could not create layer-alias map.");
        }
        return aliases;
    }

    @Override
    public Part getDerivedPart() {
        NDoc dP = source.get("derivedPart");
        if (dP != null) {
            return new PartOrient((NDoc) source.get("derivedPart"));
        }
        return null;
    }

    public NDoc getSource() {
        return source;
    }

    public Bridge copy() {
        BridgeOrient bridgeCopy = new BridgeOrient(source.copy());
        NDoc bridgeSourceCopy = bridgeCopy.getSource();
        Part partCopy = getDerivedPart().copy();
        bridgeSourceCopy.set("derivedPart", partCopy.getSource());
        return bridgeCopy;
    }
}

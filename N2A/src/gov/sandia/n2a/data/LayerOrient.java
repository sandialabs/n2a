/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.data;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;

public class LayerOrient implements Layer
{
    private NDoc source;

    public LayerOrient(NDoc record)
    {
        source = record;
    }

    public LayerOrient(String name, Part childPart, Model model) {
        source = new NDoc("gov.sandia.umf.n2a$Layer");
        source.set("name", name);
        source.set("derivedPart", childPart.getSource());
        source.set("model", model.getSource()); // Necessary?
    }

    @Override
    public String getName() {
        return source.get("name");
    }

    @Override
    public void setName(String newLayerName) {
        source.set("name", newLayerName);
    }

    @Override
    public String getCompartmentName() {
        NDoc derivedPart = source.get("derivedPart");
        NDoc parent = derivedPart.get("parent");
        return parent.get("name");
    }

    @Override
    public Part getDerivedPart() {
        NDoc dP = source.get("derivedPart");
        if (dP != null) {
            System.out.println(dP.getId());
            return new PartOrient(dP);
        }
        return null;
    }

    @Override
    public NDoc getSource() {
        return source;
    }

    public Layer copy() {
        LayerOrient layerCopy = new LayerOrient(source.copy());
        NDoc layerSourceCopy = layerCopy.getSource();
        Part partCopy = getDerivedPart().copy();
        layerSourceCopy.set("derivedPart", partCopy.getSource());
        return layerCopy;
    }
}

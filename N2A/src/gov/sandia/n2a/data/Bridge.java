/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.data;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;

import java.util.List;
import java.util.Map;

public interface Bridge {

    String getName();

    void setName(String newBridgeName);

    String getConnectionName();

    Part getDerivedPart();

    Map<String, Layer> getAliasLayerMap();

    List<Layer> getLayers();

    void setLayers(List<Layer> layers);

    NDoc getSource();

    Bridge copy();
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.data;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ui.ensemble.domains.ParameterDomain;

import java.util.List;

public interface Part {

    String getName();

    Part getParent();

    void setEqs(List<NDoc> list);

    List<NDoc> getEqs();

    NDoc getSource();

    Part copy();

    void setSelectedParameters(ParameterDomain domain);
}

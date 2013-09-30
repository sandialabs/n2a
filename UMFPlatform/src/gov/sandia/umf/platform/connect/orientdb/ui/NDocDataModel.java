/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.connect.orientdb.ui;

import java.util.List;

public interface NDocDataModel {
    public NDoc getById(String className, String id);
    public List<NDoc> getAll(String className);
    public List<NDoc> getByQuery(String className, String query);
}

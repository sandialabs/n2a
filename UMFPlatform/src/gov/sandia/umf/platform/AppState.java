/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform;

import gov.sandia.umf.platform.db.MDoc;
import gov.sandia.umf.platform.plugins.extpoints.ProductCustomization;
import java.io.File;

public class AppState extends MDoc
{
    public ProductCustomization prodCustomization;

    protected static AppState instance;
    public static AppState getInstance ()
    {
        if (instance == null) instance = new AppState ();
        return instance;
    }

    public AppState ()
    {
        super (new File (UMF.getAppResourceDir (), "client.state").getAbsolutePath ());
        UMF.getAppResourceDir ().mkdirs ();  // ensure the folder exists
    }
}

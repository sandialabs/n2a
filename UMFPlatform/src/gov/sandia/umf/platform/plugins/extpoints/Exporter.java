/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins.extpoints;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.ui.export.ExportParameters;
import gov.sandia.umf.platform.ui.export.ExportParametersPanel;

import java.io.IOException;

import javax.swing.ImageIcon;

import replete.plugins.ExtensionPoint;

public interface Exporter extends ExtensionPoint {
    public String getName();
    public String getDescription();
    public ImageIcon getIcon();

    public void export(NDoc source, ExportParameters params) throws IOException;
    public ExportParametersPanel getParametersPanel();
}

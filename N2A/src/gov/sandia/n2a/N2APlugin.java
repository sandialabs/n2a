/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a;

import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.transfer.ExportNative;
import gov.sandia.n2a.transfer.ExportNeuroML;
import gov.sandia.n2a.transfer.ImportNative;
import gov.sandia.n2a.ui.eq.ModelRecordHandler;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.ImageIcon;

import replete.plugins.DefaultPlugin;
import replete.plugins.ExtensionPoint;

public class N2APlugin extends DefaultPlugin {

    private static N2APlugin plugin;
    public static N2APlugin getInstance() {
        return plugin;
    }
    public N2APlugin() {
        plugin = this;
    }

    public String getName() {
        return "N2A Modeling Tools";
    }

    @Override
    public String getVersion ()
    {
        return "0.91";
    }

    @Override
    public String getProvider() {
        return "Sandia National Laboratories";
    }

    @Override
    public ImageIcon getIcon() {
        return ImageUtil.getImage("n2a.gif");
    }

    @Override
    public String getDescription() {
        return "<html>This plug-in provides support N2A modeling concepts.<br><br>More information: <u><font color='blue'>http://n2a.sandia.gov</font></u></html>";
    }

    @Override
    public ExtensionPoint[] getExtensions ()
    {
        return new ExtensionPoint[]
        {
            new N2AProductCustomization (),
            new ExportNeuroML (),
            new ExportNative (),
            new ImportNative (),
            //new ReferenceRecordHandler (),
            new ModelRecordHandler ()
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends ExtensionPoint>[] getExtensionPoints ()
    {
        return new Class[] {
           Operator.Factory.class
        };
    }

    @Override
    public void start ()
    {
        Operator.initFromPlugins ();
    }
}

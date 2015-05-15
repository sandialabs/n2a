/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins.base;

import gov.sandia.umf.platform.plugins.extpoints.Analyzer;
import gov.sandia.umf.platform.plugins.extpoints.Exporter;
import gov.sandia.umf.platform.plugins.extpoints.MenuItems;
import gov.sandia.umf.platform.plugins.extpoints.ProductCustomization;
import gov.sandia.umf.platform.plugins.extpoints.RecordHandler;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.ImageIcon;

import replete.plugins.DefaultPlugin;
import replete.plugins.ExtensionPoint;

public class PlatformPlugin extends DefaultPlugin {

    private static PlatformPlugin plugin;
    public static PlatformPlugin getInstance() {
        return plugin;
    }
    public PlatformPlugin() {
        plugin = this;
    }

    public String getName() {
        return "UMF Platform Plug-in";
    }

    @Override
    public String getVersion ()
    {
        return "0.9";
    }

    @Override
    public String getProvider() {
        return "Sandia National Laboratories";
    }

    @Override
    public ImageIcon getIcon() {
        return ImageUtil.getImage("model.gif");
    }

    @Override
    public String getDescription() {
        return "<html>This plug-in contains the base extension points for the UMF platform.</html>";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends ExtensionPoint>[] getExtensionPoints() {
        return new Class[] {
           ProductCustomization.class,
           Simulator.class,
           Analyzer.class,
           MenuItems.class,
           RecordHandler.class,
           Exporter.class
        };
    }

    @Override
    public ExtensionPoint[] getExtensions() {
        return new ExtensionPoint[] {
            new PlatformProductCustomization(),
            new RunRecordHandler(),
            new RunEnsembleRecordHandler(),
            new ProfileRecordHandler()
        };
    }
}

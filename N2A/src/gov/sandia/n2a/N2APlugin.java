/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a;

import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Exporter;
import gov.sandia.n2a.plugins.extpoints.Importer;
import gov.sandia.n2a.plugins.extpoints.MenuItems;
import gov.sandia.n2a.plugins.extpoints.RecordHandler;
import gov.sandia.n2a.transfer.ExportNative;
import gov.sandia.n2a.transfer.ImportNative;
import gov.sandia.n2a.ui.eq.ModelRecordHandler;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.jobs.RunHandler;

import javax.swing.ImageIcon;

import replete.plugins.DefaultPlugin;
import replete.plugins.ExtensionPoint;

public class N2APlugin extends DefaultPlugin
{
    private static N2APlugin plugin;
    public static N2APlugin getInstance ()
    {
        return plugin;
    }

    public N2APlugin ()
    {
        plugin = this;
    }

    @Override
    public String getName ()
    {
        return "N2A Modeling Tools";
    }

    @Override
    public String getVersion ()
    {
        return "0.92";
    }

    @Override
    public String getProvider ()
    {
        return "Sandia National Laboratories";
    }

    @Override
    public ImageIcon getIcon ()
    {
        return ImageUtil.getImage ("n2a.png");
    }

    @Override
    public String getDescription ()
    {
        return "<html>This plug-in provides support N2A modeling concepts.<br><br>More information: <u><font color='blue'>http://n2a.sandia.gov</font></u></html>";
    }

    @Override
    public ExtensionPoint[] getExtensions ()
    {
        return new ExtensionPoint[]
        {
            new ExportNative (),
            new ImportNative (),
            new ModelRecordHandler (),
            new RunHandler (),
            //new ReferenceRecordHandler (),
            //new RunEnsembleRecordHandler (),
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends ExtensionPoint>[] getExtensionPoints ()
    {
        return new Class[]
        {
            Backend.class,
            MenuItems.class,
            RecordHandler.class,
            Exporter.class,
            Importer.class,
            Operator.Factory.class
        };
    }

    @Override
    public void start ()
    {
        Operator.initFromPlugins ();
    }
}

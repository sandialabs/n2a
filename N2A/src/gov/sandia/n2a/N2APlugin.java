/*
Copyright 2013,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a;

import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.plugins.Plugin;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Exporter;
import gov.sandia.n2a.plugins.extpoints.Importer;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.plugins.extpoints.Activity;
import gov.sandia.n2a.transfer.ExportNative;
import gov.sandia.n2a.transfer.ImportNative;
import gov.sandia.n2a.ui.eq.ActivityModel;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.jobs.ActivityRun;
import gov.sandia.n2a.ui.ref.ActivityReference;
import gov.sandia.n2a.ui.settings.ActivitySettings;
import gov.sandia.n2a.ui.settings.SettingsLookAndFeel;
import gov.sandia.n2a.ui.settings.SettingsAbout;
import gov.sandia.n2a.ui.settings.SettingsRepo;
import gov.sandia.n2a.ui.settings.SettingsGeneral;

import javax.swing.ImageIcon;

public class N2APlugin extends Plugin
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
            new ActivityModel (),
            new ActivityRun (),
            new ActivityReference (),
            //new RunEnsembleRecordHandler (),
            new ActivitySettings (),
            new SettingsAbout (),
            new SettingsGeneral (),
            new SettingsLookAndFeel (),
            new SettingsRepo ()
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends ExtensionPoint>[] getExtensionPoints ()
    {
        return new Class[]
        {
            Activity.class,
            Backend.class,
            Exporter.class,
            Importer.class,
            Operator.Factory.class,
            Settings.class
        };
    }

    @Override
    public void start ()
    {
        Operator.initFromPlugins ();
    }
}

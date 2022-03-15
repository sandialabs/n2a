/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.host.Host;
import gov.sandia.n2a.host.RemoteSlurm;
import gov.sandia.n2a.host.RemoteUnix;
import gov.sandia.n2a.host.Unix;
import gov.sandia.n2a.host.Windows;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.plugins.Plugin;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.extpoints.Backend;
import gov.sandia.n2a.plugins.extpoints.Export;
import gov.sandia.n2a.plugins.extpoints.Import;
import gov.sandia.n2a.plugins.extpoints.Settings;
import gov.sandia.n2a.plugins.extpoints.ShutdownHook;
import gov.sandia.n2a.plugins.extpoints.Activity;
import gov.sandia.n2a.transfer.ExportNative;
import gov.sandia.n2a.transfer.ImportNative;
import gov.sandia.n2a.ui.eq.ActivityModel;
import gov.sandia.n2a.ui.images.ImageUtil;
import gov.sandia.n2a.ui.jobs.ActivityRun;
import gov.sandia.n2a.ui.ref.ActivityReference;
import gov.sandia.n2a.ui.ref.ImportBibTeX;
import gov.sandia.n2a.ui.ref.ImportEndNote;
import gov.sandia.n2a.ui.ref.ImportPubMed;
import gov.sandia.n2a.ui.ref.ImportRIS;
import gov.sandia.n2a.ui.settings.ActivitySettings;
import gov.sandia.n2a.ui.settings.SettingsLookAndFeel;
import gov.sandia.n2a.ui.settings.SettingsAbout;
import gov.sandia.n2a.ui.settings.SettingsRepo;
import gov.sandia.n2a.ui.studies.ActivityStudy;
import gov.sandia.n2a.ui.settings.SettingsGeneral;
import gov.sandia.n2a.ui.settings.SettingsHost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        List<ExtensionPoint> result = new ArrayList<ExtensionPoint> ();
        Collections.addAll (result,
            Unix.factory (),
            Windows.factory (),
            RemoteUnix.factory (),
            RemoteSlurm.factory ()
        );
        if (! AppData.properties.getBoolean ("headless"))
        {
            Collections.addAll (result,
                new ExportNative (),
                gov.sandia.n2a.ui.ref.PanelSearch.exportBibTeX,
                new ImportNative (),
                new ImportBibTeX (),
                new ImportEndNote (),
                new ImportPubMed (),
                new ImportRIS (),
                new ActivityModel (),
                new ActivityRun (),
                new ActivityReference (),
                new ActivityStudy (),
                new ActivitySettings (),
                new SettingsAbout (),
                new SettingsGeneral (),
                new SettingsHost (),
                new SettingsLookAndFeel (),
                new SettingsRepo ()
            );
        }
        return result.toArray (new ExtensionPoint[result.size ()]);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends ExtensionPoint>[] getExtensionPoints ()
    {
        return new Class[]
        {
            Activity.class,
            Backend.class,
            Export.class,
            Host.Factory.class,
            Import.class,
            Operator.Factory.class,
            Settings.class,
            ShutdownHook.class
        };
    }

    @Override
    public void start ()
    {
        Operator.initFromPlugins ();
    }
}

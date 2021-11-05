/*
Copyright 2013-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.xyce;

import gov.sandia.n2a.backend.xyce.function.Sinewave;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.plugins.Plugin;
import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.ui.images.ImageUtil;

import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;

public class XycePlugin extends Plugin
{
    public String getName ()
    {
        return "Xyce Simulation for N2A";
    }

    public String getVersion ()
    {
        return "0.9";
    }

    public String getProvider ()
    {
        return "Sandia National Laboratories";
    }

    public ImageIcon getIcon ()
    {
        return ImageUtil.getImage("n2a.gif");
    }

    public String getDescription ()
    {
        return "<html>This plug-in provides N2A-Xyce simulator integration.<br><br>More information: <u><font color='blue'>http://n2a.sandia.gov</font></u></html>";
    }

    public ExtensionPoint[] getExtensions ()
    {
        List<ExtensionPoint> result = new ArrayList<ExtensionPoint> ();
        result.add (new XyceBackend ());
        result.add (Sinewave.factory ());
        if (! AppData.properties.getBoolean ("headless"))
        {
            result.add (new SettingsXyce ());
        }
        return result.toArray (new ExtensionPoint[result.size ()]);
    }
}

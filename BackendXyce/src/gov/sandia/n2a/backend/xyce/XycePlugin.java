/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce;

import gov.sandia.n2a.backend.xyce.function.Sinewave;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import javax.swing.ImageIcon;

import replete.plugins.ExtensionPoint;
import replete.plugins.DefaultPlugin;

public class XycePlugin extends DefaultPlugin
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
        return new ExtensionPoint[]
        {
            new XyceSimulator(),
            Sinewave.factory ()
        };
    }
}

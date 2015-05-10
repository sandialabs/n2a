/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce;

import gov.sandia.n2a.backend.xyce.functions.XyceSineWaveFunction;
import gov.sandia.n2a.language.function.Grid;
import gov.sandia.n2a.ui.images.ImageUtil;

import javax.swing.ImageIcon;

import replete.plugins.ExtensionPoint;
import replete.plugins.Plugin;

public class XycePlugin implements Plugin {

    public String getName() {
        return "Xyce Simulation for N2A";
    }

    public String getVersion() {
        return VersionConstants.MAJOR + "." +
               VersionConstants.MINOR + "." +
               VersionConstants.SERVICE + "." +
               VersionConstants.BUILD;
    }

    public String getProvider() {
        return "Sandia National Laboratories";
    }

    public ImageIcon getIcon() {
        return ImageUtil.getImage("n2a.gif");
    }

    public String getDescription() {
        return "<html>This plug-in provides N2A-Xyce simulator integration.<br><br>More information: <u><font color='blue'>http://n2a.sandia.gov</font></u></html>";
    }

    @SuppressWarnings("unchecked")
    public Class<? extends ExtensionPoint>[] getExtensionPoints() {
        return new Class[] {
            TestExtPoint.class
        };
    }

    public ExtensionPoint[] getExtensions ()
    {
        return new ExtensionPoint[]
        {
            new XyceSimulator(),
            XyceSineWaveFunction.factory ()
        };
    }

    public void start() {
        // TODO: This code was used to prove that plug-ins can
        // build on each other's extension points irregardless
        // of the order that they are added to the platform.
//        List<ExtensionPoint> exts = PluginManager.getExtensionsForPoint(TestExtPoint.class);
//        for(ExtensionPoint ext : exts) {
//            TestExtPoint tep = (TestExtPoint) ext;
//            System.out.println("TEST: " + tep.getTestString());
//        }
    }
}

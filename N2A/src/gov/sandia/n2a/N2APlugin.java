/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a;

import gov.sandia.n2a.exporters.LEMSExporter;
import gov.sandia.n2a.language.op.Function;
import gov.sandia.n2a.language.op.FunctionList;
import gov.sandia.n2a.records.ModelRecordHandler;
import gov.sandia.n2a.records.PartRecordHandler;
import gov.sandia.n2a.records.ReferenceRecordHandler;
import gov.sandia.umf.platform.plugins.extpoints.MenuItems;
import gov.sandia.umf.platform.plugins.extpoints.UMFMenuActionListener;
import gov.sandia.umf.platform.plugins.extpoints.UMFMenuBarActionDescriptor;
import gov.sandia.umf.platform.ui.UIController;
import gov.sandia.umf.platform.ui.images.ImageUtil;

import java.awt.event.ActionEvent;
import java.util.LinkedHashMap;
import java.util.Map;

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
    public String getVersion() {
        return VersionConstants.MAJOR + "." +
               VersionConstants.MINOR + "." +
               VersionConstants.SERVICE + "." +
               VersionConstants.BUILD;
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
    public ExtensionPoint[] getExtensions() {
        return new ExtensionPoint[] {
                new N2AProductCustomization(),
                new LEMSExporter(),
                new PartRecordHandler(),
                new ModelRecordHandler(),
                new ReferenceRecordHandler(),
                new MenuItems() {
                    public Map<String, UMFMenuBarActionDescriptor> getMenuItems() {
                        Map<String, UMFMenuBarActionDescriptor> map = new LinkedHashMap<String, UMFMenuBarActionDescriptor>();
                        map.put("newCompOrient", new UMFMenuBarActionDescriptor("fileMenu/newMenu", "New Compartment", 'N',
                            ImageUtil.getImage("compnew.gif"), null, false, 0, false, new UMFMenuActionListener() {
                                public void actionPerformed(UIController uiController, ActionEvent e) {
                                    PartRecordHandler handler = new PartRecordHandler();
                                    uiController.openRecord(handler.createNewPart("compartment"));
                                }
                        }));
                        map.put("newConnOrient", new UMFMenuBarActionDescriptor("fileMenu/newMenu", "New Connection", 'N',
                                ImageUtil.getImage("connnew.gif"), null, false, 0, false, new UMFMenuActionListener() {
                                    public void actionPerformed(UIController uiController, ActionEvent e) {
                                        PartRecordHandler handler = new PartRecordHandler();
                                        uiController.openRecord(handler.createNewPart("connection"));
                                    }
                            }));
                        map.put("newModelOrient", new UMFMenuBarActionDescriptor("fileMenu/newMenu", "New Model", 'N',
                                ImageUtil.getImage("modelnew.gif"), null, false, 0, false, new UMFMenuActionListener() {
                                    public void actionPerformed(UIController uiController, ActionEvent e) {
                                        ModelRecordHandler handler = new ModelRecordHandler();
                                        uiController.openRecord(handler.createNewModel());
                                    }
                            }));
                        return map;
                    }
                }
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends ExtensionPoint>[] getExtensionPoints() {
        return new Class[] {
           Function.class
        };
    }

    @Override
    public void start() {
        FunctionList.initFromPlugins();
    }
}

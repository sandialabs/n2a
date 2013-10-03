/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.plugins;

import gov.sandia.umf.platform.UMF;
import gov.sandia.umf.platform.plugins.extpoints.RecordHandler;
import gov.sandia.umf.platform.plugins.extpoints.Simulator;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import replete.plugins.ExtensionPoint;
import replete.plugins.Plugin;
import replete.plugins.PluginManager;
import replete.util.CodeUtil;

public class UMFPluginManager {
    private static Map<String, RecordHandler> recordHandlers = new HashMap<String, RecordHandler>();
    private static Map<String, List<Simulator>> recordSimulators = new HashMap<String, List<Simulator>>();

    public static RecordHandler getHandler(String recordType) {
        return recordHandlers.get(recordType);
    }
    public static List<Simulator> getSimulators(String recordType) {
        return recordSimulators.get(recordType);
    }

    public static void init() {
        List<ExtensionPoint> handlers = PluginManager.getExtensionsForPoint(RecordHandler.class);
        for(ExtensionPoint handler0 : handlers) {
            RecordHandler handler = ((RecordHandler)handler0);
            String[] handledSet = handler.getHandledRecordTypes();
            if(handledSet != null) {
                for(String handled : handledSet) {
                    if(recordHandlers.containsKey(handled)) {
                        // warning!!
                    } else {
                        recordHandlers.put(handled, handler);
                    }
                }
            }
        }

        List<ExtensionPoint> simulators = PluginManager.getExtensionsForPoint(Simulator.class);
        for(ExtensionPoint simulator0 : simulators) {
            Simulator simulator = ((Simulator) simulator0);
            String[] handledSet = simulator.getCompatibleModelTypes();
            if(handledSet != null) {
                for(String handled : handledSet) {
                    List<Simulator> allSim = recordSimulators.get(handled);
                    if(allSim == null) {
                        allSim = new ArrayList<Simulator>();
                        recordSimulators.put(handled, allSim);
                    }
                    allSim.add(simulator);
                }
            }
        }
    }

    public static List<ExtensionPoint> getExtensionsForPoint(String pluginId, Class<? extends ExtensionPoint> clazz) {
        Plugin plugin = PluginManager.getPluginById(pluginId);
        List<ExtensionPoint> exts = PluginManager.getExtensionsInPlugin(plugin);
        List<ExtensionPoint> found = new ArrayList<ExtensionPoint>();
        for(ExtensionPoint ext : exts) {
            Class<? extends ExtensionPoint> extPoint = PluginManager.getPointForExtension(ext);
            if(PluginManager.getExtensionPointId(extPoint).equals(
                    PluginManager.getExtensionPointId(clazz))) {
                found.add(ext);
            }
        }
        return found;
    }

    public static File getPluginsDir ()
    {
        return new File (UMF.getAppResourceDir (), "plugins");
    }

    public static File getStorageDir (Class<?> pClass)
    {
        File pluginsDir = getPluginsDir ();
        File pluginStorage = new File (pluginsDir, pClass.getName());
        pluginStorage.mkdirs();
        return pluginStorage;
    }
}

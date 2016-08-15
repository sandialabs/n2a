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
import replete.plugins.PluginManager;

public class UMFPluginManager
{
    public static Map<String, RecordHandler>   recordHandlers   = new HashMap<String, RecordHandler> ();
    public static Map<String, List<Simulator>> recordSimulators = new HashMap<String, List<Simulator>> ();

    public static RecordHandler getHandler (String recordType)
    {
        return recordHandlers.get (recordType);
    }

    public static List<Simulator> getSimulators (String recordType)
    {
        return recordSimulators.get (recordType);
    }

    public static void init ()
    {
        List<ExtensionPoint> handlers = PluginManager.getExtensionsForPoint (RecordHandler.class);
        for (ExtensionPoint handler0 : handlers)
        {
            RecordHandler handler = (RecordHandler) handler0;
            String handled = handler.getName ();
            if (recordHandlers.containsKey (handled))
            {
                System.err.println ("More than one handler for the same record type: " + handled);
            }
            else
            {
                recordHandlers.put (handled, handler);
            }
        }

        List<ExtensionPoint> simulators = PluginManager.getExtensionsForPoint (Simulator.class);
        for (ExtensionPoint simulator0 : simulators)
        {
            Simulator simulator = (Simulator) simulator0;
            String[] handledSet = simulator.getCompatibleModelTypes ();
            if (handledSet != null)
            {
                for (String handled : handledSet)
                {
                    List<Simulator> allSim = recordSimulators.get (handled);
                    if (allSim == null)
                    {
                        allSim = new ArrayList<Simulator> ();
                        recordSimulators.put (handled, allSim);
                    }
                    allSim.add (simulator);
                }
            }
        }
    }

    public static File getPluginsDir ()
    {
        return new File (UMF.getAppResourceDir (), "plugins");
    }

    public static File getStorageDir (Class<?> pClass)
    {
        File pluginsDir = getPluginsDir ();
        File pluginStorage = new File (pluginsDir, pClass.getName ());
        pluginStorage.mkdirs ();
        return pluginStorage;
    }
}

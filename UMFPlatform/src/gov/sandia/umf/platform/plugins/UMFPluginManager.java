/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
 */

package gov.sandia.umf.platform.plugins;

import gov.sandia.umf.platform.db.AppData;
import gov.sandia.umf.platform.plugins.extpoints.Backend;
import gov.sandia.umf.platform.plugins.extpoints.RecordHandler;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import replete.plugins.ExtensionPoint;
import replete.plugins.PluginManager;

public class UMFPluginManager
{
    public static Map<String,RecordHandler> getRecordHandlers ()
    {
        Map<String,RecordHandler> result = new TreeMap<String,RecordHandler> ();
        for (ExtensionPoint ep : PluginManager.getExtensionsForPoint (RecordHandler.class))
        {
            RecordHandler rh = (RecordHandler) ep;
            result.put (rh.getName (), rh);
        }
        return result;
    }

    /**
        Finds the Backend instance that matches the given name.
        If no match is found, returns the Internal backend.
        If Internal is missing, the system is already too broken to run.
    **/
    public static Backend getBackend (String name)
    {
        Backend result = null;
        Backend internal = null;
        for (ExtensionPoint ext : PluginManager.getExtensionsForPoint (Backend.class))
        {
            Backend s = (Backend) ext;
            if (s.getName ().equalsIgnoreCase (name))
            {
                result = s;
                break;
            }
            if (s.getName ().equals ("Internal")) internal = s;
        }
        if (result == null) result = internal;
        if (result == null) System.err.println ("Couldn't find internal simulator!");
        return result;
    }

    public static File getPluginsDir ()
    {
        return new File (AppData.properties.get ("resourceDir"), "plugins");
    }

    public static File getStorageDir (Class<?> pClass)
    {
        File pluginsDir = getPluginsDir ();
        File pluginStorage = new File (pluginsDir, pClass.getName ());
        pluginStorage.mkdirs ();
        return pluginStorage;
    }
}

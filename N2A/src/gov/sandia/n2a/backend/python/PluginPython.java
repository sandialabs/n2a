/*
Copyright 2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.python;

import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;

import gov.sandia.n2a.plugins.Plugin;
import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.plugins.ExtensionPoint;

public class PluginPython extends Plugin
{
	@Override
	public String getName ()
	{
		return "Python Backend";
	}

    @Override
    public String getVersion()
    {
        return "1.2";
    }

    @Override
    public String getProvider ()
    {
        return "Sandia National Laboratories";
    }

	@Override
	public ImageIcon getIcon ()
	{
		return null;
	}

	@Override
	public String getDescription ()
	{
		return "Python Backend";
	}

    @Override
	public ExtensionPoint[] getExtensions ()
	{
        List<ExtensionPoint> result = new ArrayList<ExtensionPoint> ();
        if (! AppData.properties.getBoolean ("headless"))
        {
            result.add (new SettingsPython ());
        }
        return result.toArray (new ExtensionPoint[result.size ()]);
	}
}

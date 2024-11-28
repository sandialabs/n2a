/*
Copyright 2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.glif;

import javax.swing.ImageIcon;

import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.Plugin;

public class PluginGLIF extends Plugin
{
	@Override
	public String getName ()
	{
		return "GLIF";
	}

    public String getVersion()
    {
        return "0.9";
    }

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
		return "GLIF Import/Export";
	}

	@Override
	public ExtensionPoint[] getExtensions ()
	{
		return new ExtensionPoint[]
		{
		    new ImportGLIF ()
		};
	}
}

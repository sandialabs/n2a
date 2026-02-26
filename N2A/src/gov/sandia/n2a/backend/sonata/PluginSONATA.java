/*
Copyright 2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.sonata;

import javax.swing.ImageIcon;

import gov.sandia.n2a.plugins.Plugin;
import gov.sandia.n2a.plugins.ExtensionPoint;

public class PluginSONATA extends Plugin
{
    @Override
	public String getName ()
	{
		return "SONATA Backend";
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
		return "Exchange models in SONATA format.";
	}

	@Override
	public ExtensionPoint[] getExtensions ()
	{
        return new ExtensionPoint[]
        {
            new ImportSONATA ()
        };
	}
}

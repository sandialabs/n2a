/*
Copyright 2016,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import javax.swing.ImageIcon;

import gov.sandia.n2a.plugins.Plugin;
import gov.sandia.n2a.plugins.ExtensionPoint;

public class PluginNeuroML extends Plugin
{
	@Override
	public String getName ()
	{
		return "NeuroML Backend";
	}

    public String getVersion()
    {
        return "0.9-dev";
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
		return "Wrapper for jNeuroML. Imports/exports NeuroML files, and runs models on a range of simulators.";
	}

	@Override
	public ExtensionPoint[] getExtensions ()
	{
        return new ExtensionPoint[]
        {
            new ImportNeuroML (),
            new ExportNeuroML ()
        };
	}
}

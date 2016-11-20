/*
Copyright 2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.neuroml;

import javax.swing.ImageIcon;

import replete.plugins.DefaultPlugin;
import replete.plugins.ExtensionPoint;

public class PluginNeuroML extends DefaultPlugin
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

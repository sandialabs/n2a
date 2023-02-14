/*
Copyright 2018-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.vensim;

import javax.swing.ImageIcon;

import gov.sandia.n2a.plugins.ExtensionPoint;
import gov.sandia.n2a.plugins.Plugin;

public class PluginVensim extends Plugin
{
	@Override
	public String getName ()
	{
		return "Vensim Backend";
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
		return "Vensim Backend";
	}

	@Override
	public ExtensionPoint[] getExtensions ()
	{
		return new ExtensionPoint[]
		{
		    new ImportVensim (),
		    new ProvideSpreadsheet (),
		    Spreadsheet.factory (),
		    ColumnCode.factory (),
		    Lookup.factory ()
		};
	}
}

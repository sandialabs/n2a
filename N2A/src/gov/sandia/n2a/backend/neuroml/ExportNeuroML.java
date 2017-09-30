/*
Copyright 2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Exporter;

import java.io.File;

public class ExportNeuroML implements Exporter
{
    @Override
    public String getName ()
    {
        return "NeuroML";
    }

    @Override
    public void export (MNode source, File destination)
    {
        System.out.println ("imagine a NeuroML export to: " + destination);
    }
}

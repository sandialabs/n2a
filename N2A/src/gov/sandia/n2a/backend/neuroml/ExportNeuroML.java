/*
Copyright 2017-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
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
        if (PluginNeuroML.partMap == null) PluginNeuroML.partMap = new PartMap ();
        if (PluginNeuroML.sequencer == null)
        {
            PluginNeuroML.sequencer = new Sequencer ();
            // TODO: get non-hacked names
            // TODO: determine if carrying NeuroML XSD documents as payload contaminates the license of N2A
            PluginNeuroML.sequencer.loadXSD (new File ("/home/fred/software/work/neuroml_dev/NeuroML2/Schemas/NeuroML2/NeuroML_v2beta4.xsd"));
            PluginNeuroML.sequencer.loadXSD (new File ("/home/fred/software/work/neuroml_dev/LEMS/Schemas/LEMS/LEMS_v0.7.4.xsd"));
        }

        ExportJob job = new ExportJob (PluginNeuroML.partMap, PluginNeuroML.sequencer);
        job.process (source, destination);
    }
}

/*
Copyright 2017-2021 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuroml;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.ExportModel;

import java.nio.file.Path;

public class ExportNeuroML implements ExportModel
{
    @Override
    public String getName ()
    {
        return "NeuroML";
    }

    @Override
    public void export (MNode source, Path destination)
    {
        export (source, destination, false);
    }

    public ExportJob export (MNode source, Path destination, boolean forBackend)
    {
        if (PluginNeuroML.partMap == null) PluginNeuroML.partMap = new PartMap ();
        if (PluginNeuroML.sequencer == null)
        {
            PluginNeuroML.sequencer = new Sequencer ();
            PluginNeuroML.sequencer.loadXSD ("NeuroML_v2beta4.xsd");
            PluginNeuroML.sequencer.loadXSD ("LEMS_v0.7.4.xsd");
        }

        ExportJob job = new ExportJob (PluginNeuroML.partMap, PluginNeuroML.sequencer);
        job.forBackend = forBackend;
        job.process (source, destination);
        return job;
    }
}

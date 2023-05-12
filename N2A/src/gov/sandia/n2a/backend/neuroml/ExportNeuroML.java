/*
Copyright 2017-2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

/*
This class uses XML schema files (.xsd) from the LEMS and NeuroML distributions
to determine order during export. The latest XSD for each of NeuroML and LEMS
should be copied from their respective distributions:
    https://github.com/NeuroML/NeuroML2/tree/master/Schemas/NeuroML2
    https://github.com/LEMS/LEMS/blob/master/Schemas/LEMS
and placed into this directory. Also update export() below to reference the latest
files. Note that the software can work without these files, but the resulting XML
may not be strictly correct.
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
            PluginNeuroML.sequencer.loadXSD ("NeuroML_v2.2.xsd");
            PluginNeuroML.sequencer.loadXSD ("LEMS_v0.7.6.xsd");
        }

        ExportJob job = new ExportJob (PluginNeuroML.partMap, PluginNeuroML.sequencer);
        job.forBackend = forBackend;
        job.process (source, destination);
        return job;
    }
}

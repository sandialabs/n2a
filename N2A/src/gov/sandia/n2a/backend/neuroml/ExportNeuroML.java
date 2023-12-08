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
and placed into this directory. Also update process() below to reference the latest
files. Note that the software can work without these files, but the resulting XML
may not be strictly correct.
*/

package gov.sandia.n2a.backend.neuroml;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.ExportModel;

import java.nio.file.Files;
import java.nio.file.Path;

public class ExportNeuroML implements ExportModel
{
    @Override
    public String getName ()
    {
        return "NeuroML";
    }

    @Override
    public void process (MNode source, Path destination)
    {
        process (source, destination, false);
    }

    public ExportJob process (MNode source, Path destination, boolean forBackend)
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

    @Override
    public boolean accept (Path source)
    {
        if (Files.isDirectory (source)) return true;
        String name = source.getFileName ().toString ();
        int lastDot = name.lastIndexOf ('.');
        if (lastDot >= 0)
        {
            String suffix = name.substring (lastDot);
            if (suffix.equalsIgnoreCase (".xml" )) return true;
            if (suffix.equalsIgnoreCase (".nml" )) return true;
            if (suffix.equalsIgnoreCase (".lems")) return true;  // Not sure "lems" is an official suffix, but if specified it seems very likely to be LEMS.
        }
        return false;
    }
}

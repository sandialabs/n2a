/*
Copyright 2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.neuron;

import java.nio.file.Path;
import java.util.List;
import gov.sandia.n2a.backend.PartMap;
import gov.sandia.n2a.backend.sonata.ImportSONATA;
import gov.sandia.n2a.backend.sonata.ImportSONATApart;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.ImportModel;

public class ImportNeuron extends ImportModel implements ImportSONATApart
{
    @Override
    public String getName ()
    {
        return "NEURON";
    }

    @Override
    public MNode extractModels (Path source, String name) throws Exception
    {
        throw new Exception ("Not implemented");
    }

    @Override
    public float matches (Path source)
    {
        return 0;
    }

    @Override
    public boolean accept (Path source)
    {
        return false;
    }

    @Override
    public void processPart (gov.sandia.n2a.backend.sonata.ImportJob job, String partName, String population, String model_template, List<String> instanceAttributes)
    {
        if (PluginNeuron.partMap == null) PluginNeuron.partMap = new PartMap ("neuron");
        ImportSONATA.processPart ("neuron", PluginNeuron.partMap, job, partName, population, model_template, instanceAttributes);
    }
}

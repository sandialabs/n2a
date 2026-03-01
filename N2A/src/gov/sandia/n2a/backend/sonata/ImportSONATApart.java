/*
Copyright 2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.sonata;

import java.util.List;

/**
    A mixin for ImportModel that provides SONATA-specific processing.
**/
public interface ImportSONATApart
{
    /**
        Adds a part to main model.

        @param job A reference to the complete job object, in case we need something from it.
        @param partName The subpart that this function is creating/filling in.
        @param population Name of SONATA population.
        @param model_template Raw name. May still have schema name. Used to look up config entries.
        After the schema name is stripped, this should be useful as a backend-specific external part name.
        @param instanceAttributes A flat collection of attribute names found in the SONATA "group" associated
        with this part. These values vary with instance.
    **/
    public void processPart (ImportJob job, String partName, String population, String model_template, List<String> instanceAttributes);
}

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
        Ensures that the given model template name is in the DB, ready for use.
        @param model_template The template name without schema identifier.
        @return A suitable external name for the part, usually the same as model_template.
        The importer will later map this to an internal part name.
        In the case of a newly-imported part, this will also be the internal name, which
        the part map will treat as neutral.
    **/
    public default String prepare (ImportJob job, String model_template)
    {
        return model_template;
    }

    /**
        Adds a part to main model.

        @param job A reference to the complete ImportJob object, in case we need something from it.
        @param partName The subpart that this function is creating/filling in.
        @param population Name of SONATA population, used as key into types structure.
        @param template Internal name of model, used as key into types structure. External SONATA model_template name can be retrieved from sub-key "template".
        @param instanceAttributes A flat collection of attribute names found in the SONATA "group" associated
        with this part. These values vary with instance.
    **/
    public void processPart (ImportJob job, String partName, String population, String template, List<String> instanceAttributes);
}

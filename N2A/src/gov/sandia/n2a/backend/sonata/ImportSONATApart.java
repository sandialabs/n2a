/*
Copyright 2026 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.backend.sonata;

import java.util.List;

import gov.sandia.n2a.db.MNode;

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
        Gives the path from a parameter to metadata specifying units that this backend requires.
        If this backend does not have required units, then the return value is null.
        {"$meta", "backend"} is assumed, so these should not be included in the path.
    **/
    public default String[] unitPath ()
    {
        return null;
    }

    /**
        Adds a part to main model.

        @param job A reference to the complete ImportJob object, in case we need something from it.
        @param part The sub-part that this function is creating/filling in.
        @param instanceAttributes A flat collection of attribute names found in the SONATA "group" associated
        with this part. These values vary with instance.
    **/
    public void processPart (ImportJob job, MNode part, List<String> instanceAttributes);
}

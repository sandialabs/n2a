/*
Copyright 2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.plugins.extpoints;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPart;
import gov.sandia.n2a.plugins.ExtensionPoint;

public interface StudyHook extends ExtensionPoint
{
    /**
        @return The key associated with this hook in $meta.study.plugin
    **/
    public String name ();

    /**
        Makes one-time changes to the study setup, before any samples are generated.
        Notice that the plugin key is part of study "config".
    **/
    public void modifyStudy (MNode study);

    /**
        Makes any needed changes to a study sample before running it.
        Called after iterators have been applied and before the modified model is stored to snapshot.
        @param model The collated form of the sample model. Changes to this will be saved and
        affect the simulation.
        @param job The document for managing the job. Can be used to locate the job directory
        for further modifications, such as copying files.
    **/
    public void modifySample (MPart model, MNode job);
}

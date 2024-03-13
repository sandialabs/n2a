/*
Copyright 2023 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.ui.jobs;

import java.util.ArrayList;
import java.util.List;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MCombo;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.db.MPart;

public class MPartSnapshot extends MPart
{
    protected static final ThreadLocal<MCombo> models = new ThreadLocal<MCombo> ();

    /**
        Constructs an MPart tree for the model with the given key,
        using the given snapshot to override the main "models" database.
        The snapshot structure is only valid on this thread while this function
        is running. As a consequence, the resulting MPart tree is not suitable
        for editing $inherit. Everything else should be fine.
    **/
    public static MPart from (String key, MNode snapshot)
    {
        List<MNode> containers = new ArrayList<MNode> (2);
        containers.add (snapshot);
        containers.add (AppData.docs.childOrCreate ("models"));
        MCombo temp = new MCombo ("temp", containers);
        models.set (temp);
        MPart result = new MPartSnapshot (temp.child (key));
        models.set (null);
        temp.done ();
        return result;
    }

    protected MPartSnapshot (MNode source)
    {
        super (source);
    }

    protected MPartSnapshot (MPart container, MPart inheritedFrom, MNode source)
    {
        super (container, inheritedFrom, source);
    }

    protected MCombo getRepo ()
    {
        return models.get ();
    }

    protected MPart construct (MPart container, MPart inheritedFrom, MNode source)
    {
        return new MPartSnapshot (container, inheritedFrom, source);
    }
}

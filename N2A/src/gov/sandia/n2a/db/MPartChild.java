/*
Copyright 2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

/**
    An MPart that relies on its container to provide the working repo.
    This allows us to set up a unique repo structure for each given MPart tree
    without storing the repo information in every node of the tree.
**/
public class MPartChild extends MPart
{
    protected MPartChild (MPart container, MPart inheritedFrom, MNode source)
    {
        super (container, inheritedFrom, source);
    }

    protected MNode getRepo ()
    {
        return container.getRepo ();
    }

    protected MPart construct (MPart container, MPart inheritedFrom, MNode source)
    {
        return new MPartChild (container, inheritedFrom, source);
    }
}

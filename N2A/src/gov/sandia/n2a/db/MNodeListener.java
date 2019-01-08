/*
Copyright 2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.db;

/**
    Notify UI components of changes in an MDoc collection.
**/
public interface MNodeListener
{
    /**
        Structure has changed in a way that affects more than one or two children.
    **/
    public void changed ();

    /**
        A key that was formerly null now has data.
        If a key becomes non-null as the result of a move, then childChanged() will be sent instead.
    **/
    public void childAdded (String key);

    /**
        A key that had data has become null.
        If a key becomes null as the result of a move, then childChanged() will be sent instead.
    **/
    public void childDeleted (String key);

    /**
        Content has changed under two keys.
        The newKey will always have new content, and the oldKey will either have new content or become null.
        If a move effectively deletes the destination, then a childDeleted() message will be sent instead.
        If content changes at a single location, then oldKey==newKey.
    **/
    public void childChanged (String oldKey, String newKey);
}

/**
Copyright 2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
**/

package gov.sandia.n2a.backend.c;

public class NativeIOvector extends NativeResource
{
    protected static native long   construct (String... path);
    protected static native int    size      (long handle);
    protected static native double get       (long handle, int i);
    protected static native void   set       (long handle, int i, double value);

    /**
        Retrieves an IOvector from the simulator.

        @param path Includes the name of each part in the path from the top-level model
        to the one in question. If a given part has more than one instance in its population,
        the index should appear in the path immediately after the name of that part.
        Since the path is all strings, the index should be converted to a string as well.
    **/
    public NativeIOvector (String... path)
    {
        super (construct (path), true);
    }

    public int size ()
    {
        return size (handle);
    }

    public double get (int i)
    {
        return get (handle, i);
    }

    public void set (int i, double value)
    {
        set (handle, i, value);
    }
}

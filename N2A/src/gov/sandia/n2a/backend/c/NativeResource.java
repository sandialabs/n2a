/**
Copyright 2022 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
**/

package gov.sandia.n2a.backend.c;

import java.io.Closeable;

/**
    This class serves as a base class for any Java class that will map to
    individual instances of C++ objects via JNI. It manages a pointer to the
    native object and allows for its safe destruction.

    The close() method must always be called when done using an instance.
    The best way is to wrap the lifespan of the object in a try-with-resources
    block. The alternate is to use the Cleaner mechanism. In that case, the subclass
    of NativeResource should implement Runnable. The run() method should call close().
    The native resource should be wrapped in another object, and that outer object
    should be the one that gets registered with the Cleaner. This is the only way
    with the Cleaner pattern to have a specific reference to the resource object.
**/
public class NativeResource implements Closeable
{
    protected long    handle;
    protected boolean selfDestruct;  // Indicates that the C++ object is managed by Java rather than by another C++ object.

    /**
        Calls the virtual destructor on the C++ object. All C++ objects held by
        this proxy class inherit from a C++ class called NativeResource. In that
        class, the destructor is declared virtual, so that only a single native
        function call is sufficient to correctly select the sub-class-specific
        destruction method.
    **/
    private static native void destruct (long handle);

    /**
        @return The single Java-side object that C++ believes is associated with
        handle. Can be null.
    **/
    protected static native NativeResource getProxy (long handle);

    /**
        Tells which Java-side object represents the C++ object pointed to by handle.
    **/
    protected static native void setProxy (long handle, NativeResource proxy);

    protected NativeResource (long handle, boolean selfDestruct)
    {
        // Strictly speaking, handles must be unique across all NativeResource instances.
        // However, conflicts will likely only arise between instances of a given sub-class.
        // Thus we lock the specific sub-class rather than NativeResource.class.
        synchronized (getClass ())
        {
            if (getProxy (handle) != null) throw new RuntimeException ("Proxy slot is already occupied.");
            setProxy (handle, this);
        }
        this.handle       = handle;
        this.selfDestruct = selfDestruct;
    }

    /**
        Either retrieve the Java proxy object associated with handle, or construct
        a new one. This is essentially an abstract function, but not declared that
        way because some classes may not need to provide this function.
        @param handle To the C++ object
        @param selfDestruct Indicates that the C++ object is managed by Java rather than by another C++ object.
        @return The proxy object.
    **/
    public static NativeResource fromHandle (long handle, boolean selfDestruct)
    {
        throw new RuntimeException ("Need to define a fromHandle() function specific to the derived class.");
    }

    public long getHandle ()
    {
        return handle;
    }

    @Override
    public boolean equals (Object o)
    {
        if (! (o instanceof NativeResource)) return false;
        return handle == ((NativeResource) o).handle;
    }

    public void setSelfDestruct (boolean value)
    {
        selfDestruct = value;
    }

    /**
        This method should only be called from the c-side, when the corresponding object has been destroyed.
    **/
    private void release ()
    {
        synchronized (getClass ())
        {
            handle = 0;
        }
    }

    @Override
    public void close ()
    {
        synchronized (getClass ())
        {
            if (handle == 0) return;
            if (getProxy (handle) == this)
            {
                if (selfDestruct) destruct (handle);
                else              setProxy (handle, null);
            }
            handle = 0;
        }
    }
}

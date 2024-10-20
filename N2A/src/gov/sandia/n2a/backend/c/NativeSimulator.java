/**
Copyright 2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
**/

package gov.sandia.n2a.backend.c;

/**
    Static methods for starting, stepping and stopping a simulation.
    Normally, only one simulator is allowed for the entire process.
    If thread-local storage (TLS) is enabled, then there can be a separate
    simulator for each thread. Even in that case it is only identified
    implicitly, by being on the calling thread.
**/
public class NativeSimulator
{
    public static native void init   (String... args);
    public static native void run    (double until);
    public static native void finish ();
}

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
    public static native void init          (String... args); // Prepares simulation. "args" should contain command-line arguments. WARNING: "args" acts exactly like Posix command line, so first entry is treated as program name, and generally ignored.
    public static native void run           (double until);   // Steps the simulator until absolute sim-time reaches "until".
    public static native void finish        ();               // Closes out the current simulation, but retains memory allocations for another simulation.
    public static native void releaseMemory ();               // Finishes all cleanup.
}

/*
Copyright 2024 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.plugins.extpoints;

import gov.sandia.n2a.plugins.ExtensionPoint;

public interface Service extends ExtensionPoint
{
    public String name ();

    /**
        Starts the server on a separate thread.
        The thread should be non-daemon, or else the VM will shut down.
        The thread runs indefinitely, until it reaches its own termination condition
        or the VM receives a signal, such as SIGTERM.
        For graceful shutdown, a plugin can also register a ShutdownHook that communicates
        with the running server.
    **/
    public void start ();
}

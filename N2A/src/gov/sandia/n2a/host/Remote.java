/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.host;

import java.io.Closeable;
import java.util.Set;

public interface Remote extends Closeable
{
    public void        enable ();      // Allow this host to ask user for login information.
    public boolean     isEnabled ();   // Indicates that this host is currently able to ask for login, if needed.
    public boolean     isConnected (); // Indicates that this host has an active ssh (or other protocol) session.
    public Set<String> messages ();    // Collection of all unique informational messages the remote host has sent us.
}

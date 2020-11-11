/*
Copyright 2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import java.io.Closeable;
import java.util.Set;

public interface Remote extends Closeable
{
    public boolean     isEnabled ();
    public boolean     isConnected ();
    public Set<String> messages ();
}

/*
Copyright 2013-2020 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
    Wraps access to any system other than localhost.
    This default implementation is suitable for a unix-like system that runs jobs on its
    own processors, as opposed to queuing them on a cluster or specialized hardware.
**/
public class RemoteUnix extends Unix implements Remote
{
    protected Connection connection;

    public static Factory factory ()
    {
        return new Factory ()
        {
            public String className ()
            {
                return "RemoteUnix";
            }

            public Host createInstance ()
            {
                return new RemoteUnix ();
            }
        };
    }

    public synchronized void connect () throws Exception
    {
        if (connection == null) connection = new Connection (config);
        connection.connect ();
    }

    public synchronized void close ()
    {
        if (connection != null) connection.close ();
    }

    @Override
    public boolean isEnabled ()
    {
        return  connection != null  &&  connection.passwords.allowDialogs;
    }

    @Override
    public boolean isConnected ()
    {
        return  connection != null  &&  connection.isConnected ();
    }

    @Override
    public Set<String> messages ()
    {
        if (connection == null) return new HashSet<String> ();
        return connection.passwords.messages;
    }

    @Override
    public AnyProcessBuilder build (String... command) throws Exception
    {
        connect ();
        return connection.build (command);
    }

    @Override
    public Path getResourceDir () throws Exception
    {
        connect ();
        return connection.getFileSystem ().getPath ("n2a");  // assumes that filesystem default directory is where n2a dir should reside
    }

    @Override
    public String quote (Path path)
    {
        String result = path.toAbsolutePath ().toString ();
        if (result.contains (" ")) return "\'" + result + "\'";
        return result;
    }
}

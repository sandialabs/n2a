/*
Copyright 2013,2017 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.plugins;

public class LoadPluginException extends Exception {
    public LoadPluginException() {
        super();
    }
    public LoadPluginException(String message, Throwable cause) {
        super(message, cause);
    }
    public LoadPluginException(String message) {
        super(message);
    }
    public LoadPluginException(Throwable cause) {
        super(cause);
    }
}

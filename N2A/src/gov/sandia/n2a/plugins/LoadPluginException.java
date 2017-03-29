/*
Copyright 2013,2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
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

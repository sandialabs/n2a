/*
Copyright 2013,2017 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.plugins;

import java.io.File;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public class PluginInitializationErrors implements Serializable {
    private Map<String, Exception> memErrors = new LinkedHashMap<String, Exception>();
    private Map<File, Exception> jarErrors = new LinkedHashMap<File, Exception>();
    private Map<String, Exception> startErrors = new LinkedHashMap<String, Exception>();
    private Exception platformError = null;
    private Exception validateEP = null;

    public boolean isError() {
        return memErrors.size() != 0 || jarErrors.size() != 0 || startErrors.size() != 0 || platformError != null || validateEP != null;
    }

    public Exception getPlatformError() {
        return platformError;
    }
    public void setPlatformError(Exception pe) {
        platformError = pe;
    }

    public Map<String, Exception> getMemoryByNameErrors() {
        return memErrors;
    }

    public Map<File, Exception> getJarErrors() {
        return jarErrors;
    }

    public Map<String, Exception> getPluginStartErrors() {
        return startErrors;
    }

    public Exception getValidateExtPointsError() {
        return validateEP;
    }

    public void setValidateExtPointsError(Exception e) {
        validateEP = e;
    }

    @Override
    public String toString() {
        String ret = "";
        if(platformError != null) {
            ret += "Platform Error: " + toCompleteString(platformError);
        }
        if(memErrors.size() != 0) {
            ret += "Memory By Name Errors:\n";
            for(String mem : memErrors.keySet()) {
                ret += mem + ": " + toCompleteString(memErrors.get(mem));
            }
        }
        if(jarErrors.size() != 0) {
            ret += "JAR Directory Errors:\n";
            for(File jar : jarErrors.keySet()) {
                ret += jar + ": " + toCompleteString(jarErrors.get(jar));
            }
        }
        if(startErrors.size() != 0) {
            ret += "Plug-in Start Errors:\n";
            for(String plugin : startErrors.keySet()) {
                ret += plugin + ": " + toCompleteString(startErrors.get(plugin));
            }
        }
        if(validateEP != null) {
            ret += "Validate Ext Points Error: " + toCompleteString(validateEP);
        }
        return ret;
    }

    public static String toCompleteString(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }
}

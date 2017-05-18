/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs.beans;


public class Resource implements Comparable<Resource> {
    private String remotePath;
    public Resource(String path) {
        remotePath = path;
    }
    public String getRemotePath() {
        return remotePath;
    }

    @Override
    public int compareTo(Resource o) {
        return toString().compareTo(o.toString());
    }

    @Override
    public String toString() {
        return remotePath;
    }

    public String render() {
        return "        Resource: " + remotePath + "\n";
    }
}

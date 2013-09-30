/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.execenvs.beans;


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

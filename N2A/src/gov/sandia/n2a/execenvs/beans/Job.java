/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.execenvs.beans;

import java.util.Set;
import java.util.TreeSet;

public class Job implements Comparable<Job> {
    private String name;
    private Set<Resource> resources = new TreeSet<Resource>();

    public Job(String nm) {
        name = nm;
    }

    public String getName() {
        return name;
    }
    public Set<Resource> getResources() {
        return resources;
    }

    public void addResource(Resource r) {
        resources.add(r);
    }

    @Override
    public int compareTo(Job o) {
        return toString().compareTo(o.toString());
    }

    @Override
    public String toString() {
        return name;
    }

    public String render() {
        String ret = "    Job: " + name + "\n";
        for(Resource res : resources) {
            ret += res.render();
        }
        return ret;
    }
}

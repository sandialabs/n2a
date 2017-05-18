/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
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

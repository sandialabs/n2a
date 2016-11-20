/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.execenvs.beans;

import java.util.Set;
import java.util.TreeSet;

public class DateGroup implements Comparable<DateGroup> {
    private String name;
    private Set<Job> jobs = new TreeSet<Job>();

    public DateGroup(String nm) {
        name = nm;
    }

    public String getName() {
        return name;
    }
    public Set<Job> getJobs() {
        return jobs;
    }

    public Job addJob(String name) {
        for(Job eJob : jobs) {
            if(eJob.getName().equals(name)) {
                return eJob;
            }
        }

        Job newJob = new Job(name);
        jobs.add(newJob);
        return newJob;
    }

    @Override
    public int compareTo(DateGroup o) {
        return toString().compareTo(o.toString());
    }

    @Override
    public String toString() {
        return name;
    }

    public String render() {
        String ret = "DateGroup: " + name + "\n";
        for(Job job : jobs) {
            ret += job.render();
        }
        return ret;
    }
}

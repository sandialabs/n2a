/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
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

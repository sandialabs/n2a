/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.execenvs.beans;

import java.util.Set;
import java.util.TreeSet;

public class AllJobInfo {
    private Set<DateGroup> dateGroups = new TreeSet<DateGroup>();

    public DateGroup addDateGroup(String date) {
        for(DateGroup eGroup : dateGroups) {
            if(eGroup.getName().equals(date)) {
                return eGroup;
            }
        }
        DateGroup newGroup = new DateGroup(date);
        dateGroups.add(newGroup);
        return newGroup;
    }

    public Set<DateGroup> getDateGroups() {
        return dateGroups;
    }

    public String render() {
        String ret = "";
        for(DateGroup group : dateGroups) {
            ret += group.render();
        }
        return ret;
    }
}

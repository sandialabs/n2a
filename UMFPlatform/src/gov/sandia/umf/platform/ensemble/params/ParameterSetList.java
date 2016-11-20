/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ensemble.params;

import gov.sandia.n2a.parms.ParameterSet;

import java.util.ArrayList;
import java.util.List;

public class ParameterSetList extends ArrayList<ParameterSet> {


    ///////////////
    // ACCESSORS //
    ///////////////

    public int getNumParams() {
        if(size() == 0) {
            return 0;
        }
        return get(0).size();
    }
    public int getNumSets() {
        return size();
    }


    //////////
    // MISC //
    //////////

    public ParameterSetMap transform() {
        ParameterSetMap map = new ParameterSetMap();
        for(ParameterSet set : this) {
            for(Object paramKey : set.keySet()) {
                List<Object> data = map.get(paramKey, new ArrayList<Object>(size()));
                data.add(set.get(paramKey));
            }
        }
        return map;
    }


    //////////
    // TEST //
    //////////

    public static void main(String[] args) {
        ParameterSetList list = createTestList();

        System.out.println(list);
        System.out.println("List Params: " + list.getNumParams());
        System.out.println("List Sets: " + list.getNumSets());

        ParameterSetMap map = list.transform();
        System.out.println(map);
        System.out.println("Map Params: " + map.getNumParams());
        System.out.println("Map Sets: " + map.getNumSets());
    }

    public static ParameterSetList createTestList() {
        ParameterSet s1 = new ParameterSet();
        s1.put("level", 0);
        s1.put("height", 100);
        ParameterSet s2 = new ParameterSet();
        s2.put("level", 1);
        s2.put("height", 200);
        ParameterSet s3 = new ParameterSet();
        s3.put("level", 2);
        s3.put("height", 300);
        ParameterSetList list = new ParameterSetList();
        list.add(s1);
        list.add(s2);
        list.add(s3);
        return list;
    }
}

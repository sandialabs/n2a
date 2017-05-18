/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ensemble.params;

import java.util.List;

import replete.util.XLinkedHashMap;

public class ParameterSetMap extends XLinkedHashMap<Object, List<Object>> {


    ///////////////
    // ACCESSORS //
    ///////////////

    public int getNumParams() {
        return size();
    }
    public int getNumSets() {
        if(size() == 0) {
            return 0;
        }
        return values().toArray(new List[0])[0].size();  // How make this more efficient?
    }
    public List<Object> getByIndex(int index) {
        return values().toArray(new List[0])[index];     // How make this more efficient?
    }
    public List<Object> getDataByIndex(int index) {
        return getByIndex(index);
    }
    public Object getParamByIndex(int index) {
        return keySet().toArray(new Object[0])[index];   // How make this more efficient?
    }


    //////////
    // MISC //
    //////////

    // TODO: Transform back to ParameterSetList.
    public ParameterSetList transform() {
        ParameterSetList list = new ParameterSetList();
        /*for(ParameterSet set : this) {
            for(String paramName : set.keySet()) {
                List<Object> data = map.get(paramName, new ArrayList<Object>(size()));
                data.add(set.get(paramName));
            }
        }*/
        return list;
    }


    //////////
    // TEST //
    //////////

    public static void main(String[] args) {
        ParameterSetMap map = ParameterSetList.createTestList().transform();
        System.out.println(map.getByIndex(0));
        System.out.println(map.getByIndex(1));
    }
}

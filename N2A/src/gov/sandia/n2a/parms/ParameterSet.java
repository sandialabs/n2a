/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.parms;

import java.util.Arrays;
import java.util.LinkedHashMap;

public class ParameterSet extends LinkedHashMap<Object, Object> {

    // Nothing more than a LinkedHashMap at this point.
    // Of note is that this is nothing less than an NDoc,
    // JSONObject, or any other "map of maps".

    // All data is a Map.
    // All life is a Map.

    // A very limited KeySpec for a certain type of maps.
    // Only because we know the keys of this map are arrays.
    // Potential inclusion into AMap.
    public ParameterSet subset(Object Stop) {
        ParameterSet subset = new ParameterSet();
        for(Object K : keySet()) {
            if(K instanceof ParameterKeyPath) {
                ParameterKeyPath Kpath = (ParameterKeyPath) K;
                for(Object S : Kpath) {
                    if(S.equals(Stop)) {
                        ParameterKeyPath KpathCopy = new ParameterKeyPath(Kpath);
                        subset.put(KpathCopy, get(K));
                    }
                    break;
                }
            }
        }
        return subset;
    }

    // Related to subset above.  A tree which contains another flattened hierarchy
    // with key-path keys.  Potential inclusion into AMap.
    public void sliceKeyPathKeys() {
        for(Object K : keySet().toArray(new Object[0])) {
            Object V = get(K);
            ParameterKeyPath P = (ParameterKeyPath) K;
            remove(P);
            P.remove(0);
            put(P, V);
        }
    }


    //////////
    // TEST //
    //////////

    public static void main(String[] args) {
        ParameterSet a = new ParameterSet();
        ParameterKeyPath P1 = new ParameterKeyPath(Arrays.asList(new Object[]{"us", "co", "denver"}));
        ParameterKeyPath P2 = new ParameterKeyPath(Arrays.asList(new Object[]{"us", "co", "boulder"}));
        ParameterKeyPath P3 = new ParameterKeyPath(Arrays.asList(new Object[]{"us", "nm", "abq"}));
        ParameterKeyPath P4 = new ParameterKeyPath(Arrays.asList(new Object[]{"us", "nm", "sf"}));
        ParameterKeyPath P5 = new ParameterKeyPath(Arrays.asList(new Object[]{"mx", "yucatan", "cancun"}));
        ParameterKeyPath P6 = new ParameterKeyPath(Arrays.asList(new Object[]{"mx", "yucatan", "tulum"}));
        ParameterKeyPath P7 = new ParameterKeyPath(Arrays.asList(new Object[]{"mx", "sonora", "monterrey"}));

        a.put(P1, 1);
        a.put(P2, 2);
        a.put(P3, 3);
        a.put(P4, 4);
        a.put(P5, 5);
        a.put(P6, 6);
        a.put(P7, 7);

        System.out.println(a.get(P7));

        System.out.println(a);
        System.out.println(a.subset("us"));
        System.out.println(a.subset("mx"));
    }
}

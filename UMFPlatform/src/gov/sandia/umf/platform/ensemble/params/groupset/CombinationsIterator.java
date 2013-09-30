/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/


package gov.sandia.umf.platform.ensemble.params.groupset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 *
 *
 *
 *
 *
 *
 * @author dtrumbo
 */

public class CombinationsIterator implements Iterable<int[]> {


    ////////////
    // FIELDS //
    ////////////

    private int[] counts;
    private int[] idx;
    private List<int[]> allIdxCombos = new ArrayList<int[]>();


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public CombinationsIterator(Collection<Integer> c) {
        Integer[] cc = c.toArray(new Integer[0]);
        init(conv(cc));
    }
    public CombinationsIterator(Integer[] c) {
        init(conv(c));
    }
    public CombinationsIterator(int[] c) {
        init(c);
    }

    private int[] conv(Integer[] c) {
        int[] cc = new int[c.length];
        for(int i = 0; i < c.length; i++) {
            cc[i] = c[i];
        }
        return cc;
    }
    private void init(int[] c) {
        counts = c;
        idx = new int[counts.length];
        if(counts.length != 0) {
            enumerateCombinations(0);
        }
    }

    private void enumerateCombinations(int column) {
        for(; idx[column] < counts[column]; idx[column]++) {
            if(column + 1 < counts.length) {
                enumerateCombinations(column + 1);
            }
            if(column == counts.length - 1) {
                allIdxCombos.add(Arrays.copyOf(idx, idx.length));
            }
        }
        idx[column] = 0;
    }


    ///////////////
    // ACCESSORS //
    ///////////////

    public int getCombinationCount() {
        return allIdxCombos.size();
    }
    public Iterator<int[]> iterator() {
        return allIdxCombos.iterator();
    }


    //////////
    // TEST //
    //////////

    public static void main(String[] args) {
        CombinationsIterator cIter = new CombinationsIterator(new int[] {3,2,5,6,4});
        for(int[] combination : cIter) {
            System.out.println(Arrays.toString(combination));
        }
        System.out.println(cIter.getCombinationCount());
    }
}

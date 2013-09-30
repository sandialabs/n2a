/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ensemble.random;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

// NOTE: A lot more thought needs to go into this class and Random-object
// management.  For example, there are use case considerations for ensemble and
// run and at the different layers of architecture: UMF platform (Java),
// Model/Simulator Plug-in (Java) and the simulator itself (any).  Also,
// why would the sim code that executes for a single run want to have the
// same random seed established each time it gets called?  Are there
// use cases for both yes and no?  Also, does the user get to choose whether
// or not to enforce a given previously-used random seed, and how does he/she
// do that?  Does user choose whether the choice is one for the whole ensemble
// or for the run (such seeds would reset before each new run).  Maybe
// we need a "domain" quality for our random number generators, so they can
// be somewhat hierarchically divided (a domain would be ensemble/run/something
// else perhaps?).  Maybe random seeds need to be saved for each run?  But
// that doesn't feel right because its so easy to change the # of runs per
// ensemble so how you would tie the run to the to the random seed?  At the very
// least I know that we might want to keep the random seed for the RNGs
// used in parameter set generation, so that people can remove the vari-
// ability of the randomness while they modify/test/verify their model.
// This can also be used for repeatability.  Without recording the random
// seeds you would never be able to perfectly reproduce results.  Also,
// consider the case of a single run.  Again we want to be able to perfectly
// reproduce a single run's results - and there is so "ensemble" in this case.
// So maybe thinking about this will help figure out what to do with random
// management.

// POSSIBLE USAGE NOTE:
// Any plug-in code using RandomManager, should not call new Random() nor setSeed.
// It breaks the contract of using this class.  All access should go through this
// class and next* methods in order to preserve potential future repeatability.
// This is because there's a usage confusion otherwise. WHEN did setSeed get
// set and to what seed?  If we allow client code to call these methods, then
// we can't help provide repeatability.

public class RandomManager {


    ////////////
    // FIELDS //
    ////////////

    public static final String GLOBAL_RANDOM = "$global";
    private static Map<String, TransparentFixedRandom> rMap = new HashMap<String, TransparentFixedRandom>();


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // Accessors

    public static synchronized Set<String> getNames() {
        return rMap.keySet();
    }
    public static synchronized Random get() {
        return get(GLOBAL_RANDOM);
    }
    public static synchronized Random get(String name) {
        if(name == null) {
            throw new IllegalArgumentException("Random number generator name must not be null.");
        }
        Random r = rMap.get(name);
        if(r == null) {
            r = create(name);
        }
        return r;
    }
    public static synchronized long getGlobalRandomSeed() {
        return getRandomSeed(GLOBAL_RANDOM);
    }
    public static synchronized long getRandomSeed(String name) {
        return rMap.get(name).getInitialSeed();  // Should throw an exception if name doesn't exist.
    }

    // Mutators

    public static synchronized Random create(String name) {
        return create(name, 0, true);
    }
    // This method is how client code can provide their own seed for a random.
    public static synchronized Random create(String name, long seed) {
        return create(name, seed, false);
    }
    private static synchronized Random create(String name, long seed, boolean noSeed) {
        if(name == null) {
            throw new IllegalArgumentException("Random number generator name must not be null.");
        }
        TransparentFixedRandom r = rMap.get(name);
        if(r != null) {
            throw new IllegalArgumentException("Cannot create random number generator.  A random number generator with this name already has been created.");
        }
        if(noSeed) {
            r = new TransparentFixedRandom();
        } else {
            r = new TransparentFixedRandom(seed);
        }
        rMap.put(name, r);
        return r;
    }

    // These two methods should not be called by client code.  They
    // are for the framework to call when reloading random generators
    // with their proper seeds from previously saved state.  For example,
    // when did you call setRandomSeed, was it after you already used the
    // random object some?  It just would be indicative of using your
    // own random object if that were the case.
    public static synchronized void setGlobalRandomSeed(long seed) {
        setRandomSeed(GLOBAL_RANDOM, seed);
    }
    public static synchronized void setRandomSeed(String name, long seed) {
        rMap.remove(name);   // Can't change seed on object on purpose.
        create(name, seed);
    }


    //////////
    // TEST //
    //////////

    public static void main(String[] args) {
        Random a = RandomManager.create("A");
//        RandomManager.create("A", 2);
        System.out.println(((TransparentFixedRandom)RandomManager.get("A")).getInitialSeed());
        System.out.println(a.nextInt() + "," + a.nextInt());
//        a.setSeed(12312);
    }
}

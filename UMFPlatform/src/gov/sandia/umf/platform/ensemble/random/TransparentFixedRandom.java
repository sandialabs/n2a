/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ensemble.random;

import java.util.Random;

// Make TransparentFixedSecureRandom someday?  Mersinne(sp?) Twister
public class TransparentFixedRandom extends Random {


    ////////////
    // FIELDS //
    ////////////

    private long initialSeed;
    private boolean initialSeedSet;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public TransparentFixedRandom() {
        super();
    }
    public TransparentFixedRandom(long seed) {
        super(seed);
    }


    ///////////////
    // ACCESSORS //
    ///////////////

    public long getInitialSeed() {
        return initialSeed;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public synchronized void setSeed(long seed) {
        if(!initialSeedSet) {
            initialSeed = seed;
            initialSeedSet = true;
            super.setSeed(seed);   // Only allowed to be called once upon base class construction.
            return;
        }

        throw new IllegalStateException("Cannot reset the seed of this object.  Doing so would break potential for future repeatability.");
    }
}

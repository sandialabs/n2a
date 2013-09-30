/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ensemble.params.specs;

// Should never have non-static fields of its own.
public abstract class ParameterSpecification {


    //////////////
    // ABSTRACT //
    //////////////

    public abstract Object getValue(int groupRunCount, int idx);
    public abstract boolean isStable();
    public abstract String getShortName();
    public abstract String getParamString();
    public abstract String getDescription();


    //////////
    // MISC //
    //////////

    public int getMaxValues() {
        return -1;  // Implies that the parameter specification
                    // can be called an arbitrarily large #
                    // of times while maintaining sense of those
                    // return values.
    }

    public boolean objectEquals(Object obj) {
        return super.equals(obj);
    }

    public int objectHashCode() {
        return super.hashCode();
    }

    public String toShortString() {
        return getShortName() + " " + getParamString();
    }


    /////////////
    // UTILITY //
    /////////////

    protected boolean isIntegral(Number n) {  // Not considering any exotic Number types here.
        return n instanceof Byte || n instanceof Short ||
               n instanceof Integer || n instanceof Long;
    }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ensemble.params.specs;

import gov.sandia.n2a.parms.ParameterSpecification;

public class EvenSpacingParameterSpecification extends ParameterSpecification {


    ////////////
    // FIELDS //
    ////////////

    private Number start;   // Could also just be doubles.
    private Number end;
    private boolean midPoint = false;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public EvenSpacingParameterSpecification() {}  // TODO: Figure out better hierarchy so can access getShortName w/o this ctor
    public EvenSpacingParameterSpecification(Number st, Number ed) {
        start = st;
        end = ed;
    }
    public EvenSpacingParameterSpecification(Number st, Number ed, boolean mp) {
        start = st;
        end = ed;
        midPoint = mp;
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // These are not used by the ensemble framework, which treats
    // parameter specifications as immutable, but adjacent
    // software might have needs for these methods.

    // Accessors

    public Number getStart() {
        return start;
    }
    public Number getEnd() {
        return end;
    }
    public boolean isMidPoint() {
        return midPoint;
    }

    // Mutators

    public void setStart(Number v) {
        start = v;
    }
    public void setEnd(Number e) {
        end = e;
    }
    public void setMidPoint(boolean midPoint) {
        this.midPoint = midPoint;
    }


    ///////////
    // VALUE //
    ///////////

    @Override
    public Object getValue(int groupRunCount, int idx) {
        double diff = end.doubleValue() - start.doubleValue();
        if(midPoint) {
            double inc = diff / groupRunCount;
            double incHalf = inc / 2;
            return start.doubleValue() + incHalf + inc * idx;
        }
        // Extrema included
        double inc = diff / (groupRunCount - 1);
        return start.doubleValue() + inc * idx;
    }
    @Override
    public boolean isStable() {
        return true;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((end == null) ? 0 : end.hashCode());
        result = prime * result + (midPoint ? 1231 : 1237);
        result = prime * result + ((start == null) ? 0 : start.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if(this == obj) {
            return true;
        }
        if(obj == null) {
            return false;
        }
        if(getClass() != obj.getClass()) {
            return false;
        }
        EvenSpacingParameterSpecification other = (EvenSpacingParameterSpecification) obj;
        if(end == null) {
            if(other.end != null) {
                return false;
            }
        } else if(!end.equals(other.end)) {
            return false;
        }
        if(midPoint != other.midPoint) {
            return false;
        }
        if(start == null) {
            if(other.start != null) {
                return false;
            }
        } else if(!start.equals(other.start)) {
            return false;
        }
        return true;
    }
    @Override
    public String toString() {
        return "EvenSpacingParameterSpecification [start=" + start + ", end=" + end +
            ", midPoint=" + midPoint + "]";
    }
    @Override
    public String getShortName() {
        return "Even Spacing";
    }
    @Override
    public String getParamString() {
        return "(start=" + start + ", end=" + end + ", midPoint=" + midPoint + ")";
    }
    @Override
    public String getDescription() {
        return "An even-spacing parameter specification will produce...";
    }


    //////////
    // TEST //
    //////////

    public static void main(String[] args) {
        EvenSpacingParameterSpecification spec = new EvenSpacingParameterSpecification(0, 10, true);
        for(int i = 0; i < 4; i++) {
            System.out.println(spec.getValue(4, i));
        }
        spec.setMidPoint(false);
        for(int i = 0; i < 4; i++) {
            System.out.println(spec.getValue(4, i));
        }
    }
}

/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ensemble.params.specs;

import gov.sandia.n2a.parms.ParameterSpecification;

public class StepParameterSpecification extends ParameterSpecification {


    ////////////
    // FIELDS //
    ////////////

    private Number start;  // If both integral types, integral math done.
    private Number delta;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public StepParameterSpecification() {}  // TODO: Figure out better hierarchy so can access getShortName w/o this ctor
    public StepParameterSpecification(Number st,  Number dl) {
        start = st;
        delta = dl;
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
    public Number getDelta() {
        return delta;
    }

    // Mutators

    public void setStart(Number v) {
        start = v;
    }
    public void setDelta(Number d) {
        delta = d;
    }


    ///////////
    // VALUE //
    ///////////

    // Don't needlessly cast to doubles.
    @Override
    public Object getValue(int groupRunCount, int idx) {
        if(isIntegral(start) && isIntegral(delta)) {
            long longStart = start.longValue();
            long longDelta = delta.longValue();
            return longStart + longDelta * idx;
        }
        double longStart = start.doubleValue();
        double longDelta = delta.doubleValue();
        return longStart + longDelta * idx;
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
        result = prime * result + ((delta == null) ? 0 : delta.hashCode());
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
        StepParameterSpecification other = (StepParameterSpecification) obj;
        if(delta == null) {
            if(other.delta != null) {
                return false;
            }
        } else if(!delta.equals(other.delta)) {
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
        return "StepParameterSpecification [start=" + start + ", delta=" + delta + "]";
    }
    @Override
    public String getShortName() {
        return "Step";
    }
    @Override
    public String getParamString() {
        return "(start=" + start + ", delta=" + delta + ")";
    }
    @Override
    public String getDescription() {
        return "A step parameter specification will produce...";
    }
}
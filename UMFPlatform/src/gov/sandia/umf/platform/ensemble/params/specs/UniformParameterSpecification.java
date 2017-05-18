/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ensemble.params.specs;

import gov.sandia.n2a.parms.ParameterSpecification;
import gov.sandia.umf.platform.ensemble.random.RandomManager;

import java.util.Random;


// This should probably get the same base class as
// GaussianParameterSpecification some day
// (e.g. ProbabilityDistributionParameterSpecification)

// TODO: Should split this into two spec classes? Integral and Real?
public class UniformParameterSpecification extends ParameterSpecification {


    ////////////
    // FIELDS //
    ////////////

    private Number start;
    private Number end;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    // TODO: inclusive flag?
    // NOTE: ed - start + 1 cannot exceed an integer data type
    public UniformParameterSpecification() {}  // TODO: Figure out better hierarchy so can access getShortName w/o this ctor
    public UniformParameterSpecification(Number st, Number ed) {
        start = st;
        end = ed;
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

    // Mutators

    public void setStart(Number v) {
        start = v;
    }
    public void setEnd(Number e) {
        end = e;
    }


    ///////////
    // VALUE //
    ///////////

    @Override
    public Object getValue(int groupRunCount, int idx) {
        Random R = RandomManager.get("Ensemble/UniformParameterSpecification");
        if(isIntegral(start) && isIntegral(end)) {
            int span = (int) (end.longValue() - start.longValue() + 1);
            int where = R.nextInt(span);
            return start.longValue() + where;
        }
        double diff = end.doubleValue() - start.doubleValue();
        double D = R.nextDouble();
        return start.doubleValue() + diff * D;
    }
    @Override
    public boolean isStable() {
        return false;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((end == null) ? 0 : end.hashCode());
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
        UniformParameterSpecification other = (UniformParameterSpecification) obj;
        if(end == null) {
            if(other.end != null) {
                return false;
            }
        } else if(!end.equals(other.end)) {
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
        return "UniformParameterSpecification [start=" + start + ", end=" + end + "]";
    }
    @Override
    public String getShortName() {
        return "Uniform";
    }
    @Override
    public String getParamString() {
        return "(start=" + start + ", end=" + end + ")";
    }
    @Override
    public String getDescription() {
        return "A uniform parameter specification will produce...";
    }
}

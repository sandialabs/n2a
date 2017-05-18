/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ensemble.params.specs;

import gov.sandia.n2a.parms.ParameterSpecification;

import java.util.Arrays;

// It's possible to make a IndexedListParameterSpecification
// object that takes an array of enumerated values and a
// second ParameterSpecification object which will serve
// to calcuate which index of the enumerated value to choose.
//     @Override
//     public void getValue(int groupRunCount, int idx) {
//         int myIndex =
//             ((Number)indexChoosingSpec.getValue(groupRunCount, idx)).intValue();
//         return values[myIndex];
//     }
// Not sure how useful this would be, but possible to make easily.

public class ListParameterSpecification extends ParameterSpecification {


    ///////////
    // FIELD //
    ///////////

    private Object[] values;  // Usually Number, String, or Boolean
                              // but could be any object that the target
                              // software needs (e.g. a Person objects).


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ListParameterSpecification() {}  // TODO: Figure out better hierarchy so can access getShortName w/o this ctor
    public ListParameterSpecification(Object... v) {
        values = v;
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // These are not used by the ensemble framework, which treats
    // parameter specifications as immutable, but adjacent
    // software might have needs for these methods.

    public Object[] getValues() {
        return values;
    }
    public void setValues(Object[] v) {
        values = v;
    }


    ///////////
    // VALUE //
    ///////////

    @Override
    public Object getValue(int groupRunCount, int idx) {
        return values[idx];
    }
    @Override
    public boolean isStable() {
        return true;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public int getMaxValues() {
        return values.length;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(values);
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
        ListParameterSpecification other = (ListParameterSpecification) obj;
        if(!Arrays.equals(values, other.values)) {
            return false;
        }
        return true;
    }
    @Override
    public String toString() {
        return "ListParameterSpecification [values=" + Arrays.toString(values) + "]";
    }
    @Override
    public String getShortName() {
        return "List";
    }
    @Override
    public String getParamString() {
        return "(values=" + Arrays.toString(values) + ")";
    }
    @Override
    public String getDescription() {
        return "Listy Listerson";
    }
}

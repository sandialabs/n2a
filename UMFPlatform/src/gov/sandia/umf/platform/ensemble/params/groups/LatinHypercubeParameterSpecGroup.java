/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ensemble.params.groups;

import gov.sandia.umf.platform.ensemble.params.specs.EvenSpacingParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.ParameterSpecification;


// Convenience class - nothing is done here that can't
// be done with regular ParameterSpecGroup.

// TODO: This class is not perfectly well thought out yet.
// Assumes that all the variables are uniformly distributed across
// their range at this point. Latin hypercube distribution points
// make sure the probability of each range between points is equal.
// Could technically be a sibling subclass with
// ConstantParameterSpecGroup if desired.

public class LatinHypercubeParameterSpecGroup extends ParameterSpecGroup {


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public LatinHypercubeParameterSpecGroup(int count) {
        super(count);
    }


    /////////////
    // MUTATOR //
    /////////////

    // Really just a convenience method.
    public void addLatinHypercubeParameter(Object paramKey, Number start, Number end) {  // Also, distribution in future
        put(paramKey, new EvenSpacingParameterSpecification(start, end));
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    // Enforce type of parameter specifications.
    @Override
    public ParameterSpecification put(Object paramKey, ParameterSpecification spec) {
        if(!(spec instanceof EvenSpacingParameterSpecification)) {
            throw new IllegalArgumentException("All latin hypercube parameter specifications must be of type '" +
                EvenSpacingParameterSpecification.class.getSimpleName() + "'.");
        }
        return super.put(paramKey, spec);
    }
    @Override
    public ParameterSpecification put(Object paramKey, ParameterSpecification spec, boolean enforceStability) {
        if(!(spec instanceof EvenSpacingParameterSpecification)) {
            throw new IllegalArgumentException("All latin hypercube parameter specifications must be of type '" +
                EvenSpacingParameterSpecification.class.getSimpleName() + "'.");
        }
        return super.put(paramKey, spec, enforceStability);
    }
    @Override
    public String toString() {
        return "LHPGroup(" + runCount + "|" + keySet() + ")";
    }
}

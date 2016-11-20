/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ensemble.params.groups;

import gov.sandia.n2a.parms.ParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.UniformParameterSpecification;


// Convenience class - nothing is done here that can't
// be done with regular ParameterSpecGroup.

// TODO: This class is not perfectly well thought out yet.
// Assumes that all the variables are uniformly distributed across
// their range at this point.

public class MonteCarloParameterSpecGroup extends ParameterSpecGroup {


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public MonteCarloParameterSpecGroup(int count) {
        super(count);
    }


    /////////////
    // MUTATOR //
    /////////////

    // Really just a convenience method.
    public void addMonteCarloParameter(Object paramKey, Number start, Number end) {  // Also, distribution in future
        put(paramKey, new UniformParameterSpecification(start, end));
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    // Enforce type of parameter specifications.
    @Override
    public ParameterSpecification put(Object paramKey, ParameterSpecification spec) {
        if(!(spec instanceof UniformParameterSpecification)) {
            throw new IllegalArgumentException("All monte carlo parameter specifications must be of type '" +
                UniformParameterSpecification.class.getSimpleName() + "'.");
        }
        return super.put(paramKey, spec);
    }
    @Override
    public ParameterSpecification put(Object paramKey, ParameterSpecification spec, boolean enforceStability) {
        if(!(spec instanceof UniformParameterSpecification)) {
            throw new IllegalArgumentException("All monte carlo parameter specifications must be of type '" +
                UniformParameterSpecification.class.getSimpleName() + "'.");
        }
        return super.put(paramKey, spec, enforceStability);
    }
    @Override
    public String toString() {
        return "MCPGroup(" + runCount + "|" + keySet() + ")";
    }
}

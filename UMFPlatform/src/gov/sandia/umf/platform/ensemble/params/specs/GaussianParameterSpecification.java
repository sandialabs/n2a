/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ensemble.params.specs;

import gov.sandia.n2a.parms.ParameterSpecification;


// This should probably get the same base class as
// UniformParameterSpecification some day
// (e.g. ProbabilityDistributionParameterSpecification)

public class GaussianParameterSpecification extends ParameterSpecification {
    private Object value;
    public GaussianParameterSpecification() {}  // TODO: Figure out better hierarchy so can access getShortName w/o this ctor
    public GaussianParameterSpecification(Object v) {
        value = v;
    }
    public Object getValue() {
        return value;
    }
    @Override
    public Object getValue(int groupRunCount, int idx) {
        return value;
    }
    @Override
    public boolean isStable() {
        return false;
    }
    @Override
    public String getShortName() {
        return "Gaussian";
    }
    @Override
    public String getParamString() {
        return "(mu=" + 0 + ", sigma=" + 1 + ")";
    }
    @Override
    public String getDescription() {
        return "A gaussian parameter specification will produce...";
    }
}

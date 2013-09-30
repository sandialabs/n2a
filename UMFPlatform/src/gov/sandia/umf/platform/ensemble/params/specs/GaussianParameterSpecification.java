/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ensemble.params.specs;


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

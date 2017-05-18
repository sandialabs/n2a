/*
Copyright 2013 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.umf.platform.ensemble.params.groups;

import gov.sandia.n2a.parms.ParameterSpecification;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import replete.util.StringUtil;
import replete.util.XHashMap;


public class ParameterSpecGroup extends LinkedHashMap<Object, ParameterSpecification> {
// Object key is parameter name/path, e.g. "Layers.HH.V"

    ///////////
    // FIELD //
    ///////////

    protected int runCount;
    private Map<Object, Boolean> paramStability =
        new HashMap<Object, Boolean>();
    private XHashMap<Object, Map<Integer, Object>> stabilityCache =
        new XHashMap<Object, Map<Integer, Object>>();


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ParameterSpecGroup() {
        this(1);
    }
    public ParameterSpecGroup(int count) {
        runCount = count;
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // Accessors

    public int getRunCount() {
        return runCount;
    }
    public boolean isEnforceStability(Object paramKey) {
        return paramStability.containsKey(paramKey) && paramStability.get(paramKey);
    }
    public boolean isInStabilityCache(Object paramKey, Integer idx) {
        return stabilityCache.containsKey(paramKey) && stabilityCache.get(paramKey).containsKey(idx);
    }
    public Object getFromStabilityCache(Object paramKey, Integer idx) {
        return stabilityCache.get(paramKey).get(idx);
    }
    public void list() {
        list(0);
    }
    public void list(int indent) {
        String sp = StringUtil.spaces(indent);
        System.out.println(sp + "Group Cardinality: " + runCount);
        for(Object paramKey : keySet()) {
            System.out.println(sp + paramKey + " = " + get(paramKey));
        }
    }

    // Mutators

    public ParameterSpecification put(Object paramKey, ParameterSpecification spec, boolean enforceStability) {
        paramStability.put(paramKey, enforceStability);
        return super.put(paramKey, spec);
    }
    public void putInStabilityCache(Object paramKey, Integer idx, Object value) {
        Map<Integer, Object> paramCache = stabilityCache.get(paramKey, new HashMap<Integer, Object>());
        paramCache.put(idx, value);
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    // Convenience method
    public ParameterSpecification add(Object paramKey, ParameterSpecification value) {
        return super.put(paramKey, value);
    }
    @Override
    public String toString() {
        return "PGroup(" + runCount + "|" + keySet() + ")";
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + runCount;
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if(this == obj) {
            return true;
        }
        if(!super.equals(obj)) {
            return false;
        }
        if(getClass() != obj.getClass()) {
            return false;
        }
        ParameterSpecGroup other = (ParameterSpecGroup) obj;
        if(runCount != other.runCount) {
            return false;
        }
        return true;
    }
}

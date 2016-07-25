/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ensemble.params.groupset;

import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.ensemble.params.ParameterSet;
import gov.sandia.umf.platform.ensemble.params.ParameterSetList;
import gov.sandia.umf.platform.ensemble.params.groups.ConstantParameterSpecGroup;
import gov.sandia.umf.platform.ensemble.params.groups.LatinHypercubeParameterSpecGroup;
import gov.sandia.umf.platform.ensemble.params.groups.MonteCarloParameterSpecGroup;
import gov.sandia.umf.platform.ensemble.params.groups.ParameterSpecGroup;
import gov.sandia.umf.platform.ensemble.params.specs.EvenSpacingParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.ListParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.ParameterSpecification;
import gov.sandia.umf.platform.ensemble.params.specs.UniformParameterSpecification;
import gov.sandia.umf.platform.ensemble.random.RandomManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import replete.util.StringUtil;


// Produces a fixed, predetermined list of parameter sets given
// a set of parameter specification groups.  Currently the
// default value group is directly added to the group set, as
// opposed to being attached separately.  This has added to
// the code complexity and could be refactored some day.
public class ParameterSpecGroupSet extends ArrayList<ParameterSpecGroup> {


    ///////////
    // FIELD //
    ///////////

    private ConstantParameterSpecGroup defaultValueGroup;


    //////////////////
    // CONSTRUCTORS //
    //////////////////

    // Inherited

    public ParameterSpecGroupSet ()
    {
        super ();
    }

    public ParameterSpecGroupSet (int initialCapacity)
    {
        super (initialCapacity);
    }

    public ParameterSpecGroupSet (MNode doc)
    {
        super ();
        // TODO: unpack the document into appropriate structrures
    }

    public ParameterSpecGroupSet(Collection<? extends ParameterSpecGroup> c)
    {
        super (c);
        if (c instanceof ParameterSpecGroupSet) defaultValueGroup = ((ParameterSpecGroupSet) c).getDefaultValueGroup ();
    }

    // Custom

    public ParameterSpecGroupSet(ConstantParameterSpecGroup defaultValueGroup) {
        setDefaultValueGroup(defaultValueGroup);
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // Accessor

    public ConstantParameterSpecGroup getDefaultValueGroup() {
        return defaultValueGroup;
    }

    // Mutators

    public void setDefaultValueGroup(ConstantParameterSpecGroup dvGroup) {
        setDefaultValueGroup(dvGroup, 0);    // Default position at front.
    }
    public void setDefaultValueGroup(ConstantParameterSpecGroup dvGroup, int newIndex) {
        int prevIndex = defaultValueGroup == null ? -1 : indexOf(defaultValueGroup);
        removeDefaultValueGroup();
        if(prevIndex != -1 && prevIndex < newIndex) {
            newIndex--;
        }
        defaultValueGroup = dvGroup;
        if(dvGroup != null) {
            add(newIndex, dvGroup);   // Could refactor so DV group not actually in list with others.
        }
    }

    public ConstantParameterSpecGroup removeDefaultValueGroup() {
        ConstantParameterSpecGroup prevGroup = defaultValueGroup;
        remove(defaultValueGroup);
        defaultValueGroup = null;
        return prevGroup;
    }

    // Just short hand for a SINGLE-parameter spec group,
    // but still returns the reference to the group for
    // convenience.
    public ParameterSpecGroup add(int iterations, String name, ParameterSpecification spec) {
        ParameterSpecGroup group = new ParameterSpecGroup(iterations);
        group.put(name, spec);
        add(group);
        return group;
    }


    ////////////////
    // MONEY CODE //
    ////////////////

    public ParameterSetList generateAllSetsFromSpecs()
            throws ParameterSpecGroupSetValidationException {
        return generateAllSetsFromSpecs(true);
    }

    public ParameterSetList generateAllSetsFromSpecs(boolean includeDVGroup)
            throws ParameterSpecGroupSetValidationException {

        validate();
        Set<Object> skipDVGroupParams = findSkipDVGroupParams();
        ParameterSetList sets = new ParameterSetList();

        // Special case for there being ONLY a default value group but
        // client code has chosen not to ask for default values.  This
        // means that a single run has happened, but that no parameters
        // are explicitly specified in that run.  BUT if there are
        // no groups defined, not even a DV group, then this method
        // does return "zero" runs.
        if(size() == 1 && get(0) == defaultValueGroup && !includeDVGroup) {
            sets.add(new ParameterSet());
            return sets;
        }

        // Make sure that the default value group is applied at the end for
        // this process, just to have a nicely-sorted final parameter set,
        // and use the sorted copy of this group set
        // to access group information from this point forward in
        // the method.  Wouldn't need this sorted copy if code
        // were refactored to not have the DV group as part of the
        // actual group list.
        ParameterSpecGroupSet sortedCopy = new ParameterSpecGroupSet(this);
        sortedCopy.setDefaultValueGroup(defaultValueGroup, sortedCopy.size());

        // Get the counts of each group.
        Integer[] groupRunCounts = sortedCopy.getGroupRunCounts(includeDVGroup);

        for(int[] idxs : new CombinationsIterator(groupRunCounts)) {
            ParameterSet set = new ParameterSet();

            for(int i = 0, g = 0; i < idxs.length; i++) {
                ParameterSpecGroup group = sortedCopy.get(g++);

                // Ignore the default values if desired.
                if(!includeDVGroup && group == defaultValueGroup) {
                    group = sortedCopy.get(g++);         // Just leap past this one, move to next.
                }

                int idx = idxs[i];
                addGroupParamsToParamSet(set, group, idx, skipDVGroupParams);
            }

            //if(includeDVGroup && defaultValueGroup != null) { // If refactored to have DV group separate reference.
            //    addGroupParamsToParamSet(skipDVGroupParams, set, idx, defaultValueGroup);
            //}

            sets.add(set);
        }

        return sets;
    }

    private void addGroupParamsToParamSet(ParameterSet set, ParameterSpecGroup group,
                                          int idx, Set<Object> skipDVGroupParams) {
        for(Object paramKey : group.keySet()) {

            // If DV group not being ignored, skip default value parameters that
            // are contained in other non-default value groups.
            if(group == defaultValueGroup && skipDVGroupParams.contains(paramKey)) {
                continue;
            }

            ParameterSpecification paramSpec = group.get(paramKey);
            Object value;
            if(!paramSpec.isStable() && group.isEnforceStability(paramKey)) {
                // Save and reload values from cache.
                if(group.isInStabilityCache(paramKey, idx)) {
                    value = group.getFromStabilityCache(paramKey, idx);
                } else {
                    value = paramSpec.getValue(group.getRunCount(), idx);
                    group.putInStabilityCache(paramKey, idx, value);
                }
            } else {
                value = paramSpec.getValue(group.getRunCount(), idx);
            }
            set.put(paramKey, value);
        }
    }

    // Default value group parameters do not provide values to
    // the group set's parameter sets if another non-default value
    // group provides a specification for that parameter.
    private Set<Object> findSkipDVGroupParams() {
        Set<Object> skipDVGroupParamKeys = new HashSet<Object>();
        if(defaultValueGroup != null) {
            for(Object paramKey : defaultValueGroup.keySet()) {
                for(int i = 0; i < size(); i++) {
                    ParameterSpecGroup group = get(i);
                    if(group != defaultValueGroup && group.containsKey(paramKey)) {
                        skipDVGroupParamKeys.add(paramKey);
                        break;
                    }
                }
            }
        }
        return skipDVGroupParamKeys;
    }


    //////////
    // MISC //
    //////////

    public void validate() throws ParameterSpecGroupSetValidationException {
        Set<Object> paramKeys = new HashSet<Object>();
        Set<Integer> specHashCodes = new HashSet<Integer>();

        for(ParameterSpecGroup group : this) {
            for(Object paramKey : group.keySet()) {
                ParameterSpecification spec = group.get(paramKey);

                // Validate
                if(specHashCodes.contains(spec.objectHashCode())) {
                    throw new ParameterSpecGroupSetValidationException(
                        "Multiple parameter groups contain the same specification object '" +
                        spec.getClass().getSimpleName() + "@" + spec.hashCode() +
                        "'.  A specification object should be added to only a single group because it is permitted to save state between calls.");
                }
                if(group != defaultValueGroup && paramKeys.contains(paramKey)) {
                    throw new ParameterSpecGroupSetValidationException(
                        "Multiple parameter groups contain a specification for the parameter '" +
                        paramKey + "'.");
                }

                // Save
                specHashCodes.add(spec.objectHashCode());
                if(group != defaultValueGroup) {
                    paramKeys.add(paramKey);    // Default value parameters do not prevent
                                                // other groups from having the same parameters.
                }

                // Max values
                int max = spec.getMaxValues();
                if(max != -1 && group.getRunCount() > max) {
                    throw new ParameterSpecGroupSetValidationException(
                        "Specification for parameter '" + paramKey +
                        "' has a maximum run count less than the run count of its group.");
                }
            }
        }
    }

    private Integer[] getGroupRunCounts(boolean includeDVGroup) {
        List<Integer> groupRunCounts = new ArrayList<Integer>();
        for(ParameterSpecGroup group : this) {
            if(!includeDVGroup && group == defaultValueGroup) {
                continue;
            }
            groupRunCounts.add(group.getRunCount());
        }
        return groupRunCounts.toArray(new Integer[0]);
    }

    public long getRunCount() throws ParameterSpecGroupSetValidationException {
        return getRunCount(true);
    }
    public long getRunCount(boolean includeDVGroup) throws ParameterSpecGroupSetValidationException {
        validate();
        long product = 1;
        boolean atLeastOne = false;
        for(ParameterSpecGroup group : this) {
            if(!includeDVGroup && group == defaultValueGroup) {
                continue;
            }
            product *= group.getRunCount();
            atLeastOne = true;
        }
        return atLeastOne ? product : 0;
    }


    //////////////
    // PRINTING //
    //////////////

    public void printParameterSets() {
        printParameterSets(true);
    }
    public void printParameterSets(boolean includeDVGroup) {
        ParameterSetList sets = generateAllSetsFromSpecs(includeDVGroup);
        System.out.println(sets.size() + " Parameter Set" + StringUtil.s(sets.size()) + ":");
        for(ParameterSet set : sets) {
            System.out.println(set);
        }
    }

    public void list() {
        list(true);
    }
    public void list(boolean includeDVGroup) {
        for(ParameterSpecGroup group : this) {
            if(!includeDVGroup && group == defaultValueGroup) {
                continue;
            }
            if(group == defaultValueGroup) {
                System.out.print("<DV>");
            }
            System.out.println(group);
            group.list(4);
        }
    }


    /////////////////
    // CONTAINMENT //
    /////////////////

    public boolean containsParamKey(Object paramKey) {
        return containsParamKey(paramKey, true);
    }
    public boolean containsParamKey(Object paramKey, boolean includeDVGroup) {
        return getGroupForParamKey(paramKey, includeDVGroup) != null;
    }
    public boolean containsParamKeyInDefaultValues(Object paramKey) {
        validate();
        return defaultValueGroup != null && defaultValueGroup.containsKey(paramKey);
    }
    public ParameterSpecGroup getGroupForParamKey(Object paramKey) {
        return getGroupForParamKey(paramKey, true);
    }
    public ParameterSpecGroup getGroupForParamKey(Object paramKey, boolean includeDVGroup) {
        validate();
        for(ParameterSpecGroup group : this) {
            if(!includeDVGroup && group == defaultValueGroup) {
                continue;
            }
            if(group.containsKey(paramKey)) {
                return group;
            }
        }
        return null;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public String toString() {
        return toString(true);
    }
    public String toString(boolean includeDVGroup) {
        String ret = "[";
        for(ParameterSpecGroup group : this) {
            if(!includeDVGroup && group == defaultValueGroup) {
                continue;
            }
            if(group == defaultValueGroup) {
                ret += "<DV>";
            }
            ret += group.toString() + ", ";
        }
        if(!isEmpty()) {
            ret = StringUtil.cut(ret, ", ");
        }
        return ret + "]";
    }


    //////////
    // TEST //
    //////////

    public static void main(String[] rgs) {
//        monteCarloGroup();
//        latinHyperCube();
//        multiple();
//        consts();
        ParameterSpecGroupSet groups = new ParameterSpecGroupSet();
        groups.setDefaultValueGroup(new ConstantParameterSpecGroup("hi", 123, "alpha", 123.3123));
        System.out.println(groups.getRunCount(false));
    }

    private static void multiple() {
        RandomManager.setRandomSeed("Ensemble/UniformParameterSpecification", 123);

        ParameterSpecGroup knGrp = new ParameterSpecGroup(3);
        knGrp.put("Knowledge",
            new ListParameterSpecification(new Object[] {0.2, 0.5, 0.8}));

        ParameterSpecGroup gravGrp = new ParameterSpecGroup(6);
        gravGrp.put("Gravity",
            new EvenSpacingParameterSpecification(10, 20));

        ParameterSpecGroup uniGrp = new ParameterSpecGroup(3);
        uniGrp.put("UNIFORM", new UniformParameterSpecification(0, 10.0), true);

        ParameterSpecGroupSet groups = new ParameterSpecGroupSet();
        groups.add(knGrp);
        groups.add(gravGrp);
        groups.add(uniGrp);

//        ConstantParameterSpecGroup cgroup = new ConstantParameterSpecGroup();
//        cgroup.addConstParameter("Gravity", 2222);
//        cgroup.addConstParameter("Gravityx", 2222);
//        groups.setDefaultValueGroup(cgroup);
        groups.setDefaultValueGroup(new ConstantParameterSpecGroup("Gravity", 22, "xyz", "what", "pisquared", 10.237, "asdf"));

//        groups.printParameterSets(false);
        groups.printParameterSets(true);
    }

    private static void consts() {
        ConstantParameterSpecGroup group = new ConstantParameterSpecGroup();
        group.addConstParameter("A", 43.2);
        group.addConstParameter("B", 32.4);
        group.addConstParameter("C", 87.2);
        group.addConstParameter("D", 111.2);

        ParameterSpecGroup group2 = new ParameterSpecGroup(4);
        group2.add("Z", new ListParameterSpecification(true, false, false, true));

        ParameterSpecGroupSet groups = new ParameterSpecGroupSet();
        groups.add(group);
        groups.add(group2);

        groups.printParameterSets();
    }

    private static void latinHypercubeGroup() {

        // Assumes an EvenSpacingParameterSpecification
        // (a uniform distribution of variables is assumed)
        LatinHypercubeParameterSpecGroup group = new LatinHypercubeParameterSpecGroup(5);
        group.addLatinHypercubeParameter("A", 10, 20 /* some distribution some day */);
        group.addLatinHypercubeParameter("B", 200, 300);
        group.addLatinHypercubeParameter("C", 9000, 18000);

        ParameterSpecGroupSet groups = new ParameterSpecGroupSet();
        groups.add(group);

        groups.printParameterSets();
    }

    private static void monteCarloGroup() {

        // Assumes an UniformParameterSpecification
        // (a uniform distribution of variables is assumed)
        MonteCarloParameterSpecGroup group = new MonteCarloParameterSpecGroup(5);
        group.addMonteCarloParameter("A", 10, 20 /* some distribution some day */);
        group.addMonteCarloParameter("B", 200, 300);
        group.addMonteCarloParameter("C", 9000, 18000);

        ParameterSpecGroupSet groups = new ParameterSpecGroupSet();
        groups.add(group);

        groups.printParameterSets();
    }
}

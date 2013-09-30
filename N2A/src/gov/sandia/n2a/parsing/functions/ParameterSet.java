/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.parsing.functions;

import java.util.ArrayList;
import java.util.List;

public class ParameterSet {


    ////////////
    // FIELDS //
    ////////////

    // Indicates a given class is the return type, not a parameter type.
    // This allows the constructors to be as easy to use as possible.
    public static final String RET = "!RET";

    private String[] names;
    private Class<?>[] types;
    private Class<?> retType;
    private boolean orderMatters;
    private boolean allowsVarArgs;


    //////////////////
    // CONSTRUCTORS //
    //////////////////

    public ParameterSet(Object... args) {
        init(args);
        orderMatters = true;            // TODO! should be false, but areValidArgs not set up to handle order matters == false.
        allowsVarArgs = false;
    }

    public ParameterSet(boolean om, boolean va, Object... args) {
        init(args);
        orderMatters = om;
        allowsVarArgs = va;
    }


    ////////////////////
    // INITIALIZATION //
    ////////////////////

    private void init(Object[] args) {
        List<String> namesL = new ArrayList<String>();
        List<Class<?>> typesL = new ArrayList<Class<?>>();

        for(Object arg : args) {
            if(arg instanceof String) {
                namesL.add((String) arg);
            } else if(arg instanceof Class<?>) {
                typesL.add((Class<?>) arg);
            } else {
                throw new IllegalArgumentException("Only objects of type String or Class are allowed when constructing a ParameterSet.");
            }
        }

        while(namesL.size() < typesL.size()) {
            namesL.add("arg" + namesL.size());
        }

        if(namesL.size() != typesL.size()) {
            throw new IllegalArgumentException("The number of String objects must match the number of Class objects when constructing a ParameterSet.");
        }

        // Extract return type from provided arguments.
        int retIdx = namesL.indexOf(RET);
        if(retIdx != -1) {
            retType = typesL.get(retIdx);
            namesL.remove(retIdx);
            typesL.remove(retIdx);
        }

        names = namesL.toArray(new String[0]);
        types = typesL.toArray(new Class<?>[0]);
    }


    ////////////////
    // VALIDATION //
    ////////////////

    public boolean areValidArgs(Object[] args) {
        if(orderMatters && !allowsVarArgs) {
            if(args.length != types.length) {
                return false;
            }
            for(int a = 0; a < args.length; a++) {
                Class<?> argClass = (args[a] == null ? Object.class : args[a].getClass());
                if(!types[a].isAssignableFrom(argClass)) {
                    return false;
                }
            }
            return true;
        }

        // TODO not impl
        return false;
    }


    ///////////////
    // ACCESSORS //
    ///////////////

    public String[] getNames() {
        return names;
    }
    public Class<?>[] getParamTypes() {
        return types;
    }
    public Class<?> getReturnType() {
        return retType;
    }
    public boolean doesOrderMatter() {
        return orderMatters;
    }
    public boolean allowsVarArgs() {
        return allowsVarArgs;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public String toString() {
        String ret = "";
        int c = 0;
        if(retType == null) {
            ret += "[void] ";
        } else {
            ret += "[" + retType.getSimpleName() + "] ";
        }
        for(Class<?> type : types) {
            ret += type.getSimpleName() + " " + names[c++] + ", ";
        }
        if(ret.length() != 0) {
            ret = ret.substring(0, ret.length() - 2);
        }
        return ret.trim();
    }
}

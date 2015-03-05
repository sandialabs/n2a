/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.functions;

import replete.plugins.ExtensionPoint;
import replete.util.StringUtil;


public abstract class Function implements ExtensionPoint {


    //////////////
    // ABSTRACT //
    //////////////

    public abstract String getName();
    public abstract String getDescription();
    public abstract ParameterSet[] getAllowedParameterSets();  // Returning null means any set valid.
    protected abstract Object eval(Object[] args, int parameterSetIndex);
    // Category?

    // Override to make something an assignment function.
    // This places the result of the calculation back into
    // the left-most variable during evaluation.
    public boolean isAssignment() {
        return false;
    }

    public boolean isOutput ()
    {
        return false;
    }

    //////////
    // EVAL //
    //////////

    public Object eval(Object[] args) throws EvaluationException {
        ParameterSet[] allowedSets = getAllowedParameterSets();
        int paramIdx = -1;
        int curIdx = 0;
        if(allowedSets != null) {
            for(ParameterSet set : allowedSets) {
                if(set.areValidArgs(args)) {
                    paramIdx = curIdx;
                    break;
                }
                curIdx++;
            }
            if(paramIdx == -1) {
                String argStr = "";
                for(Object arg : args) {
                    if(arg == null) {
                        argStr += "null";
                    } else {
                        argStr += arg.getClass().getSimpleName();
                    }
                    argStr += ", ";
                }
                if(!argStr.equals("")) {
                    argStr = StringUtil.removeEnd(argStr, ", ");
                }
                throw new EvaluationException("Invalid function arguments supplied for '" +
                    getName() + "'.  Function not applicable for (" + argStr + ").");
            }
        }
        Object ret = eval(args, paramIdx);
        if(ret != null && allowedSets != null) {
            Class<?> expected = allowedSets[paramIdx].getReturnType();
            Class<?> actual = ret.getClass();
            if(!expected.isAssignableFrom(actual)) {
                throw new EvaluationException(
                    "Invalid function return type encountered for '" +
                    getName() + "'.  Found '" + actual.getSimpleName() + "' but expected '" +
                    expected.getSimpleName() + "'.");
            }
        } else {
            // Proper behavior for null return value?
        }
        return ret;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public String toString() {
        return getName();
    }
}

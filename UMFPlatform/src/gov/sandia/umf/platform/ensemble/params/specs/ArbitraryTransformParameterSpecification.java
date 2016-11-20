/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.umf.platform.ensemble.params.specs;

import gov.sandia.n2a.parms.ParameterSpecification;

import java.util.ArrayList;
import java.util.List;

// TODO: Not done.  Needs to be tied into an evaluatable AST.

public class ArbitraryTransformParameterSpecification extends ParameterSpecification {


    ////////////
    // FIELDS //
    ////////////

    private Number start;   // Could also just be doubles.
    private String expr;    // Transform expression
    // private TreeNode exprTree;
    private List<Object> prevValues = new ArrayList<Object>();  // Save previous calls.


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    // e.g. xf = "10 * $prev" // need special variable.
    public ArbitraryTransformParameterSpecification() {}  // TODO: Figure out better hierarchy so can access getShortName w/o this ctor
    public ArbitraryTransformParameterSpecification(Number st, String xf) {
        start = st;
        expr = xf;
        // compile tree
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
    public String getExpression() {
        return expr;
    }

    // Mutators

    public void setStart(Number v) {
        start = v;
    }
    public void setExpression(String e) {
        expr = e;
        // compile tree
    }


    ///////////
    // VALUE //
    ///////////

    @Override
    public Object getValue(int groupRunCount, int idx) {
        Object prevValue = null;
        if(prevValues.size() >= idx + 1) {
            prevValue = prevValues.get(idx);
        }
        while(prevValues.size() < idx + 1) {
            if(prevValues.size() == 0) {
                prevValue = start;
            } else {
                prevValue = prevValues.get(prevValues.size() - 1);
//                prevValue = executeTree(prevValue);
            }
            prevValues.add(prevValue);
        }
        return prevValue;
    }
    @Override
    public boolean isStable() {
        return true;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((expr == null) ? 0 : expr.hashCode());
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
        ArbitraryTransformParameterSpecification other = (ArbitraryTransformParameterSpecification) obj;
        if(expr == null) {
            if(other.expr != null) {
                return false;
            }
        } else if(!expr.equals(other.expr)) {
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
        return "ArbitraryTransformParameterSpecification [start=" + start + ", expr=" + expr + "]";
    }

    @Override
    public String getParamString() {
        return "(start=" + start + ", expr=" + expr + ")";
    }
    @Override
    public String getShortName() {
        return "Arbitrary";
    }
    @Override
    public String getDescription() {
        return "An arbitrary transform parameter specification will produce...";
   }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language;

import gov.sandia.n2a.language.gen.ASTNodeBase;

public class Annotation {


    ////////////
    // FIELDS //
    ////////////

    private ASTNodeBase tree;
    private String name;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public Annotation(ASTNodeBase t) {
        tree = t;

        // No compound assignment, yes single symbol, no non-zero order.
        name = t.getVariableName(false, true, false, false);
    }


    ///////////////
    // ACCESSORS //
    ///////////////

    public ASTNodeBase getTree() {
        return tree;
    }
    public String getName() {
        return name;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public String toString() {
        return "@" + tree.toReadableShort();
    }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.eqset;

import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.util.NDocList;

import java.util.List;

public class DataModelLoopException extends DataModelException {


    ////////////
    // FIELDS //
    ////////////

    private NDoc target;
    private NDocList parts;
    private List<String> assembleReasons;


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public DataModelLoopException(NDoc t, NDocList p, List<String> ar) {
        // Message is supplied later after construction.
        target = t;
        parts = p;
        assembleReasons = ar;
    }


    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    public NDoc getTarget() {
        return target;
    }
    public NDocList getParts() {
        return parts;
    }
    public List<String> getAssembleReasons() {
        return assembleReasons;
    }


    ////////////
    // HELPER //
    ////////////

    public static String getErrorLine(DataModelLoopException dmle, int line) {
        NDoc part = dmle.parts.get(line).bean;
        String ar = dmle.assembleReasons.get(line);
        String msg = "";
        if(!part.isPersisted()) {
            // Not sure if possible?  Maybe if you hooked into an
            // existing loop hierarchy?  Should always be "(target)"
            // though if it is possible.
            msg += ar + " <NEW>";
        } else {
            msg += ar + " " + part.get("name") + " [" + part.getId() + "]";
        }
        return msg;
    }
}

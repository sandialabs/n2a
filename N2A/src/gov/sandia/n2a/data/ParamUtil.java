/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.data;

import gov.sandia.n2a.parsing.Annotation;
import gov.sandia.n2a.parsing.ParsedEquation;

public class ParamUtil {

    public static String getExtra(ParsedEquation eq) 
    {
        String extra = "";
        if(!eq.getTree().isSimpleAssignment()) {
            extra += " {+=}";
        }
        if(eq.getAnnotations().size() != 0) {
            for(Annotation annot : eq.getAnnotations().values()) {
                extra += " " + annot;
            }
        }  
        return extra;
    }


}

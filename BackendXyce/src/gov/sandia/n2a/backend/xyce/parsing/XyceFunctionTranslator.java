/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.parsing;

import gov.sandia.n2a.language.gen.ASTNodeBase;
import gov.sandia.n2a.language.gen.ASTNodeRenderer;
import gov.sandia.n2a.language.gen.ASTRenderingContext;

public class XyceFunctionTranslator implements ASTNodeRenderer
{

    @Override
    public String render(ASTNodeBase node, ASTRenderingContext context) 
    {
        Object value = node.getValue ();
        if (value.toString().toLowerCase().equals("uniform")) {
            return translateUniform(node, context);
        }
        String ret = value + "(";
        for(int a = 0; a < node.getCount(); a++) {
            ret += context.render (node.getChild(a));
            if(a != node.getCount() - 1) {
                ret += ", ";
            }
        }
        return ret + ")";
    }
    
    private String translateUniform(ASTNodeBase node, ASTRenderingContext context) 
    {
        String ret = "rand()";
        int numParams = node.getCount();
        if (numParams==1) {
            String b = context.render (node.getChild(0));
            ret += "*" + b;
        }
        if (numParams==2) {
            String a = context.render (node.getChild(0));
            String b = context.render (node.getChild(1));
            ret += "*(" + b + "-" + a + ") + " + a;
        }
        return ret;
    }
}
/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.parse;

public abstract class ASTNodeBase extends SimpleNode {
    public ASTNodeBase(Object value, int id) {
        super(id);
        jjtSetValue(value);
    }

    public ASTNodeBase(int id) {
        super(id);
    }

    public ASTNodeBase(ExpressionParser p, int id) {
        super(p, id);
    }

    /** Accept the visitor. **/
    @Override
    public Object jjtAccept(ExpressionParserVisitor visitor, Object data) throws ParseException {
        return visitor.visit(this, data);
    }
}

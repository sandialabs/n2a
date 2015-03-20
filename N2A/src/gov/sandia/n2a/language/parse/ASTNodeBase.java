/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.language.parse;

import gov.sandia.n2a.language.EvaluationContext;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Function;
import gov.sandia.n2a.language.Type;
import gov.sandia.n2a.language.operator.Assign;

import java.util.HashSet;
import java.util.Set;

import replete.util.StringUtil;

public abstract class ASTNodeBase extends SimpleNode {


    ////////////////////
    // AUTO-GENERATED //
    ////////////////////

    public ASTNodeBase(Object value, int id) {
        super(id);
        setValue(value);
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


    ////////////
    // CUSTOM //
    ////////////

    // Source is the original line of text parsed to create
    // the tree.  Right now this value is only set on the
    // root node.
    private String source;
    public void setSource(String src) {
        source = src;
    }
    public String getSource() {
        return source;
    }

    // Returns whether or not the expression tree is in the form:
    //   Variable = Expression
    //   Variable' = Expression
    // The top node must be an OpNode with the function = and the left
    // child of that node must be a VarNode.
    // Right now, this is NOT allowed (should it be?):
    //   cos(x) = Expression
    public boolean isSimpleAssignment() {
        if(this instanceof ASTOpNode) {
            Function func = (Function) getValue();
            if(func.getClass().equals(Assign.class)) {
                if(getChild(0) instanceof ASTVarNode) {
                    return true;
                }
            }
        }
        return false;
    }

    // TODO: isSimpleAssignment & isAssignment do different things due
    // to left hand side possibly being different!
    // Right now this IS allowed (should it be?):
    //  cos(x) = Expression
    public boolean isAssignment ()
    {
        if (this instanceof ASTOpNode)
        {
            Function func = (Function) getValue ();
            return func.assignment;
        }
        return false;
    }

    public boolean isSingleSymbol(){
        return this instanceof ASTVarNode;
    }
    
    public boolean isListVar() {
        if (this instanceof ASTOpNode) {
            ASTNodeBase right = getChild(1);
            if (right instanceof ASTListNode) {
                return true;
            }
        }
        return false;
    }

    public boolean containsOutput ()
    {
        if (this instanceof ASTFunNode)
        {
            if (getValue ().toString ().equals ("trace")) return true;
        }

        int children = getCount ();
        for (int i = 0; i < children; i++)
        {
            if (getChild (i).containsOutput ()) return true;
        }

        return false;
    }

    /**
        Determine if this node depends only on constants and Variables that remain constant after the init phase.
    **/
    public boolean isInitOnly ()
    {
        int children = getCount ();
        for (int i = 0; i < children; i++)
        {
            if (! getChild (i).isInitOnly ()) return false;
        }
        // Implicitly, a node with no children is "initOnly"
        return true;
    }

    public ASTVarNode getVarNode() {
        if(isSingleSymbol()) {
            return (ASTVarNode) this;
        } else if(isAssignment() || isListVar()) {
            ASTNodeBase left = getChild(0);
            if(left instanceof ASTVarNode) {  // Need additional check for isAssignment.
                return (ASTVarNode) left;
            }
        }
        return null;
    }

    // Returns the name of the variable on the left-hand
    // side of an assignment operator, or null if that is
    // not applicable for this node.  The arguments further
    // allow you to specify whether or not you want compound
    // assignments to be allowed (x += 3) and whether or
    // not you want non-zero order variables allowed (x' = 4y).
    public String getVariableName(boolean allowCompoundAssignment, boolean allowSingleSymbol, boolean allowNonZeroOrder, boolean includeOrder) {

        ASTVarNode varNode;

        if(this instanceof ASTVarNode) {
            if(!allowSingleSymbol) {
                return null;
            }
            varNode = (ASTVarNode) this;
        } else if(this instanceof ASTOpNode) {
            Function func = (Function) getValue();
            if(!func.getClass().equals(Assign.class)) {
                if(!func.assignment || !allowCompoundAssignment) {
                    return null;
                }
            }

            ASTNodeBase left = getChild(0);
            if(!(left instanceof ASTVarNode)) {
                return null;
            }

            varNode = (ASTVarNode) left;
        } else {
            return null;
        }

        if(!allowNonZeroOrder && varNode.getOrder() != 0) {
            return null;
        }
        if(includeOrder) {
            return varNode.getVariableNameWithOrder();
        }
        return varNode.getVariableName();
    }


    ///////////////
    // RENDERING //
    ///////////////

    /**
     * Produces a string representation of this node. Uses given context to render children,
     * but no specialized renderers will affect this node directly. In other words, this
     * function provides the default renderer for this node.
     */
    public abstract String render(ASTRenderingContext context);

    // Convenience methods that populate specific options for you.
    public String toReadableLong() {
        return new ASTRenderingContext (false).render (this);
    }
    public String toReadableShort() {
        return new ASTRenderingContext (true).render (this);
    }

    public String toDebugTree() {
        return toDebugTree(this, 0);
    }
    private String toDebugTree(ASTNodeBase n, int level) {
        String tree =
            StringUtil.spaces(level * 3) +
            "NODE: " + n.getClass().getSimpleName() +
            ", CH#: " +
            n.jjtGetNumChildren() +
            ", VALUE: " + n + " (" + n.getValue().getClass().getSimpleName() + ")\n";

        for(int i = 0; i < n.jjtGetNumChildren(); i++) {
            tree += toDebugTree(n.getChild(i), level + 1);
        }

        return tree;
    }

    public Set<String> getSymbols() {
        Set<String> symbols = new HashSet<String>();
        getSymbols(symbols);
        return symbols;
    }

    private void getSymbols(Set<String> symbols) {
        if(this instanceof ASTVarNode) {
            symbols.add(((ASTVarNode) this).getVariableName());
        } else if(this instanceof ASTFunNode) {
            symbols.add(((ASTFunNode) this).getFunction().name);
        }
        for(int c = 0; c < getCount(); c++) {
            ASTNodeBase child = getChild(c);
            child.getSymbols(symbols);
        }
    }

    public Set<String> getVariables() {
        Set<String> symbols = new HashSet<String>();
        getVariables(symbols);
        return symbols;
    }

    private void getVariables(Set<String> symbols) {
        if(this instanceof ASTVarNode) {
            symbols.add(((ASTVarNode) this).getVariableName());
        }
        for(int c = 0; c < getCount(); c++) {
            ASTNodeBase child = getChild(c);
            child.getVariables(symbols);
        }
    }

    // Convenience methods for improved readability
    // and typing ("jjt" prefix is annoying).
    public ASTNodeBase getChild(int i) {
        return (ASTNodeBase) jjtGetChild(i);
    }
    public void setChild(Node n, int i) {
        jjtAddChild(n, i);
    }
    public void clearChildren () {
        children = null;  // forces a new list to be created when another child is added
    }
    public int getCount() {
        return jjtGetNumChildren();
    }
    public Object getValue() {
        return jjtGetValue();
    }
    public void setValue(Object o) {
        jjtSetValue(o);
    }
    public Object getParent() {
        return jjtGetParent();
    }


    ////////////////////
    // TRANSFORMATION //
    ////////////////////

    public ASTNodeBase transform (ASTTransformationContext context)
    {
        int children = getCount ();
        for (int i = 0; i < children; i++)
        {
            setChild (getChild (i).transform (context), i);
        }
        return context.transform (this);
    }


    ////////////////
    // EVALUATION //
    ////////////////

    // Every node has the ability to evaluate itself
    // to a single result object.  The context is a
    // where the tree gets and sets the/ variable values
    // to be used in the evaluation.
    //
    // In other words, when you evaluate
    //    y = x + z
    // You need to have a context that can provide the
    // values for x and z, and set the value for y.
    // The latter is needed since this evaluation process
    // is happening recursively, and y might be used in
    // a later evaluation:
    //    x = 10
    //    z = 20
    //    y = x + z
    //    c = y ^ 2

    public Type eval () throws EvaluationException
    {
        return eval (new EvaluationContext ());
    }
    public abstract Type eval (EvaluationContext context) throws EvaluationException;
}

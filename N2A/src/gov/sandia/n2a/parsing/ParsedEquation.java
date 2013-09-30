/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.parsing;

import gov.sandia.n2a.parsing.gen.ASTNodeBase;
import gov.sandia.n2a.parsing.gen.ASTVarNode;
import gov.sandia.n2a.parsing.gen.ExpressionParser;
import gov.sandia.n2a.parsing.gen.ParseException;
import gov.sandia.umf.platform.UMF;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import replete.util.StringUtil;

public class ParsedEquation implements Comparable<ParsedEquation>
{


    ////////////
    // FIELDS //
    ////////////

    // TODO: The following fields should be the only ones used in this class.
    public String name;
    public int order;
    public String conditional;
    public NDoc equation;
    public ASTNodeBase parsedEquation;
    public ASTNodeBase parsedConditional;

    // TODO: Use the above fields and new methods instead.  Deprecated!
    public String source;
    public Map<String, Annotation> annotations = new LinkedHashMap<String, Annotation>();
    public Map<String, Object> metadata = new HashMap<String, Object>();


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    public ParsedEquation (String name)
    {
        this (name, -1, "*");
    }

    public ParsedEquation (String name, int order)
    {
        this (name, order, "*");
    }

    public ParsedEquation (String name, int order, String conditional)
    {
        this.name        = name;
        this.order       = order;
        this.conditional = conditional;
    }

    public ParsedEquation (NDoc equation) throws ParseException
    {
        this.equation = equation;
        String[] parts = ((String) equation.get("value")).split ("@");
        source = parts[0].trim ();
        parsedEquation = ExpressionParser.parse (source);
        ASTVarNode node = parsedEquation.getVarNode ();
        name = node.getVariableName ();
        order = node.getOrder ();
        if (parts.length > 1)
        {
            parsedConditional = ExpressionParser.parse (parts[1]);
            conditional = parsedConditional.toReadableShort ();
        }
        else
        {
            conditional = "";
        }
    }

    public ParsedEquation(String src, ASTNodeBase t, List<Annotation> an) {
        source = src;
        parsedEquation = t;
        for(Annotation a : an) {
            annotations.put(a.getName(), a);
        }
        name = getVarName();
        if(parsedEquation.getVarNode() == null) {
            order = -1;
        } else {
            order = parsedEquation.getVarNode().getOrder();
        }
        conditional = getConditional(src);
    }

    private String getConditional(String src)
    {
        String[] parts = src.split ("@");
        if (parts.length > 1)
        {
            try {
                parsedConditional = ExpressionParser.parse (parts[1]);
                return parsedConditional.toReadableShort ();
            } catch (ParseException e) {
                return "";
            }
        }
        return "";
    }


    ///////////////
    // ACCESSORS //
    ///////////////

    // Accessors

    public String getSource() {
        return source;
    }
    public ASTNodeBase getTree() {
        return parsedEquation;
    }
    public Map<String, Annotation> getAnnotations() {
        return annotations;
    }
    public String getVarName() {
        // Yes compound assignment, no single symbol, yes non-zero order, no include order.
        return parsedEquation.getVariableName(true, true, true, false);
    }
    public String getVarNameWithOrder() {
        // Yes compound assignment, no single symbol, yes non-zero order, include order.
        return parsedEquation.getVariableName(true, true, true, true);
    }
    public Annotation getAnnotation(String name) {
        return annotations.get(name);
    }

    public boolean hasAnnotation(String name) {
        return getAnnotation(name) != null;
    }
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    // Mutators

    public void addAnnotation(String eq) {
        eq = eq.trim();
        if(eq.startsWith("@")) {
            eq = StringUtil.snip(eq, 1);
        }
        try {
            Annotation anno = new Annotation(ExpressionParser.parse(eq));
            annotations.put(anno.getName(), anno);
        } catch(ParseException e) {
            UMF.handleUnexpectedError(null, e, "Could not parse annotation.");
        }
    }
    public void addAnnotation(Annotation a) {
        annotations.put(a.getName(), a);
    }
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    public void copyMetadata(ParsedEquation peq) {
        for(String key : peq.getMetadata().keySet()) {
            setMetadata(key, peq.getMetadata(key));
        }
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public String toString() {
        String annoStr = "";
        for(Annotation anno : annotations.values()) {
            annoStr += "  " + anno;
        }
        return parsedEquation.toReadableShort() + annoStr;
    }

    @Override
    public int compareTo (ParsedEquation that)
    {
        int result = name.compareTo (that.name);
        if (result != 0) {
            return result;
        }
        if (order >= 0  &&  that.order >= 0)
        {
            result = that.order - order;  // descending order; makes it easier to generate all orders of variables
            if (result != 0) {
                return result;
            }
        }
        if (conditional.equals ("*")  ||  that.conditional.equals ("*")) {
            return 0;
        }
        return conditional.compareTo (that.conditional);
    }

    @Override
    public boolean equals (Object that)
    {
        if (this == that) {
            return true;
        }
        ParsedEquation pet = (ParsedEquation) that;
        if (pet == null) {
            return false;
        }
        return compareTo (pet) == 0;
    }
}

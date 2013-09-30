/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.eqset;

import gov.sandia.n2a.parsing.Annotation;
import gov.sandia.n2a.parsing.EquationParser;
import gov.sandia.n2a.parsing.ParsedEquation;
import gov.sandia.n2a.parsing.SpecialVariables;
import gov.sandia.n2a.parsing.gen.ASTNodeBase;
import gov.sandia.n2a.parsing.gen.ASTNodeRenderer;
import gov.sandia.n2a.parsing.gen.ASTRenderingContext;
import gov.sandia.n2a.parsing.gen.ASTVarNode;
import gov.sandia.n2a.parsing.gen.ParseException;
import gov.sandia.umf.platform.UMF;
import gov.sandia.umf.platform.connect.orientdb.ui.NDoc;
import gov.sandia.umf.platform.util.NDocList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import replete.util.ReflectionUtil;

public class EquationAssembler {

    public static final String PREFIX_SEP = ".";

    public static PartEquationMap getAssembledPartEquations(NDoc target) {
        return getAssembledPartEquations(target, false);
    }
    public static PartEquationMap getAssembledPartEquations(NDoc target, boolean recursiveCheckOnly) {
        try {
            NDocList parts = new NDocList();
            List<String> assembleReasons = new ArrayList<String>();
            PartEquationMap map = getAssembledPartEquations(target, parts, assembleReasons, "(target)", recursiveCheckOnly);
            if(map != null) {
                map.performConsistencyCheck();   // Fills in missing symbol information.
            }
            return map;
        } catch(DataModelLoopException e) {
            String cna = "Could not assemble " + target.get("type") + " equations.  ";
            String msg = (!recursiveCheckOnly ? cna : "") + "A loop exists in the parent and/or include hierarchy:";
            ReflectionUtil.set("detailMessage", e, msg + "\n" + e.getMessage());
            throw e;
        } catch(ParseException e) {
            throw new DataModelException("Could not assemble " + target.get("type") + " equations.  Equation parse error.", e);
        }
    }

    private static PartEquationMap getAssembledPartEquations(NDoc target,
            NDocList parts, List<String> assembleReasons, String assembleR, boolean recursiveCheckOnly)
            throws ParseException, DataModelLoopException {


        // Perform recursion check.  Make sure that the part being processed
        // has not yet been seen during this recursive assembly process.
        if(parts.contains(target)) {
            parts.add(target);
            assembleReasons.add(assembleR);

            DataModelLoopException dmle =
                new DataModelLoopException(target, parts, assembleReasons);
            String msg = "";
            for(int i = 0; i < parts.size(); i++) {
                msg += DataModelLoopException.getErrorLine(dmle, i);
                if(i != parts.size() - 1) {
                    msg += " --> ";
                }
            }
            ReflectionUtil.set("detailMessage", dmle, msg);
            throw dmle;
        }

        // Push tracking information for this part onto some lists.
        // This enables recursion detection.
        parts.add(target);
        assembleReasons.add(assembleR);

        // Start off with a blank equation map for this part.
        PartEquationMap map = recursiveCheckOnly ? null : new PartEquationMap(PREFIX_SEP);

        // Inheritance: Add all the parent's equations to the map.
        NDoc parent = target.get("parent");
        if(parent != null) {
            PartEquationMap parMap = getAssembledPartEquations(parent, parts, assembleReasons, "(parent)", recursiveCheckOnly);
            if(!recursiveCheckOnly) {

                // Set the reason for the equations being added to the equation
                // map at this point in time (could be overridden later).  This
                // is for sorting purposes.
                for(String s : parMap.keySet()) {
                    List<ParsedEquation> peqs = parMap.get(s);
                    for(ParsedEquation peq : peqs) {
                        peq.setMetadata("reason", "parent");
                    }
                }

                map.putAll(parMap);
                map.getOverridingEquations().addAll(parMap.getOverridingEquations());
                map.getOverriddenEquations().addAll(parMap.getOverriddenEquations());
            }
        }

        // Inclusion: Add all of the included parts' equations to the map.
        List<NDoc> assocs = target.getAndSetValid("associations", new ArrayList<String>(), List.class);
        List<NDoc> includes = new ArrayList<NDoc>();
        for (NDoc doc : assocs) {
            if(((String) doc.get("type")).equalsIgnoreCase("include")) {
                includes.add(doc);
            }
        }
        for(NDoc assoc : includes) {
            NDoc destPart = assoc.get("dest");
            final PartEquationMap paMap = getAssembledPartEquations(destPart, parts, assembleReasons, "(include)", recursiveCheckOnly);
            if(!recursiveCheckOnly) {
                final String prefix =
                    (assoc.get("name") == null ?
                     (String) destPart.get("name") :
                     (String) assoc.get("name"));
                for(String key : paMap.keySet()) {
                    List<ParsedEquation> includedPeqs = paMap.get(key);
                    for(int i = 0; i < includedPeqs.size(); i++) {
                        ParsedEquation includedPeq = includedPeqs.get(i);
                        ASTRenderingContext context = new ASTRenderingContext (true);
                        context.add (ASTVarNode.class, new ASTNodeRenderer() {
                            public String render(ASTNodeBase node, ASTRenderingContext context) {
                                ASTVarNode varNode = (ASTVarNode) node;
                                if(!paMap.containsKey(varNode.getVariableName()) || paMap.existsPlusEqualsForVar(varNode.getVariableName())) {
                                    return node.toString();
                                }
                                return prefix + PREFIX_SEP + node.toString();
                            }
                        });

                        String transformedLine = context.render (includedPeq.getTree());
                        for(Annotation anno : includedPeq.getAnnotations().values()) {
                            transformedLine += "  @" + context.render (anno.getTree());
                        }
                        ParsedEquation transformedPeq = EquationParser.parse(transformedLine);
                        transformedPeq.copyMetadata(includedPeq);
                        includedPeqs.set(i, transformedPeq);
                    }

                    // Set the reason for the equations being added to the equation
                    // map at this point in time (could be overridden later).  This
                    // is for sorting purposes.
                    for(ParsedEquation peq : includedPeqs) {
                        peq.setMetadata("reason", "include");
                    }

                    if(!paMap.containsKey(key) || paMap.existsPlusEqualsForVar(key)) {
                        if(map.containsKey(key)) {
                            map.get(key).addAll(includedPeqs);
                        } else {
                            map.put(key, includedPeqs);
                        }
                    }
                    else {
                        map.put(prefix + PREFIX_SEP + key, includedPeqs);
                    }
                }
            }
        }

        if(!recursiveCheckOnly) {
            List<ParsedEquation> overriding = new ArrayList<ParsedEquation>();
            List<ParsedEquation> overridden = new ArrayList<ParsedEquation>();
            int eqIdx = 0;
            List<NDoc> eqs = (List<NDoc>) target.getAndSetValid("eqs", new ArrayList<NDoc>(), List.class);
            for(NDoc eq : eqs) {
                addParsedEquation(map, eq, overriding, overridden, parts.size() - 1, eqIdx, assembleReasons.get(assembleReasons.size() - 1));
                eqIdx++;
            }
            map.getOverridingEquations().addAll(overriding);
            map.getOverriddenEquations().addAll(overridden);
        }

        // Remove tracking information for this part.
        parts.remove(parts.size() - 1);
        assembleReasons.remove(assembleReasons.size() - 1);

        return map;
    }

    private static void addParsedEquation(PartEquationMap map, NDoc eq, List<ParsedEquation> overriding, List<ParsedEquation> overridden, int level, int eqIdx, String reason) throws ParseException {
        ParsedEquation peq = getParsedEquation(eq);

        // TODO: This is really kind weird... can sorting be done a
        // different way?

        // Record important metadata for this ParsedEquation object
        // so that the ParsedEquation objects carry with them some
        // indication of where they came from.  This is necessary
        // in part because the EquationAssembler class does not
        // return a map containing Equation objects, but rather
        // ParsedEquation objects.  This was done this way since
        // the assembly process creates new parsed equations that
        // don't exist in the database explicitly ('IncludeName.var =')
        // and I didn't think it was good to have such a modified
        // equation inside of a database-tied bean.  However, you
        // could probably do this just fine assuming you never
        // attempted to persist that bean.
        peq.setMetadata("eqid", eq.getId());
        peq.setMetadata("eq", eq);
        peq.setMetadata("order", eq.get("order"));  // Sorting
        peq.setMetadata("level", level);            // Sorting
        peq.setMetadata("index", eqIdx);            // Sorting

        List<ParsedEquation> curEqs = map.get(peq.getVarName());

        // If this symbol has never been seen before, then give it
        // a new list in the map containing just this equation.
        if(curEqs == null) {
            curEqs = new ArrayList<ParsedEquation>();
            curEqs.add(peq);
            map.put(peq.getVarName(), curEqs);

        // Else if this equation's symbol already has one or more
        // equations in the map, then decide if any replacements
        // need to be made.
        } else {
            Map<String, Annotation> ovAnnos = null;
            for(int i = 0; i < curEqs.size(); i++) {
                ParsedEquation stmt = curEqs.get(i);
                if(sameEnoughToOverride(stmt, peq)){
                    overriding.add(peq);
                    ParsedEquation ovEq = curEqs.get(i);
                    overridden.add(ovEq);
                    ovAnnos = ovEq.getAnnotations();
                    curEqs.remove(i);
                    break;
                }
            }
            if(ovAnnos != null) {
                for(Annotation ovAnno : ovAnnos.values()) {
                    if(!peq.hasAnnotation(ovAnno.getName())) {
                        peq.addAnnotation(ovAnno);
                    }
                }
                // TODO: Need special syntax for an inherited equation
                // to remove an annotation?
                //   Example:
                //      In Parent: x = 23 + $pi  @req = t > 40
                //      In Child:  x = 13 + $pi  @!req
            }
            curEqs.add(peq);
        }

        // Set the reason for the equations being added to the equation
        // map at this point in time (could be overridden later).  This
        // is for sorting purposes.
        peq.setMetadata("reason", "target");
    }

    // To be "same enough to override", two equations must have the same
    // selection criteria and the same order.  If two equations don't
    // meet these conditions, then both must be kept.
    private static boolean sameEnoughToOverride(ParsedEquation s1, ParsedEquation s2) {
        Annotation a1 = s1.getAnnotation(SpecialVariables.WHERE);
        Annotation a2 = s2.getAnnotation(SpecialVariables.WHERE);

        boolean sameSelCrit =
            (a1 == null && a2 == null) ||
            (a1 != null && a2 != null && a1.equals(a2)); // TODO: what does a1 == a2 mean?

        ASTVarNode vn1 = s1.getTree().getVarNode();
        ASTVarNode vn2 = s2.getTree().getVarNode();

        if(vn1 == null || vn2 == null) {
            return false;
        }

        int o1 = vn1.getOrder();
        int o2 = vn2.getOrder();

        boolean sameOrder = (o1 == o2);

        boolean bothSimpleAssign =
            s1.getTree().isSimpleAssignment() &&
            s2.getTree().isSimpleAssignment();

        return sameSelCrit && sameOrder && bothSimpleAssign;
    }

    public static ParsedEquation getParsedEquation(NDoc eq) throws ParseException {
        if(eq.get("value") == null) {
            return null;
        }
        try {
            return EquationParser.parse((String) eq.get("value"));
        } catch(ParseException e) {
            UMF.handleUnexpectedError(null, e, "Could not parse equation.");
            throw e;
        }
    }
}

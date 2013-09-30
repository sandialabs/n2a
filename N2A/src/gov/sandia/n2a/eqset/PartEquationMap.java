/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.eqset;

import gov.sandia.n2a.parsing.ParsedEquation;
import gov.sandia.n2a.parsing.functions.AdditionFunction;
import gov.sandia.n2a.parsing.functions.FunctionList;
import gov.sandia.n2a.parsing.gen.ASTNodeBase;
import gov.sandia.n2a.parsing.gen.ASTOpNode;
import gov.sandia.n2a.parsing.gen.ASTTransformationContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import replete.util.StringUtil;

public class PartEquationMap extends TreeMap<String, List<ParsedEquation>> {

    // This data structure allows us to hold
    // various different versions of an equation
    // for the same symbol.  For example:
    //
    //      x -> [x = 2]
    //      y -> [y = 3]
    //      z -> [z = 4, z' = 0]
    //      a -> [a = 6, a = 7 @where = grid(1,1)]
    //
    // This data structure is a map of symbol name
    // to a list of equations that are relevant
    // for that symbol.  These different versions
    // of an equation are non-overlapping.  This
    // means that the different versions might mean
    // different things under different circumstances.
    // Therefore, none can be dismissed.  Right now
    // we decide if equations are non-overlapping if:
    //
    //  1) It has a different "order"
    //        V = <eq> & V' = <eq> & V'' = <eq>
    //     are 3 valid, non-overlapping equations for
    //     the same symbol.
    //
    //  2) It has different selection criteria
    //        I_inj = 0
    //        I_inj = pulse(2, 3) @where = grid(1,1)
    //     are 2 valid, non-overlapping equations for
    //     the same symbol.  This is because where
    //     the equation is "relevant" depends on the
    //     context denoted by the annotation @where.
    //
    // In other words, you will never see this list of
    // equations for 'x' in this map:
    //
    //      x -> [x = 2, x = 3]
    //
    // This is because both the equation "x = 2" and
    // "x = 3" have the same order AND the same
    // selection criteria (i.e. none).  Thus the two
    // would be in direct conflict with each other and
    // one would override the other based on the
    // hierarchy of the parts involved.
    //
    // This data structure is created with the above
    // semantics by the DataModel class.


    ////////////
    // FIELDS //
    ////////////

    // Const

    private static final String TS_SYM_HEADER = "Symbol";
    private static final String TS_EQ_HEADER = "Equations";

    // Other

    private Set<String> unk = new TreeSet<String>();
    private List<ParsedEquation> overridden = new ArrayList<ParsedEquation>();
    private List<ParsedEquation> overriding = new ArrayList<ParsedEquation>();


    /////////////////
    // CONSTRUCTOR //
    /////////////////

    // TODO: Currently failing for "ABC @ where" && "hi * there"... need to allow or prevent this...
    public PartEquationMap(final String prefixSep) {
        // TODO: This breaks for equations like "abc" (no = sign).
        super(new Comparator<String>() {
            public int compare(String o1, String o2) {
                String ere = escRegEx(prefixSep);

                String[] p1 = null;
                String[] p2 = null;

                if(o1 == null) {
                    p1 = new String[0];
                } else {
                    p1 = o1.split(ere);
                }

                if(o2 == null) {
                    p2 = new String[0];
                } else {
                    p2 = o2.split(ere);
                }

                if(p1.length == p2.length) {
                    if(o1 == null && o2 == null) {
                        return 0;
                    } else if(o1 == null) {
                        return -1;
                    } else if(o2 == null) {
                        return 1;
                    }
                    return o1.compareTo(o2);       // Alphabetical by symbol name
                }
                return p1.length - p2.length;      // Group by depth
            }
        });
    }

    public List<ParsedEquation> getAsList() {
        List<ParsedEquation> peqList = new ArrayList<ParsedEquation>();
        for(String key : keySet()) {
            for(ParsedEquation peq : get(key)) {
                peqList.add(peq);
            }
        }
        return peqList;
    }

    private static String escRegEx(String inStr) {
        return inStr.replaceAll("([\\\\*+\\[\\](){}\\$.?\\^|])", "\\\\$1");
    }


    //////////
    // MISC //
    //////////

    public void transform(ASTTransformationContext context) {
        for(List<ParsedEquation> peqs : values()) {
            for(ParsedEquation peq : peqs) {
                peq.getTree().transform(context);
            }
        }
    }

    public void performConsistencyCheck() {
        for(List<ParsedEquation> peqs : values()) {
            for(ParsedEquation peq : peqs) {
                ASTNodeBase node = peq.getTree();
                if(node.isAssignment()) {
                    ASTNodeBase right = node.getChild(1);
                    Set<String> rightSyms = right.getSymbols();
                    for(String rightSym : rightSyms) {
                        if(get(rightSym) == null) {
                            unk.add(rightSym);
                        }
                    }
                }
            }
        }
    }

    public boolean isPlusEqualsEq(ParsedEquation pe) {
        ASTNodeBase n = pe.getTree();
        if(n instanceof ASTOpNode && ((ASTOpNode) n).getFunction().equals(FunctionList.get(FunctionList.OP_AASSIGN))) {
            return true;
        }
        return false;
    }

    public boolean existsPlusEqualsForVar(String varName) {
        if (containsKey(varName)) {
            List<ParsedEquation> peqs = get(varName);
            for (ParsedEquation pe : peqs) {
                if (isPlusEqualsEq(pe)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void combinePlusEquals() {
        for(String key : keySet()) {
            HashMap<Integer, ParsedEquation> summedEqs = new HashMap<Integer, ParsedEquation>();
            List<ParsedEquation> peqs = get(key);
            for (int i=peqs.size()-1; i>=0; i--) {
                ParsedEquation pe = peqs.get(i);
                if (isPlusEqualsEq(pe)) {
                    // TODO:  think about whether annotations change this scheme
                    int order = getDiffOrder(pe);
                    if (!summedEqs.containsKey(order)) {
                        summedEqs.put(order, pe);
                    }
                    else {
                        addEquations(summedEqs.get(order), pe);
                    }
                    peqs.remove(i);
                }
            }
            // if we did find at least one += equation for this key, there might also
            // be a single = equation that should be added in also
            // go back through whatever's left in peqs and check
            if (!summedEqs.isEmpty()) {
                for (int i=peqs.size()-1; i>=0; i--) {
                    ParsedEquation pe = peqs.get(i);
                    int order = getDiffOrder(pe);
                    if (summedEqs.containsKey(order)) {
                        // TODO - still need to check annotations
                        // also, this is assuming that pe is = equation...
                        addEquations(summedEqs.get(order), pe);
                        peqs.remove(i);
                    }
                }
            }
            peqs.addAll(summedEqs.values());
        }
    }

    // TODO: Why is this easier to write than the ASTVarNode.getOrder?
    // Is this a bad way to do it?
    private int getDiffOrder(ParsedEquation eq) {
        return eq.getVarNameWithOrder().length() - eq.getVarName().length();
    }

    private void addEquations(ParsedEquation origPE, ParsedEquation newPE) {
        ASTNodeBase origRHS = origPE.getTree().getChild(1);
        ASTNodeBase addRHS = newPE.getTree().getChild(1);
        // need a new + OpNode; the above pes' trees' RHSs should be its children
        // and this new node should replace origRHS in origPE
        ASTOpNode top = new ASTOpNode(new AdditionFunction());
        top.jjtAddChild(origRHS, 0);
        top.jjtAddChild(addRHS, 1);
        origPE.getTree().jjtAddChild(top,1);
        origPE.getSource().concat(newPE.getSource().substring(newPE.getSource().indexOf("=")));
        origPE.getAnnotations().putAll(newPE.getAnnotations());
    }

    //////////////////////////
    // ACCESSORS / MUTATORS //
    //////////////////////////

    // Accessors

    public Set<String> getUnknownSymbols() {
        return unk;
    }

    public List<ParsedEquation> getOverridingEquations() {
        return overriding;
    }
    public void setOverridingEquations(List<ParsedEquation> o) {
        overriding = o;
    }

    public List<ParsedEquation> getOverriddenEquations() {
        return overridden;
    }
    public void setOverriddenEquations(List<ParsedEquation> o) {
        overridden = o;
    }


    ////////////////
    // OVERRIDDEN //
    ////////////////

    @Override
    public String toString() {
        String ret = "";

        // Column headers.
        int longestK = calcLongestKey();
        int longestV = calcLongestValue();
        ret += String.format(" %" + longestK + "s   =>   %-20s\n", "Symbol", "Equations");
        ret += String.format(" %" + longestK + "s        %-20s\n", StringUtil.replicateChar('-', longestK), StringUtil.replicateChar('-', longestV));

        // Equations.
        for(String key : keySet()) {
            List<ParsedEquation> eqs = get(key);
            for(int i = 0; i < eqs.size(); i++) {
                if(i == 0) {
                    ret += String.format(" %" + longestK + "s   =>   %-20s\n", key, eqs.get(i));
                } else {
                    ret += String.format(" %" + longestK + "s   =>   %-20s\n", " ", eqs.get(i));
                }
            }
        }
        return ret;
    }

    private int calcLongestKey() {
        int longest = -1;
        for(String key : keySet()) {
            if(key.length() > longest) {
                longest = key.length();
            }
        }
        if(TS_SYM_HEADER.length() > longest) {
            longest = TS_SYM_HEADER.length();
        }
        return longest;
    }

    private int calcLongestValue() {
        int longest = -1;
        for(List<ParsedEquation> peqs : values()) {
            for(ParsedEquation peq : peqs) {
                int len = peq.toString().length();
                if(len > longest) {
                    longest = len;
                }
            }
        }
        if(TS_EQ_HEADER.length() > longest) {
            longest = TS_EQ_HEADER.length();
        }
        return longest;
    }

}


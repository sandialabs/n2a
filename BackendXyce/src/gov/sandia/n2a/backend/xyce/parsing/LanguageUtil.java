/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.parsing;

import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.Annotation;
import gov.sandia.n2a.language.LanguageException;
import gov.sandia.n2a.language.parse.ASTNodeBase;

import java.util.List;
import java.util.NavigableSet;
import java.util.Set;

public class LanguageUtil {
    public static final String $TIME = "$t";
    public static final String $INDEX = "$index";
    public static final String $COORDS = "$xyz";
    public static final String $N = "$n";
    public static final String $PCONNECT = "$p";
    public static final String $CARD = "$card";    // connection cardinality
    public static final String $REFPOP = "$ref";

    public static final String ANN_INIT = "$init";
    public static final String ANN_SELECT = "where";

    public static boolean hasAnnotation(EquationEntry eq, String annName) 
    {
        if (eq.conditional == null) {
           return false;
        }
        return eq.conditional.getSymbols().contains(annName);
    }
    
    public static boolean isInitEq(EquationEntry eq)
    {
        return hasAnnotation(eq, ANN_INIT);
    }

    public static boolean isInstanceSpecific(EquationEntry eq)
    {
        return hasAnnotation(eq, ANN_SELECT) || isInstanceDependent(eq);
    }

    public static boolean isInstanceDependent(EquationEntry eq)
    {
        Set<String> symbols = eq.expression.getSymbols();
        // TODO - better way to specify that anything involving a distribution is instance-specific?
        if (symbols.contains("uniform") ||
                symbols.contains("gaussian") ||
                symbols.contains("lognormal")) {
                return true;
        }
        for (String symbol : symbols) {
            if (symbol.contains($INDEX) ||
                symbol.contains($COORDS) ) {
                return true;
            }
        }
        return false;
    }

    public static Annotation getSelectionAnn(EquationEntry eq)
    {
        if (hasAnnotation(eq, ANN_SELECT)) {
            return new Annotation(eq.conditional);
        }
        return null;
    }

    public static boolean isSpecialVar(String var) {
        return var.contains("$");
    }

    // used when there should only be only one PE for a particular variable name
    public static EquationEntry getSinglePE(EquationSet eqSet, String varname, boolean required)
            throws LanguageException
    {
        Variable var = eqSet.find(new Variable(varname));
        if (var == null) {
            if (required) {
                throw new LanguageException(varname + " not specified");
            } else {
                return null;
            }
        }
        NavigableSet<EquationEntry> eqs = var.equations;
        if (required && (eqs == null || eqs.size()==0)) {
            throw new LanguageException(varname + " not specified");
        }
        if (eqs == null) {
            return null;
        }
        if (eqs.size()>1) {
            throw new LanguageException("multiple equations are not allowed for " + varname);
        }
        return eqs.first();
    }

    public static EquationEntry getPositionEq(EquationSet eqSet)
            throws LanguageException
    {
        return getSinglePE(eqSet, $COORDS, false);
    }

    public static EquationEntry getConnectionEq(EquationSet eqSet)
            throws LanguageException
    {
        return getSinglePE(eqSet, $PCONNECT, false);
    }

    public static EquationEntry getNEq(EquationSet eqSet)
            throws LanguageException
    {
        return getSinglePE(eqSet, $N, false);
    }

}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.eq;

import gov.sandia.n2a.eqset.PartEquationMap;
import gov.sandia.n2a.parsing.Annotation;
import gov.sandia.n2a.parsing.ParsedEquation;

import java.util.List;
import java.util.Set;

public class EquationLanguageUtil
{
    public static boolean isInitEq(ParsedEquation pe)
    {
        return pe.hasAnnotation(EquationLanguageConstants.ANN_INIT);
    }

    public static boolean isInstanceSpecific(ParsedEquation pe)
    {
        return pe.hasAnnotation(EquationLanguageConstants.ANN_SELECT) || isInstanceDependent(pe);
    }

    public static boolean isInstanceDependent(ParsedEquation pe)
    {
        Set<String> symbols = pe.getTree().getSymbols();
        // TODO - better way to specify that anything involving a distribution is instance-specific?
        if (symbols.contains("uniform") ||
                symbols.contains("gaussian") ||
                symbols.contains("lognormal")) {
                return true;
        }
        for (String symbol : symbols) {
            if (symbol.contains(EquationLanguageConstants.$INDEX) ||
                symbol.contains(EquationLanguageConstants.$COORDS) ) {
                return true;
            }
        }
        return false;
    }

    public static Annotation getSelectionAnn(ParsedEquation pe)
    {
        return pe.getAnnotation(EquationLanguageConstants.ANN_SELECT);
    }

    public static boolean isSpecialVar(String var) {
        return var.contains("$");
    }

    // used when there should only be only one PE for a particular variable name
    public static ParsedEquation getSinglePE(PartEquationMap pem, String varname, boolean required)
            throws N2ALanguageException
    {
        List<ParsedEquation> pes = pem.get(varname);
        if (required && (pes == null || pes.size()==0)) {
            throw new N2ALanguageException(varname + " not specified");
        }
        if (pes == null) {
            return null;
        }
        if (pes.size()>1) {
            throw new N2ALanguageException("multiple equations are not allowed for " + varname);
        }
        return pes.get(0);
    }

    public static ParsedEquation getPositionEq(PartEquationMap pem)
            throws N2ALanguageException
    {
        return getSinglePE(pem, EquationLanguageConstants.$COORDS, false);
    }

    public static ParsedEquation getConnectionEq(PartEquationMap pem)
            throws N2ALanguageException
    {
        return getSinglePE(pem, EquationLanguageConstants.$PCONNECT, false);
    }

    public static ParsedEquation getNEq(PartEquationMap pem)
            throws N2ALanguageException
    {
        return getSinglePE(pem, EquationLanguageConstants.$N, false);
    }
}

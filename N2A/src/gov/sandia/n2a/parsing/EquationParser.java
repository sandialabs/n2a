/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.parsing;

import gov.sandia.n2a.parsing.gen.ASTNodeBase;
import gov.sandia.n2a.parsing.gen.ExpressionParser;
import gov.sandia.n2a.parsing.gen.ParseException;

import java.util.ArrayList;
import java.util.List;

public class EquationParser {
    public static ParsedEquation parse(String line) throws ParseException {
        // Example Equation Syntax:
        //   a = x + cos(2)  @anno1 = x + 2 - 3  @what = func1() - func2()

        String[] parts = line.split("@");
        String eqPart = parts[0].trim();
        ASTNodeBase eqRootNode = ExpressionParser.parse(eqPart);
        List<Annotation> annos = new ArrayList<Annotation>();
        for(int p = 1; p < parts.length; p++) {
            String annoPart = parts[p].trim();
            ASTNodeBase annoPe = ExpressionParser.parse(annoPart);
            Annotation an = new Annotation(annoPe);
            annos.add(an);
        }
        return new ParsedEquation(line, eqRootNode, annos);
    }
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.network;

import gov.sandia.n2a.backend.xyce.XyceTranslationException;
import gov.sandia.n2a.backend.xyce.parsing.LanguageUtil;
import gov.sandia.n2a.backend.xyce.parsing.XyceASTUtil;
import gov.sandia.n2a.data.Part;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.language.EvaluationContext;

import java.awt.geom.Point2D;
import java.util.Random;

public class CompartmentLayerOrient extends PartSetOrient {

    public CompartmentLayerOrient(
            EquationSet eqns,
            Random rng,
            PartInstanceCounter counter)
        throws NetworkGenerationException, XyceTranslationException
    {
        super(eqns, rng, counter);

        firstSN = counter.getNextSerialNumber();
        createCompartments();
        findInitEqs();
    }

    private void createCompartments() throws NetworkGenerationException, XyceTranslationException
    {
        for (int i=0; i<getN(); i++)
        {
            int SN;
            if (i==0) {
                SN = firstSN;
            } else {
                SN = counter.getNextSerialNumber();
            }
            CompartmentInstance instance = null;
            Point2D.Double position = getPosition(i);
            instance = new CompartmentInstance(this, SN, position.x, position.y);
            instances.add(instance);
            allSNs.add(instance.serialNumber);
        }
    }

    // TODO - really want to switch this to Point3D...
    public Point2D.Double getPosition(int index)
            throws NetworkGenerationException, XyceTranslationException
    {
        EquationEntry eq = null;
        try {
            eq = LanguageUtil.getPositionEq(eqns);
        }
        catch (Exception ex) {
            throw new NetworkGenerationException("unable to determine position of " +
                    eqns.name + " instance " + index, ex.getCause());
        }
        if (eq==null) {
            return getDefaultPosition();
        }
        EvaluationContext context = XyceASTUtil.getEvalContext(eq, eqns);
        Object evalResult = XyceASTUtil.evaluateEq(eq, context, index);
        if (evalResult == null) {
            throw new NetworkGenerationException("Cannot evaluate " + eq);
        }
        if (!evalResult.getClass().isArray())
        {
            throw new NetworkGenerationException("position equation does not evaluate to matrix");
        }
        Object[] resultArray = (Object[]) evalResult;
//        if (resultMatrix.length!=1) {
//            throw new NetworkGenerationException("position equation does not evaluate to vector");
//        }
//        Object[] resultArray = (Object[]) resultMatrix[0];
        if (resultArray.length<1 || resultArray.length>3) {
            throw new NetworkGenerationException("position equation result has unexpected dimensions");
        }
        for (int i=0; i<resultArray.length; i++) {
            if ( !(resultArray[i] instanceof Number) ) {
                throw new NetworkGenerationException("position equation does not produce numeric values");
            }
        }
        Double xPosition = ((Number)resultArray[0]).doubleValue();
        Double yPosition = resultArray.length>1 ? ((Number)resultArray[1]).doubleValue() : 0.0;

        return new Point2D.Double(xPosition,yPosition);
    }

    private Point2D.Double getDefaultPosition()
    {
        return new Point2D.Double(0.0, 0.0);
    }

    public int getFirstSN() {
        return firstSN;
    }
}

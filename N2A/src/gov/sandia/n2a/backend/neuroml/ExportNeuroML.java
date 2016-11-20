/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.neuroml;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.plugins.extpoints.Exporter;

import java.io.File;

import javax.swing.JComponent;
import javax.swing.JFileChooser;

public class ExportNeuroML implements Exporter
{
    @Override
    public String getName ()
    {
        return "NeuroML";
    }

    @Override
    public JComponent getAccessory (JFileChooser fc)
    {
        return null;
    }

    @Override
    public void export (MNode source, File destination, JComponent accessory)
    {
        System.out.println ("imagine a NeuroML export to: " + destination);
    }

    /*
    private void translate(PartX part)
    {
        // TODO
        writer.startElement(sb, "ComponentType", "name="+part.getName());
        // TODO - metadata

        // TODO which elements are required?
        // Parameters - if I can get away without using these, it would simplify my life greatly
        // Behavior - key thing to focus on now; this is where we translate equations

        writer.startElement(sb, "Behavior");
        // TODO - resolve ASAP whether to use old PE or Fred's EquationSet or something else!!
        PartEquationMap pem = EquationAssembler.getAssembledPartEquations(part);
        for(String var : pem.keySet())
        {
            for (ParsedEquation pe : pem.get(var))
            {
                translate(pe);
            }
        }
        writer.endElement(sb, "Behavior");

        writer.endElement(sb, "ComponentType");
    }

    private void translate(ParsedEquation pe)
    {
        String varname = pe.getVarName();
        String RHS = pe.getSource().substring(pe.getSource().indexOf("=")+1);

        // handle diff eqs first
        int diffOrder = pe.order;
        if (diffOrder > 1)
        {
            System.out.println("Support for higher order differential equations not implemented yet (" + pe + ")");
        }
        if (diffOrder == 1)
        {
            translateDiffEq(varname, RHS);
        }

        // not a diff eq; could be
        //    initial condition
        //    constant
        //    variable definition
        //    input... special case of variable definition
        //    other possibilities?
        else if (EquationLanguageUtil.isInitEq(pe))
        {
            translateIC(varname, RHS);
        }
        else
        {
            // For now, just assume this is an algebraic behavior equation
            writer.startElement(sb, "DerivedVariable", "variable="+varname, "value="+RHS, true);
        }
     }

    private void translateIC(String varname, String RHS)
    {
        writer.startElement(sb, "OnStart");
        writer.startElement(sb, "StateAssignment", "variable="+varname, "value="+RHS, true);
        writer.endElement(sb, "OnStart");
    }

    private void translateDiffEq(String varname, String RHS)
    {
        // TODO - do I assume every diff eq is a StateVariable also??
        // TODO - does RHS need to be MathML?
        writer.startElement(sb, "TimeDerivative", "variable="+varname, "value="+RHS, true);
    }
    */
}

/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.exporters;

import gov.sandia.umf.platform.db.MNode;
import gov.sandia.umf.platform.plugins.extpoints.Exporter;
import gov.sandia.umf.platform.ui.export.ExportParameters;
import gov.sandia.umf.platform.ui.export.ExportParametersPanel;

import java.io.IOException;

import javax.swing.ImageIcon;

public class LEMSExporter implements Exporter
{

    @Override
    public String getName() {
        return null;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public ImageIcon getIcon() {
        return null;
    }

    @Override
    public void export (MNode source, ExportParameters params) throws IOException
    {
    }

    @Override
    public ExportParametersPanel getParametersPanel() {
        return null;
    }/*
    StringBuilder sb;
    XMLWriter writer;

    public LEMSExporter()
    {
        sb = new StringBuilder();
        writer = new XMLWriter();
    }

    public String getName() {
        return "LEMS";
    }

    public String getDescription() {
        return "Converts an N2A model into an XML file.";
    }

    public ImageIcon getIcon() {
        return ImageUtil.getImage("lems.gif");
    }

    public ExportParametersPanel getParametersPanel() {
        return new LEMSExporterParameterPanel();
    }

    // For testing?
    public void export(BeanBase source) throws IOException
    {
        translate(source);
        writeToFile(getTarget(source));
    }

    @Override
    public void export(BeanBase source, ExportParameters params) throws IOException {
        LEMSExporterParameters params1 = (LEMSExporterParameters) params;
        translate(source);
        writeToFile(params1.path);
    }

    // For testing?
    public void export(BeanBase source, File target) throws IOException
    {
        translate(source);
        writeToFile(target);
    }

    // For testing?
    private File getTarget(BeanBase source)
    {
        String filename = source.getBeanTitle() + ".nml";
        // TODO - what directory??
        // use part/model name for filename base, lems extension
        File result = new File(filename);
        return result;
    }

    private void translate(BeanBase source)
    {
        writer.startElement(sb, "Lems");
        if (source instanceof PartX) {
            translate((PartX)source);
        } else {
            System.out.println("Export of " + source.getClass().toString() + " not yet implemented!");
        }
        writer.endElement(sb, "Lems");
    }

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

    // TODO - resolve SOON whether to use old PE or Fred's EquationSet or something else!!
    // TODO - create some enums and code to determine what 'type' of equation an equation is
    //   borrow from (and modify) SymbolDefFactory code
    //   need to understand what types LEMS cares about
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

	private void writeToFile(File file) throws IOException
    {
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(file));
        osw.write(sb.toString());
        osw.close();
   }


    ///////////////////
    // INNER CLASSES //
    ///////////////////

    private class LEMSExporterParameters extends ExportParameters {
        private File path;
        public LEMSExporterParameters(File p) {
            path = p;
        }
    }

    private class LEMSExporterParameterPanel extends ExportParametersPanel {
        private FileSelectionPanel pnl;

        public LEMSExporterParameterPanel() {
            Lay.BLtg(this,
                "N", Lay.BL(
                    "W", Lay.lb("Destination: "),
                    "C", pnl = new FileSelectionPanel(
                        null, "Choose Destination",
                        null,
                        Type.SAVE, JFileChooser.FILES_ONLY)
                )
            );
            CommonFileChooser chooser = pnl.getChooser();
            FilterBuilder builder = new FilterBuilder(chooser, true);
            builder.append("LEMS File (*.nml)", "nml");
        }

        @Override
        public String getValidation() {
            if(pnl.getFile() == null) {
                pnl.focus();
                return "Please choose a destination file.";
            }
            return null;
        }

        @Override
        public ExportParameters getParams() {
            File file = pnl.getFile();
            return new LEMSExporterParameters(file);
        }
    }*/
}

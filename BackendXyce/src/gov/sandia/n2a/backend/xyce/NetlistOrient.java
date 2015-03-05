/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce;

import gov.sandia.n2a.backend.xyce.network.ModelInstanceOrient;
import gov.sandia.n2a.backend.xyce.network.NetworkGenerationException;
import gov.sandia.n2a.backend.xyce.network.NetworkOrient;
import gov.sandia.n2a.backend.xyce.network.PartInstance;
import gov.sandia.n2a.backend.xyce.network.PartSetInterface;
import gov.sandia.n2a.backend.xyce.parsing.LanguageUtil;
import gov.sandia.n2a.backend.xyce.parsing.XyceRHSTranslator;
import gov.sandia.n2a.backend.xyce.symbol.SymbolDef;
import gov.sandia.n2a.backend.xyce.symbol.SymbolManager;
import gov.sandia.n2a.backend.xyce.symbol.XyceDeviceSymbolDef;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.language.gen.ASTNodeBase;
import gov.sandia.n2a.language.gen.ASTVarNode;
import gov.sandia.n2a.language.gen.ParseException;
import gov.sandia.umf.platform.ensemble.params.groupset.ParameterSpecGroupSet;
import gov.sandia.umf.platform.plugins.Run;
import gov.sandia.umf.platform.plugins.RunOrient;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetlistOrient {

    private ModelInstanceOrient mi;
    private NetworkOrient net;
    private Run runParams;
    private XyceSimulation simParams;
    private Map<String, XyceDeviceSymbolDef> deviceSymbolDefs;
    private SymbolManager symMgr;
    private Writer writer;

    public NetlistOrient(ModelInstanceOrient mi, RunOrient r, XyceSimulation sim, Writer w)
            throws Exception
    {
        this.mi = mi;
        symMgr = new SymbolManager();
        runParams = r;
        simParams = sim;
        net = mi.getNetwork();
        deviceSymbolDefs = new HashMap<String, XyceDeviceSymbolDef>();
        writer = w;
        generateNetlist();
    }

    public void generateNetlist()
            throws Exception
    {
        appendRunControl();
        appendNetEquations();
        appendOutputs();
        appendEnd();
    }

    /*
     * Assembles netlist header, RNG seed, and simulation run time
     */
    private void appendRunControl() throws IOException
    {
        writer.append(runParams.getName() + "\n");
        writer.append("\n");
        writer.append("* seed:  " + simParams.getSeed() + "\n");
        writer.append(".tran 0 " + runParams.getSimDuration() + "\n");
    }

    /*
     * This is where the bulk of the equation translation to Xyce language starts
     */
    private void appendNetEquations()
            throws XyceTranslationException, NetworkGenerationException, IOException
    {
        handleParamSpecs();
        for (PartSetInterface pset : net.getParts())
        {
            setupXyceDevice(pset);
            writeDefinitions(pset);
         }
    }

    private void handleParamSpecs() 
    {
        ParameterSpecGroupSet groups = simParams.getParamsToHandle();
        if (groups==null) {
            return;
        }
        // not currently supported
        System.out.println("attempt to have Xyce simulator handle some parameter variation; not supported!");
/*        for (ParameterSpecGroup group : groups) {
            if (group == groups.getDefaultValueGroup()) {
                continue;
            }
            for (Object key : group.keySet()) {
                // key should be strings like "model.layers.hh.V"
                try {
                    ParameterKeyPath keyPath = (ParameterKeyPath) key;
                    String partName = (String) keyPath.get(2);
                    String varName = (String) keyPath.get(3);
                    PartSetInterface pSet = net.getPartSet(partName);
                    PartInstance pi = pSet.getFirstInstance();
                    EquationEntry eq = pSet.getEquation(varName, pi, false);
                    StepParameterSpecification spec = (StepParameterSpecification) group.get(key);
                    int n = group.getRunCount();
                    SymbolDef sd = new StepParamSymbolDef(eq, pi, spec, n);
                    symMgr.add(eq, sd);
                } catch (Exception e) {
                    throw new RuntimeException("NetlistOrient cannot handle '" + key.toString() + 
                            "' with specification '" + group.get(key).getShortName());
                }
            }
        }
*/    }

    private XyceDeviceSymbolDef setupXyceDevice(PartSetInterface pSet)
            throws XyceTranslationException
    {
        XyceDeviceSymbolDef result = null;
        if (XyceDeviceSymbolDef.isXyceDevice(pSet))
        {
            result = new XyceDeviceSymbolDef(pSet);
            deviceSymbolDefs.put(pSet.getName(), result);
        }
        return result;
    }

    private void writeDefinitions(PartSetInterface pSet)
            throws XyceTranslationException, NetworkGenerationException, IOException
    {
        if (deviceSymbolDefs.containsKey(pSet.getName())) {
            processDeviceInstances(deviceSymbolDefs.get(pSet.getName()), pSet);
        }
        // have to process non-device equations - parameters, etc. - whether this is a device or not
        processEquationInstances(pSet);
    }

    private void processDeviceInstances(XyceDeviceSymbolDef deviceSymbol, PartSetInterface pSet)
            throws XyceTranslationException, IOException
    {
        for (PartInstance pi : pSet.getInstances()) {
            writer.append(deviceSymbol.getDefinition(symMgr, pi));
        }
    }

    protected void processEquationInstances(PartSetInterface pset)
            throws NetworkGenerationException, XyceTranslationException, IOException
    {
        writer.append("\n* initial condition equations for " + pset.getName() + "\n");
        for (EquationEntry eq : pset.getInitEqs())
        {
            for (PartInstance pi : pset.getInstances())
            {
                writeDefinition(pset, eq, pi);
            }
        }
        writer.append("\n* remaining equations for " + pset.getName() + "\n");
        for (PartInstance pi : pset.getInstances())
        {
            Map<String, EquationEntry> eqs = pset.getEqns(pi, false);
            for (EquationEntry eq : eqs.values())
            {
                writeDefinition(pset, eq, pi);
            }
        }
    }

    private void writeDefinition(PartSetInterface pset, EquationEntry eq, PartInstance pi)
            throws XyceTranslationException, NetworkGenerationException, IOException
    {
        if (ignore(eq)) {
            return;
        }
        SymbolDef def = symMgr.getSymbolDef(pset, eq);
        writer.append(def.getDefinition(symMgr, pi));
    }


    private boolean ignore(EquationEntry eq)
    {
        // misc reasons to ignore an equation when writing definitions:
        // don't need to write out special variables like $n, $xyz, etc.
        // don't need to write out equations defining dynamics already defined by a device
        if (eq.variable.name.contains("$")) {
            return true;
        }
        if (XyceDeviceSymbolDef.ignoreEquation(eq)) {
            return true;
        }
        return false;
    }

    private void appendOutputs()
            throws Exception
    {
        writer.append("\n* outputs\n");
        List<EquationEntry> eqs = null;
        try {
            eqs = mi.getOutputEqs();
        } catch (ParseException e) {
            throw new XyceTranslationException("could not parse output equations");
        }
        if (eqs.size() == 0) {
            appendDefaultOutput();
            return;
        }
        boolean first = true;
        for (EquationEntry eq : eqs) {
            if (first) {
              writer.append(".print tran ");
              first = false;
            }
                writer.append(getOutputVar(eq));
        }
        writer.append("\n");
    }

    private void appendDefaultOutput() throws IOException {
        writer.append(".print tran v(*)\n\n");
    }

    private String getOutputVar(EquationEntry eq)
            throws XyceTranslationException
    {
        // Now expecting these 'equations' to be of the form:
        //     HHmod.V[0]     or A.V[0]
        // where
        //     HHmod is the layer (or bridge) name or A is the part alias
        //     V is the variable of interest
        //     0 is the instance index - optional
        // But could also be a single top-level variable name, no namespace, no index
        // TODO:
        //     allow arbitrary name of nested part names, e.g. A.B.C.HHmod.V[1]
        //         manage through NetworkOrient, or just SymbolManager?
        // TODO - rework this whole thing; eq.expression may not work because it's supposed to be RHS of an equation
        String fullName = "";
        int index = -1;
        ASTNodeBase topNode = eq.expression;
        if (topNode.getCount()>0) {
            // expect left child to be the part before the brackets, and right child the part inside the brackets
            try {
                fullName = ((ASTVarNode) eq.expression.getChild(0)).getVariableNameWithOrder();
                Number ind = (Number) (eq.expression.getChild(1).getChild(0)).getValue();
                index = ind.intValue();
            } catch (Exception e) {
                throw new XyceTranslationException("could not parse output variable " + eq.toString());
            }
        }
        else if (topNode instanceof ASTVarNode) {
            fullName = ((ASTVarNode) topNode).getVariableNameWithOrder();
            // ignore output variable corresponding to sim time; Xyce outputs that by default
            if (fullName.equals(LanguageUtil.$TIME)) {
                return "";
            }
        }
        else {
            throw new XyceTranslationException("could not parse output variable " + eq.toString());
        }
        String[] names = fullName.split("[.]");
        if (names.length != 2) {
            throw new XyceTranslationException("output variable name not in expected format; " + eq.toString() +
                " should have 2 parts, has " + names.length);
        }
        PartSetInterface set = net.getPartSet(names[0]);
        String varname;
        if (set == null) {
            set = net.getPartSet("Model");
            varname = fullName;
        }
        else {
            varname = names[1];
        }
        if (index==-1) {
            StringBuilder sb = new StringBuilder();
            for (int i=0; i<set.getInstances().size(); i++) {
                sb.append(getOutputInstance(set, varname, i));
            }
            return sb.toString();
        }
        else {
            return getOutputInstance(set, varname, index);
        }
    }

    private String getOutputInstance(PartSetInterface set, String varname, int index) {
        PartInstance pi = set.getInstances().get(index);
        if (deviceSymbolDefs.containsKey(set.getName())) {
            XyceDeviceSymbolDef def = deviceSymbolDefs.get(set.getName());
            if (def.isInternalVariable(varname)) {
                return def.getOutputString(varname, pi);
            }
            if (def.isNodeVariable(varname)) {
                return Xyceisms.referenceStateVar(varname, pi.serialNumber);
            }
        }
        StringBuilder sb = new StringBuilder();
        XyceRHSTranslator xlator = new XyceRHSTranslator(symMgr, pi, null, false);
        // since output variable might actually be a function, need to treat it
        // like a Xyce expression (enclose in {}) - redundant for variables, but
        // won't hurt
        sb.append("{" + xlator.change(varname) + "} ");
        return sb.toString();
    }

    public void appendEnd() throws IOException {
        writer.append(".end\n");
    }
}

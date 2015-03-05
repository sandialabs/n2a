/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.symbol;

import gov.sandia.n2a.backend.xyce.XyceTranslationException;
import gov.sandia.n2a.backend.xyce.Xyceisms;
import gov.sandia.n2a.backend.xyce.device.XyceDevice;
import gov.sandia.n2a.backend.xyce.device.XyceDeviceFactory;
import gov.sandia.n2a.backend.xyce.network.ConnectionInstance;
import gov.sandia.n2a.backend.xyce.network.PartInstance;
import gov.sandia.n2a.backend.xyce.network.PartSetInterface;
import gov.sandia.n2a.backend.xyce.parsing.LanguageUtil;
import gov.sandia.n2a.backend.xyce.parsing.XyceASTUtil;
import gov.sandia.n2a.backend.xyce.parsing.XyceRHSTranslator;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.language.gen.ASTNodeBase;
import gov.sandia.n2a.language.gen.ASTRenderingContext;
import gov.sandia.n2a.language.gen.ExpressionParser;
import gov.sandia.n2a.language.gen.ParseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


// Create a XyceDeviceSymbolDef from equations and metadata.
// The nodes involved in a device (other than ground) are typically state variables
// Need correspondence between varnames that other Parts might reference
// and nodes that this device uses (device references state vars, doesn't
// define them as capacitors as when using B device).
// Note that some N2A equations correspond to equations defined internally to the Xyce code;
// these equations should have xyce.nodeIndex metadata.  Changing these equations in N2A
// will NOT affect the simulation dynamics!
// There is a method provided to "inject current" into a Xyce device that does affect
// the internal state variables - equations doing this should get the xyce.inputIndex tag.
// For internal dynamics like v' = m*(V-E)/C_m +I and external input I = I_inj,
// the metadata would be xyce.nodeIndex=1 for the first and xyce.inputIndex=1 for the second
// (assuming v corresponds to node 1 for this device).
// Also need correspondence between parameter names and equation names
// XyceDevice object needs:
//        device type and level
//        name to use for device instance - based on partname and SN
//        names to use for nodes - based on varname and SN
//        map of device params to expressions from equations
//    needs to be able to take SN and write out appropriate instance-specific netlist line -

public class XyceDeviceSymbolDef implements SymbolDef {
    private static final String DEVICE_TAG = "backend.xyce.device";
    private static final String PARAM_TAG = "xyce.param";
    public static final String NODE_TAG = "xyce.nodeIndex";
    public static final String INPUT_TAG = "xyce.inputIndex";
    private static final String IVAR_TAG = "xyce.internalVar";
    public static final String IGNORE_TAG = "xyce.ignore";

    private PartSetInterface partSet;
    private EquationSet eqSet;
    XyceDevice device;

    // A device definition depends on ordered list of named nodes; these lists equate node index with 
    // those names, which are created from a combination of the variable name used in the relevant equation
    // and the appropriate serial number for a specific part instance
    // Node index appears in the xyce hint : this code assumes the first hint index is 1
    // so that the variable associated with xyce.nodeIndex=1 maps to varnames[0]
    List<String> varnames;
    
    // Allows for current injections on the device nodes; map below uses indices of those nodes as keys
    // and the relevant user equations as the values (typically expect only one such input, but no reason to exclude
    // more; might possibly have inputs to both ends of a synapse for testing perhaps??)
    Map<Integer, EquationEntry> inputs;

    // Map device parameter names to equation variable names - note that the 'variable' in this case actually
    // must be something that evaluates to a constant
    private Map<String, String> paramList;

    private boolean modelWritten = false;
    private Map<String, String> ivars;        // internal device variables user may want to trace

    public XyceDeviceSymbolDef(PartSetInterface partSet)
            throws XyceTranslationException
    {
        eqSet = partSet.getEqSet();
        this.partSet = partSet;
        paramList = new HashMap<String, String>();
        inputs = new HashMap<Integer, EquationEntry>();
        ivars = new HashMap<String, String>();
        setXyceDeviceType();
    }

    @Override
    public String getDefinition(SymbolManager symMgr, PartInstance pi)
    {
        StringBuilder result = new StringBuilder();
        String modelName = partSet.getName() + "Model";
        if (!modelWritten) {
            result.append(getModelDefinition(modelName, pi, symMgr));
            modelWritten = true;
        }
        result.append(Xyceisms.defineYDeviceWithModel(device.getDeviceTypeName(),
                partSet.getName(), getNodeNames(pi), pi.serialNumber, modelName,
                getInstanceParams(symMgr, pi)));
        result.append(getInputDefs(symMgr, pi));
        return result.toString();
    }

    @Override
    public String getReference(int SN)
    {
        // This method should not be used
        // other parts may reference state variables IN this device
        // through StateVarSymbolDef
        System.out.println("Warning - call to XyceDeviceSybolDef::getReference()");
        return "";
    }

    public static boolean isXyceDevice(PartSetInterface pSet)
    {
        return pSet.getEqSet().metadata.keySet().contains(DEVICE_TAG);
    }

    public static boolean ignoreEquation(EquationEntry eq)
    {
        // These equations don't have to be translated like others;
        // device already implements them or doesn't need them.
        // Parameter and input equations DO have to be processed/understood elsewhere
        if (eq.metadata != null) {
            for (String metaKey : eq.metadata.keySet()) {
                if (metaKey.equals(IGNORE_TAG) || metaKey.equals(NODE_TAG) || 
                    metaKey.equals(IVAR_TAG)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getModelDefinition(String modelName, PartInstance pi, SymbolManager symMgr)
    {
        // set up map of xyce device parameter name to "expression"
        Map<String,String> params = new HashMap<String,String>();
        for (String xyceParamName : paramList.keySet())
        {
            XyceRHSTranslator xlator = new XyceRHSTranslator(symMgr, pi, new ArrayList<String>(), false);
            String translatedName = xlator.change(paramList.get(xyceParamName));
            params.put(xyceParamName, translatedName);
        }
        return Xyceisms.defineModel(device.getDeviceTypeName(), device.getDeviceLevel(), modelName, params);
    }

    private String getInputDefs(SymbolManager symMgr, PartInstance pi)
    {
        StringBuilder result = new StringBuilder();
        for (Integer inputNode : inputs.keySet()) {
            // Write a B device definition to update the node associated with the input
            // For neurons, this assumes we want to add a current of the form I/C_m
            // where the user specifies I and C_m is known.
            // Not at all clear what it means to add current to synapse, which doesn't
            // have a capacitance of its own, but this will allow adding a user-defined
            // current I to a synapse node.
            String inputEq = inputs.get(inputNode).variable.name;
            if (device.getDeviceTypeName().equals("neuron")) {
                inputEq +=  "/" + paramList.get(device.getCapacitanceVar());
            }
            String eqName = Xyceisms.referenceVariable(inputEq, pi.serialNumber);
            String instanceVarName = getInstanceVarname(varnames.get(inputNode), pi);
            XyceRHSTranslator xlator = new XyceRHSTranslator(symMgr, pi, new ArrayList<String>(), false);
            try {
                ASTNodeBase ast = ExpressionParser.parse(inputEq);
                String translatedEq = XyceASTUtil.getReadableShort(ast, xlator);
                result.append(Xyceisms.updateDiffEq(eqName, instanceVarName, translatedEq));
            } catch (ParseException e) {
                // Auto-generated catch block
                // just don't append this input equation if we can't parse
                e.printStackTrace();
            }
        }
        return result.toString();
    }

    private List<String> getNodeNames(PartInstance pi)
    {
        // varnames determined previously are placeholders; need to use
        // instance-specific versions
        List<String> nodenames = new ArrayList<String>();
        for (int i=0; i<varnames.size(); i++)
        {
            if (varnames.get(i).equals("0")) {
                nodenames.add(i,"0");
            }
            else {
            	// TODO - use Variable or EquationEntry here to get the right instance name
                nodenames.add(i, getInstanceVarname(varnames.get(i), pi));
            }
        }
        return nodenames;
    }

    public String getInstanceVarname(String eqVarName, PartInstance pi)
    {
        String result = null;
        if (pi instanceof ConnectionInstance)
        {
            ConnectionInstance ci = (ConnectionInstance) pi;
            if (eqVarName.startsWith(XyceRHSTranslator.REFPRE)) {
                result = eqVarName.substring(XyceRHSTranslator.REFPRE.length()) +
                        "_" + ci.A.serialNumber;
            }
            else if (eqVarName.startsWith(XyceRHSTranslator.REFPOST)) {
                result = eqVarName.substring(XyceRHSTranslator.REFPOST.length()) +
                        "_" + ci.B.serialNumber;
            }
            else {    // symbol is defined in connection
                result = Xyceisms.referenceVariable(eqVarName, pi.serialNumber);
            }
        } else {    // assume this is a compartment and symbol is defined here
            result = Xyceisms.referenceVariable(eqVarName, pi.serialNumber);
        }
        return result;
    }

    private Map<String, String> getInstanceParams(SymbolManager symMgr, PartInstance pi)
    {
        Map<String,String> params = new HashMap<String,String>();
        for (String xyceParamName : paramList.keySet())
        {
            // To avoid unnecessarily rewriting parameter values already defined in the default model,
            // determine whether the values are actually instance-specific and only redefine them if so
            if (isInstanceSpecific(paramList.get(xyceParamName), pi))
            {
                XyceRHSTranslator xlator = new XyceRHSTranslator(symMgr, pi, new ArrayList<String>(), false);
                String translatedName = xlator.change(paramList.get(xyceParamName));
                params.put(xyceParamName, translatedName);
            }
        }
        return params;
    }

    // intended to be used just for parameter values; equation order assumed to be 0
    private boolean isInstanceSpecific(String varname, PartInstance pi)
    {
        EquationEntry eq = null;
        try {
            Variable var = eqSet.find(new Variable(varname, 0));
            eq = pi.getPartSet().getEquation(var, pi, false);
        }
        catch (Exception ex) {
            // this probably will have caused problems elsewhere;
            // try to stumble along anyway without processing this parameter
            return false;
        }
        return LanguageUtil.isInstanceSpecific(eq);
    }

    private String getDeviceName()
    {
        Map<String,String> terms = eqSet.metadata;
        if (terms != null && !terms.isEmpty()) {
            for (String term : terms.keySet()) {
                if (term.equals(DEVICE_TAG)) {
                    return terms.get(term);
                }
            }
        }
        return null;
    }

    private void setXyceDeviceType()
            throws XyceTranslationException
    {
        device = null;
        String deviceName = getDeviceName();
        if (XyceDeviceFactory.isSupported(deviceName)) {
            device = XyceDeviceFactory.getDevice(deviceName);
        }
        else {
            throw new XyceTranslationException(
                    "unrecognized device name " + deviceName +
                    " for " + partSet.getName());
        }
        if (device != null)
        {
            setupVarList();
            checkEquations();
        }
    }

    private void setupVarList()
    {
        // This really just sets up the appropriate size of the list for this device,
        // and sets any of the ground nodes to "0";
        // most of the entries will be placeholder strings to be filled in by checkNodeAssociation
        varnames = device.getDefaultNodes();
    }

    private void checkEquations() throws XyceTranslationException
    {
        // This checks for equations with 'xyce' annotations
        // and saves the appropriate information
        for (Variable v : eqSet.variables)
        {
            for (EquationEntry eq : v.equations)
            {
                if (eq.metadata == null) {
                    continue;
                }
                for (String metaKey : eq.metadata.keySet()) {
                    String value = eq.metadata.get(metaKey).replace("\"", "");
                    if (metaKey.equals(PARAM_TAG)) {
                        checkParamAssociation(eq, value);
                    }
                    else if (metaKey.equals(NODE_TAG)) {
                        checkNodeAssociation(eq, value);
                    }
                    else if (metaKey.equals(IVAR_TAG)) {
                        checkIVarAssociation(eq, value);
                    }
                    else if (metaKey.equals(INPUT_TAG)) {
                        inputs.put(Integer.parseInt(value)-1, eq);
                    }
                }
            }
        }
    }

    private void checkParamAssociation(EquationEntry eq, String xyceParamName)
            throws XyceTranslationException
    {
        String eqParamName = eq.variable.name;
        if (device.isAllowedModelParameter(xyceParamName)) {
            paramList.put(xyceParamName.toUpperCase(), eqParamName);
        } else {
            throw new XyceTranslationException("unrecognized parameter " + xyceParamName +
                    " for device " + device.getDeviceTypeName() + device.getDeviceLevel());
        }
    }

    private void checkNodeAssociation(EquationEntry eq, String metaValue) throws XyceTranslationException
    {
        // is RHS of node index hint a number?
        // is that number within the range of expected device nodes?
        // assumes node indices in xyce hints start at 1
        int index = Integer.parseInt(metaValue) - 1;
        if (device.isValidNodeIndex(index)) {
            varnames.set(index, eq.variable.name);
        } else {
            throw new XyceTranslationException("unrecognized/illegal node index " + index +
                    " for device " + device.getDeviceTypeName() + device.getDeviceLevel() +
                    " variable " + eq.variable.name);
        }
    }

    void checkIVarAssociation(EquationEntry eq, String xyceIVarName)
            throws XyceTranslationException
    {
        // IVAR_TAG - designates 'internal variables' in Xyce devices,
        // like 'u' in level 7 neuron
        // Main purpose would be in case user wants to output those vars
        if (device.isInternalVariable(xyceIVarName)) {
            ivars.put(eq.variable.name, xyceIVarName);
        } else {
            throw new XyceTranslationException("unrecognized internal variable " + xyceIVarName +
                    " for device " + device.getDeviceTypeName() + device.getDeviceLevel());
        }
    }

    public boolean isNodeVariable(String varname)
    {
        return varnames.contains(varname);
    }

    public boolean isInternalVariable(String varname)
    {
        return ivars.containsKey(varname);
    }

    // for internal variables
    public String getOutputString(String varname, PartInstance pi)
    {
        // example n(y%synapse%syn_w), where 'w' is the varname passed in
        return "n(y%" + device.getDeviceTypeName() + "%" +
                pi.getPartSet().getName() + "-" + pi.serialNumber +
                "_" + ivars.get(varname) + ") ";
    }
}

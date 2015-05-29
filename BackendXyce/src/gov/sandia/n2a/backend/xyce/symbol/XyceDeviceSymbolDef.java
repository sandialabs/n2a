/*
Copyright 2013 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a.backend.xyce.symbol;

import gov.sandia.n2a.backend.internal.Connection;
import gov.sandia.n2a.backend.xyce.XyceBackendData;
import gov.sandia.n2a.backend.xyce.Xyceisms;
import gov.sandia.n2a.backend.xyce.device.XyceDevice;
import gov.sandia.n2a.backend.xyce.parsing.XyceASTUtil;
import gov.sandia.n2a.backend.xyce.parsing.XyceRenderer;
import gov.sandia.n2a.eqset.EquationEntry;
import gov.sandia.n2a.eqset.EquationSet;
import gov.sandia.n2a.eqset.Variable;
import gov.sandia.n2a.eqset.VariableReference;
import gov.sandia.n2a.language.EvaluationException;
import gov.sandia.n2a.language.Operator;
import gov.sandia.n2a.language.parse.ParseException;
import gov.sandia.n2a.language.type.Instance;

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

public class XyceDeviceSymbolDef implements SymbolDef
{
    public static final String DEVICE_TAG = "backend.xyce.device";
    public static final String PARAM_TAG  = "backend.xyce.param";
    public static final String NODE_TAG   = "backend.xyce.nodeIndex";
    public static final String INPUT_TAG  = "backend.xyce.inputIndex";
    public static final String IVAR_TAG   = "backend.xyce.internalVar";
    public static final String IGNORE_TAG = "backend.xyce.ignore";

    public EquationSet eqSet;
    public XyceDevice device;

    // A device definition depends on ordered list of named nodes; these lists equate node index with 
    // those names, which are created from a combination of the variable name used in the relevant equation
    // and the appropriate serial number for a specific part instance
    // Node index appears in the xyce hint : this code assumes the first hint index is 1
    // so that the variable associated with xyce.nodeIndex=1 maps to varnames[0]
    public List<EquationEntry> varnames;

    // Allows for current injections on the device nodes; map below uses indices of those nodes as keys
    // and the relevant user equations as the values (typically expect only one such input, but no reason to exclude
    // more; might possibly have inputs to both ends of a synapse for testing perhaps??)
    public Map<Integer,EquationEntry> inputs = new HashMap<Integer,EquationEntry> ();

    // Map device parameter names to part variable names - note that the 'variable' in this case actually
    // must be something that evaluates to a constant
    public Map<String,EquationEntry> paramList = new HashMap<String,EquationEntry> ();

    // Map from part variable to name of xyce device internal variable; used primarily for tracing (printed output)
    public Map<Variable,String> ivars = new HashMap<Variable,String> ();

    public boolean modelWritten;

    public XyceDeviceSymbolDef (EquationSet s) throws EvaluationException
    {
        eqSet = s;

        String deviceName = s.metadata.get (DEVICE_TAG);
        if (deviceName != null) device = XyceDevice.createFor (deviceName);
        if (device == null) throw new EvaluationException ("unrecognized device name " + deviceName + " for " + eqSet.name);

        // This really just sets up the appropriate size of the list for this device,
        // and sets any of the ground nodes to "0". Most of the entries will be
        // placeholders to be filled in below.
        varnames = device.getDefaultNodes ();

        // This checks for equations with 'xyce' annotations
        // and saves the appropriate information
        for (Variable v : eqSet.variables)
        {
            for (EquationEntry eq : v.equations)
            {
                if (eq.metadata == null) continue;
                for (String metaKey : eq.metadata.keySet ())
                {
                    String value = eq.metadata.get (metaKey).replace ("\"", "");
                    if (metaKey.equals (PARAM_TAG))
                    {
                        String eqParamName = eq.variable.name;
                        if (! device.isAllowedModelParameter (value))
                        {
                            throw new EvaluationException ("unrecognized parameter " + value + " for device " + deviceName);
                        }
                        paramList.put (value.toUpperCase (), eq);
                    }
                    else if (metaKey.equals (NODE_TAG ))
                    {
                        // is RHS of node index hint a number?
                        // is that number within the range of expected device nodes?
                        // assumes node indices in xyce hints start at 1
                        int index = Integer.parseInt (value) - 1;
                        if (! device.isValidNodeIndex (index))
                        {
                            throw new EvaluationException("unrecognized/illegal node index " + index + " for device " + deviceName + " variable " + v.name);
                        }
                        varnames.set (index, eq);
                    }
                    else if (metaKey.equals (IVAR_TAG ))
                    {
                        // IVAR_TAG - designates 'internal variables' in Xyce devices,
                        // like 'u' in level 7 neuron
                        // Main purpose would be in case user wants to output those vars
                        if (! device.isInternalVariable (value))
                        {
                            throw new EvaluationException ("unrecognized internal variable " + value + " for device " + deviceName);
                        }
                        ivars.put (v, value);
                    }
                    else if (metaKey.equals (INPUT_TAG))
                    {
                        inputs.put (Integer.parseInt (value) - 1, eq);
                    }
                }
            }
        }
    }

    @Override
    public String getDefinition (Instance pi)
    {
        StringBuilder result = new StringBuilder ();
        String modelName = eqSet.name + "Model";
        if (! modelWritten)
        {
            XyceBackendData bed = (XyceBackendData) eqSet.backendData;
            // set up map of xyce device parameter name to "expression"
            Map<String,String> params = new HashMap<String,String> ();
            for (String xyceParamName : paramList.keySet ())
            {
                XyceRenderer xlator = new XyceRenderer (bed, pi, new ArrayList<String> (), false);
                String translatedName = xlator.change (paramList.get (xyceParamName));
                params.put (xyceParamName, translatedName);
            }
            result.append (Xyceisms.defineModel (device.getDeviceTypeName (), device.getDeviceLevel (), modelName, params));
            modelWritten = true;
        }
        result.append (Xyceisms.defineYDeviceWithModel (device.getDeviceTypeName (),
                                                        eqSet.name,
                                                        getNodeNames (pi),
                                                        pi.hashCode (),
                                                        modelName,
                                                        getInstanceParams (pi)));
        result.append (getInputDefs (pi));
        return result.toString ();
    }

    @Override
    public String getReference (Instance pi)
    {
        // This method should not be used
        // other parts may reference state variables IN this device
        // through StateVarSymbolDef
        System.out.println ("Warning - call to XyceDeviceSybolDef::getReference()");
        return "";
    }

    private String getInputDefs (Instance pi)
    {
        StringBuilder result = new StringBuilder ();
        XyceBackendData bed = (XyceBackendData) eqSet.backendData;
        for (Integer inputNode : inputs.keySet ())
        {
            // Write a B device definition to update the node associated with the input
            // For neurons, this assumes we want to add a current of the form I/C_m
            // where the user specifies I and C_m is known.
            // Not at all clear what it means to add current to synapse, which doesn't
            // have a capacitance of its own, but this will allow adding a user-defined
            // current I to a synapse node.
            String inputEq = inputs.get (inputNode).variable.name;
            if (device.getDeviceTypeName ().equals ("neuron"))
            {
                inputEq +=  "/" + paramList.get(device.getCapacitanceVar());
            }
            String eqName = Xyceisms.referenceVariable (inputEq, pi.hashCode ());
            String instanceVarName = getInstanceVarname (varnames.get (inputNode), pi);
            XyceRenderer xlator = new XyceRenderer (bed, pi, new ArrayList<String> (), false);
            try
            {
                Operator ast = Operator.parse (inputEq);
                String translatedEq = XyceASTUtil.getReadableShort (ast, xlator);
                result.append(Xyceisms.updateDiffEq (eqName, instanceVarName, translatedEq));
            }
            catch (ParseException e)
            {
                // Auto-generated catch block
                // just don't append this input equation if we can't parse
                e.printStackTrace ();
            }
        }
        return result.toString();
    }

    private List<String> getNodeNames (Instance pi)
    {
        // varnames determined previously are placeholders; need to use
        // instance-specific versions
        List<String> nodenames = new ArrayList<String> ();
        for (int i = 0; i < varnames.size (); i++)
        {
            if (varnames.get (i).equals ("0"))  // TODO: compare vars with vars, not strings
            {
                nodenames.add (i, "0");
            }
            else
            {
            	// TODO - use Variable or EquationEntry here to get the right instance name
                nodenames.add(i, getInstanceVarname(varnames.get(i), pi));
            }
        }
        return nodenames;
    }

    public String getInstanceVarname (Variable v, Instance pi)
    {
        // If this symbol refers to a symbol in another part, we don't re-define the
        // variable, we create another diff eq that updates the existing one.
        VariableReference r = v.reference;
        if (! (pi instanceof Connection)  ||  v == r.variable)  // symbol is defined here; no += allowed within same part
        {
            return Xyceisms.referenceVariable (v.name, pi.hashCode ());
        }

        Instance target = (Instance) pi.valuesType[r.index];
        return r.variable.name + "_" + target.hashCode ();
    }

    private Map<String,String> getInstanceParams (Instance pi)
    {
        Map<String,String> params = new HashMap<String,String> ();
        XyceBackendData bed = (XyceBackendData) eqSet.backendData;
        for (String xyceParamName : paramList.keySet ())
        {
            // To avoid unnecessarily rewriting parameter values already defined in the default model,
            // determine whether the values are actually instance-specific and only redefine them if so
            Variable v = paramList.get (xyceParamName);
            if (! v.global)
            {
                XyceRenderer xlator = new XyceRenderer (bed, pi, new ArrayList<String>(), false);
                String translatedName = xlator.change (v);  // I think the goal here is to get the value of the parameter.
                params.put (xyceParamName, translatedName);
            }
        }
        return params;
    }

    public static boolean isXyceDevice (EquationSet s)
    {
        return s.metadata.containsKey (DEVICE_TAG);
    }

    public boolean isNodeVariable (Variable v)
    {
        return varnames.contains (v);
    }

    public boolean isInternalVariable (Variable v)
    {
        return ivars.containsKey (v);
    }

    public static boolean ignoreEquation (EquationEntry eq)
    {
        // These equations don't have to be translated like others;
        // device already implements them or doesn't need them.
        // Parameter and input equations DO have to be processed/understood elsewhere
        if (eq.metadata != null)
        {
            for (String metaKey : eq.metadata.keySet ())
            {
                if (metaKey.equals (IGNORE_TAG)  ||  metaKey.equals (NODE_TAG)  ||  metaKey.equals (IVAR_TAG))
                {
                    return true;
                }
            }
        }
        return false;
    }

    // Xyceism for tracing internal variables
    public String getTracer (Variable v, Instance pi)
    {
        if (ivars.containsKey (v))
        {
            // example n(y%synapse%syn_w), where 'w' is the varname passed in
            return "n(y%" + device.getDeviceTypeName () + "%" + pi.equations.name + "-" + pi.hashCode () + "_" + ivars.get (v) + ")";
        }
        if (varnames.contains (v))
        {
            return Xyceisms.referenceStateVar (v.name, pi.hashCode ());
        }
        XyceBackendData bed = (XyceBackendData) eqSet.backendData;
        XyceRenderer xlator = new XyceRenderer (bed, pi, null, false);
        return "{" + xlator.change (v.name) + "}";
    }
}
